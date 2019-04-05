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

import java.net.URI
import ga4gh.Reads.ReadAlignment
import net.liftweb.json.Serialization.write
import net.liftweb.json._
import org.apache.spark.SparkContext
import org.bdgenomics.adam.models.{ SequenceRecord, ReferencePosition, ReferenceRegion, SequenceDictionary }
import org.bdgenomics.mango.cli.util.Materializer
import org.bdgenomics.mango.converters.{ SearchFeaturesRequestGA4GH, SearchVariantsRequestGA4GH, SearchReadsRequestGA4GH }
import org.bdgenomics.mango.core.util.{ Genome, GenomeConfig, VizUtils, VizCacheIndicator }
import org.bdgenomics.mango.filters._
import org.bdgenomics.mango.models._
import org.bdgenomics.utils.cli._
import org.bdgenomics.utils.instrumentation.Metrics
import org.bdgenomics.utils.misc.Logging
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.webapp.WebAppContext
import org.eclipse.jetty.server.handler.ContextHandlerCollection
import org.eclipse.jetty.util.resource.Resource
import org.fusesource.scalate.TemplateEngine
import org.kohsuke.args4j.{ Argument, Option => Args4jOption }
import org.scalatra._
import scala.io.Source

object VizTimers extends Metrics {
  //HTTP requests
  val ReadsRequest = timer("GET reads")
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

/**
 * Contains caching, error and util function information for formatting and serving Json data
 */
object VizReads extends BDGCommandCompanion with Logging {

  val commandName: String = "Mango"
  val commandDescription: String = "Genomic visualization for ADAM"
  implicit val formats = net.liftweb.json.DefaultFormats

  val GENES_REQUEST = "REFSEQ_REQUEST" // key for genes request from the loaded genome

  var sc: SparkContext = null
  var server: org.eclipse.jetty.server.Server = null

  // Structures storing data types. All but reference is optional
  var genome: Genome = null

  // holds coverage paths
  var coveragePaths: Array[String] = Array()
  var materializer: Materializer = null

  var cacheSize: Int = 1000

  /**
   * Caching information for frontend
   */
  // stores synchonization objects
  var syncObject: Map[String, Object] = Map.empty[String, Object]

  // placeholder for indicators
  val region = ReferenceRegion("N", 0, 1)

  // reads cache
  object readsWait
  var readsCache: Map[String, Array[ReadAlignment]] = Map.empty[String, Array[ReadAlignment]]
  var readsIndicator = VizCacheIndicator(region, 1)

  // variant cache
  object variantsWait
  var variantsCache: Map[String, Array[ga4gh.Variants.Variant]] = Map.empty[String, Array[ga4gh.Variants.Variant]]
  var variantsIndicator = VizCacheIndicator(region, 1)

  // features cache
  object featuresWait
  var featuresCache: Map[String, Array[ga4gh.SequenceAnnotations.Feature]] = Map.empty[String, Array[ga4gh.SequenceAnnotations.Feature]]
  var featuresIndicator = VizCacheIndicator(region, 1)

  // regions to prefetch during discovery. sent to front
  // end for visual processing
  var prefetchedRegions: List[(ReferenceRegion, Double)] = List()

  // HTTP ERROR RESPONSES
  object errors {
    var outOfBounds = NotFound("Region not found in Reference Sequence Dictionary")
    var largeRegion = RequestEntityTooLarge("Region too large")
    var unprocessableFile = UnprocessableEntity("File type not supported")
    def notFound(name: String) = NotFound(s"$name not found")
    def unknownError(str: String) = NotFound(str)
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

class VizReadsArgs extends Args4jBase with ParquetArgs {

  @Argument(required = true, metaVar = "genome", usage = "Path to compressed .genome file. To build a new genome file, run bin/make_genome.", index = 0)
  var genomePath: String = null

