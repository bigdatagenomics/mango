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

import org.apache.hadoop.mapreduce.Job
import org.apache.spark.{ Logging, SparkContext }
import org.bdgenomics.adam.models.VariantContext
import org.apache.spark.rdd.RDD
import org.bdgenomics.adam.cli._
import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.adam.models.ReferencePosition
import org.bdgenomics.adam.projections.{ Projection, VariantField, AlignmentRecordField, GenotypeField, NucleotideContigFragmentField, FeatureField }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.mango.models.OrderedTrackedLayout
import org.bdgenomics.formats.avro.{ AlignmentRecord, Feature, Genotype, GenotypeAllele, NucleotideContigFragment }
import org.fusesource.scalate.TemplateEngine
import org.json4s._
import org.kohsuke.args4j.{ Argument, Option => Args4jOption }
import org.scalatra.json._
import org.scalatra.ScalatraServlet
import parquet.filter2.predicate.FilterPredicate
import parquet.filter2.dsl.Dsl._

object VizReads extends ADAMCommandCompanion with Logging {
  val commandName: String = "viz"
  val commandDescription: String = "Genomic visualization for ADAM"

  var sc: SparkContext = null
  var referencePath: String = ""
  var refName: String = ""
  var readsPath: String = ""
  var readsExist: Boolean = false
  var variantsPath: String = ""
  var variantsExist: Boolean = false
  var featuresPath: String = ""
  var featuresExist: Boolean = false
  var server: org.eclipse.jetty.server.Server = null

  def apply(cmdLine: Array[String]): ADAMCommand = {
    new VizReads(Args4j[VizReadsArgs](cmdLine))
  }

  def printTrackJson(layout: OrderedTrackedLayout[AlignmentRecord]): List[TrackJson] = {
    var tracks = new scala.collection.mutable.ListBuffer[TrackJson]
    for ((rec, track) <- layout.trackAssignments) {
      val aRec = rec._2.asInstanceOf[AlignmentRecord]
      tracks += new TrackJson(aRec.getReadName, aRec.getStart, aRec.getEnd, track)
    }
    tracks.toList
  }

  def printFeatureJson(layout: OrderedTrackedLayout[Feature]): List[FeatureJson] = {
    var tracks = new scala.collection.mutable.ListBuffer[FeatureJson]
    for ((rec, track) <- layout.trackAssignments) {
      val fRec = rec._2.asInstanceOf[Feature]
      tracks += new FeatureJson(fRec.getFeatureId, fRec.getFeatureType, fRec.getStart, fRec.getEnd, track)
    }
    tracks.toList
  }

  def printVariationJson(layout: OrderedTrackedLayout[Genotype]): List[VariationJson] = {
    var tracks = new scala.collection.mutable.ListBuffer[VariationJson]
    for ((rec, track) <- layout.trackAssignments) {
      val vRec = rec._2.asInstanceOf[Genotype]
      tracks += new VariationJson(vRec.getVariant.getContig.getContigName, vRec.getAlleles.mkString(" / "), vRec.getVariant.getStart, vRec.getVariant.getEnd, track)
    }
    tracks.toList
  }

  def printJsonFreq(array: Array[AlignmentRecord], region: ReferenceRegion): List[FreqJson] = {
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

case class TrackJson(readName: String, start: Long, end: Long, track: Long)
case class VariationJson(contigName: String, alleles: String, start: Long, end: Long, track: Long)
case class FreqJson(base: Long, freq: Long)
case class FeatureJson(featureId: String, featureType: String, start: Long, end: Long, track: Long)
case class ReferenceJson(reference: String)

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

class VizServlet extends ScalatraServlet with JacksonJsonSupport {
  protected implicit val jsonFormats: Formats = DefaultFormats
  var viewRegion = ReferenceRegion(VizReads.refName, 0, 100)

  get("/?") {
    redirect(url("overall"))
  }

  get("/quit") {
    VizReads.quit()
  }

  get("/reads") {
    contentType = "text/html"
    val templateEngine = new TemplateEngine
    if (VizReads.readsExist) {
      if (VizReads.readsPath.endsWith(".adam")) {
        val pred: FilterPredicate = ((LongColumn("start") >= viewRegion.start) && (LongColumn("start") <= viewRegion.end))
        val proj = Projection(AlignmentRecordField.contig, AlignmentRecordField.readName, AlignmentRecordField.start, AlignmentRecordField.end)
        val readsRDD: RDD[AlignmentRecord] = VizReads.sc.loadParquetAlignments(VizReads.readsPath, predicate = Some(pred), projection = Some(proj))
        val trackinput: RDD[(ReferenceRegion, AlignmentRecord)] = readsRDD.keyBy(ReferenceRegion(_))
        val filteredLayout = new OrderedTrackedLayout(trackinput.collect())
        val templateEngine = new TemplateEngine
        templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/reads.ssp",
          Map("viewRegion" -> (viewRegion.referenceName, viewRegion.start.toString, viewRegion.end.toString),
            "numTracks" -> filteredLayout.numTracks.toString))
      } else if (VizReads.readsPath.endsWith(".sam") || VizReads.readsPath.endsWith(".bam")) {
        val readsRDD: RDD[AlignmentRecord] = VizReads.sc.loadBam(VizReads.readsPath).filterByOverlappingRegion(viewRegion)
        val trackinput: RDD[(ReferenceRegion, AlignmentRecord)] = readsRDD.keyBy(ReferenceRegion(_))
        val filteredLayout = new OrderedTrackedLayout(trackinput.collect())
        val templateEngine = new TemplateEngine
        templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/reads.ssp",
          Map("viewRegion" -> (viewRegion.referenceName, viewRegion.start.toString, viewRegion.end.toString),
            "numTracks" -> filteredLayout.numTracks.toString))
      }
    } else {
      templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/noreads.ssp")
    }
  }

