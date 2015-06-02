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
import org.apache.spark.SparkContext
import org.bdgenomics.adam.models.VariantContext
import org.apache.spark.rdd.RDD
import org.bdgenomics.adam.cli._
import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.adam.models.ReferencePosition
import org.bdgenomics.adam.projections.AlignmentRecordField._
import org.bdgenomics.adam.projections.Projection
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.mango.models.OrderedTrackedLayout
import org.bdgenomics.formats.avro.{ AlignmentRecord, Feature, Genotype, GenotypeAllele, NucleotideContigFragment }
import org.fusesource.scalate.TemplateEngine
import org.json4s._
import org.kohsuke.args4j.{ Argument, Option => Args4jOption }
import org.scalatra.json._
import org.scalatra.ScalatraServlet

object VizReads extends ADAMCommandCompanion {
  val commandName: String = "viz"
  val commandDescription: String = "Genomic visualization for ADAM"
  var refName = ""

  var reads: RDD[AlignmentRecord] = null
  var variants: RDD[Genotype] = null
  var reference: RDD[NucleotideContigFragment] = null
  var features: RDD[Feature] = null

  val trackHeight = 10
  val width = 1200
  val height = 400
  val base = 50

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
    var freqBuffer = new scala.collection.mutable.ListBuffer[FreqJson]

    // initiates map with 0 values
    var i0 = region.start
    while (i0 <= region.end) {
      freqMap.put(i0, 0)
      i0 += 1
    }

    // creates a point for each base showing its frequency
    for (rec <- array) {
      val aRec = rec.asInstanceOf[AlignmentRecord]
      var i = aRec.getStart
      while (i <= aRec.getEnd) {
        if (i >= region.start && i <= region.end)
          freqMap.put(i, freqMap(i) + 1)
        i += 1
      }
    }

    // convert to list of FreqJsons
    val iter = freqMap.keySet.iterator
    var key = 0L
    while (iter.hasNext) {
      key = iter.next()
      freqBuffer += FreqJson(key, freqMap(key))
    }

    freqBuffer.toList
  }

  def filterByOverlappingRegion(query: ReferenceRegion): RDD[Feature] = {
    def overlapsQuery(rec: Feature): Boolean =
      rec.getContig.getContigName.toString == query.referenceName &&
        rec.getStart < query.end &&
        rec.getEnd > query.start
    VizReads.features.filter(overlapsQuery)
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

  @Args4jOption(required = false, name = "-ref_name", usage = "The name of the reference we're looking at")
  var refName: String = null

  @Args4jOption(required = false, name = "-read_file", usage = "The reads file to view")
  var readPath: String = null

  @Args4jOption(required = false, name = "-var_file", usage = "The variants file to view")
  var variantsPath: String = null

  @Args4jOption(required = false, name = "-feat_file", usage = "The feature file to view")
  var featurePath: String = null

  @Args4jOption(required = false, name = "-port", usage = "The port to bind to for visualization. The default is 8080.")
  var port: Int = 8080
}

class VizServlet extends ScalatraServlet with JacksonJsonSupport {
  protected implicit val jsonFormats: Formats = DefaultFormats
  var viewRegion = ReferenceRegion(VizReads.refName, 0, 100)

  get("/?") {
    redirect(url("overall"))
  }

