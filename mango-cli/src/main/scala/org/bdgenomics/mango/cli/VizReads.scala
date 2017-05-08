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

import java.io.FileNotFoundException

import net.liftweb.json.Serialization.write
import net.liftweb.json._
import org.apache.spark.SparkContext
import org.bdgenomics.adam.models.{ ReferencePosition, ReferenceRegion, SequenceDictionary }
import org.bdgenomics.mango.cli
import org.bdgenomics.mango.core.util.{ SearchVariantsRequestGA4GH, SearchVariantsRequestGA4GHBinning, VizCacheIndicator, VizUtils }
import org.bdgenomics.mango.filters._
import org.bdgenomics.mango.layout.{ BedRowJson, GenotypeJson, PositionCount }
import org.bdgenomics.mango.models._
import org.bdgenomics.mango.util.Bookkeep
import org.bdgenomics.utils.cli._
import org.bdgenomics.utils.instrumentation.Metrics
import org.bdgenomics.utils.misc.Logging
import org.fusesource.scalate.TemplateEngine
import org.ga4gh.GAReadAlignment
import org.kohsuke.args4j.{ Argument, Option => Args4jOption }
import org.scalatra._
import org.bdgenomics.adam.models.VariantContext
import org.bdgenomics.formats.avro.Feature
import org.bdgenomics.mango.converters.GA4GHConverter

import ga4gh.SequenceAnnotations

object VizTimers extends Metrics {
  //HTTP requests
  val ReadsRequest = timer("GET reads")
  val CoverageRequest = timer("GET coverage")
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

case class Materializer(objects: Seq[LazyMaterialization[_, _]]) {

  /**
   * Access functions for materializer
   */
  def getReads(): Option[AlignmentRecordMaterialization] = {
    val x = objects.flatMap(r =>
      r match {
        case m: AlignmentRecordMaterialization => Some(m)
        case _                                 => None
      })
    if (x.isEmpty) None
    else Some(x.head)
  }

  def getCoverage(): Option[CoverageMaterialization] = {
    val x = objects.flatMap(r =>
      r match {
        case m: CoverageMaterialization => Some(m)
        case _                          => None
      })
    if (x.isEmpty) None
    else Some(x.head)
  }

  def getVariantContext(): Option[VariantContextMaterialization] = {
    val x = objects.flatMap(r =>
      r match {
        case m: VariantContextMaterialization => Some(m)
        case _                                => None
      })
    if (x.isEmpty) None
    else Some(x.head)
  }

  def getFeatures(): Option[FeatureMaterialization] = {
    val x = objects.flatMap(r =>
      r match {
        case m: FeatureMaterialization => Some(m)
        case _                         => None
      })
    if (x.isEmpty) None
    else Some(x.head)
  }

  /**
   * definitions tracking whether optional datatypes were loaded
   */
  def readsExist: Boolean = getReads().isDefined

  def coveragesExist: Boolean = getCoverage().isDefined

  def variantContextExist: Boolean = getVariantContext().isDefined

  def featuresExist: Boolean = getFeatures().isDefined
}

/**
 * Contains caching, error and util function information for formatting and serving Json data
 */
object VizReads extends BDGCommandCompanion with Logging {

  val commandName: String = "Mango"
  val commandDescription: String = "Genomic visualization for ADAM"
  implicit val formats = net.liftweb.json.DefaultFormats

  var sc: SparkContext = null
  var server: org.eclipse.jetty.server.Server = null
  var globalDict: SequenceDictionary = null

  // Structures storing data types. All but reference is optional
  var annotationRDD: AnnotationMaterialization = null
  var materializer: Materializer = null

  var cacheSize: Int = 1000

  // Gene URL
  var genes: Option[String] = None

  /**
   * Caching information for frontend
   */
  // stores synchonization objects
  var syncObject: Map[String, Object] = Map.empty[String, Object]

  // placeholder for indicators
  val region = ReferenceRegion("N", 0, 1)

  // reads cache
  object readsWait
  var readsCache: Map[String, Array[GAReadAlignment]] = Map.empty[String, Array[GAReadAlignment]]
  var readsIndicator = VizCacheIndicator(region, 1)

  // coverage reads cache
  object readsCoverageWait
  var readsCoverageCache: Map[String, Array[PositionCount]] = Map.empty[String, Array[PositionCount]]
  var readsCoverageIndicator = VizCacheIndicator(region, 1)

