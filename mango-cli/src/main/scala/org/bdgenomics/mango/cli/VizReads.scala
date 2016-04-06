/**
 * Licensed to Big Data Genomics (BDG) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The BDG licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.bdgenomics.mango.cli

import java.io.File

import htsjdk.samtools.reference.{ FastaSequenceFile, IndexedFastaSequenceFile }
import htsjdk.samtools.{ SAMRecord, SamReader, SamReaderFactory }
import net.liftweb.json.Serialization.write
import org.apache.parquet.filter2.dsl.Dsl._
import org.apache.parquet.filter2.predicate.FilterPredicate
import org.apache.spark.{ Logging, SparkContext }
import org.apache.spark.rdd.RDD

import org.bdgenomics.mango.core.util.{ ResourceUtils, VizUtils }
import org.bdgenomics.mango.filters.AlignmentRecordFilter
import org.bdgenomics.mango.filters.VariantRecordFilter
import org.bdgenomics.utils.cli._
import org.bdgenomics.adam.models.{ SequenceDictionary, SequenceRecord, ReferenceRegion }
import org.bdgenomics.adam.projections.{ Projection, FeatureField }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.formats.avro.{ NucleotideContigFragment, AlignmentRecord, Feature, Genotype }
import org.bdgenomics.mango.layout._
import org.bdgenomics.mango.models.{ ReferenceRDD, GenotypeMaterialization, AlignmentRecordMaterialization, LazyMaterialization }

import org.bdgenomics.utils.instrumentation.Metrics
import org.fusesource.scalate.TemplateEngine
import org.kohsuke.args4j.{ Argument, Option => Args4jOption }
import org.scalatra.ScalatraServlet

object VizTimers extends Metrics {
  //HTTP requests
  val ReadsRequest = timer("GET reads")
  val MergedReadsRequest = timer("GET merged reads")
  val FreqRequest = timer("GET frequency")
  val VarRequest = timer("GET variants")
  val VarFreqRequest = timer("Get variant frequency")
  val FeatRequest = timer("GET features")
  val AlignmentRequest = timer("GET alignment")

  //RDD operations
  val FreqRDDTimer = timer("RDD Freq operations")
  val VarRDDTimer = timer("RDD Var operations")
  val FeatRDDTimer = timer("RDD Feat operations")
  val RefRDDTimer = timer("RDD Ref operations")
  val GetPartChunkTimer = timer("Calculate block chunk")

  //Generating Json
  val MakingTrack = timer("Making Track")
  val DoingCollect = timer("Doing Collect")
  val PrintReferenceTimer = timer("JSON get reference string")
}

object VizReads extends BDGCommandCompanion with Logging {


  val commandName: String = "viz"
  val commandDescription: String = "Genomic visualization for ADAM"

  var sc: SparkContext = null
  var faWithIndex: Option[IndexedFastaSequenceFile] = None
  var partitionCount: Int = 0
  var referencePath: String = ""
  var readsPaths: List[String] = null
  var sampNames: List[String] = null
  var readsExist: Boolean = false
  var variantsPath: List[String] = null
  var variantsExist: Boolean = false
  var featuresPath: String = ""
  var featuresExist: Boolean = false
  var globalDict: SequenceDictionary = null
  var refRDD: ReferenceRDD = null
  var readsData: AlignmentRecordMaterialization = null
  var variantData: GenotypeMaterialization = null
  var server: org.eclipse.jetty.server.Server = null
  var screenSize: Int = 1

  def apply(cmdLine: Array[String]): BDGCommand = {
    new VizReads(Args4j[VizReadsArgs](cmdLine))
  }

  def printReferenceJson(region: ReferenceRegion): List[ReferenceJson] = VizTimers.PrintReferenceTimer.time {
    val splitReferenceOpt: Option[String] = refRDD.getReference(region)
    splitReferenceOpt match {
      case Some(_) => {
        val splitReference = splitReferenceOpt.get.split("")
        var tracks = new scala.collection.mutable.ListBuffer[ReferenceJson]
        var positionCount: Long = region.start
        for (base <- splitReference) {
          tracks += new ReferenceJson(base.toUpperCase, positionCount)
          positionCount += 1
        }
        tracks.toList
      } case None => {
        List()
      }
    }

  }

  /**
   * Returns stringified version of sequence dictionary
   *
   * @param dict: dictionary to format to a string
   * @return List of sequence dictionary strings of form referenceName:0-referenceName.length
   */
  def formatDictionaryOpts(dict: SequenceDictionary): List[String] = {
    dict.records.map(r => r.name + ":0-" + r.length).toList
  }

  //Correctly shuts down the server
  def quit() {
    val thread = new Thread {
      override def run() {
        try {
          log.info("Shutting down the server")
          println("Shutting down the server")
          server.stop()
          log.info("Server has stopped")
          println("Server has stopped")
        } catch {
          case e: Exception => {
            log.info("Error when stopping Jetty server: " + e.getMessage, e)
            println("Error when stopping Jetty server: " + e.getMessage, e)
          }
        }
      }
    }
    thread.start()
  }

}

