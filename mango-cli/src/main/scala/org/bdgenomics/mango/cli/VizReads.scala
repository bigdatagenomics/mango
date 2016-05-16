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

import htsjdk.samtools.reference.IndexedFastaSequenceFile
import htsjdk.samtools.{ SAMRecord, SamReader, SamReaderFactory }
import net.liftweb.json.Serialization.write
import org.apache.parquet.filter2.dsl.Dsl._
import org.apache.parquet.filter2.predicate.FilterPredicate
import org.apache.spark.rdd.RDD
import org.apache.spark.{ Logging, SparkContext }
import org.bdgenomics.adam.models.{ ReferenceRegion, SequenceDictionary }
import org.bdgenomics.adam.projections.{ FeatureField, Projection }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.formats.avro.{ Feature, Genotype }
import org.bdgenomics.mango.RDD.ReferenceRDD
import org.bdgenomics.mango.core.util.{ ResourceUtils, VizUtils }
import org.bdgenomics.mango.filters.AlignmentRecordFilter
import org.bdgenomics.mango.layout._
import org.bdgenomics.mango.models.{ AlignmentRecordMaterialization, GenotypeMaterialization }
import org.bdgenomics.utils.cli._
import org.bdgenomics.utils.instrumentation.Metrics
import org.fusesource.scalate.TemplateEngine
import org.kohsuke.args4j.{ Argument, Option => Args4jOption }
import org.scalatra._

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
  implicit val formats = net.liftweb.json.DefaultFormats

  var sc: SparkContext = null
  var faWithIndex: Option[IndexedFastaSequenceFile] = None
  var partitionCount: Int = 0
  var referencePath: String = ""
  var readsPaths: List[String] = null
  var sampNames: Option[List[String]] = None
  var readsExist: Boolean = false
  var variantsPaths: List[String] = null
  var variantsExist: Boolean = false
  var featuresPath: String = ""
  var featuresExist: Boolean = false
  var globalDict: SequenceDictionary = null
  var refRDD: ReferenceRDD = null
  var readsData: AlignmentRecordMaterialization = null
  var variantData: GenotypeMaterialization = null
  var server: org.eclipse.jetty.server.Server = null
  var screenSize: Int = 1000

  // HTTP ERROR RESPONSES
  object errors {
    var outOfBounds = NotFound("Region Out of Bounds")
    var largeRegion = RequestEntityTooLarge("Region too large")
  }

  def apply(cmdLine: Array[String]): BDGCommand = {
    new VizReads(Args4j[VizReadsArgs](cmdLine))
  }

  def printReferenceJson(region: ReferenceRegion): ActionResult = VizTimers.PrintReferenceTimer.time {

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
        Ok(write(tracks.toList))
      } case None => {
        VizReads.errors.outOfBounds
      }
    }
  }

  /**
   * Returns stringified version of sequence dictionary
   *
   * @param dict: dictionary to format to a string
   * @return List of sequence dictionary strings of form referenceName:0-referenceName.length
   */
  def formatDictionaryOpts(dict: SequenceDictionary): String = {
    dict.records.map(r => r.name + ":0-" + r.length).mkString(",")
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

  get("/reads/:ref") {
    VizTimers.AlignmentRequest.time {
      val viewRegion = ReferenceRegion(params("ref"), params("start").toLong, params("end").toLong)
      contentType = "json"
      val dictOpt = VizReads.globalDict(viewRegion.referenceName)
      dictOpt match {
        case Some(_) => {
          if (viewRegion.end > dictOpt.get.length) {
            VizReads.errors.outOfBounds
          }
          val end: Long = VizUtils.getEnd(viewRegion.end, VizReads.globalDict(viewRegion.referenceName))
          val sampleIds: List[String] = params("sample").split(",").toList
          val readQuality = params.getOrElse("quality", "0")
          val dataOption = VizReads.readsData.multiget(viewRegion, sampleIds)
          dataOption match {
            case Some(_) => {
              val jsonData: Map[String, SampleTrack] = AlignmentRecordLayout(dataOption.get.toRDD().collect, sampleIds)
              var readRetJson: String = ""
              for (sample <- sampleIds) {
                val sampleData = jsonData.get(sample)
                sampleData match {
                  case Some(_) =>
                    readRetJson += "\"" + sample + "\":" +
                      "{ \"tracks\": " + write(sampleData.get.records) +
                      ", \"indels\": " + write(sampleData.get.mismatches.filter(_.op != "M")) +
                      ", \"mismatches\": " + write(sampleData.get.mismatches.filter(_.op == "M")) +
                      ", \"matePairs\": " + write(sampleData.get.matePairs) + "},"
                  case None =>
                    readRetJson += "\"" + sample + "\""
                }

              }
              readRetJson = readRetJson.dropRight(1)
              readRetJson = "{" + readRetJson + "}"
              Ok(readRetJson)
            }
            case None => {
              write("")
            }
          }
        }
        case None => VizReads.errors.outOfBounds
      }
    }
  }

  get("/freq/:ref") {
    VizTimers.AlignmentRequest.time {
      val viewRegion = ReferenceRegion(params("ref"), params("start").toLong, params("end").toLong)
      contentType = "json"
      val dictOpt = VizReads.globalDict(viewRegion.referenceName)
      dictOpt match {
        case Some(_) => {
          if (viewRegion.end > dictOpt.get.length) {
            write("")
          }
          val end: Long = VizUtils.getEnd(viewRegion.end, VizReads.globalDict(viewRegion.referenceName))
          val region = new ReferenceRegion(params("ref").toString, params("start").toLong, end)
          val sampleIds: List[String] = params("sample").split(",").toList
          Ok(write(VizReads.readsData.getFrequency(region, sampleIds)))
        } case None => VizReads.errors.outOfBounds
      }
    }
  }

  get("/mergedReads/:ref") {
    VizTimers.AlignmentRequest.time {
      val viewRegion = ReferenceRegion(params("ref"), params("start").toLong, params("end").toLong)

      contentType = "json"
      val dictOpt = VizReads.globalDict(viewRegion.referenceName)
      dictOpt match {
        case Some(_) => {
          if (viewRegion.end > dictOpt.get.length) {
            write("")
          }
          val end: Long = VizUtils.getEnd(viewRegion.end, VizReads.globalDict(viewRegion.referenceName))
          val region = new ReferenceRegion(params("ref").toString, params("start").toLong, end)
          val sampleIds: List[String] = params("sample").split(",").toList
          val readQuality = params.getOrElse("quality", "0")
          val diffReads = params.getOrElse("diff", "0") != "0"
          val dataOption = VizReads.readsData.multiget(viewRegion, sampleIds)
          dataOption match {
            case Some(_) => {
              val filteredData: RDD[(ReferenceRegion, CalculatedAlignmentRecord)] =
                AlignmentRecordFilter.filterByRecordQuality(dataOption.get.toRDD(), readQuality)
              val binSize = VizUtils.getBinSize(region, VizReads.screenSize)
              var alignmentData: Map[String, List[MutationCount]] = MergedAlignmentRecordLayout(filteredData, binSize)

              val fileMap = VizReads.readsData.getFileMap
              var readRetJson: String = ""
              if (diffReads) {
                alignmentData = MergedAlignmentRecordLayout.diffRecords(sampleIds, alignmentData)
              }
              for (sample <- sampleIds) {
                val sampleData = alignmentData.get(sample)
                sampleData match {
                  case Some(_) =>
                    readRetJson += "\"" + sample + "\":" +
                      "{ \"filename\": " + write(fileMap(sample)) +
                      ", \"indels\": " + write(sampleData.get.filter(_.op != "M")) +
                      ", \"mismatches\": " + write(sampleData.get.filter(_.op == "M")) +
                      ", \"binSize\": " + binSize + "},"
                  case None =>
                    readRetJson += "\"" + sample + "\":" +
                      "{ \"filename\": " + write(fileMap(sample)) + "},"
                }

              }
              readRetJson = readRetJson.dropRight(1)
              readRetJson = "{" + readRetJson + "}"
              readRetJson
            } case None => {
              write("")
            }
          }
        } case None => VizReads.errors.outOfBounds
      }
    }
  }

  get("/overall") {
    contentType = "text/html"
    val templateEngine = new TemplateEngine
    templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/overall.ssp",
      Map("dictionary" -> VizReads.formatDictionaryOpts(VizReads.globalDict),
        "readsSamples" -> VizReads.sampNames,
        "readsExist" -> VizReads.readsExist,
        "variantsExist" -> VizReads.variantsExist,
        "featuresExist" -> VizReads.featuresExist))
  }

  get("/variants") {
    contentType = "text/html"
    if (!session.contains("ref")) {
      session("ref") = "chr"
      session("start") = "1"
      session("end") = "100"
    }
    val globalViewRegion: ReferenceRegion =
      ReferenceRegion(session("ref").toString, session("start").toString.toLong, session("end").toString.toLong)
    val templateEngine = new TemplateEngine
    templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/variants.ssp",
      Map("viewRegion" -> (globalViewRegion.referenceName, globalViewRegion.start.toString, globalViewRegion.end.toString)))

  }

  get("/viewregion/:ref") {
    contentType = "json"
    session("ref") = params("ref")
    session("start") = params("start")
    session("end") = VizUtils.getEnd(params("end").toLong, VizReads.globalDict(params("ref").toString)).toString
  }

  get("/variants/:ref") {
    VizTimers.VarRequest.time {
      contentType = "json"
      val viewRegion = ReferenceRegion(params("ref"), params("start").toLong,
        VizUtils.getEnd(params("end").toLong, VizReads.globalDict(params("ref").toString)))
      val variantRDDOption = VizReads.variantData.multiget(viewRegion, VizReads.variantsPaths)
      variantRDDOption match {
        case Some(_) => {
          val variantRDD: RDD[(ReferenceRegion, Genotype)] = variantRDDOption.get.toRDD()
          write(VariantLayout(variantRDD))
        } case None => {
          write("")
        }
      }

    }
  }

  get("/variantfreq/:ref") {
    VizTimers.VarFreqRequest.time {
      contentType = "json"
      val viewRegion = ReferenceRegion(params("ref"), params("start").toLong,
        VizUtils.getEnd(params("end").toLong, VizReads.globalDict(params("ref").toString)))
      val variantRDDOption = VizReads.variantData.multiget(viewRegion, VizReads.variantsPaths)
      variantRDDOption match {
        case Some(_) => {
          val variantRDD: RDD[(ReferenceRegion, Genotype)] = variantRDDOption.get.toRDD()
          write(VariantFreqLayout(variantRDD))
        } case None => {
          write("")
        }
      }
    }
  }

  get("/features/:ref") {
    val viewRegion = ReferenceRegion(params("ref"), params("start").toLong,
      VizUtils.getEnd(params("end").toLong, VizReads.globalDict(params("ref"))))
    VizTimers.FeatRequest.time {
      val featureRDD: Option[RDD[Feature]] = {
        if (VizReads.featuresPath.endsWith(".adam")) {
          val pred: FilterPredicate = (LongColumn("end") >= viewRegion.start) && (LongColumn("start") <= viewRegion.end)
          val proj = Projection(FeatureField.contig, FeatureField.featureId, FeatureField.featureType, FeatureField.start, FeatureField.end)
          Option(VizReads.sc.loadParquetFeatures(VizReads.featuresPath, predicate = Some(pred), projection = Some(proj)))
        } else if (VizReads.featuresPath.endsWith(".bed")) {
          Option(VizReads.sc.loadFeatures(VizReads.featuresPath).filterByOverlappingRegion(viewRegion))
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
    val viewRegion = ReferenceRegion(params("ref"), params("start").toLong,
      VizUtils.getEnd(params("end").toLong, VizReads.globalDict(params("ref"))))
    if (viewRegion.end - viewRegion.start > 2000)
      VizReads.errors.largeRegion
    else VizReads.printReferenceJson(viewRegion)
  }
}

class VizReads(protected val args: VizReadsArgs) extends BDGSparkCommand[VizReadsArgs] with Logging {
  val companion: BDGCommandCompanion = VizReads

  override def run(sc: SparkContext): Unit = {
    VizReads.sc = sc

    VizReads.partitionCount =
      if (args.partitionCount <= 0)
        VizReads.sc.defaultParallelism
      else
        args.partitionCount

    // initialize all datasets
    initReference
    initAlignments
    initVariants
    initFeatures

    // run preprocessing if preprocessing file was provided
    preprocess

    // start server
    startServer

    /*
     * Initialize required reference file
     */
    def initReference = {
      val referencePath = Option(args.referencePath)

      VizReads.referencePath =
        referencePath match {
          case Some(_) => referencePath.get
          // TODO: throw exception
        }

      VizReads.refRDD = new ReferenceRDD(sc, VizReads.referencePath)
      VizReads.globalDict = VizReads.refRDD.getSequenceDictionary
    }

    /*
     * Initialize loaded alignment files
     */
    def initAlignments = {
      val readsPaths = Option(args.readsPaths)
      if (readsPaths.isDefined) {
        VizReads.readsPaths = args.readsPaths.split(",").toList
        VizReads.readsExist = true
        VizReads.readsData = AlignmentRecordMaterialization(sc, VizReads.globalDict, VizReads.partitionCount, VizReads.refRDD)
        var sampNamesBuffer = new scala.collection.mutable.ListBuffer[String]
        for (readsPath <- VizReads.readsPaths) {
          if (ResourceUtils.isLocal(readsPath, sc)) {
            if (readsPath.endsWith(".bam") || readsPath.endsWith(".sam")) {
              val srf: SamReaderFactory = SamReaderFactory.make()
              val samReader: SamReader = srf.open(new File(readsPath))
              val rec: SAMRecord = samReader.iterator().next()
              val sample = rec.getReadGroup.getSample
              sampNamesBuffer += sample
              VizReads.readsData.loadSample(readsPath, Option(sample))
            } else if (readsPath.endsWith(".adam")) {
              sampNamesBuffer += VizReads.readsData.loadADAMSample(readsPath)
            } else {
              log.info("WARNING: Invalid input for reads file on local fs")
            }
          } else {
            if (readsPath.endsWith(".adam")) {
              sampNamesBuffer += VizReads.readsData.loadADAMSample(readsPath)
            } else {
              log.info("WARNING: Invalid input for reads file on remote fs")
            }
          }
        }
        VizReads.sampNames = Option(sampNamesBuffer.toList)
      }
    }

    /*
     * Initialize loaded variant files
     */
    def initVariants = {
      VizReads.variantData = GenotypeMaterialization(sc, VizReads.globalDict, VizReads.partitionCount)
      val variantsPath = Option(args.variantsPaths)
      variantsPath match {
        case Some(_) => {
          VizReads.variantsPaths = args.variantsPaths.split(",").toList
          VizReads.variantsExist = true
          for (varPath <- VizReads.variantsPaths) {
            if (varPath.endsWith(".vcf")) {
              VizReads.variantData.loadSample(varPath)
            } else if (varPath.endsWith(".adam")) {
              VizReads.variantData.loadSample(varPath)
            } else {
              log.info("WARNING: Invalid input for variants file")
            }
          }
        }
        case None => {
          log.info("WARNING: No variants file provided")
        }
      }

    }

    /*
     * Initialize loaded feature files
     */
    def initFeatures = {
      val featuresPath = Option(args.featuresPath)
      featuresPath match {
        case Some(_) => {
          if (args.featuresPath.endsWith(".bed") || args.featuresPath.endsWith(".adam")) {
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
    }

    /*
     * Preloads data specified in optional text file int the format Name, Start, End where Name is
     * the chromosomal location, start is start position and end is end position
     */
    def preprocess = {
      val path = Option(args.preprocessPath)
      if (path.isDefined && VizReads.sampNames.isDefined) {
        val file = new File(path.get)
        if (file.exists()) {
          val lines = scala.io.Source.fromFile(path.get).getLines
          lines.foreach(r => {
            val line = r.split(",")
            try {
              val region = ReferenceRegion(line(0), line(1).toLong, line(2).toLong)
              val rdd = VizReads.readsData.multiget(region,
                VizReads.sampNames.get)
              val recordCount = rdd match {
                case Some(_) => rdd.get.count
                case None    => 0
              }
              log.info("records preprocessed: ", region, recordCount)
            } catch {
              case e: Exception => log.warn("preprocessing file requires format referenceName,start,end")
            }
          })
        }
      }
    }

    /*
     * Starts server once on startup
     */
    def startServer = {
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
}

case class Record(name: String, length: Long)