  @Args4jOption(required = false, name = "-reads", usage = "A list of reads files to view, separated by commas (,)")
  var readsPaths: String = null

  @Args4jOption(required = false, name = "-coverage", usage = "A list of coverage files to view, separated by commas (,)")
  var coveragePaths: String = null

  @Args4jOption(required = false, name = "-variants", usage = "A list of variants files to view, separated by commas (,). " +
    "Vcf files require a corresponding tbi index.")
  var variantsPaths: String = null

  @Args4jOption(required = false, name = "-repartition", usage = "Repartitions data to default number of partitions.")
  var repartition: Boolean = false

  @Args4jOption(required = false, name = "-features", usage = "The feature files to view, separated by commas (,)")
  var featurePaths: String = null

  @Args4jOption(required = false, name = "-port", usage = "The port to bind to for visualization. The default is 8080.")
  var port: Int = 8080

  @Args4jOption(required = false, name = "-test", usage = "For debugging purposes.")
  var testMode: Boolean = false

  @Args4jOption(required = false, name = "-debugFrontend", usage = "For debugging purposes. Sets front end in source code to " +
    "avoid recompilation.")
  var debugFrontend: Boolean = false

  @Args4jOption(required = false, name = "-discover", usage = "This turns on discovery mode on start up.")
  var discoveryMode: Boolean = false

  @Args4jOption(required = false, name = "-prefetchSize", usage = "Bp to prefetch in executors.")
  var prefetchSize: Int = 10000

  @Args4jOption(required = false, name = "-cacheSize", usage = "Bp to cache on driver.")
  var cacheSize: Int = VizReads.cacheSize

  @Args4jOption(required = false, name = "-preload", usage = "Chromosomes to prefetch, separated by commas (,).")
  var preload: String = null

  @Args4jOption(required = false, name = "-parquetIsBinned", usage = "This turns on binned parquet pre-fetch warmup step")
  var parquetIsBinned: Boolean = false

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
    val firstChr = VizReads.genome.chromSizes.records.head.name
    session("referenceRegion") = ReferenceRegion(firstChr, 1, 100)
    templateEngine.layout("/WEB-INF/layouts/overall.ssp",
      Map("dictionary" -> VizReads.formatDictionaryOpts(VizReads.genome.chromSizes),
        "regions" -> VizReads.formatClickableRegions(VizReads.prefetchedRegions)))
  }

  // Used to set the viewRegion in the backend when a user clicks on the home page
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
        val firstChr = VizReads.genome.chromSizes.records.head.name
        session("referenceRegion") = ReferenceRegion(firstChr, 0, 100)
    }

    val templateEngine = new TemplateEngine

    // generate file keys for front end
    val readsSamples: Option[List[MaterializedFile]] = try {
      Some(VizReads.materializer.getReads().get.getFiles(false))
    } catch {
      case e: Exception => None
    }

    val variantSamples: Option[Map[MaterializedFile, String]] = try {
      Some(VizReads.materializer.getVariantContext().get.samples.map(r => (r._1, r._2.map(_.getId).mkString(","))))
    } catch {
      case e: Exception => None
    }

    val featureSamples: Option[List[(MaterializedFile, Boolean)]] = try {
      Some(VizReads.materializer.getFeatures().get.getFiles(false).map(r => {
        (r, VizReads.coveragePaths.contains(r))
      }))
    } catch {
      case e: Exception => None
    }

    templateEngine.layout("/WEB-INF/layouts/browser.ssp",
      Map("dictionary" -> VizReads.formatDictionaryOpts(VizReads.genome.chromSizes),
        "twoBitUrl" -> VizReads.genome.twoBitPath,
        "genes" -> (if (VizReads.genome.genes.isDefined) Some(VizReads.GENES_REQUEST) else None),
        "reads" -> readsSamples,
        "variants" -> variantSamples,
        "features" -> featureSamples,
        "region" -> session("referenceRegion").asInstanceOf[ReferenceRegion]))
  }