case class ReferenceJson(reference: String, position: Long)

class VizReadsArgs extends Args4jBase with ParquetArgs {
  @Argument(required = true, metaVar = "reference", usage = "The reference file to view, required", index = 0)
  var referencePath: String = null

  @Args4jOption(required = false, name = "-preprocess_path", usage = "Path to file containing reference regions to be preprocessed")
  var preprocessPath: String = null

  @Args4jOption(required = false, name = "-repartition", usage = "The number of partitions")
  var partitionCount: Int = 0

  @Args4jOption(required = false, name = "-read_files", usage = "A list of reads files to view, separated by commas (,)")
  var readsPaths: String = null

  @Args4jOption(required = false, name = "-var_files", usage = "A list of variants files to view, separated by commas (,)")
  var variantsPaths: String = null

  @Args4jOption(required = false, name = "-feat_file", usage = "The feature file to view")
  var featuresPath: String = null

  @Args4jOption(required = false, name = "-port", usage = "The port to bind to for visualization. The default is 8080.")
  var port: Int = 8080
}

class VizServlet extends ScalatraServlet {
  implicit val formats = net.liftweb.json.DefaultFormats

  get("/?") {
    redirect("/overall")
  }

  get("/quit") {
    VizReads.quit()
  }

  get("/init/:pixels") {
    VizReads.screenSize = params("pixels").toInt
    write(VizReads.formatDictionaryOpts(VizReads.globalDict))
   }

  get("/reads/:ref") {
    VizTimers.AlignmentRequest.time {
      contentType = "json"
      val dictOpt = VizReads.readsData.dict(viewRegion.referenceName)
      dictOpt match {
        case Some(_) => {
          val end: Long = Math.min(viewRegion.end, VizReads.readsData.dict(viewRegion.referenceName).get.length)
          val region = new ReferenceRegion(params("ref").toString, params("start").toLong, end)
          val sampleIds: List[String] = params("sample").split(",").toList
          val reference = VizReads.getReference(region)
          val readQuality = params.getOrElse("quality", "0")

          val dataOption = VizReads.readsData.multiget(viewRegion, sampleIds)
          dataOption match {
            case Some(_) => {
              val data: RDD[(ReferenceRegion, AlignmentRecord)] = dataOption.get.toRDD
              val filteredData = AlignmentRecordFilter.filterByRecordQuality(data, readQuality)
              val alignmentData: List[ReadJson] = AlignmentRecordLayout(filteredData, reference, region)
              val freqData: Map[String, List[FreqJson]] = FrequencyLayout(filteredData.map(_._2), region, sampleIds)
              val fileMap = VizReads.readsData.getFileMap()
              var readRetJson: String = ""
              for (sample <- sampleIds) {
                val sampleData = alignmentData.get(sample)
                readRetJson += "\"" + sample + "\":" +
                  "{ \"filename\": " + write(fileMap(sample)) +
                  ", \"tracks\": " + write(sampleData.get.records) +
                  ", \"indels\": " + write(sampleData.get.mismatches.filter(_.op != "M")) +
                  ", \"mismatches\": " + write(sampleData.get.mismatches.filter(_.op == "M")) +
                  ", \"matePairs\": " + write(sampleData.get.matePairs) +
                  ", \"freq\": " + write(freqData.get(sample)) + "},"
              }
              readRetJson = readRetJson.dropRight(1)
              readRetJson = "{" + readRetJson + "}"
              readRetJson
            } case None => {
              write("")
            }
          }
        } case None => write("")
      }
    }
  }

