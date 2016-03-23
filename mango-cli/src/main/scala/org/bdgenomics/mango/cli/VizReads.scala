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
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel
import org.apache.spark.{ Logging, SparkContext }
import org.bdgenomics.adam.models.{ ReferenceRegion, SequenceDictionary, SequenceRecord }
import org.bdgenomics.adam.projections.{ FeatureField, Projection }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.formats.avro.{ AlignmentRecord, Feature, Genotype, NucleotideContigFragment }
import org.bdgenomics.mango.filters.AlignmentRecordFilter
import org.bdgenomics.mango.layout._
import org.bdgenomics.mango.models.LazyMaterialization
import org.bdgenomics.utils.cli._
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
  var referencePath: String = ""
  var partitionCount: Int = 0
  var readsPaths: List[String] = null
  var sampNames: List[String] = null
  var readsExist: Boolean = false
  var variantsPath: String = ""
  var variantsExist: Boolean = false
  var featuresPath: String = ""
  var featuresExist: Boolean = false
  var globalDict: SequenceDictionary = null
  var refRDD: RDD[NucleotideContigFragment] = null
  var readsData: LazyMaterialization[AlignmentRecord] = null
  var variantData: LazyMaterialization[Genotype] = null
  var server: org.eclipse.jetty.server.Server = null
  var screenSize: Int = 1

  def apply(cmdLine: Array[String]): BDGCommand = {
    new VizReads(Args4j[VizReadsArgs](cmdLine))
  }

  /**
   * Prints java heap map availability and usage
   */
  def printSysUsage() = {
    val mb: Int = 1024 * 1024
    //Getting the runtime reference from system
    val runtime: Runtime = Runtime.getRuntime

    println("##### Heap utilization statistics [MB] #####")

    //Print used memory
    println("Used Memory:"
      + (runtime.totalMemory() - runtime.freeMemory()) / mb)

    //Print free memory
    println("Free Memory:" + runtime.freeMemory() / mb)

    //Print total available memory
    println("Total Memory:" + runtime.totalMemory() / mb)

    //Print Maximum available memory
    println("Max Memory:" + runtime.maxMemory() / mb)
  }

  def setSequenceDictionary(filePath: String) = {
    if (LazyMaterialization.isLocal(filePath, sc)) {
      if (filePath.endsWith(".fa") || filePath.endsWith(".fasta")) {
        val fseq: FastaSequenceFile = new FastaSequenceFile(new File(filePath), true) //truncateNamesAtWhitespace
        val extension: String = if (filePath.endsWith(".fa")) ".fa" else ".fasta"
        val dictFile: File = new File(filePath.replace(extension, ".dict"))
        require(dictFile.exists, "Generated sequence dictionary does not exist, use Picard to generate")
        globalDict = SequenceDictionary(fseq.getSequenceDictionary())
      } else { //ADAM LOCAL
        globalDict = sc.adamDictionaryLoad[NucleotideContigFragment](filePath)
      }
    } else { //REMOTE
      globalDict = sc.adamDictionaryLoad[NucleotideContigFragment](filePath)
    }
  }

  def printReferenceJson(region: ReferenceRegion): List[ReferenceJson] = VizTimers.PrintReferenceTimer.time {
    val splitReferenceOpt: Option[String] = getReference(region)
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
   * Returns the bin size for calculation based on the client's screen
   * size
   * @param region Region to bin over
   * @return Bin size to calculate over
   */
  def getBinSize(region: ReferenceRegion): Int = {
    val binSize = ((region.end - region.start) / VizReads.screenSize).toInt
    Math.max(1, binSize)
  }

  /**
   * Returns reference region string that is padded to encompass all reads for
   * mismatch calculation
   *
   * @param region: ReferenceRegion to be viewed
   * @return Option of Padded Reference
   */
  def getPaddedReference(region: ReferenceRegion): (ReferenceRegion, Option[String]) = {
    val padding = 200
    val start = Math.max(0, region.start - padding)
    val end = VizReads.getEnd(region.end, VizReads.globalDict(region.referenceName))
    val paddedRegion = ReferenceRegion(region.referenceName, start, end)
    val reference = getReference(paddedRegion)
    (paddedRegion, reference)
  }

  /**
   * Returns reference from reference RDD working set
   *
   * @param region: ReferenceRegion to be viewed
   * @return Option of Padded Reference
   */
  def getReference(region: ReferenceRegion): Option[String] = {
    val seqRecord = VizReads.globalDict(region.referenceName)
    seqRecord match {
      case Some(_) => {
        val end: Long = VizReads.getEnd(region.end, seqRecord)
        val newRegion = ReferenceRegion(region.referenceName, region.start, end)
        Option(refRDD.adamGetReferenceString(region).toUpperCase)
      } case None => {
        None
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

  /**
   * Returns the very last base in a given chromosome that
   * can be viewed relative to the reference
   *
   * @param end: end of referenceregion that has been queried
   * @param rec: Option of sequence record that is being viewed
   * @return Last valid base in region being viewed
   */
  def getEnd(end: Long, rec: Option[SequenceRecord]): Long = {
    rec match {
      case Some(_) => Math.min(end, rec.get.length)
      case None    => end
    }
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

  @Args4jOption(required = false, name = "-repartition", usage = "The number of partitions")
  var partitionCount: Int = 0

  @Args4jOption(required = false, name = "-read_files", usage = "A list of reads files to view, separated by commas (,)")
  var readsPaths: String = null

  @Args4jOption(required = false, name = "-var_file", usage = "The variants file to view")
  var variantsPath: String = null

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
      val end = VizReads.getEnd(params("end").toLong, VizReads.globalDict(params("ref").toString))
      val viewRegion = ReferenceRegion(params("ref"), params("start").toLong, end)
      contentType = "json"
      val dictOpt = VizReads.globalDict(viewRegion.referenceName)
      dictOpt match {
        case Some(_) => {
          val end: Long = VizReads.getEnd(viewRegion.end, VizReads.globalDict(viewRegion.referenceName))
          val region = new ReferenceRegion(params("ref").toString, params("start").toLong, end)
          val sampleIds: List[String] = params("sample").split(",").toList
          val readQuality = params.getOrElse("quality", "0")

          val dataOption = VizReads.readsData.multiget(viewRegion, sampleIds)
          dataOption match {
            case Some(_) => {
              val data: RDD[(ReferenceRegion, AlignmentRecord)] = dataOption.get.toRDD()
              val filteredData = AlignmentRecordFilter.filterByRecordQuality(data, readQuality)
              val (paddedRegion, reference) = VizReads.getPaddedReference(region)

              val alignmentData: Map[String, SampleTrack] = AlignmentRecordLayout(filteredData, reference, paddedRegion, sampleIds)
              val fileMap = VizReads.readsData.getFileMap
              var readRetJson: String = ""
              for (sample <- sampleIds) {
                val sampleData = alignmentData.get(sample)
                val dictionary = VizReads.formatDictionaryOpts(VizReads.globalDict)
                sampleData match {
                  case Some(_) =>
                    readRetJson += "\"" + sample + "\":" +
                      "{ \"tracks\": " + write(sampleData.get.records) +
                      ", \"indels\": " + write(sampleData.get.mismatches.filter(_.op != "M")) +
                      ", \"mismatches\": " + write(sampleData.get.mismatches.filter(_.op == "M")) +
                      ", \"matePairs\": " + write(sampleData.get.matePairs) + "},"
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
        } case None => write("")
      }
    }
  }

  get("/mergedReads/:ref") {
    VizTimers.AlignmentRequest.time {
      val viewRegion = ReferenceRegion(params("ref"), params("start").toLong,
        VizReads.getEnd(params("end").toLong, VizReads.globalDict(params("ref").toString)))
      contentType = "json"
      val dictOpt = VizReads.globalDict(viewRegion.referenceName)
      dictOpt match {
        case Some(_) => {
          val end: Long = VizReads.getEnd(viewRegion.end, VizReads.globalDict(viewRegion.referenceName))
          val region = new ReferenceRegion(params("ref").toString, params("start").toLong, end)
          val sampleIds: List[String] = params("sample").split(",").toList
          val readQuality = params.getOrElse("quality", "0")

          val dataOption = VizReads.readsData.multiget(viewRegion, sampleIds)
          dataOption match {
            case Some(_) => {
              val data: RDD[(ReferenceRegion, AlignmentRecord)] = dataOption.get.toRDD()
              val filteredData = AlignmentRecordFilter.filterByRecordQuality(data, readQuality)
              val (paddedRegion, reference) = VizReads.getPaddedReference(region)
              val binSize = VizReads.getBinSize(region)
              val alignmentData: Map[String, List[MutationCount]] = MergedAlignmentRecordLayout(filteredData, reference, paddedRegion, sampleIds, VizReads.getBinSize(region))
              val freqData: Map[String, List[FreqJson]] = FrequencyLayout(filteredData.map(_._2), region, binSize, sampleIds)
              val fileMap = VizReads.readsData.getFileMap
              var readRetJson: String = ""
              for (sample <- sampleIds) {
                val sampleData = alignmentData.get(sample)
                sampleData match {
                  case Some(_) =>
                    readRetJson += "\"" + sample + "\":" +
                      "{ \"filename\": " + write(fileMap(sample)) +
                      ", \"indels\": " + write(sampleData.get.filter(_.op != "M")) +
                      ", \"mismatches\": " + write(sampleData.get.filter(_.op == "M")) +
                      ", \"binSize\": " + binSize +
                      ", \"freq\": " + write(freqData.get(sample)) + "},"
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
        } case None => write("")
      }
    }
  }

  get("/overall") {
    contentType = "text/html"
    if (!session.contains("ref")) {
      session("ref") = "chr"
      session("start") = "1"
      session("end") = "100"
    }
    val globalViewRegion: ReferenceRegion =
      ReferenceRegion(session("ref").toString, session("start").toString.toLong, session("end").toString.toLong)
    val templateEngine = new TemplateEngine
    templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/overall.ssp",
      Map("viewRegion" -> (globalViewRegion.referenceName, globalViewRegion.start.toString, globalViewRegion.end.toString),
        "samples" -> VizReads.sampNames.mkString(","),
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
    if (VizReads.variantsExist) {
      templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/variants.ssp",
        Map("viewRegion" -> (globalViewRegion.referenceName, globalViewRegion.start.toString, globalViewRegion.end.toString)))
    } else {
      templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/novariants.ssp")
    }
  }

  get("/viewregion/:ref") {
    contentType = "json"
    session("ref") = params("ref")
    session("start") = params("start")
    session("end") = VizReads.getEnd(params("end").toLong, VizReads.globalDict(params("ref").toString)).toString
  }

  get("/variants/:ref") {
    VizTimers.VarRequest.time {
      contentType = "json"
      val viewRegion = ReferenceRegion(params("ref"), params("start").toLong,
        VizReads.getEnd(params("end").toLong, VizReads.globalDict(params("ref").toString)))
      val variantRDDOption = VizReads.variantData.get(viewRegion, "callset1")
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
        VizReads.getEnd(params("end").toLong, VizReads.globalDict(params("ref").toString)))
      val variantRDDOption = VizReads.variantData.get(viewRegion, "callset1")
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

  get("/features") {
    contentType = "text/html"
    val templateEngine = new TemplateEngine
    if (!session.contains("ref")) {
      session("ref") = "chr"
      session("start") = "1"
      session("end") = "100"
    }
    val globalViewRegion: ReferenceRegion =
      ReferenceRegion(session("ref").toString, session("start").toString.toInt, session("end").toString.toInt)
    if (VizReads.featuresExist) {
      templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/features.ssp",
        Map("viewRegion" -> (globalViewRegion.referenceName, globalViewRegion.start.toString, globalViewRegion.end.toString)))
    } else {
      templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/nofeatures.ssp")
    }
  }

  get("/features/:ref") {
    val viewRegion = ReferenceRegion(params("ref"), params("start").toLong,
      VizReads.getEnd(params("end").toLong, VizReads.globalDict(params("ref"))))
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
      VizReads.getEnd(params("end").toLong, VizReads.globalDict(params("ref"))))
    write(VizReads.printReferenceJson(viewRegion))
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

    if (args.referencePath.endsWith(".fa") || args.referencePath.endsWith(".fasta") || args.referencePath.endsWith(".adam")) {
      VizReads.referencePath = args.referencePath
      VizReads.setSequenceDictionary(args.referencePath)
      VizReads.refRDD = VizReads.sc.loadSequence(VizReads.referencePath)
      VizReads.refRDD.persist(StorageLevel.MEMORY_AND_DISK)
    } else {
      log.info("WARNING: Invalid reference file")
      println("WARNING: Invalid reference file")
    }

    VizReads.readsData = LazyMaterialization(sc, VizReads.globalDict, VizReads.partitionCount)
    val readsPaths = Option(args.readsPaths)
    readsPaths match {
      case Some(_) => {
        VizReads.readsPaths = args.readsPaths.split(",").toList
        VizReads.readsExist = true
        var sampNamesBuffer = new scala.collection.mutable.ListBuffer[String]
        for (readsPath <- VizReads.readsPaths) {
          if (LazyMaterialization.isLocal(readsPath, sc)) {
            if (readsPath.endsWith(".bam") || readsPath.endsWith(".sam")) {
              val srf: SamReaderFactory = SamReaderFactory.make()
              val samReader: SamReader = srf.open(new File(readsPath))
              val rec: SAMRecord = samReader.iterator().next()
              val sample = rec.getReadGroup.getSample
              sampNamesBuffer += sample
              VizReads.readsData.loadSample(sample, readsPath)
            } else if (readsPath.endsWith(".adam")) {
              sampNamesBuffer += VizReads.readsData.loadADAMSample(readsPath)
            } else {
              log.info("WARNING: Invalid input for reads file on local fs")
              println("WARNING: Invalid input for reads file on local fs")
            }
          } else {
            if (readsPath.endsWith(".adam")) {
              sampNamesBuffer += VizReads.readsData.loadADAMSample(readsPath)
            } else {
              log.info("WARNING: Invalid input for reads file on remote fs")
              println("WARNING: Invalid input for reads file on remote fs")
            }
          }
        }
        VizReads.sampNames = sampNamesBuffer.toList
      } case None => {
        log.info("WARNING: No reads file provided")
        println("WARNING: No reads file provided")
      }
    }

    VizReads.variantData = LazyMaterialization(sc, VizReads.globalDict, VizReads.partitionCount)
    val variantsPath = Option(args.variantsPath)
    variantsPath match {
      case Some(_) => {
        if (args.variantsPath.endsWith(".vcf")) {
          VizReads.variantsPath = args.variantsPath
          // TODO: remove hardcode for callset1
          VizReads.variantData.loadSample("callset1", VizReads.variantsPath)
          VizReads.variantsExist = true
        } else if (args.variantsPath.endsWith(".adam")) {
          VizReads.readsData.loadADAMSample(VizReads.variantsPath)
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