  get("/reads/:ref") {
    contentType = formats("json")
    viewRegion = ReferenceRegion(params("ref"), params("start").toLong, params("end").toLong)
    if (VizReads.readsPath.endsWith(".adam")) {
      val pred: FilterPredicate = ((LongColumn("start") >= viewRegion.start) && (LongColumn("start") <= viewRegion.end))
      val proj = Projection(AlignmentRecordField.contig, AlignmentRecordField.readName, AlignmentRecordField.start, AlignmentRecordField.end)
      val readsRDD: RDD[AlignmentRecord] = VizReads.sc.loadParquetAlignments(VizReads.readsPath, predicate = Some(pred), projection = Some(proj))
      val trackinput: RDD[(ReferenceRegion, AlignmentRecord)] = readsRDD.keyBy(ReferenceRegion(_))
      val filteredLayout = new OrderedTrackedLayout(trackinput.collect())
      VizReads.printTrackJson(filteredLayout)
    } else if (VizReads.readsPath.endsWith(".sam") || VizReads.readsPath.endsWith(".bam")) {
      val readsRDD: RDD[AlignmentRecord] = VizReads.sc.loadBam(VizReads.readsPath).filterByOverlappingRegion(viewRegion)
      val trackinput: RDD[(ReferenceRegion, AlignmentRecord)] = readsRDD.keyBy(ReferenceRegion(_))
      val filteredLayout = new OrderedTrackedLayout(trackinput.collect())
      VizReads.printTrackJson(filteredLayout)
    }
  }