  get("/reads") {
    contentType = "text/html"
    var readsInput = Option(VizReads.reads)
    readsInput match {
      case Some(_) => {
        val readsRDD: RDD[AlignmentRecord] = VizReads.reads.filterByOverlappingRegion(viewRegion)
        val trackinput: RDD[(ReferenceRegion, AlignmentRecord)] = readsRDD.keyBy(ReferenceRegion(_))
        val filteredLayout = new OrderedTrackedLayout(trackinput.collect())
        val templateEngine = new TemplateEngine
        templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/reads.ssp",
          Map("viewRegion" -> (viewRegion.referenceName, viewRegion.start.toString, viewRegion.end.toString),
            "width" -> VizReads.width.toString,
            "base" -> VizReads.base.toString,
            "numTracks" -> filteredLayout.numTracks.toString,
            "trackHeight" -> VizReads.trackHeight.toString))
      }
      case None => {
        println("MISSING FILE: No reads file provided")
        val templateEngine = new TemplateEngine
        templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/noreads.ssp")
      }
    }
  }

  get("/reads/:ref") {
    contentType = formats("json")
    viewRegion = ReferenceRegion(params("ref"), params("start").toLong, params("end").toLong)
    val readsRDD: RDD[AlignmentRecord] = VizReads.reads.filterByOverlappingRegion(viewRegion)
    val trackinput: RDD[(ReferenceRegion, AlignmentRecord)] = readsRDD.keyBy(ReferenceRegion(_))
    val filteredLayout = new OrderedTrackedLayout(trackinput.collect())
    VizReads.printTrackJson(filteredLayout)
  }

  get("/overall") {
    contentType = "text/html"
    val readsRDD: RDD[AlignmentRecord] = VizReads.reads.filterByOverlappingRegion(viewRegion)
    val trackinput: RDD[(ReferenceRegion, AlignmentRecord)] = readsRDD.keyBy(ReferenceRegion(_))
    val filteredLayout = new OrderedTrackedLayout(trackinput.collect())
    val templateEngine = new TemplateEngine
    templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/overall.ssp",
      Map("viewRegion" -> (viewRegion.referenceName, viewRegion.start.toString, viewRegion.end.toString),
        "width" -> VizReads.width.toString,
        "base" -> VizReads.base.toString,
        "numTracks" -> filteredLayout.numTracks.toString,
        "trackHeight" -> VizReads.trackHeight.toString))
  }

  get("/freq") {
    contentType = "text/html"
    var readsInput = Option(VizReads.reads)
    readsInput match {
      case Some(_) => {
        val templateEngine = new TemplateEngine
        templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/freq.ssp",
          Map("viewRegion" -> (viewRegion.referenceName, viewRegion.start.toString, viewRegion.end.toString),
            "width" -> VizReads.width.toString,
            "height" -> VizReads.height.toString,
            "base" -> VizReads.base.toString))
      }
      case None => {
        println("MISSING FILE: No reads file provided")
        val templateEngine = new TemplateEngine
        templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/nofreq.ssp")
      }
    }
  }

  get("/freq/:ref") {
    contentType = formats("json")
    viewRegion = ReferenceRegion(params("ref"), params("start").toLong, params("end").toLong)
    val filteredArray = VizReads.reads.filterByOverlappingRegion(viewRegion).collect()
    VizReads.printJsonFreq(filteredArray, viewRegion)
  }

  get("/variants") {
    contentType = "text/html"
    var variantsInput = Option(VizReads.variants)
    variantsInput match {
      case Some(_) => {
        val variantsRDD: RDD[Genotype] = VizReads.variants.filterByOverlappingRegion(viewRegion)
        val trackinput: RDD[(ReferenceRegion, Genotype)] = variantsRDD.keyBy(v => ReferenceRegion(ReferencePosition(v)))
        val filteredGenotypeTrack = new OrderedTrackedLayout(trackinput.collect())
        val templateEngine = new TemplateEngine
        val displayMap = Map("viewRegion" -> (viewRegion.referenceName, viewRegion.start.toString, viewRegion.end.toString),
          "width" -> VizReads.width.toString,
          "base" -> VizReads.base.toString,
          "numTracks" -> filteredGenotypeTrack.numTracks.toString,
          "trackHeight" -> VizReads.trackHeight.toString)
        templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/variants.ssp",
          displayMap)
      }
      case None => {
        println("MISSING FILE: No variants file provided")
        val templateEngine = new TemplateEngine
        templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/novariants.ssp")
      }
    }

  }

  get("/variants/:ref") {
    contentType = formats("json")
    viewRegion = ReferenceRegion(params("ref"), params("start").toLong, params("end").toLong)
    val variantsRDD: RDD[Genotype] = VizReads.variants.filterByOverlappingRegion(viewRegion)
    val trackinput: RDD[(ReferenceRegion, Genotype)] = variantsRDD.keyBy(v => ReferenceRegion(ReferencePosition(v)))
    val filteredGenotypeTrack = new OrderedTrackedLayout(trackinput.collect())
    VizReads.printVariationJson(filteredGenotypeTrack)
  }

  get("/features") {
    contentType = "text/html"
    var featuresInput = Option(VizReads.features)
    featuresInput match {
      case Some(_) => {
        val featureRDD: RDD[Feature] = VizReads.features.filterByOverlappingRegion(viewRegion)
        val trackinput: RDD[(ReferenceRegion, Feature)] = featureRDD.keyBy(ReferenceRegion(_))
        val filteredFeatureTrack = new OrderedTrackedLayout(trackinput.collect())
        val templateEngine = new TemplateEngine
        val displayMap = Map("viewRegion" -> (viewRegion.referenceName, viewRegion.start.toString, viewRegion.end.toString),
          "width" -> VizReads.width.toString,
          "base" -> VizReads.base.toString,
          "numTracks" -> filteredFeatureTrack.numTracks.toString,
          "trackHeight" -> VizReads.trackHeight.toString)
        templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/features.ssp",
          displayMap)
      }
      case None => {
        println("MISSING FILE: No features file provided")
        val templateEngine = new TemplateEngine
        templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/nofeatures.ssp")
      }
    }

  }
  get("/features/:ref") {
    viewRegion = ReferenceRegion(params("ref"), params("start").toLong, params("end").toLong)
    val featureRDD: RDD[Feature] = VizReads.filterByOverlappingRegion(viewRegion)
    val trackinput: RDD[(ReferenceRegion, Feature)] = featureRDD.keyBy(ReferenceRegion(_))
    val filteredFeatureTrack = new OrderedTrackedLayout(trackinput.collect())
    VizReads.printFeatureJson(filteredFeatureTrack)
  }

  get("/reference/:ref") {
    viewRegion = ReferenceRegion(params("ref"), params("start").toLong, params("end").toLong)
    val referenceString: String = VizReads.reference.adamGetReferenceString(viewRegion)
    val splitReference: Array[String] = referenceString.split("")
    var tracks = new scala.collection.mutable.ListBuffer[ReferenceJson]
    for (base <- splitReference) {
      tracks += new ReferenceJson(base)
    }
    tracks.toList
  }
}