  get("/overall") {
    contentType = "text/html"
    val templateEngine = new TemplateEngine
    templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/overall.ssp",
      Map("viewRegion" -> (viewRegion.referenceName, viewRegion.start.toString, viewRegion.end.toString),
        "samples" -> (VizReads.sampNames.mkString(",")),
        "readsExist" -> VizReads.readsExist,
        "variantsExist" -> VizReads.variantsExist,
        "featuresExist" -> VizReads.featuresExist))
  }

  get("/variants") {
    contentType = "text/html"
    val templateEngine = new TemplateEngine
    if (VizReads.variantsExist) {
      templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/variants.ssp",
        Map("viewRegion" -> (viewRegion.referenceName, viewRegion.start.toString, viewRegion.end.toString)))
    } else {
      templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/novariants.ssp")
    }
  }

  get("/variants/:ref") {
    VizTimers.VarRequest.time {
      contentType = "json"
      viewRegion = ReferenceRegion(params("ref"), params("start").toLong, params("end").toLong)
      val genotypeQuality = params("quality").toInt
      val variantRDDOption = VizReads.variantData.get(viewRegion, "callset1")
      variantRDDOption match {
        case Some(_) => {
          val variantRDD: RDD[(ReferenceRegion, Genotype)] = variantRDDOption.get.toRDD
          val filteredRDD = VariantRecordFilter.filterByGenotypeQuality(variantRDD, genotypeQuality)
          write(VariantLayout(filteredRDD))
        } case None => {
          write("")
        }
      }
    }
  }

  get("/variantfreq") {
    contentType = "text/html"
    val templateEngine = new TemplateEngine
    if (VizReads.variantsExist) {
      templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/variants.ssp",
        Map("viewRegion" -> (viewRegion.referenceName, viewRegion.start.toString, viewRegion.end.toString)))
    } else {
      templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/novariants.ssp")
    }
  }

  get("/variantfreq/:ref") {
    VizTimers.VarFreqRequest.time {
      contentType = "json"
      viewRegion = ReferenceRegion(params("ref"), params("start").toLong, params("end").toLong)
      val variantRDDOption = VizReads.variantData.get(viewRegion, "callset1")
      variantRDDOption match {
        case Some(_) => {
          val variantRDD: RDD[(ReferenceRegion, Genotype)] = variantRDDOption.get.toRDD
          write(VariantFreqLayout(variantRDD))
        } case None => {
          write("")
        }
      }
    }
  }

  get("/features") {
    contentType = "text/html"
    val templateEngine = new TemplateEngine
    if (VizReads.featuresExist) {
      templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/features.ssp",
        Map("viewRegion" -> (viewRegion.referenceName, viewRegion.start.toString, viewRegion.end.toString)))
    } else {
      templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/nofeatures.ssp")
    }
  }

  get("/features/:ref") {
    viewRegion = ReferenceRegion(params("ref"), params("start").toLong, params("end").toLong)
    VizTimers.FeatRequest.time {
      val region = ReferenceRegion(params("ref"), params("start").toLong, params("end").toLong)
      val featureRDD: Option[RDD[Feature]] = {
        if (VizReads.featuresPath.endsWith(".adam")) {
          val pred: FilterPredicate = ((LongColumn("end") >= region.start) && (LongColumn("start") <= region.end))
          val proj = Projection(FeatureField.contig, FeatureField.featureId, FeatureField.featureType, FeatureField.start, FeatureField.end)
          Option(VizReads.sc.loadParquetFeatures(VizReads.featuresPath, predicate = Some(pred), projection = Some(proj)))
        } else if (VizReads.featuresPath.endsWith(".bed")) {
          Option(VizReads.sc.loadFeatures(VizReads.featuresPath).filterByOverlappingRegion(region))
        } else {
          None
        }
      }
      featureRDD match {
        case Some(_) => {
          write(FeatureLayout(featureRDD.get))
        } case None => {
          write("")
        }
      }
    }
  }

  get("/reference/:ref") {
    VizTimers.RefRequest.time {
      viewRegion = ReferenceRegion(params("ref"), params("start").toLong, params("end").toLong)
      write(VizReads.printReferenceJson(viewRegion))
    }
  }
}