  // used in browser.ssp to set contig list for wheel
  get("/sequenceDictionary") {
    Ok(write(VizReads.genome.chromSizes.records))
  }

  // post command for genome reads.
  // Takes SearchReadsRequestGA4GH as parameters.
  post("/reads/search") {
    try {
      VizTimers.ReadsRequest.time {

        val searchReadsRequest: SearchReadsRequestGA4GH = net.liftweb.json.parse(request.body).extract[SearchReadsRequestGA4GH]

        if (!VizReads.materializer.readsExist) {
          VizReads.errors.notFound("ReadsMaterialization")
        } else {
          val viewRegion = ReferenceRegion(searchReadsRequest.referenceId, searchReadsRequest.start.toLong,
            VizUtils.getEnd(searchReadsRequest.end.toLong, VizReads.genome.chromSizes(searchReadsRequest.referenceId)))
          val key: String = searchReadsRequest.readGroupIds(0) // there will never be more than 1 readGroup for the browser
          contentType = "json"

          val dictOpt = VizReads.genome.chromSizes(viewRegion.referenceName)
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
            val dataForKey = VizReads.readsCache.get(key)

            val data = dataForKey.getOrElse(Array.empty).filter(r => {
              ReferencePosition(r.getAlignment.getPosition.getReferenceName, r.getAlignment.getPosition.getPosition).overlaps(viewRegion)
            })
            results = Some(VizReads.materializer.getReads().get.stringify(data))

            if (results.isDefined) {
              Ok(results.get)
            } else VizReads.errors.noContent(viewRegion)
          } else VizReads.errors.outOfBounds
        }
      }
    } catch { // catch all for errors
      case e: Exception => {
        e.printStackTrace()
        VizReads.errors.unknownError(e.getMessage)
      }
    }
  }

  post("/variants/search") {
    try {
      VizTimers.VarRequest.time {

        val searchVariantsRequest: SearchVariantsRequestGA4GH =
          net.liftweb.json.parse(request.body).extract[SearchVariantsRequestGA4GH]

        if (!VizReads.materializer.variantContextExist)
          VizReads.errors.notFound("VariantContextMaterialization")
        else {
          val viewRegion = ReferenceRegion(searchVariantsRequest.referenceName, searchVariantsRequest.start,
            VizUtils.getEnd(searchVariantsRequest.end, VizReads.genome.chromSizes(searchVariantsRequest.referenceName)))

          val key: String = searchVariantsRequest.variantSetId
          contentType = "json"

          // if region is in bounds of reference, return data
          val dictOpt = VizReads.genome.chromSizes(viewRegion.referenceName)
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
                VizReads.variantsCache = VizReads.materializer.getVariantContext().get.getJson(expanded)
                VizReads.variantsIndicator = VizCacheIndicator(expanded, binning)
              }
            }
            // filter data overlapping viewRegion and stringify
            val dataForKey = VizReads.variantsCache.get(key)
            val data = dataForKey.getOrElse(Array.empty)
              .filter(r => ReferenceRegion(r.getReferenceName, r.getStart, r.getEnd).overlaps(viewRegion))
            results = Some(VizReads.materializer.getVariantContext().get.stringify(data))
            if (results.isDefined) {
              // extract variants only and parse to stringified json
              Ok(results.get)
            } else VizReads.errors.noContent(viewRegion)
          } else VizReads.errors.outOfBounds
        }
      }
    } catch { // catch all for errors
      case e: Exception => {
        e.printStackTrace()
        VizReads.errors.unknownError(e.getMessage)
      }
    }
  }

  post("/features/search") {
    try {
      VizTimers.FeatRequest.time {

        val searchFeaturesRequest: SearchFeaturesRequestGA4GH =
          net.liftweb.json.parse(request.body).extract[SearchFeaturesRequestGA4GH]

        val key: String = searchFeaturesRequest.featureSetId

        val viewRegion = ReferenceRegion(searchFeaturesRequest.referenceName, searchFeaturesRequest.start,
          VizUtils.getEnd(searchFeaturesRequest.end, VizReads.genome.chromSizes(searchFeaturesRequest.referenceName)))

        // if this is a genes request, return genes
        if (key == VizReads.GENES_REQUEST && VizReads.genome.genes.isDefined) {

          val genes = VizReads.genome.genes.get.get(viewRegion).toArray.map(_._2)

          Ok(FeatureMaterialization.stringify(genes))

          // otherwise, process features
        } else {

          if (!VizReads.materializer.featuresExist)
            VizReads.errors.notFound("FeaturesMaterialization")
          else {

            contentType = "json"

            // if region is in bounds of reference, return data
            val dictOpt = VizReads.genome.chromSizes(viewRegion.referenceName)
            if (dictOpt.isDefined) {
              var results: Option[String] = None
              val binning: Int =
                try {
                  params("binning").toInt
                } catch {
                  case e: Exception => 1
                }
              VizReads.featuresWait.synchronized {
                // region was not already collected, reset cache
                if (!VizReads.featuresIndicator.region.contains(viewRegion) || binning != VizReads.featuresIndicator.resolution) {
                  val expanded = VizReads.expand(viewRegion)
                  VizReads.featuresCache = VizReads.materializer.getFeatures().get.getJson(expanded)
                  VizReads.featuresIndicator = VizCacheIndicator(expanded, binning)
                }
              }
              // filter data overlapping viewRegion and stringify
              val dataForKey = VizReads.featuresCache.get(key)
              val data = dataForKey.getOrElse(Array.empty)
                .filter(r => ReferenceRegion(r.getReferenceName, r.getStart, r.getEnd).overlaps(viewRegion))
              results = Some(VizReads.materializer.getFeatures().get.stringify(data))
              if (results.isDefined) {
                Ok(results.get)
              } else VizReads.errors.noContent(viewRegion)
            } else VizReads.errors.outOfBounds
          }
        }
      }
    } catch { // catch all for errors
      case e: Exception => {
        e.printStackTrace()
        VizReads.errors.unknownError(e.getMessage)
      }
    }
  }

}