class VizReads(protected val args: VizReadsArgs) extends ADAMSparkCommand[VizReadsArgs] {
  val companion: ADAMCommandCompanion = VizReads

  override def run(sc: SparkContext, job: Job): Unit = {
    val proj = Projection(contig, readMapped, readName, start, end)
    if (args.referencePath.endsWith(".fa")) {
      VizReads.reference = sc.loadSequence(args.referencePath, projection = Some(proj))
    } else {
      println("WARNING: invalid reference file")
    }

    val readPath = Option(args.readPath)
    readPath match {
      case Some(_) => {
        if (args.readPath.endsWith(".bam") || args.readPath.endsWith(".sam") || args.readPath.endsWith(".align.adam")) {
          VizReads.reads = sc.loadAlignments(args.readPath, projection = Some(proj))
        }
      }
      case None => println("WARNING: No read file provided")
    }

    val refName = Option(args.refName)
    refName match {
      case Some(_) => {
        VizReads.refName = args.refName
      }
      case None => println("WARNING: No refname provided")
    }

    val variantsPath = Option(args.variantsPath)
    variantsPath match {
      case Some(_) => {
        if (args.variantsPath.endsWith(".vcf") || args.variantsPath.endsWith(".gt.adam")) {
          VizReads.variants = sc.loadGenotypes(args.variantsPath, projection = Some(proj))
        }
      }
      case None => println("WARNING: No variants file provided")
    }

    val featurePath = Option(args.featurePath)
    featurePath match {
      case Some(_) => {
        if (args.featurePath.endsWith(".bed")) {
          VizReads.features = sc.loadFeatures(args.featurePath, projection = Some(proj))
        }
      }
      case None => println("WARNING: No features file provided")
    }

    val server = new org.eclipse.jetty.server.Server(args.port)
    val handlers = new org.eclipse.jetty.server.handler.ContextHandlerCollection()
    server.setHandler(handlers)
    handlers.addHandler(new org.eclipse.jetty.webapp.WebAppContext("mango-cli/src/main/webapp", "/"))
    server.start()
    println("View the visualization at: " + args.port)
    println("Frequency visualization at: /freq")
    println("Overlapping reads visualization at: /reads")
    println("Variant visualization at: /variants")
    println("Feature visualization at /features")
    println("Overall visualization at: /overall")
    server.join()
  }
}
