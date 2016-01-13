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
import org.bdgenomics.mango.models._
import org.bdgenomics.formats.avro.{ AlignmentRecord, Feature, Fragment, Genotype, GenotypeAllele, NucleotideContigFragment }
import org.bdgenomics.utils.instrumentation.Metrics
import org.fusesource.scalate.TemplateEngine
import org.json4s._
import org.kohsuke.args4j.{ Argument, Option => Args4jOption }
import net.liftweb.json.Serialization.write
import org.scalatra.ScalatraServlet
import scala.reflect.ClassTag
import scala.collection.mutable.ListBuffer

import edu.berkeley.cs.amplab.lazymango.LazyMaterialization
import com.github.akmorrow13.intervaltree._
import edu.berkeley.cs.amplab.spark.intervalrdd._

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
  var readsPath1: String = ""
  var samp1Name: String = ""
  var readsPath2: String = ""
  var samp2Name: String = ""
  var readsExist: Boolean = false
  var variantsPath: String = ""
  var variantsExist: Boolean = false
  var featuresPath: String = ""
  var featuresExist: Boolean = false
  var readsData: LazyMaterialization[AlignmentRecord] = null //TODO: make this generic
  var variantData: LazyMaterialization[Genotype] = null //TODO: make this generic
  var server: org.eclipse.jetty.server.Server = null
  def apply(cmdLine: Array[String]): BDGCommand = {
    new VizReads(Args4j[VizReadsArgs](cmdLine))
  }

  //Prepares reads information in json format
  def printTrackJson(layout: OrderedTrackedLayout[AlignmentRecord]): List[TrackJson] = VizTimers.PrintTrackJsonTimer.time {
    var tracks = new scala.collection.mutable.ListBuffer[TrackJson]
    for (rec <- layout.trackAssignments) {
      val aRec = rec._1._2.asInstanceOf[AlignmentRecord]
      // +1 due to null at beginning of reference
      tracks += new TrackJson(aRec.getReadName, aRec.getStart + 1, aRec.getEnd, aRec.getReadNegativeStrand, aRec.getSequence, aRec.getCigar, rec._2)
    }
    tracks.toList
  }

  def printStringJson(str: String): String = {
    return str
  }

  //Prepares reads information in json format
  def printMatePairJson(layout: OrderedTrackedLayout[AlignmentRecord]): List[MatePairJson] = VizTimers.PrintTrackJsonTimer.time {
    var matePairs = new scala.collection.mutable.ListBuffer[MatePairJson]
    for (track <- layout.trackBuilder) {
      for (matePair <- track.matePairs) {
        matePairs += new MatePairJson(matePair.start, matePair.end, track.idx)
      }
    }
    matePairs.toList
  }

  //Prepares frequency information in Json format
  def printJsonFreq(array: Array[AlignmentRecord], region: ReferenceRegion): List[FreqJson] = VizTimers.PrintJsonFreqTimer.time {
    val freqMap = new java.util.TreeMap[Long, Long]
    var i = region.start.toInt
    while (i <= region.end.toInt) {
      val currSubset = array.filter(value => ((value.getStart <= i) && (value.getEnd >= i)))
      freqMap.put(i, currSubset.length)
      val regionSize = region.end.toInt - region.start.toInt
      val scale = (regionSize / 500) + 1
      i = i + scale
    }

    // convert to list of FreqJsons
    var freqBuffer = new ListBuffer[FreqJson]
    val iter = freqMap.keySet.iterator
    var key = 0L
    while (iter.hasNext) {
      key = iter.next()
      freqBuffer += FreqJson(key, freqMap(key))
    }
    freqBuffer.toList
  }

  //Prepares variant frequency information in Json format
  def printVariationJson(layout: OrderedTrackedLayout[Genotype]): List[VariationJson] = VizTimers.PrintVariationJsonTimer.time {
    var tracks = new ListBuffer[VariationJson]
    for (rec <- layout.trackAssignments) {
      val vRec = rec._1._2.asInstanceOf[Genotype]
      tracks += new VariationJson(vRec.getVariant.getContig.getContigName, vRec.getAlleles.mkString(" / "), vRec.getVariant.getStart, vRec.getVariant.getEnd, rec._2)
    }
    tracks.toList
  }

  //Prepares variants information in Json format
  def printVariationFreqJson(variantFreq: scala.collection.immutable.Map[ReferenceRegion, Long]): List[VariationFreqJson] = VizTimers.PrintVariationJsonTimer.time {
    var tracks = new ListBuffer[VariationFreqJson]
    for (rec <- variantFreq) {
      tracks += VariationFreqJson(rec._1.referenceName, rec._1.start, rec._1.end, rec._2)
    }
    tracks.toList
  }

  //Prepares features information in Json format
  def printFeatureJson(layout: OrderedTrackedLayout[Feature]): List[FeatureJson] = VizTimers.PrintFeatureJsonTimer.time {
    var tracks = new ListBuffer[FeatureJson]
    for (rec <- layout.trackAssignments) {
      val fRec = rec._1._2.asInstanceOf[Feature]
      tracks += new FeatureJson(fRec.getFeatureId, fRec.getFeatureType, fRec.getStart, fRec.getEnd, rec._2)
    }
    tracks.toList
  }

  //Prepares reference information in Json format
  def printReferenceJson(rdd: RDD[Fragment], region: ReferenceRegion): List[ReferenceJson] = VizTimers.PrintReferenceTimer.time {
    // val referenceString: String = rdd.adamGetReferenceString(region)
    // val splitReference: Array[String] = referenceString.split("")
    // var tracks = new scala.collection.mutable.ListBuffer[ReferenceJson]
    // var positionCount: Long = region.start
    // for (base <- splitReference) {
    //   tracks += new ReferenceJson(base.toUpperCase(), positionCount)
    //   positionCount += 1
    // }
    // tracks.toList
    null
  }

  def printIndexedReferenceJson(ifsq: IndexedFastaSequenceFile, region: ReferenceRegion): List[ReferenceJson] = VizTimers.PrintReferenceTimer.time {
    val refSeq = ifsq.getSubsequenceAt(region.referenceName, region.start, region.end)
    val referenceString = new String(refSeq.getBases())
    val splitReference: Array[String] = referenceString.split("")
    var tracks = new scala.collection.mutable.ListBuffer[ReferenceJson]
    var positionCount: Long = region.start
    for (base <- splitReference) {
      tracks += new ReferenceJson(base.toUpperCase(), positionCount)
      positionCount += 1
    }
    tracks.toList
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

case class TrackJson(readName: String, start: Long, end: Long, readNegativeStrand: Boolean, sequence: String, cigar: String, track: Long)
case class MatePairJson(start: Long, end: Long, track: Long)
case class VariationJson(contigName: String, alleles: String, start: Long, end: Long, track: Long)
case class SampleVariationJson(sampleId: String, variationJson: VariationJson)
case class VariationFreqJson(contigName: String, start: Long, end: Long, count: Long)
case class FreqJson(base: Long, freq: Long)
case class FeatureJson(featureId: String, featureType: String, start: Long, end: Long, track: Long)
case class ReferenceJson(reference: String, position: Long)

class VizReadsArgs extends Args4jBase with ParquetArgs {
  @Argument(required = true, metaVar = "reference", usage = "The reference file to view, required", index = 0)
  var referencePath: String = null

  @Argument(required = true, metaVar = "ref_name", usage = "The name of the reference we're looking at", index = 1)
  var refName: String = null

  @Args4jOption(required = false, name = "-sample1", usage = "The name of the first sample")
  var samp1Name: String = null

  @Args4jOption(required = false, name = "-read_file1", usage = "The first reads file to view")
  var readsPath1: String = null

  @Args4jOption(required = false, name = "-sample2", usage = "The name of the second sample")
  var samp2Name: String = null

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
      viewRegion = ReferenceRegion(params("ref"), params("start").toLong, params("end").toLong)
      val sampleIds: List[String] = params("sample").split(",").toList
      val input: Map[String, Array[AlignmentRecord]] = VizReads.readsData.multiget(viewRegion, sampleIds)
      val fileMap = VizReads.readsData.getFileMap()
      val withRefReg: List[(String, Array[(ReferenceRegion, AlignmentRecord)])] = input.toList.map(elem => (elem._1, elem._2.map(t => (ReferenceRegion(t), t))))
      var retJson = ""
      for (elem <- withRefReg) {
        val filteredLayout = new OrderedTrackedLayout(elem._2)
        retJson += "\"" + elem._1 + "\":" +
          "{ \"filename\": " + write(VizReads.printStringJson(fileMap(elem._1))) +
          ", \"tracks\": " + write(VizReads.printTrackJson(filteredLayout)) +
          ", \"matePairs\": " + write(VizReads.printMatePairJson(filteredLayout)) + "},"
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
      viewRegion = ReferenceRegion(params("ref"), params("start").toLong, params("end").toLong)
      val sampleIds: List[String] = params("sample").split(",").toList
      val input: Map[String, Array[AlignmentRecord]] = VizReads.readsData.multiget(viewRegion, sampleIds)

      var retJson = ""
      for (elem <- input) {
        retJson += "\"" + elem._1 + "\":" +
          write(VizReads.printJsonFreq(elem._2, viewRegion)) + ","
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

      val input: List[Genotype] = VizReads.variantData.get(viewRegion, "callset1")
      val trackinput: RDD[(ReferenceRegion, Genotype)] = VizReads.sc.parallelize(input).keyBy(v => ReferenceRegion(ReferencePosition(v)))

      val filteredGenotypeTrack = new OrderedTrackedLayout(trackinput.collect())

      val variantFreq = trackinput.countByKey
      var tracks = new ListBuffer[VariationFreqJson]
      for (rec <- variantFreq) {
        tracks += VariationFreqJson(rec._1.referenceName, rec._1.start, rec._1.end, rec._2)
      }

      val retJson =
        "\"variants\": " + write(VizReads.printVariationJson(filteredGenotypeTrack)) +
          ", \"frequencies\": " + write(tracks.toList)
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
      viewRegion = ReferenceRegion(params("ref"), params("start").toLong, params("end").toLong)
      if (VizReads.featuresPath.endsWith(".adam")) {
        val pred: FilterPredicate = ((LongColumn("end") >= viewRegion.start) && (LongColumn("start") <= viewRegion.end))
        val proj = Projection(FeatureField.contig, FeatureField.featureId, FeatureField.featureType, FeatureField.start, FeatureField.end)
        val featureRDD: RDD[Feature] = VizReads.sc.loadParquetFeatures(VizReads.featuresPath, predicate = Some(pred), projection = Some(proj))
        val trackinput: RDD[(ReferenceRegion, Feature)] = featureRDD.keyBy(ReferenceRegion(_))
        val filteredFeatureTrack = new OrderedTrackedLayout(trackinput.collect())
        write(VizReads.printFeatureJson(filteredFeatureTrack))
      } else if (VizReads.featuresPath.endsWith(".bed")) {
        val featureRDD: RDD[Feature] = VizReads.sc.loadFeatures(VizReads.featuresPath).filterByOverlappingRegion(viewRegion)
        val trackinput: RDD[(ReferenceRegion, Feature)] = featureRDD.keyBy(ReferenceRegion(_))
        val filteredFeatureTrack = new OrderedTrackedLayout(trackinput.collect())
        write(VizReads.printFeatureJson(filteredFeatureTrack))
      }
    }
  }

  get("/reference/:ref") {
    VizTimers.RefRequest.time {
      viewRegion = ReferenceRegion(params("ref"), params("start").toLong, params("end").toLong)
      if (VizReads.referencePath.endsWith(".adam")) {
        val pred: FilterPredicate = ((LongColumn("fragmentStartPosition") >= viewRegion.start) && (LongColumn("fragmentStartPosition") <= viewRegion.end))
        val referenceRDD: RDD[Fragment] = VizReads.sc.loadParquetFragments(VizReads.referencePath, predicate = Some(pred))
        write(VizReads.printReferenceJson(referenceRDD, viewRegion))
      } else if (VizReads.referencePath.endsWith(".fa") || VizReads.referencePath.endsWith(".fasta") || VizReads.referencePath.endsWith(".adam")) {
        val idx = new File(VizReads.referencePath + ".fai")
        if (idx.exists() && !idx.isDirectory()) {
          VizReads.faWithIndex match {
            case Some(_) => write(VizReads.printIndexedReferenceJson(VizReads.faWithIndex.get, viewRegion))
            case None => {
              val faidx: FastaSequenceIndex = new FastaSequenceIndex(new File(VizReads.referencePath + ".fai"))
              VizReads.faWithIndex = Some(new IndexedFastaSequenceFile(new File(VizReads.referencePath), faidx))
              write(VizReads.printIndexedReferenceJson(VizReads.faWithIndex.get, viewRegion))
            }
          }
        } else {
          // TODO: not supported
          // val referenceRDD: RDD[NucleotideContigFragment] = VizReads.sc.loadSequence(VizReads.referencePath)
          // write(VizReads.printReferenceJson(referenceRDD, viewRegion))
        }
      }
    }
  }

}

class VizReads(protected val args: VizReadsArgs) extends BDGSparkCommand[VizReadsArgs] with Logging {
  val companion: BDGCommandCompanion = VizReads

  override def run(sc: SparkContext): Unit = {
    VizReads.sc = sc
    VizReads.readsData = LazyMaterialization(sc)
    VizReads.variantData = LazyMaterialization(sc)

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
        if (args.readsPath1.endsWith(".bam") || args.readsPath1.endsWith(".sam") || args.readsPath1.endsWith(".adam")) {
          VizReads.readsPath1 = args.readsPath1
          VizReads.samp1Name = args.samp1Name
          VizReads.readsExist = true
          VizReads.readsData.loadSample(args.samp1Name, args.readsPath1)
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
        if (args.readsPath2.endsWith(".bam") || args.readsPath2.endsWith(".sam") || args.readsPath2.endsWith(".adam")) {
          VizReads.readsPath2 = args.readsPath2
          VizReads.samp2Name = args.samp2Name
          VizReads.readsExist = true
          VizReads.readsData.loadSample(args.samp2Name, args.readsPath2)
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