  // coverage cache
  object coverageWait
  var coverageCache: Map[String, Array[PositionCount]] = Map.empty[String, Array[PositionCount]]
  var coverageIndicator = VizCacheIndicator(region, 1)

  // variant cache
  object variantsWait
  var variantsCache: Map[String, Array[VariantContext]] = Map.empty[String, Array[VariantContext]]
  var variantsIndicator = VizCacheIndicator(region, 1)
  var showGenotypes: Boolean = false

  // features cache
  object featuresWait
  var featuresCache: Map[String, Array[BedRowJson]] = Map.empty[String, Array[BedRowJson]]
  var featuresIndicator = VizCacheIndicator(region, 1)

  // regions to prefetch during discovery. sent to front
  // end for visual processing
  var prefetchedRegions: List[(ReferenceRegion, Double)] = List()

  // HTTP ERROR RESPONSES
  object errors {
    var outOfBounds = NotFound("Region not found in Reference Sequence Dictionary")
    var largeRegion = RequestEntityTooLarge("Region too large")
    var unprocessableFile = UnprocessableEntity("File type not supported")
    var notFound = NotFound("File not found")
    def noContent(region: ReferenceRegion): ActionResult = {
      val msg = s"No content available at ${region.toString}"
      NoContent(Map.empty, msg)
    }
  }

  def apply(cmdLine: Array[String]): BDGCommand = {
    new VizReads(Args4j[VizReadsArgs](cmdLine))
  }

  /**
   * Returns stringified version of sequence dictionary
   *
   * @param dict: dictionary to format to a string
   * @return List of sequence dictionary strings of form referenceName:0-referenceName.length
   */
  def formatDictionaryOpts(dict: SequenceDictionary): String = {
    val sorted = dict.records.sortBy(_.length).reverse
    sorted.map(r => r.name + ":0-" + r.length).mkString(",")
  }

  /**
   * Returns stringified version of sequence dictionary
   *
   * @param regions: regions to format to string
   * @return list of strinified reference regions
   */
  def formatClickableRegions(regions: List[(ReferenceRegion, Double)]): String = {
    regions.map(r => s"${r._1.referenceName}:${r._1.start}-${r._1.end}" +
      s"-${BigDecimal(r._2).setScale(2, BigDecimal.RoundingMode.HALF_UP).toDouble}").mkString(",")
  }

  def expand(region: ReferenceRegion, minLength: Long = VizReads.cacheSize): ReferenceRegion = {
    require(minLength > 0, s"minimum length ${minLength} must be greater than 0")

    val start = Math.max(0, region.start - minLength / 2)
    val end = region.end + minLength / 2

    region.copy(start = start, end = end)
  }

