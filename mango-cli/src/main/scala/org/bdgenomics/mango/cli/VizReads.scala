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

import htsjdk.samtools.reference.FastaSequenceIndex
import htsjdk.samtools.reference.IndexedFastaSequenceFile
import htsjdk.samtools.reference.ReferenceSequence
import htsjdk.samtools.{ SAMRecord, SAMReadGroupRecord, SamReader, SamReaderFactory }
import java.io.File
import org.apache.hadoop.mapreduce.Job
import org.apache.spark.{ Logging, SparkContext }
import org.apache.spark.SparkContext._
import org.bdgenomics.adam.models.VariantContext
import org.apache.parquet.filter2.predicate.FilterPredicate
import org.apache.parquet.filter2.dsl.Dsl._
import org.apache.spark.rdd.RDD
import org.bdgenomics.utils.cli._
import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.adam.models.ReferencePosition
import org.bdgenomics.adam.projections.{ Projection, VariantField, AlignmentRecordField, GenotypeField, NucleotideContigFragmentField, FeatureField }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.mango.layout._
import org.bdgenomics.formats.avro.{ AlignmentRecord, Feature, Fragment, Genotype, GenotypeAllele, NucleotideContigFragment }
import org.bdgenomics.utils.instrumentation.Metrics
import org.fusesource.scalate.TemplateEngine
import org.json4s._
import org.kohsuke.args4j.{ Argument, Option => Args4jOption }
import net.liftweb.json.Serialization.write
import org.scalatra.ScalatraServlet
import scala.reflect.ClassTag
import scala.collection.mutable.ListBuffer

import org.bdgenomics.mango.models.LazyMaterialization
import com.github.akmorrow13.intervaltree._
import edu.berkeley.cs.amplab.spark.intervalrdd._

// TODO: remove
import org.bdgenomics.adam.rdd.read.realignment._

object VizTimers extends Metrics {
  //HTTP requests
  val ReadsRequest = timer("GET reads")
  val FreqRequest = timer("GET frequency")
  val VarRequest = timer("GET variants")
  val FeatRequest = timer("GET features")
  val RefRequest = timer("GET reference")

  //RDD operations
  var LoadParquetFile = timer("Loading from Parquet")
  val ReadsRDDTimer = timer("RDD Reads operations")
  val FreqRDDTimer = timer("RDD Freq operations")
  val VarRDDTimer = timer("RDD Var operations")
  val FeatRDDTimer = timer("RDD Feat operations")
  val RefRDDTimer = timer("RDD Ref operations")
  val GetPartChunkTimer = timer("Calculate block chunk")

  //Generating Json
  val MakingTrack = timer("Making Track")
  val DoingCollect = timer("Doing Collect")
  val PrintTrackJsonTimer = timer("JSON printTrackJson")
  val PrintJsonFreqTimer = timer("JSON printJsonFreq")
  val PrintVariationJsonTimer = timer("JSON printVariationJson")
  val PrintFeatureJsonTimer = timer("JSON printFeatureJson")
  val PrintReferenceTimer = timer("JSON get reference string")
}

object VizReads extends BDGCommandCompanion with Logging {

  var readsRDD: RDD[(ReferenceRegion, AlignmentRecord)] = null
  var testRDD: RDD[(String, AlignmentRecord)] = null
  var testRDD2: RDD[Genotype] = null

  val commandName: String = "viz"
  val commandDescription: String = "Genomic visualization for ADAM"

  var sc: SparkContext = null
  var faWithIndex: Option[IndexedFastaSequenceFile] = None
  var referencePath: String = ""
  var refName: String = ""
  var partitionCount: Int = 0
  var readsPath1: String = ""
  var samp1Name: String = ""
  var readsPath2: String = ""
  var samp2Name: String = ""
  var readsExist: Boolean = false
  var variantsPath: String = ""
  var variantsExist: Boolean = false
  var featuresPath: String = ""
  var featuresExist: Boolean = false
  var readsData: LazyMaterialization[AlignmentRecord] = null
  var variantData: LazyMaterialization[Genotype] = null
  var server: org.eclipse.jetty.server.Server = null
  def apply(cmdLine: Array[String]): BDGCommand = {
    new VizReads(Args4j[VizReadsArgs](cmdLine))
  }

  def printReferenceJson(region: ReferenceRegion): List[ReferenceJson] = VizTimers.PrintReferenceTimer.time {
    val splitReference: Array[String] = getReference(region).split("")
    var tracks = new scala.collection.mutable.ListBuffer[ReferenceJson]
    var positionCount: Long = region.start
    for (base <- splitReference) {
      tracks += new ReferenceJson(base.toUpperCase(), positionCount)
      positionCount += 1
    }
    tracks.toList
  }