  get("/overall") {
    contentType = "text/html"
    var numTracks = "0"
    if (VizReads.readsExist) {
      if (VizReads.readsPath.endsWith(".adam")) {
        val pred: FilterPredicate = ((LongColumn("start") >= viewRegion.start) && (LongColumn("start") <= viewRegion.end))
        val proj = Projection(AlignmentRecordField.contig, AlignmentRecordField.readName, AlignmentRecordField.start, AlignmentRecordField.end)
        val readsRDD: RDD[AlignmentRecord] = VizReads.sc.loadParquetAlignments(VizReads.readsPath, predicate = Some(pred), projection = Some(proj))
        val trackinput: RDD[(ReferenceRegion, AlignmentRecord)] = readsRDD.keyBy(ReferenceRegion(_))
        val filteredLayout = new OrderedTrackedLayout(trackinput.collect())
        numTracks = filteredLayout.numTracks.toString
      } else if (VizReads.readsPath.endsWith(".sam") || VizReads.readsPath.endsWith(".bam")) {
        val readsRDD: RDD[AlignmentRecord] = VizReads.sc.loadBam(VizReads.readsPath).filterByOverlappingRegion(viewRegion)
        val trackinput: RDD[(ReferenceRegion, AlignmentRecord)] = readsRDD.keyBy(ReferenceRegion(_))
        val filteredLayout = new OrderedTrackedLayout(trackinput.collect())
        numTracks = filteredLayout.numTracks.toString
      }
    }
    val templateEngine = new TemplateEngine
    templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/overall.ssp",
      Map("viewRegion" -> (viewRegion.referenceName, viewRegion.start.toString, viewRegion.end.toString),
        "numTracks" -> numTracks,
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
    contentType = formats("json")
    viewRegion = ReferenceRegion(params("ref"), params("start").toLong, params("end").toLong)
    if (VizReads.readsPath.endsWith(".adam")) {
      val pred: FilterPredicate = ((LongColumn("start") >= viewRegion.start) && (LongColumn("start") <= viewRegion.end))
      val proj = Projection(AlignmentRecordField.readName, AlignmentRecordField.start, AlignmentRecordField.end)
      val readsRDD: RDD[AlignmentRecord] = VizReads.sc.loadParquetAlignments(VizReads.readsPath, predicate = Some(pred), projection = Some(proj))
      val filteredArray = readsRDD.collect()
      VizReads.printJsonFreq(filteredArray, viewRegion)
    } else if (VizReads.readsPath.endsWith(".sam") || VizReads.readsPath.endsWith(".bam")) {
      val readsRDD: RDD[AlignmentRecord] = VizReads.sc.loadBam(VizReads.readsPath).filterByOverlappingRegion(viewRegion)
      val filteredArray = readsRDD.collect()
      VizReads.printJsonFreq(filteredArray, viewRegion)
    }
  }

  get("/variants") {
    contentType = "text/html"
    val templateEngine = new TemplateEngine
    if (VizReads.variantsExist) {
      if (VizReads.variantsPath.endsWith(".adam")) {
        val pred: FilterPredicate = ((LongColumn("variant.start") >= viewRegion.start) && (LongColumn("variant.start") <= viewRegion.end))
        val proj = Projection(GenotypeField.variant, GenotypeField.alleles)
        val variantsRDD: RDD[Genotype] = VizReads.sc.loadParquetGenotypes(VizReads.variantsPath, predicate = Some(pred), projection = Some(proj))
        val trackinput: RDD[(ReferenceRegion, Genotype)] = variantsRDD.keyBy(v => ReferenceRegion(ReferencePosition(v)))
        val filteredGenotypeTrack = new OrderedTrackedLayout(trackinput.collect())
        val templateEngine = new TemplateEngine
        val displayMap = Map("viewRegion" -> (viewRegion.referenceName, viewRegion.start.toString, viewRegion.end.toString),
          "numTracks" -> filteredGenotypeTrack.numTracks.toString)
        templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/variants.ssp",
          displayMap)
      } else if (VizReads.variantsPath.endsWith(".vcf")) {
        val variantsRDD: RDD[Genotype] = VizReads.sc.loadGenotypes(VizReads.variantsPath).filterByOverlappingRegion(viewRegion)
        val trackinput: RDD[(ReferenceRegion, Genotype)] = variantsRDD.keyBy(v => ReferenceRegion(ReferencePosition(v)))
        val filteredGenotypeTrack = new OrderedTrackedLayout(trackinput.collect())
        val templateEngine = new TemplateEngine
        val displayMap = Map("viewRegion" -> (viewRegion.referenceName, viewRegion.start.toString, viewRegion.end.toString),
          "numTracks" -> filteredGenotypeTrack.numTracks.toString)
        templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/variants.ssp",
          displayMap)
      }
    } else {
      templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/novariants.ssp")
    }
  }

  get("/variants/:ref") {
    contentType = formats("json")
    viewRegion = ReferenceRegion(params("ref"), params("start").toLong, params("end").toLong)
    if (VizReads.variantsPath.endsWith(".adam")) {
      val pred: FilterPredicate = ((LongColumn("variant.start") >= viewRegion.start) && (LongColumn("variant.start") <= viewRegion.end))
      val proj = Projection(GenotypeField.variant, GenotypeField.alleles)
      val variantsRDD: RDD[Genotype] = VizReads.sc.loadParquetGenotypes(VizReads.variantsPath, predicate = Some(pred), projection = Some(proj))
      val trackinput: RDD[(ReferenceRegion, Genotype)] = variantsRDD.keyBy(v => ReferenceRegion(ReferencePosition(v)))
      val filteredGenotypeTrack = new OrderedTrackedLayout(trackinput.collect())
      VizReads.printVariationJson(filteredGenotypeTrack)
    } else if (VizReads.variantsPath.endsWith(".vcf")) {
      val variantsRDD: RDD[Genotype] = VizReads.sc.loadGenotypes(VizReads.variantsPath).filterByOverlappingRegion(viewRegion)
      val trackinput: RDD[(ReferenceRegion, Genotype)] = variantsRDD.keyBy(v => ReferenceRegion(ReferencePosition(v)))
      val filteredGenotypeTrack = new OrderedTrackedLayout(trackinput.collect())
      VizReads.printVariationJson(filteredGenotypeTrack)
    }
  }

  get("/features") {
    contentType = "text/html"
    val templateEngine = new TemplateEngine
    if (VizReads.featuresExist) {
      if (VizReads.featuresPath.endsWith(".adam")) {
        val pred: FilterPredicate = ((LongColumn("start") >= viewRegion.start) && (LongColumn("start") <= viewRegion.end))
        val proj = Projection(FeatureField.contig, FeatureField.featureId, FeatureField.featureType, FeatureField.start, FeatureField.end)
        val featureRDD: RDD[Feature] = VizReads.sc.loadParquetFeatures(VizReads.featuresPath, predicate = Some(pred), projection = Some(proj))
        val trackinput: RDD[(ReferenceRegion, Feature)] = featureRDD.keyBy(ReferenceRegion(_))
        val filteredFeatureTrack = new OrderedTrackedLayout(trackinput.collect())
        val displayMap = Map("viewRegion" -> (viewRegion.referenceName, viewRegion.start.toString, viewRegion.end.toString),
          "numTracks" -> filteredFeatureTrack.numTracks.toString)
        templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/features.ssp",
          displayMap)
      } else if (VizReads.featuresPath.endsWith(".bed")) {
        val featureRDD: RDD[Feature] = VizReads.sc.loadFeatures(VizReads.featuresPath).filterByOverlappingRegion(viewRegion)
        val trackinput: RDD[(ReferenceRegion, Feature)] = featureRDD.keyBy(ReferenceRegion(_))
        val filteredFeatureTrack = new OrderedTrackedLayout(trackinput.collect())
        val displayMap = Map("viewRegion" -> (viewRegion.referenceName, viewRegion.start.toString, viewRegion.end.toString),
          "numTracks" -> filteredFeatureTrack.numTracks.toString)
        templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/features.ssp",
          displayMap)
      }
    } else {
      templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/nofeatures.ssp")
    }
  }

  get("/features/:ref") {
    viewRegion = ReferenceRegion(params("ref"), params("start").toLong, params("end").toLong)
    if (VizReads.featuresPath.endsWith(".adam")) {
      val pred: FilterPredicate = ((LongColumn("start") >= viewRegion.start) && (LongColumn("start") <= viewRegion.end))
      val proj = Projection(FeatureField.contig, FeatureField.featureId, FeatureField.featureType, FeatureField.start, FeatureField.end)
      val featureRDD: RDD[Feature] = VizReads.sc.loadParquetFeatures(VizReads.featuresPath, predicate = Some(pred), projection = Some(proj))
      val trackinput: RDD[(ReferenceRegion, Feature)] = featureRDD.keyBy(ReferenceRegion(_))
      val filteredFeatureTrack = new OrderedTrackedLayout(trackinput.collect())
      VizReads.printFeatureJson(filteredFeatureTrack)
    } else if (VizReads.featuresPath.endsWith(".bed")) {
      val featureRDD: RDD[Feature] = VizReads.sc.loadFeatures(VizReads.featuresPath).filterByOverlappingRegion(viewRegion)
      val trackinput: RDD[(ReferenceRegion, Feature)] = featureRDD.keyBy(ReferenceRegion(_))
      val filteredFeatureTrack = new OrderedTrackedLayout(trackinput.collect())
      VizReads.printFeatureJson(filteredFeatureTrack)
    }
  }

  get("/reference/:ref") {
    viewRegion = ReferenceRegion(params("ref"), params("start").toLong, params("end").toLong)
    if (VizReads.referencePath.endsWith(".adam")) {
      val pred: FilterPredicate = ((LongColumn("fragmentStartPosition") >= viewRegion.start) && (LongColumn("fragmentStartPosition") <= viewRegion.end))
      val referenceRDD: RDD[NucleotideContigFragment] = VizReads.sc.loadParquetFragments(VizReads.referencePath, predicate = Some(pred))
      val referenceString: String = referenceRDD.adamGetReferenceString(viewRegion)
      val splitReference: Array[String] = referenceString.split("")
      var tracks = new scala.collection.mutable.ListBuffer[ReferenceJson]
      for (base <- splitReference) {
        tracks += new ReferenceJson(base.toUpperCase())
      }
      tracks.toList
    } else if (VizReads.referencePath.endsWith(".fa") || VizReads.referencePath.endsWith(".fasta") || VizReads.referencePath.endsWith(".adam")) {
      val referenceString: String = VizReads.sc.loadSequence(VizReads.referencePath).adamGetReferenceString(viewRegion)
      val splitReference: Array[String] = referenceString.split("")
      var tracks = new scala.collection.mutable.ListBuffer[ReferenceJson]
      for (base <- splitReference) {
        tracks += new ReferenceJson(base.toUpperCase())
      }
      tracks.toList
    }
  }
}

class VizReads(protected val args: VizReadsArgs) extends ADAMSparkCommand[VizReadsArgs] with Logging {
  val companion: ADAMCommandCompanion = VizReads

  override def run(sc: SparkContext, job: Job): Unit = {
    VizReads.sc = sc

    if (args.referencePath.endsWith(".fa") || args.referencePath.endsWith(".fasta") || args.referencePath.endsWith(".adam")) {
      VizReads.referencePath = args.referencePath
    } else {
      log.info("WARNING: Invalid reference file")
      println("WARNING: invalid reference file")
    }

    VizReads.refName = args.refName

    val readsPath = Option(args.readsPath)
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
    println("Feature visualization at /features")
    println("Overall visualization at: /overall")
    println("Quit at /quit")
    VizReads.server.join()
  }
}
