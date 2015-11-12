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
import org.bdgenomics.adam.models.VariantContext
import org.apache.parquet.filter2.predicate.FilterPredicate
import org.apache.parquet.filter2.dsl.Dsl._
import org.apache.spark.rdd.RDD
import org.apache.spark.rdd.MetricsContext._
import org.bdgenomics.utils.cli._
import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.adam.models.ReferencePosition
import org.bdgenomics.adam.projections.{ Projection, VariantField, AlignmentRecordField, GenotypeField, NucleotideContigFragmentField, FeatureField }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.mango.models._
import org.bdgenomics.formats.avro.{ AlignmentRecord, Feature, Genotype, GenotypeAllele, NucleotideContigFragment }
import org.bdgenomics.utils.instrumentation.Metrics
import org.fusesource.scalate.TemplateEngine
import org.json4s._
import org.kohsuke.args4j.{ Argument, Option => Args4jOption }
import net.liftweb.json.Serialization.write
import org.scalatra.ScalatraServlet
import scala.reflect.ClassTag

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

  // var partTree = new IntervalTree[Long]()
  val blockSize = 1000
  // TODO: insert preprocessed data here
  var readsRDD: RDD[(ReferenceRegion, AlignmentRecord)] = null

  val commandName: String = "viz"
  val commandDescription: String = "Genomic visualization for ADAM"

  var sc: SparkContext = null
  var faWithIndex: Option[IndexedFastaSequenceFile] = None
  var referencePath: String = ""
  var refName: String = ""
  var readsPath: String = ""
  var readsExist: Boolean = false
  var variantsPath: String = ""
  var variantsExist: Boolean = false
  var featuresPath: String = ""
  var featuresExist: Boolean = false
  var lazyMatVR: LazyMaterialization = null
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
    var freqBuffer = new scala.collection.mutable.ListBuffer[FreqJson]
    val iter = freqMap.keySet.iterator
    var key = 0L
    while (iter.hasNext) {
      key = iter.next()
      freqBuffer += FreqJson(key, freqMap(key))
    }
    freqBuffer.toList
  }

  //Prepares variants information in Json format
  def printVariationJson(layout: OrderedTrackedLayout[Genotype]): List[VariationJson] = VizTimers.PrintVariationJsonTimer.time {
    var tracks = new scala.collection.mutable.ListBuffer[VariationJson]
    log.info("Number of trackAssignments in printVariationJson is: " + layout.trackAssignments.size)
    for (rec <- layout.trackAssignments) {
      val vRec = rec._1._2.asInstanceOf[Genotype]
      tracks += new VariationJson(vRec.getVariant.getContig.getContigName, vRec.getAlleles.mkString(" / "), vRec.getVariant.getStart, vRec.getVariant.getEnd, rec._2)
    }
    tracks.toList
  }

  //Prepares features information in Json format
  def printFeatureJson(layout: OrderedTrackedLayout[Feature]): List[FeatureJson] = VizTimers.PrintFeatureJsonTimer.time {
    var tracks = new scala.collection.mutable.ListBuffer[FeatureJson]
    for (rec <- layout.trackAssignments) {
      val fRec = rec._1._2.asInstanceOf[Feature]
      tracks += new FeatureJson(fRec.getFeatureId, fRec.getFeatureType, fRec.getStart, fRec.getEnd, rec._2)
    }
    tracks.toList
  }

  //Prepares reference information in Json format
  def printReferenceJson(rdd: RDD[NucleotideContigFragment], region: ReferenceRegion): List[ReferenceJson] = VizTimers.PrintReferenceTimer.time {
    val referenceString: String = rdd.adamGetReferenceString(region)
    val splitReference: Array[String] = referenceString.split("")
    var tracks = new scala.collection.mutable.ListBuffer[ReferenceJson]
    var positionCount: Long = region.start
    for (base <- splitReference) {
      tracks += new ReferenceJson(base.toUpperCase(), positionCount)
      positionCount += 1
    }
    tracks.toList
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
case class FreqJson(base: Long, freq: Long)
case class FeatureJson(featureId: String, featureType: String, start: Long, end: Long, track: Long)
case class ReferenceJson(reference: String, position: Long)

class VizReadsArgs extends Args4jBase with ParquetArgs {
  @Argument(required = true, metaVar = "reference", usage = "The reference file to view, required", index = 0)
  var referencePath: String = null

  @Argument(required = true, metaVar = "ref_name", usage = "The name of the reference we're looking at", index = 1)
  var refName: String = null

  @Args4jOption(required = false, name = "-read_file", usage = "The reads file to view")
  var readsPath: String = null

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
        Map("viewRegion" -> (viewRegion.referenceName, viewRegion.start.toString, viewRegion.end.toString)))
    } else {
      templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/noreads.ssp")
    }
  }

  get("/reads/:ref") {
    VizTimers.ReadsRequest.time {

      contentType = "json"
      viewRegion = ReferenceRegion(params("ref"), params("start").toLong, params("end").toLong)
      println("view region")
      println(viewRegion)
      if (VizReads.lazyMatVR == null) {
        VizReads.lazyMatVR = LazyMaterialization(VizReads.readsPath, VizReads.sc)
      }
      if (VizReads.readsPath.endsWith(".adam")) {
        val pred: FilterPredicate = ((LongColumn("end") >= viewRegion.start) && (LongColumn("start") <= viewRegion.end))
        val proj = Projection(AlignmentRecordField.contig, AlignmentRecordField.readName, AlignmentRecordField.start, AlignmentRecordField.end, AlignmentRecordField.sequence, AlignmentRecordField.cigar, AlignmentRecordField.readNegativeStrand, AlignmentRecordField.readPaired)
        val readsRDD: RDD[AlignmentRecord] = VizTimers.LoadParquetFile.time {
          VizReads.sc.loadParquetAlignments(VizReads.readsPath, predicate = Some(pred), projection = Some(proj))
        }

        //TODO: somehow save the chromosome of the current interval (currently viewRegion.refName, make sure it's chrM and not M or settle on standard notation)
        //TODO: the person1 flag is just tempoary
        //get(chr: String, i: Interval[Long], k: String)
        // println(lazyMat.get("chrM", new Interval(viewRegion.start, viewRegion.end), "person1"))

        //TODO: make this take in the input of LazyMaterialization.get, which is Option[Map[Interval[Long], List[(String, AlignmentRecord)]]]

        // val trackinput: RDD[(ReferenceRegion, AlignmentRecord)] = readsRDD.keyBy(ReferenceRegion(_))
        // val collected: Array[(ReferenceRegion, AlignmentRecord)] = VizTimers.DoingCollect.time {

        // //TODO: make this take in a map
        // val filteredLayout = VizTimers.MakingTrack.time {
        //   new OrderedTrackedLayout(collected)
        // }
        // println(filteredLayout)
        // val json = "{ \"tracks\": " + write(VizReads.printTrackJson(filteredLayout)) + ", \"matePairs\": " + write(VizReads.printMatePairJson(filteredLayout)) + "}"
        // json
      } else if (VizReads.readsPath.endsWith(".sam") || VizReads.readsPath.endsWith(".bam")) {
        val idxFile: File = new File(VizReads.readsPath + ".bai")
        if (!idxFile.exists()) {
          val readsRDD: RDD[AlignmentRecord] = VizReads.sc.loadBam(VizReads.readsPath).filterByOverlappingRegion(viewRegion)
          val trackinput: RDD[(ReferenceRegion, AlignmentRecord)] = readsRDD.keyBy(ReferenceRegion(_))
          val filteredLayout = new OrderedTrackedLayout(trackinput.collect())
          write(VizReads.printTrackJson(filteredLayout))
        } else {
          // 15/10/13 22:28:08 INFO OrderedTrackedLayout: Number of values: 100
          // 15/10/13 22:28:08 INFO OrderedTrackedLayout: Number of tracks: 91
          println("processing bam")
          val getMap = VizReads.lazyMatVR.get(viewRegion, "person1")
          val input: List[Map[ReferenceRegion, List[(String, List[AlignmentRecord])]]] = getMap.toList
          val convertedInput: List[(String, List[AlignmentRecord])] = input.flatMap(elem => elem.flatMap(test => test._2))
          val justAlignments: List[AlignmentRecord] = convertedInput.map(elem => elem._2).flatten
          val correct: List[(ReferenceRegion, AlignmentRecord)] = justAlignments.map(elem => (ReferenceRegion(elem), elem))
          println(correct.size)
          val filteredLayout = new OrderedTrackedLayout(correct)
          val json = "{ \"tracks\": " + write(VizReads.printTrackJson(filteredLayout)) + ", \"matePairs\": " + write(VizReads.printMatePairJson(filteredLayout)) + "}"
          json
          // 15/10/13 22:25:06 INFO OrderedTrackedLayout: Number of values: 1074
          // 15/10/13 22:25:07 INFO OrderedTrackedLayout: Number of tracks: 1006
          // val readsRDD: RDD[AlignmentRecord] = VizReads.sc.loadIndexedBam(VizReads.readsPath, viewRegion)
          // val trackinput: RDD[(ReferenceRegion, AlignmentRecord)] = readsRDD.keyBy(ReferenceRegion(_))
          // val filteredLayout = new OrderedTrackedLayout(trackinput.collect())
          // val json = "{ \"tracks\": " + write(VizReads.printTrackJson(filteredLayout)) + ", \"matePairs\": " + write(VizReads.printMatePairJson(filteredLayout)) + "}"
          // json
        }
      }
    }
  }

  get("/overall") {
    contentType = "text/html"
    val templateEngine = new TemplateEngine
    templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/overall.ssp",
      Map("viewRegion" -> (viewRegion.referenceName, viewRegion.start.toString, viewRegion.end.toString),
        "readsExist" -> VizReads.readsExist,
        "variantsExist" -> VizReads.variantsExist,
        "featuresExist" -> VizReads.featuresExist))
  }

  get("/freq") {
    contentType = "text/html"
    val templateEngine = new TemplateEngine
    if (VizReads.readsExist) {
      templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/freq.ssp",
        Map("viewRegion" -> (viewRegion.referenceName, viewRegion.start.toString, viewRegion.end.toString)))
    } else {
      templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/nofreq.ssp")
    }
  }

  get("/freq/:ref") {
    VizTimers.FreqRequest.time {
      contentType = "json"
      viewRegion = ReferenceRegion(params("ref"), params("start").toLong, params("end").toLong)
      if (VizReads.readsPath.endsWith(".adam")) {
        val pred: FilterPredicate = ((LongColumn("end") >= viewRegion.start) && (LongColumn("start") <= viewRegion.end))
        val proj = Projection(AlignmentRecordField.readName, AlignmentRecordField.start, AlignmentRecordField.end)
        val readsRDD: RDD[AlignmentRecord] = VizReads.sc.loadParquetAlignments(VizReads.readsPath, predicate = Some(pred), projection = Some(proj))
        val filteredArray = readsRDD.collect()
        write(VizReads.printJsonFreq(filteredArray, viewRegion))
      } else if (VizReads.readsPath.endsWith(".sam") || VizReads.readsPath.endsWith(".bam")) {
        val idxFile: File = new File(VizReads.readsPath + ".bai")
        if (!idxFile.exists()) {
          val readsRDD: RDD[AlignmentRecord] = VizReads.sc.loadBam(VizReads.readsPath).filterByOverlappingRegion(viewRegion)
          val filteredArray = readsRDD.collect()
          write(VizReads.printJsonFreq(filteredArray, viewRegion))
        } else {
          val readsRDD: RDD[AlignmentRecord] = VizReads.sc.loadIndexedBam(VizReads.readsPath, viewRegion)
          val filteredArray = readsRDD.collect()
          write(VizReads.printJsonFreq(filteredArray, viewRegion))
        }
      }
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
      if (VizReads.variantsPath.endsWith(".adam")) {
        val pred: FilterPredicate = ((LongColumn("variant.end") >= viewRegion.start) && (LongColumn("variant.start") <= viewRegion.end))
        val proj = Projection(GenotypeField.variant, GenotypeField.alleles)
        val variantsRDD: RDD[Genotype] = VizTimers.LoadParquetFile.time {
          VizReads.sc.loadParquetGenotypes(VizReads.variantsPath, predicate = Some(pred), projection = Some(proj))
        }
        val trackinput: RDD[(ReferenceRegion, Genotype)] = variantsRDD.keyBy(v => ReferenceRegion(ReferencePosition(v)))
        val collected = VizTimers.DoingCollect.time {
          trackinput.collect()
        }
        val filteredGenotypeTrack = VizTimers.MakingTrack.time {
          new OrderedTrackedLayout(collected)
        }
        write(VizReads.printVariationJson(filteredGenotypeTrack))
      } else if (VizReads.variantsPath.endsWith(".vcf")) {
        val variantsRDD: RDD[Genotype] = VizReads.sc.loadGenotypes(VizReads.variantsPath).filterByOverlappingRegion(viewRegion)
        val trackinput: RDD[(ReferenceRegion, Genotype)] = variantsRDD.keyBy(v => ReferenceRegion(ReferencePosition(v)))
        val filteredGenotypeTrack = new OrderedTrackedLayout(trackinput.collect())
        write(VizReads.printVariationJson(filteredGenotypeTrack))
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
        val referenceRDD: RDD[NucleotideContigFragment] = VizReads.sc.loadParquetFragments(VizReads.referencePath, predicate = Some(pred))
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
          val referenceRDD: RDD[NucleotideContigFragment] = VizReads.sc.loadSequence(VizReads.referencePath)
          write(VizReads.printReferenceJson(referenceRDD, viewRegion))
        }
      }
    }
  }

}

class VizReads(protected val args: VizReadsArgs) extends BDGSparkCommand[VizReadsArgs] with Logging {
  val companion: BDGCommandCompanion = VizReads

  override def run(sc: SparkContext): Unit = {
    VizReads.sc = sc

    if (args.referencePath.endsWith(".fa") || args.referencePath.endsWith(".fasta") || args.referencePath.endsWith(".adam")) {
      VizReads.referencePath = args.referencePath
    } else {
      log.info("WARNING: Invalid reference file")
      println("WARNING: Invalid reference file")
    }

    VizReads.refName = args.refName

    val readsPath = Option(args.readsPath)
    // VizReads.lazyMat = LazyMaterialization(readsPath, sc)

    readsPath match {
      case Some(_) => {
        if (args.readsPath.endsWith(".bam") || args.readsPath.endsWith(".sam") || args.readsPath.endsWith(".adam")) {
          VizReads.readsPath = args.readsPath
          VizReads.readsExist = true
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

    val variantsPath = Option(args.variantsPath)
    variantsPath match {
      case Some(_) => {
        if (args.variantsPath.endsWith(".vcf") || args.variantsPath.endsWith(".adam")) {
          VizReads.variantsPath = args.variantsPath
          VizReads.variantsExist = true
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