  def getReference(region: ReferenceRegion): String = {
    if (VizReads.referencePath.endsWith(".adam")) {
      //val pred: FilterPredicate = ((LongColumn("fragmentStartPosition") >= region.start) && (LongColumn("fragmentStartPosition") <= region.end))
      //val referenceRDD: RDD[Fragment] = VizReads.sc.loadParquetFragments(VizReads.referencePath, predicate = Some(pred))
      //referenceRDD.adamGetReferenceString(region)
    } else if (VizReads.referencePath.endsWith(".fa") || VizReads.referencePath.endsWith(".fasta") || VizReads.referencePath.endsWith(".adam")) {
      val idx = new File(VizReads.referencePath + ".fai")
      if (idx.exists() && !idx.isDirectory()) {
        VizReads.faWithIndex match {
          case Some(_) => {
            val bases = VizReads.faWithIndex.get.getSubsequenceAt(region.referenceName, region.start, region.end).getBases
            return new String(bases)
          }
          case None => {
            val faidx: FastaSequenceIndex = new FastaSequenceIndex(new File(VizReads.referencePath + ".fai"))
            VizReads.faWithIndex = Some(new IndexedFastaSequenceFile(new File(VizReads.referencePath), faidx))
            val bases = VizReads.faWithIndex.get.getSubsequenceAt(region.referenceName, region.start, region.end).getBases
            return new String(bases)
          }
        }
      } else {
        log.warn("reference file type ", VizReads.referencePath, " not supported")
      }
    }
    null
  }