  //Correctly shuts down the server
  def quit() {
    val thread = new Thread {
      override def run() {
        try {
          log.info("Shutting down the server")
          server.stop()
          log.info("Server has stopped")
        } catch {
          case e: Exception => {
            log.info("Error when stopping Jetty server: " + e.getMessage, e)
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

  @Args4jOption(required = false, name = "-genes", usage = "Gene URL.")
  var genePath: String = null

  @Args4jOption(required = false, name = "-reads", usage = "A list of reads files to view, separated by commas (,)")
  var readsPaths: String = null

  @Args4jOption(required = false, name = "-coverage", usage = "A list of coverage files to view, separated by commas (,)")
  var coveragePaths: String = null

  @Args4jOption(required = false, name = "-variants", usage = "A list of variants files to view, separated by commas (,). " +
    "Vcf files require a corresponding tbi index.")
  var variantsPaths: String = null

  @Args4jOption(required = false, name = "-show_genotypes", usage = "Shows genotypes if available in variant files.")
  var showGenotypes: Boolean = false

  @Args4jOption(required = false, name = "-features", usage = "The feature files to view, separated by commas (,)")
  var featurePaths: String = null

  @Args4jOption(required = false, name = "-port", usage = "The port to bind to for visualization. The default is 8080.")
  var port: Int = 8080

  @Args4jOption(required = false, name = "-test", usage = "For debugging purposes.")
  var testMode: Boolean = false

  @Args4jOption(required = false, name = "-discover", usage = "This turns on discovery mode on start up.")
  var discoveryMode: Boolean = false

  @Args4jOption(required = false, name = "-prefetchSize", usage = "Bp to prefetch in executors.")
  var prefetchSize: Int = 10000

  @Args4jOption(required = false, name = "-cacheSize", usage = "Bp to cache on driver.")
  var cacheSize: Int = VizReads.cacheSize

  @Args4jOption(required = false, name = "-preload", usage = "Chromosomes to prefetch, separated by commas (,).")
  var preload: String = null

}

class VizServlet extends ScalatraServlet {
  implicit val formats = net.liftweb.json.DefaultFormats

  get("/?") {
    redirect("/overall")
  }

  get("/quit") {
    VizReads.quit()
  }

  get("/overall") {
    contentType = "text/html"
    val templateEngine = new TemplateEngine
    // set initial referenceRegion so it is defined. pick first chromosome to view
    val firstChr = VizReads.globalDict.records.head.name
    session("referenceRegion") = ReferenceRegion(firstChr, 1, 100)
    templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/overall.ssp",
      Map("dictionary" -> VizReads.formatDictionaryOpts(VizReads.globalDict),
        "regions" -> VizReads.formatClickableRegions(VizReads.prefetchedRegions)))
  }

  get("/setContig/:ref") {
    val viewRegion = ReferenceRegion(params("ref"), params("start").toLong, params("end").toLong)
    session("referenceRegion") = viewRegion
  }

  get("/browser") {
    contentType = "text/html"
    // if session variable for reference region is not yet set, randomly set it
    try {
      session("referenceRegion")
    } catch {
      case e: Exception =>
        val firstChr = VizReads.globalDict.records.head.name
        session("referenceRegion") = ReferenceRegion(firstChr, 0, 100)
    }

    val templateEngine = new TemplateEngine
    // set initial referenceRegion so it is defined
    val region = session("referenceRegion").asInstanceOf[ReferenceRegion]
    val indicator = VizCacheIndicator(region, 1)
    VizReads.readsIndicator = indicator
    VizReads.variantsIndicator = indicator
    VizReads.featuresIndicator = indicator

    // generate file keys for front end
    val readsSamples: Option[List[(String, Option[String])]] = try {
      val reads = VizReads.materializer.getReads().get.getFiles.map(r => LazyMaterialization.filterKeyFromFile(r))

      // check if there are precomputed coverage files for reads. If so, send this information to the frontend
      // to avoid extra coverage computation
      if (VizReads.materializer.coveragesExist) {
        Some(reads.map(r => {
          val coverage = VizReads.materializer.getCoverage().get.getFiles.map(c => LazyMaterialization.filterKeyFromFile(c))
            .find(c => {
              c.contains(r)
            })
          (r, coverage)
        }))
      } else Some(reads.map((_, None)))

    } catch {
      case e: Exception => None
    }

    val coverageSamples = try {
      val coverage = VizReads.materializer.getCoverage().get.getFiles.map(r => LazyMaterialization.filterKeyFromFile(r))

      // filter out coverage samples that will be displayed with reads
      if (readsSamples.isDefined) {
        val readsCoverage = readsSamples.get.map(_._2).flatten
        Some(coverage.filter(c => !readsCoverage.contains(c)))
      } else Some(coverage)
    } catch {
      case e: Exception => None
    }

    val variantSamples = try {
      if (VizReads.showGenotypes)
        Some(VizReads.materializer.getVariantContext().get.getGenotypeSamples().map(r => (LazyMaterialization.filterKeyFromFile(r._1), r._2.mkString(","))))
      else Some(VizReads.materializer.getVariantContext().get.getFiles.map(r => (LazyMaterialization.filterKeyFromFile(r), "")))
    } catch {
      case e: Exception => None
    }

    val featureSamples = try {
      Some(VizReads.materializer.getFeatures().get.getFiles.map(r => LazyMaterialization.filterKeyFromFile(r)))
    } catch {
      case e: Exception => None
    }

    templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/browser.ssp",
      Map("dictionary" -> VizReads.formatDictionaryOpts(VizReads.globalDict),
        "genes" -> VizReads.genes,
        "reads" -> readsSamples,
        "coverage" -> coverageSamples,
        "variants" -> variantSamples,
        "features" -> featureSamples,
        "contig" -> session("referenceRegion").asInstanceOf[ReferenceRegion].referenceName,
        "start" -> session("referenceRegion").asInstanceOf[ReferenceRegion].start.toString,
        "end" -> session("referenceRegion").asInstanceOf[ReferenceRegion].end.toString))
  }

  get("/reference/:ref") {
    val viewRegion = ReferenceRegion(params("ref"), params("start").toLong, params("end").toLong)
    session("referenceRegion") = viewRegion
    val dictOpt = VizReads.globalDict(viewRegion.referenceName)
    if (dictOpt.isDefined) {
      Ok(write(VizReads.annotationRDD.getReferenceString(viewRegion)))
    } else VizReads.errors.outOfBounds
  }

  get("/sequenceDictionary") {
    Ok(write(VizReads.annotationRDD.getSequenceDictionary.records))
  }

  get("/reads/:key/:ref") {
    VizTimers.ReadsRequest.time {

      if (!VizReads.materializer.readsExist) {
        VizReads.errors.notFound
      } else {
        val viewRegion = ReferenceRegion(params("ref"), params("start").toLong,
          VizUtils.getEnd(params("end").toLong, VizReads.globalDict(params("ref"))))
        val key: String = params("key")
        contentType = "json"

        val dictOpt = VizReads.globalDict(viewRegion.referenceName)
        if (dictOpt.isDefined) {
          var results: Option[String] = None
          // region was already collected, grab from cache
          VizReads.readsWait.synchronized {
            if (!VizReads.readsIndicator.region.contains(viewRegion)) {
              val expanded = VizReads.expand(viewRegion)
              VizReads.readsCache = VizReads.materializer.getReads().get.getJson(expanded)
              VizReads.readsIndicator = VizCacheIndicator(expanded, 1)
            }
          }
          // filter data overlapping viewRegion and stringify
          val data = VizReads.readsCache.get(key).getOrElse(Array.empty).filter(r => {
            ReferencePosition(r.getAlignment.getPosition.getReferenceName, r.getAlignment.getPosition.getPosition).overlaps(viewRegion)
          })
          results = Some(VizReads.materializer.getReads().get.stringify(data))
          if (results.isDefined) {
            Ok(results.get)
          } else VizReads.errors.noContent(viewRegion)
        } else VizReads.errors.outOfBounds
      }
    }
  }

  get("/coverage/:key/:ref") {
    val viewRegion = ReferenceRegion(params("ref"), params("start").toLong,
      VizUtils.getEnd(params("end").toLong, VizReads.globalDict(params("ref"))))
    val key: String = params("key")
    val binning: Int =
      try
        params("binning").toInt
      catch {
        case e: Exception => 1
      }
    getCoverage(viewRegion, key, binning)
  }

  get("/reads/coverage/:key/:ref") {
    VizTimers.ReadsRequest.time {

      if (!VizReads.materializer.readsExist) {
        VizReads.errors.notFound
      } else {
        val viewRegion = ReferenceRegion(params("ref"), params("start").toLong,
          VizUtils.getEnd(params("end").toLong, VizReads.globalDict(params("ref"))))
        val key: String = params("key")
        contentType = "json"

        // get all coverage files that have been loaded
        val coverageFiles =
          if (VizReads.materializer.coveragesExist) {
            Some(VizReads.materializer.getCoverage().get.getFiles.map(f => LazyMaterialization.filterKeyFromFile(f)))
          } else None

        // check if there is a precomputed coverage file for this reads file
        if (coverageFiles.isDefined && coverageFiles.get.contains(key)) {
          val binning: Int =
            try
              params("binning").toInt
            catch {
              case e: Exception => 1
            }

          getCoverage(viewRegion, key, binning)
        } else {
          // no precomputed coverage
          val dictOpt = VizReads.globalDict(viewRegion.referenceName)
          if (dictOpt.isDefined) {
            var results: Option[String] = None
            // region was already collected, grab from cache
            VizReads.readsCoverageWait.synchronized {
              if (!VizReads.readsCoverageIndicator.region.contains(viewRegion)) {
                val expanded = VizReads.expand(viewRegion)
                VizReads.readsCoverageCache = VizReads.materializer.getReads().get.getCoverage(expanded)
                VizReads.readsIndicator = VizCacheIndicator(expanded, 1)
              }
            }
            // filter data overlapping viewRegion and stringify
            val data = VizReads.readsCoverageCache.get(key).getOrElse(Array.empty).filter(_.overlaps(viewRegion))
            results = Some(write(data))
            if (results.isDefined) {
              Ok(results.get)
            } else VizReads.errors.noContent(viewRegion)
          } else VizReads.errors.outOfBounds
        }
      }
    }
  }

  get("/variants/:key/:ref") {
    VizTimers.VarRequest.time {
      if (!VizReads.materializer.variantContextExist)
        VizReads.errors.notFound
      else {
        val viewRegion = ReferenceRegion(params("ref"), params("start").toLong,
          VizUtils.getEnd(params("end").toLong, VizReads.globalDict(params("ref"))))
        val key: String = params("key")

        contentType = "json"

        // if region is in bounds of reference, return data
        val dictOpt = VizReads.globalDict(viewRegion.referenceName)
        if (dictOpt.isDefined) {
          var results: Option[String] = None
          val binning: Int =
            try {
              params("binning").toInt
            } catch {
              case e: Exception => 1
            }

          // region was already collected, grab from cache
          VizReads.variantsWait.synchronized {
            if (!VizReads.variantsIndicator.region.contains(viewRegion) || binning != VizReads.variantsIndicator.resolution) {
              val expanded = VizReads.expand(viewRegion)
              VizReads.variantsCache = VizReads.materializer.getVariantContext().get.getJson(expanded,
                VizReads.showGenotypes,
                binning)
              VizReads.variantsIndicator = VizCacheIndicator(expanded, binning)
            }
          }
          // filter data overlapping viewRegion and stringify
          //val data = VizReads.variantsCache.get(key).getOrElse(Array.empty).filter(_.overlaps(viewRegion))
          val data: Array[VariantContext] = VizReads.variantsCache.get(key).getOrElse(Array.empty)
            .filter(z => { ReferenceRegion(z.variant.variant).overlaps(viewRegion) })

          results = Some(VizReads.materializer.getVariantContext().get.stringifyLegacy(data, binning))

          if (results.isDefined) {
            // extract variants only and parse to stringified json
            Ok(results.get)
          } else VizReads.errors.noContent(viewRegion)
        } else VizReads.errors.outOfBounds
      }
    }
  }

  post("/ga4gh/variants/search") {

    val jsonPostString = request.body
    val searchVariantsRequest: SearchVariantsRequestGA4GH = net.liftweb.json.parse(jsonPostString)
      .extract[SearchVariantsRequestGA4GH]

    if (!VizReads.materializer.variantContextExist)
      VizReads.errors.notFound
    else {
      val viewRegion = ReferenceRegion(searchVariantsRequest.referenceName,
        searchVariantsRequest.start.toLong,
        VizUtils.getEnd(searchVariantsRequest.end.toLong,
          VizReads.globalDict(searchVariantsRequest.referenceName)))

      //todo: remove this hard-coded key
      val key: String = "ALL_chr17_7500000-7515000_phase3_shapeit2_mvncall_integrated_v5a_20130502_genotypes_vcf"
      contentType = "json"

      val dictOpt = VizReads.globalDict(viewRegion.referenceName)

      if (dictOpt.isDefined) {
        var results: Option[String] = None

        val binning: Int = 1

        VizReads.variantsWait.synchronized {
          if (!VizReads.variantsIndicator.region.contains(viewRegion) || binning != VizReads.variantsIndicator.resolution) {
            val expanded = VizReads.expand(viewRegion)
            VizReads.variantsCache = VizReads.materializer.getVariantContext().get.getJson(expanded,
              VizReads.showGenotypes,
              binning)
            VizReads.variantsIndicator = VizCacheIndicator(expanded, binning)
          }
        }

        // filter data overlapping viewRegion and stringify
        val data: Array[VariantContext] = VizReads.variantsCache.get(key).getOrElse(Array.empty)
          .filter(z => { ReferenceRegion(z.variant.variant).overlaps(viewRegion) })

        println("#In vizreads count data: " + data.length)

        val outFormat = "Legacy"

        if (outFormat == "Legacy") {
          println("### Priting legacy")
          results = Some(VizReads.materializer.getVariantContext().get.stringifyLegacy(data))
        } else {
          println("### Printing GA4GH")
          results = Some(VizReads.materializer.getVariantContext().get.stringifyGA4GH(data))
        }

        if (results.isDefined) {
          // extract variants only and parse to stringified json
          Ok(results.get)
        } else VizReads.errors.noContent(viewRegion)
      } else VizReads.errors.outOfBounds
    }
  }

  post("/ga4gh/variants/search/bin") {

    val jsonPostString = request.body

    val searchVariantsRequest: SearchVariantsRequestGA4GHBinning = net.liftweb.json.parse(jsonPostString)
      .extract[SearchVariantsRequestGA4GHBinning]

    if (!VizReads.materializer.variantContextExist)
      VizReads.errors.notFound
    else {

      val viewRegion = ReferenceRegion(searchVariantsRequest.referenceName,
        searchVariantsRequest.start.toLong,
        VizUtils.getEnd(searchVariantsRequest.end.toLong,
          VizReads.globalDict(searchVariantsRequest.referenceName)))

      println("#INn vizReads: " + viewRegion)

      //todo: remove this hard-coded key
      val key: String = "ALL_chr17_7500000-7515000_phase3_shapeit2_mvncall_integrated_v5a_20130502_genotypes_vcf"
      contentType = "json"

      val dictOpt = VizReads.globalDict(viewRegion.referenceName)

      if (dictOpt.isDefined) {
        var results: Option[String] = None

        val binning: Int =
          try {
            searchVariantsRequest.binning.toInt
          } catch {
            case e: Exception => 1
          }

        // binning ga4gh endpoint is not current cached
        // but something like below should be added back later
        /*
        VizReads.variantsWait.synchronized {
          if (!VizReads.variantsIndicator.region.contains(viewRegion) || binning != VizReads.variantsIndicator.resolution) {
            val expanded = VizReads.expand(viewRegion)
            VizReads.variantsCache = VizReads.materializer.getVariantContext().get.getJsonBinning(expanded,
              VizReads.showGenotypes,
              binning)
            VizReads.variantsIndicator = VizCacheIndicator(expanded, binning)
          }
        } */

        val expanded: ReferenceRegion = VizReads.expand(viewRegion)

        val features: Array[Feature] = VizReads.materializer.getVariantContext().get.getJsonBinning(expanded,
          VizReads.showGenotypes, binning).filter(z => { ReferenceRegion.unstranded(z).overlaps(viewRegion) })

        val featureGA4GH: Seq[SequenceAnnotations.Feature] = features.map(l => GA4GHConverter.toGA4GHFeature(l)).toList

        results = Some(VizReads.materializer.getVariantContext().get.stringifyFeatureGA4GH(featureGA4GH))

        if (results.isDefined) {
          // extract variants only and parse to stringified json
          Ok(results.get)
        } else VizReads.errors.noContent(viewRegion)
      } else VizReads.errors.outOfBounds
    }
  }

  get("/features/:key/:ref") {
    VizTimers.FeatRequest.time {
      if (!VizReads.materializer.featuresExist)
        VizReads.errors.notFound
      else {
        val viewRegion = ReferenceRegion(params("ref"), params("start").toLong,
          VizUtils.getEnd(params("end").toLong, VizReads.globalDict(params("ref"))))
        val key: String = params("key")
        contentType = "json"

        // if region is in bounds of reference, return data
        val dictOpt = VizReads.globalDict(viewRegion.referenceName)
        if (dictOpt.isDefined) {
          var results: Option[String] = None
          val binning: Int =
            try {
              params("binning").toInt
            } catch {
              case e: Exception => 1
            }
          VizReads.featuresWait.synchronized {
            // region was already collected, grab from cache
            if (!VizReads.featuresIndicator.region.contains(viewRegion) || binning != VizReads.featuresIndicator.resolution) {
              val expanded = VizReads.expand(viewRegion)
              VizReads.featuresCache = VizReads.materializer.getFeatures().get.getJson(expanded, binning)
              VizReads.featuresIndicator = VizCacheIndicator(expanded, binning)
            }
          }
          // filter data overlapping viewRegion and stringify

          val data = VizReads.featuresCache.get(key).getOrElse(Array.empty).filter(_.overlaps(viewRegion))
          results = Some(VizReads.materializer.getFeatures().get.stringify(data))
          if (results.isDefined) {
            Ok(results.get)
          } else VizReads.errors.noContent(viewRegion)
        } else VizReads.errors.outOfBounds
      }
    }
  }

  /**
   * Gets Coverage for a get Request. This is used to get both Reads based coverage and generic coverage.
   * @param viewRegion ReferenceRegion to view coverage over
   * @param key key for coverage file (see LazyMaterialization)
   * @return ActionResult of coverage json
   */
  def getCoverage(viewRegion: ReferenceRegion, key: String, binning: Int = 1): ActionResult = {
    VizTimers.CoverageRequest.time {
      if (!VizReads.materializer.coveragesExist) {
        VizReads.errors.notFound
      } else {
        contentType = "json"
        val dictOpt = VizReads.globalDict(viewRegion.referenceName)
        if (dictOpt.isDefined) {
          var results: Option[String] = None
          // region was already collected, grab from cache
          VizReads.coverageWait.synchronized {
            if (!VizReads.coverageIndicator.region.contains(viewRegion) || binning != VizReads.coverageIndicator.resolution) {
              val expanded = VizReads.expand(viewRegion)
              VizReads.coverageCache = VizReads.materializer.getCoverage().get.getCoverage(expanded, binning)
              VizReads.coverageIndicator = VizCacheIndicator(expanded, binning)
            }
          }
          // filter data overlapping viewRegion and stringify
          val data = VizReads.coverageCache.get(key).getOrElse(Array.empty).filter(_.overlaps(viewRegion))
          results = Some(write(data))
          if (results.isDefined) {
            Ok(results.get)
          } else VizReads.errors.noContent(viewRegion)
        } else VizReads.errors.outOfBounds
      }
    }
  }
}

class VizReads(protected val args: VizReadsArgs) extends BDGSparkCommand[VizReadsArgs] with Logging {
  val companion: BDGCommandCompanion = VizReads

  override def run(sc: SparkContext): Unit = {
    VizReads.sc = sc

    // initialize all datasets
    initAnnotations(sc)

    VizReads.cacheSize = args.cacheSize

    // set materializer
    VizReads.materializer = Materializer(Seq(initAlignments(sc, args.prefetchSize),
      initCoverages(sc, args.prefetchSize),
      initVariantContext(sc, args.prefetchSize),
      initFeatures(sc, args.prefetchSize)).flatten)

    // discover regions
    if (args.discoveryMode) {
      VizReads.prefetchedRegions = discoverFrequencies(sc)
    }

    val preload = Option(args.preload).getOrElse("").split(',').flatMap(r => LazyMaterialization.getContigPredicate(r))

    // run discovery mode if it is specified in the startup script
    if (!preload.isEmpty) {
      val preloaded = VizReads.globalDict.records.filter(r => preload.contains(r.name))
        .map(r => ReferenceRegion(r.name, 0, r.length))
      preprocess(preloaded)
    }

    // check whether genePath was supplied
    if (args.genePath != null) {
      VizReads.genes = Some(args.genePath)
    }

    // start server
    if (!args.testMode) startServer()

  }

  /*
   * Initialize required reference file
   */
  def initAnnotations(sc: SparkContext) = {
    val referencePath = Option(args.referencePath).getOrElse({
      throw new FileNotFoundException("reference file not provided")
    })

    VizReads.annotationRDD = new AnnotationMaterialization(sc, referencePath)
    VizReads.globalDict = VizReads.annotationRDD.getSequenceDictionary
  }

  /*
 * Initialize loaded alignment files
 */
  def initAlignments(sc: SparkContext, prefetch: Int): Option[AlignmentRecordMaterialization] = {
    if (Option(args.readsPaths).isDefined) {
      val readsPaths = args.readsPaths.split(",").toList

      if (readsPaths.nonEmpty) {
        object readsWait
        VizReads.syncObject += (AlignmentRecordMaterialization.name -> readsWait)
        Some(new AlignmentRecordMaterialization(sc, readsPaths, VizReads.globalDict, Some(prefetch)))
      } else None
    } else None
  }

  /*
 * Initialize coverage files
 */
  def initCoverages(sc: SparkContext, prefetch: Int): Option[CoverageMaterialization] = {
    if (Option(args.coveragePaths).isDefined) {
      val coveragePaths = args.coveragePaths.split(",").toList

      if (coveragePaths.nonEmpty) {
        object coverageWait
        VizReads.syncObject += (CoverageMaterialization.name -> coverageWait)
        Some(new CoverageMaterialization(sc, coveragePaths, VizReads.globalDict, Some(prefetch)))
      } else None
    } else None
  }

  /**
   * Initialize loaded variant files
   */
  def initVariantContext(sc: SparkContext, prefetch: Int): Option[VariantContextMaterialization] =

    {
      // set flag for visualizing genotypes
      VizReads.showGenotypes = args.showGenotypes

      if (Option(args.variantsPaths).isDefined) {
        val variantsPaths = args.variantsPaths.split(",").toList

        if (variantsPaths.nonEmpty) {
          object variantsWait
          VizReads.syncObject += (VariantContextMaterialization.name -> variantsWait)
          Some(new VariantContextMaterialization(sc, variantsPaths, VizReads.globalDict, Some(prefetch)))
        } else None
      } else None
    }

  /**
   * Initialize loaded feature files
   */
  def initFeatures(sc: SparkContext, prefetch: Int): Option[FeatureMaterialization] = {
    val featurePaths = Option(args.featurePaths)
    if (featurePaths.isDefined) {
      val featurePaths = args.featurePaths.split(",").toList
      if (featurePaths.nonEmpty) {
        object featuresWait
        VizReads.syncObject += (FeatureMaterialization.name -> featuresWait)
        Some(new FeatureMaterialization(sc, featurePaths, VizReads.globalDict, Some(prefetch)))
      } else None
    } else None
  }

  /**
   * Runs total data scan over all feature, variant and coverage files, calculating the normalied frequency at all
   * windows in the genome.
   *
   * @return Returns list of windowed regions in the genome and their corresponding normalized frequencies
   */
  def discoverFrequencies(sc: SparkContext): List[(ReferenceRegion, Double)] = {

    val discovery = Discovery(VizReads.annotationRDD.getSequenceDictionary)
    var regions: List[(ReferenceRegion, Double)] = List()

    // parse all materialization structures to calculate region frequencies
    VizReads.materializer.objects
      .foreach(m => m match {
        case vcm: VariantContextMaterialization => {
          regions = regions ++ discovery.getFrequencies(vcm.get()
            .map(r => ReferenceRegion(r._2.variant.variant)))
        }
        case fm: FeatureMaterialization => {
          regions = regions ++ discovery.getFrequencies(fm.get()
            .map(r => ReferenceRegion.unstranded(r._2)))
        }
        case _ => {
          // no op
        }
      })

    // group all regions together and reduce down for all data types
    regions = regions.groupBy(_._1).map(r => (r._1, r._2.map(a => a._2).sum)).toList

    // normalize and filter by regions with data
    val max = regions.map(_._2).reduceOption(_ max _).getOrElse(1.0)
    regions.map(r => (r._1, r._2 / max))
      .filter(_._2 > 0.0)
  }

  /**
   * preprocesses data by loading specified regions into memory for reads, coverage, variants and features
   *
   * @param regions Regions to be preprocessed
   */
  def preprocess(regions: Vector[ReferenceRegion]) = {
    // select two of the highest occupied regions to load
    // The number of selected regions is low to reduce unnecessary loading while
    // jump starting Thread setup for Spark on the specific data files

    for (region <- regions) {
      if (VizReads.materializer.featuresExist)
        VizReads.materializer.getFeatures().get.get(Some(region)).count()
      if (VizReads.materializer.readsExist)
        VizReads.materializer.getReads().get.get(Some(region)).count()
      if (VizReads.materializer.coveragesExist)
        VizReads.materializer.getCoverage.get.get(Some(region)).count()
      if (VizReads.materializer.variantContextExist)
        VizReads.materializer.getVariantContext().get.get(Some(region)).count()
    }
  }

  /**
   * Starts server once on startup
   */
  def startServer() = {
    VizReads.server = new org.eclipse.jetty.server.Server(args.port)
    val handlers = new org.eclipse.jetty.server.handler.ContextHandlerCollection()
    VizReads.server.setHandler(handlers)
    handlers.addHandler(new org.eclipse.jetty.webapp.WebAppContext("mango-cli/src/main/webapp", "/"))
    VizReads.server.start()
    println("View the visualization at: " + args.port)
    println("Quit at: /quit")
    VizReads.server.join()
  }

}