class VizReads(protected val args: VizReadsArgs) extends BDGSparkCommand[VizReadsArgs] with Logging {
  val companion: BDGCommandCompanion = VizReads

  override def run(sc: SparkContext): Unit = {
    VizReads.sc = sc

    VizReads.genome = GenomeConfig.loadZippedGenome(args.genomePath)

    VizReads.cacheSize = args.cacheSize

    // set materializer
    VizReads.materializer = Materializer(Seq(initAlignments(sc, args.prefetchSize),
      initVariantContext(sc, args.prefetchSize),
      initFeatures(sc, args.prefetchSize)).flatten)

    // discover regions
    if (args.discoveryMode) {
      VizReads.prefetchedRegions = discoverFrequencies(sc)
    }

    val preload = Option(args.preload).getOrElse("").split(',').flatMap(r => LazyMaterialization.getReferencePredicate(r))

    // run discovery mode if it is specified in the startup script
    if (!preload.isEmpty) {
      val preloaded = VizReads.genome.chromSizes.records.filter(r => preload.contains(r.name))
        .map(r => ReferenceRegion(r.name, 0, r.length))
      preprocess(preloaded)
    }

    // initialize binned parquet by doing a small query to force warm-up
    if (args.parquetIsBinned) {
      VizReads.readsCache = VizReads.materializer.getReads().get.getJson(ReferenceRegion(VizReads.materializer.getReads().get.getDictionary.records.head.name, 2L, 100L))
    }

    // start server
    if (!args.testMode) {
      if (args.debugFrontend)
        startTestServer()
      else
        startServer()
    }

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
        Some(new AlignmentRecordMaterialization(sc, readsPaths, VizReads.genome.chromSizes, args.repartition, Some(prefetch)))
      } else None
    } else None
  }

  /**
   * Initialize loaded variant files
   */
  def initVariantContext(sc: SparkContext, prefetch: Int): Option[VariantContextMaterialization] = {

    if (Option(args.variantsPaths).isDefined) {
      val variantsPaths = args.variantsPaths.split(",").toList

      if (variantsPaths.nonEmpty) {
        object variantsWait
        VizReads.syncObject += (VariantContextMaterialization.name -> variantsWait)
        Some(new VariantContextMaterialization(sc, variantsPaths, VizReads.genome.chromSizes, args.repartition, Some(prefetch)))
      } else None
    } else None
  }

  /**
   * Initialize loaded feature files
   */
  def initFeatures(sc: SparkContext, prefetch: Int): Option[FeatureMaterialization] = {
    var paths = Array[(String, Boolean)]()

    // boolean indicates whether or not it will be coverage
    if (Option(args.featurePaths).isDefined) {
      paths = paths ++ args.featurePaths.split(",").map(r => (r, false))
    }
    if (Option(args.coveragePaths).isDefined) {

      VizReads.coveragePaths = args.coveragePaths.split(",")
      paths = paths ++ VizReads.coveragePaths.map(r => (r, true))
    }
    if (paths.length > 0) {
      object featuresWait
      VizReads.syncObject += (FeatureMaterialization.name -> featuresWait)
      Some(new FeatureMaterialization(sc, paths.map(r => r._1).toList, VizReads.genome.chromSizes, args.repartition, Some(prefetch)))
    } else None
  }

  /**
   * Runs total data scan over all feature and variant files, calculating the normalied frequency at all
   * windows in the genome.
   *
   * @return Returns list of windowed regions in the genome and their corresponding normalized frequencies
   */
  def discoverFrequencies(sc: SparkContext): List[(ReferenceRegion, Double)] = {

    val discovery = Discovery(VizReads.genome.chromSizes)
    var regions: List[(ReferenceRegion, Double)] = List()

    // parse all materialization structures to calculate region frequencies
    VizReads.materializer.objects
      .foreach(m => m match {
        case vcm: VariantContextMaterialization => {
          regions = regions ++ discovery.getFrequencies(vcm.get()
            .map(r => ReferenceRegion(r._2.position)))
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
   * preprocesses data by loading specified regions into memory for reads, variants and features
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
      if (VizReads.materializer.variantContextExist)
        VizReads.materializer.getVariantContext().get.get(Some(region)).count()
    }
  }

  def startTestServer() = {
    VizReads.server = new org.eclipse.jetty.server.Server(args.port)
    val handlers = new org.eclipse.jetty.server.handler.ContextHandlerCollection()
    VizReads.server.setHandler(handlers)
    handlers.addHandler(new org.eclipse.jetty.webapp.WebAppContext("mango-cli/src/main/webapp", "/"))
    VizReads.server.start()
    println("View the visualization at: " + args.port)
    println("Quit at: /quit")
    VizReads.server.join()
  }

  /**
   * Starts server once on startup
   */
  def startServer() = {
    VizReads.server = new Server(args.port)

    val webRootLocation = this.getClass().getResource("/mango-index.html")

    if (webRootLocation == null) {
      throw new IllegalStateException("Unable to determine webroot URL location")
    }

    val webRootUri = URI.create(webRootLocation.toURI().toASCIIString().replaceFirst("/mango-index.html$", "/"))

    val context = new WebAppContext()
    context.setContextPath("/")
    context.setBaseResource(Resource.newResource(webRootUri))

    val handlers = new ContextHandlerCollection()
    VizReads.server.setHandler(handlers)
    handlers.addHandler(context)
    VizReads.server.start()
    println("View the visualization at: " + args.port)
    println("Quit at: /quit")
    VizReads.server.join()
  }

}