  //Correctly shuts down the server
  def quit() {
    val thread = new Thread {
      override def run {
        try {
          log.info("Shutting down the server")
          println("Shutting down the server")
          server.stop();
          log.info("Server has stopped")
          println("Server has stopped")
        } catch {
          case e: Exception => {
            log.info("Error when stopping Jetty server: " + e.getMessage(), e)
            println("Error when stopping Jetty server: " + e.getMessage(), e)
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

  @Argument(required = true, metaVar = "ref_name", usage = "The name of the reference we're looking at", index = 1)
  var refName: String = null

  @Argument(required = false, metaVar = "part_count", usage = "The number of partitions", index = 2)
  var partitionCount: Int = 0

  @Args4jOption(required = false, name = "-read_file1", usage = "The first reads file to view")
  var readsPath1: String = null

  @Args4jOption(required = false, name = "-read_file2", usage = "The second reads file to view")
  var readsPath2: String = null

  @Args4jOption(required = false, name = "-var_file", usage = "The variants file to view")
  var variantsPath: String = null

  @Args4jOption(required = false, name = "-feat_file", usage = "The feature file to view")
  var featuresPath: String = null

  @Args4jOption(required = false, name = "-port", usage = "The port to bind to for visualization. The default is 8080.")
  var port: Int = 8080
}

class VizServlet extends ScalatraServlet {
  implicit val formats = net.liftweb.json.DefaultFormats
  var viewRegion = ReferenceRegion(VizReads.refName, 0, 100)

  get("/?") {
    redirect("/overall")
  }

  get("/quit") {
    VizReads.quit()
  }

  get("/reads") {
    contentType = "text/html"
    val templateEngine = new TemplateEngine
    if (VizReads.readsExist) {
      templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/reads.ssp",
        Map("viewRegion" -> (viewRegion.referenceName, viewRegion.start.toString, viewRegion.end.toString),
          "samples" -> (VizReads.samp1Name, VizReads.samp2Name)))
    } else {
      templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/noreads.ssp")
    }
  }

  get("/reads/:ref") {
    VizTimers.ReadsRequest.time {
      contentType = "json"
      viewRegion = new ReferenceRegion(params("ref").toString, params("start").toLong, params("end").toLong)
      val region = new ReferenceRegion(params("ref").toString, params("start").toLong, params("end").toLong)
      val sampleIds: List[String] = params("sample").split(",").toList
      val data: RDD[(ReferenceRegion, AlignmentRecord)] = VizReads.readsData.multiget(viewRegion, sampleIds).toRDD
      val reference = VizReads.getReference(viewRegion)
      val alignmentData = AlignmentRecordLayout(data, reference, region, sampleIds)

      val fileMap = VizReads.readsData.getFileMap()
      var retJson = ""

      for (sampleData <- alignmentData) {
        val sample = sampleData.sample
        retJson += "\"" + sample + "\":" +
          "{ \"filename\": " + write(fileMap(sample)) +
          ", \"tracks\": " + write(sampleData.records) +
          ", \"indels\": " + write(sampleData.mismatches.filter(_.op != "M")) +
          ", \"mismatches\": " + write(sampleData.mismatches.filter(_.op == "M")) +
          ", \"matePairs\": " + write(sampleData.matePairs) + "},"
      }
      retJson = retJson.dropRight(1)
      retJson = "{" + retJson + "}"
      retJson
    }
  }

  get("/overall") {
    contentType = "text/html"
    val templateEngine = new TemplateEngine
    templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/overall.ssp",
      Map("viewRegion" -> (viewRegion.referenceName, viewRegion.start.toString, viewRegion.end.toString),
        "samples" -> (VizReads.samp1Name, VizReads.samp2Name),
        "readsExist" -> VizReads.readsExist,
        "variantsExist" -> VizReads.variantsExist,
        "featuresExist" -> VizReads.featuresExist))
  }

  get("/freq") {
    contentType = "text/html"
    val templateEngine = new TemplateEngine
    if (VizReads.readsExist) {
      templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/freq.ssp",
        Map("viewRegion" -> (viewRegion.referenceName, viewRegion.start.toString, viewRegion.end.toString),
          "samples" -> (VizReads.samp1Name, VizReads.samp2Name)))
    } else {
      templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/nofreq.ssp")
    }
  }

  get("/freq/:ref") {
    VizTimers.FreqRequest.time {
      contentType = "json"
      val region = ReferenceRegion(params("ref"), params("start").toLong, params("end").toLong)
      val sampleIds: List[String] = params("sample").split(",").toList
      val data: RDD[AlignmentRecord] = VizReads.readsData.multiget(viewRegion, sampleIds).toRDD.map(r => r._2)
      val freqData = data.mapPartitions(FrequencyLayout(_, region)).collect

      var retJson = ""
      for (sample <- sampleIds) {
        val sampleData = freqData.filter(_._1 == sample).map(r => FreqJson(r._2, r._3))
        retJson += "\"" + sample + "\":" +
          write(sampleData) + ","
      }
      retJson = retJson.dropRight(1)
      retJson = "{" + retJson + "}"
      retJson
    }
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
      val variantRDD: RDD[(ReferenceRegion, Genotype)] = VizReads.variantData.get(viewRegion, "callset1").toRDD
      val variationJson = VariantLayout(variantRDD)
      val retJson =
        "\"variants\": " + write(variationJson.variants) +
          ", \"frequencies\": " + write(variationJson.freq)
      val ret = "{" + retJson + "}"
      ret
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
    viewRegion = ReferenceRegion(params("ref"), params("start").toLong, params("end").toLong)
    write(VizReads.printReferenceJson(viewRegion))
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

    VizReads.refName = args.refName

    val readsPath1 = Option(args.readsPath1)
    readsPath1 match {
      case Some(_) => {
        VizReads.readsPath1 = args.readsPath1
        VizReads.readsExist = true
        if (args.readsPath1.endsWith(".bam") || args.readsPath1.endsWith(".sam")) {
          val srf: SamReaderFactory = SamReaderFactory.make()
          val samReader: SamReader = srf.open(new File(args.readsPath1))
          val rec: SAMRecord = samReader.iterator().next()
          VizReads.samp1Name = rec.getReadGroup().getSample()
          VizReads.readsData.loadSample(VizReads.samp1Name, args.readsPath1)
        } else if (args.readsPath1.endsWith(".adam")) {
          VizReads.samp1Name = VizReads.sc.loadParquetAlignments(args.readsPath1).first.recordGroupSample
          VizReads.readsData.loadSample(VizReads.samp1Name, args.readsPath1)
        } else {
          log.info("WARNING: Invalid input for reads file")
          println("WARNING: Invalid input for reads file")
        }
      }
      case None => {
        log.info("WARNING: No reads file provided")
        println("WARNING: No reads file provided")
      }
    }

    val readsPath2 = Option(args.readsPath2)
    readsPath2 match {
      case Some(_) => {
        VizReads.readsPath2 = args.readsPath2
        VizReads.readsExist = true
        if (args.readsPath2.endsWith(".bam") || args.readsPath2.endsWith(".sam")) {
          val srf: SamReaderFactory = SamReaderFactory.make()
          val samReader: SamReader = srf.open(new File(args.readsPath2))
          val rec: SAMRecord = samReader.iterator().next()
          VizReads.samp2Name = rec.getReadGroup().getSample()
          VizReads.readsData.loadSample(VizReads.samp2Name, args.readsPath2)
        } else if (args.readsPath2.endsWith(".adam")) {
          VizReads.samp2Name = VizReads.sc.loadParquetAlignments(args.readsPath2).first.recordGroupSample
          VizReads.readsData.loadSample(VizReads.samp2Name, args.readsPath2)
        } else {
          log.info("WARNING: Invalid input for second reads file")
          println("WARNING: Invalid input for second reads file")
        }
      }
      case None => {
        log.info("WARNING: No second reads file provided")
        println("WARNING: No second reads file provided")
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
    println("Frequency visualization at: /freq")
    println("Overlapping reads visualization at: /reads")
    println("Variant visualization at: /variants")
    println("Feature visualization at: /features")
    println("Overall visualization at: /overall")
    println("Quit at: /quit")
    VizReads.server.join()
  }
}