class VizReads(protected val args: VizReadsArgs) extends BDGSparkCommand[VizReadsArgs] with Logging {
  val companion: BDGCommandCompanion = VizReads

  override def run(sc: SparkContext): Unit = {
    VizReads.sc = sc
    val partitionCount = Option(args.partitionCount)
    partitionCount match {
      case Some(_) => {
        VizReads.partitionCount = partitionCount.get
      } case None => {
        VizReads.partitionCount = 10
      }
    }
    VizReads.readsData = LazyMaterialization(sc, VizReads.partitionCount)
    VizReads.variantData = LazyMaterialization(sc, VizReads.partitionCount)

    if (args.referencePath.endsWith(".fa") || args.referencePath.endsWith(".fasta") || args.referencePath.endsWith(".adam")) {
      VizReads.referencePath = args.referencePath
    } else {
      log.info("WARNING: Invalid reference file")
      println("WARNING: Invalid reference file")
    }

    val readsPaths = Option(args.readsPaths)
    readsPaths match {
      case Some(_) => {
        VizReads.readsPaths = args.readsPaths.split(",").toList
        VizReads.readsExist = true
        var sampNamesBuffer = new scala.collection.mutable.ListBuffer[String]
        for (readsPath <- VizReads.readsPaths) {
          if (readsPath.endsWith(".bam") || readsPath.endsWith(".sam")) {
            val srf: SamReaderFactory = SamReaderFactory.make()
            val samReader: SamReader = srf.open(new File(readsPath))
            val rec: SAMRecord = samReader.iterator().next()
            val sample = rec.getReadGroup().getSample()
            sampNamesBuffer += sample
            VizReads.readsData.loadSample(sample, readsPath)
          } else if (readsPath.endsWith(".adam")) {
            sampNamesBuffer += VizReads.readsData.loadADAMSample(readsPath)
          } else {
            log.info("WARNING: Invalid input for reads file")
            println("WARNING: Invalid input for reads file")
          }
        }
        VizReads.sampNames = sampNamesBuffer.toList
      } case None => {
        log.info("WARNING: No reads file provided")
        println("WARNING: No reads file provided")
      }
    }

    val variantsPath = Option(args.variantsPath)
    variantsPath match {
      case Some(_) => {
        if (args.variantsPath.endsWith(".vcf") || args.variantsPath.endsWith(".adam")) {
          VizReads.variantsPath = args.variantsPath
          // TODO: remove hardcode for callset1
          VizReads.variantData.loadSample("callset1", VizReads.variantsPath)
          VizReads.variantsExist = true
          VizReads.testRDD2 = VizReads.sc.loadParquetGenotypes(args.variantsPath)
          VizReads.testRDD2.cache()
        } else {
          log.info("WARNING: Invalid input for variants file")
          println("WARNING: Invalid input for variants file")
        }
      }
      case None => {
        log.info("WARNING: No variants file provided")
        println("WARNING: No variants file provided")
      }
    }

    val featuresPath = Option(args.featuresPath)
    featuresPath match {
      case Some(_) => {
        if (args.featuresPath.endsWith(".bed") || args.variantsPath.endsWith(".adam")) {
          VizReads.featuresPath = args.featuresPath
          VizReads.featuresExist = true
        } else {
          log.info("WARNING: Invalid input for features file")
          println("WARNING: Invalid input for features file")
        }
      }
      case None => {
        log.info("WARNING: No features file provided")
        println("WARNING: No features file provided")
      }
    }

    VizReads.server = new org.eclipse.jetty.server.Server(args.port)
    val handlers = new org.eclipse.jetty.server.handler.ContextHandlerCollection()
    VizReads.server.setHandler(handlers)
    handlers.addHandler(new org.eclipse.jetty.webapp.WebAppContext("mango-cli/src/main/webapp", "/"))
    VizReads.server.start()
    println("View the visualization at: " + args.port)
    println("Variant visualization at: /variants")
    println("Overall visualization at: /overall")
    println("Quit at: /quit")
    VizReads.server.join()
  }
}
