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
import org.apache.spark.rdd.RDD
import org.apache.spark.SparkContext
import org.bdgenomics.adam.models.{ ReferenceRegion, SequenceDictionary }
import org.bdgenomics.formats.avro.{ Feature, Genotype }
import org.bdgenomics.mango.core.util.VizUtils
import org.bdgenomics.mango.filters.{ FeatureFilterType, GenotypeFilterType, FeatureFilter, GenotypeFilter }
import org.bdgenomics.mango.models._
import org.bdgenomics.mango.util.Bookkeep
import org.bdgenomics.utils.cli._
import org.bdgenomics.utils.instrumentation.Metrics
import org.bdgenomics.utils.misc.Logging
import org.fusesource.scalate.TemplateEngine
import org.kohsuke.args4j.{ Argument, Option => Args4jOption }
import org.scalatra._

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

object VizReads extends BDGCommandCompanion with Logging {

  val commandName: String = "viz"
  val commandDescription: String = "Genomic visualization for ADAM"
  implicit val formats = net.liftweb.json.DefaultFormats

  var sc: SparkContext = null
  var server: org.eclipse.jetty.server.Server = null
  var globalDict: SequenceDictionary = null

  // Gene URL
  var genes: Option[String] = None

  // Structures storing data types. All but reference is optional
  var annotationRDD: AnnotationMaterialization = null
  var readsData: Option[AlignmentRecordMaterialization] = None
  var variantData: Option[VariantMaterialization] = None
  var genotypeData: Option[GenotypeMaterialization] = None
  var featureData: Option[FeatureMaterialization] = None

  // variables tracking whether optional datatypes were loaded
  def readsExist: Boolean = readsData.isDefined
  def variantsExist: Boolean = variantData.isDefined
  def genotypeExist: Boolean = genotypeData.isDefined
  def featuresExist: Boolean = featureData.isDefined

  // reads cache
  object readsWait
  var readsCache: Map[String, String] = Map.empty[String, String]
  var readsRegion: ReferenceRegion = null

  // coverage cache
  object readsCoverageWait
  var readsCoverageCache: Map[String, String] = Map.empty[String, String]
  var readsCoverageRegion: ReferenceRegion = null

  // variant cache
  object variantsWait
  var variantsCache: Map[String, String] = Map.empty[String, String]
  var variantsRegion: ReferenceRegion = null

  // variant cache
  object genotypesWait
  var genotypesCache: Map[String, String] = Map.empty[String, String]
  var genotypesRegion: ReferenceRegion = null

  // features cache
  object featuresWait
  var featuresCache: Map[String, String] = Map.empty[String, String]
  var featuresRegion: ReferenceRegion = null

  // regions to prefetch during variant discovery. sent to front
  // end for visual processing
  var prefetchedRegions: List[ReferenceRegion] = List()

  // used to determine size of data tiles
  var chunkSize: Int = 1000

  // thresholds used for visualization binning and limits
  var screenSize: Int = 1000

  // HTTP ERROR RESPONSES
  object errors {
    var outOfBounds = NotFound("Region not found in Reference Sequence Dictionary")
    var largeRegion = RequestEntityTooLarge("Region too large")
    var unprocessableFile = UnprocessableEntity("File type not supported")
    var notFound = NotFound("File not found")
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
  def formatReferenceRegions(regions: List[ReferenceRegion]): String = {
    regions.map(r => r.referenceName + ":" + r.start + "-" + r.end).mkString(",")
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

  @Args4jOption(required = false, name = "-repartition", usage = "The number of partitions")
  var partitionCount: Int = 0

  @Args4jOption(required = false, name = "-read_files", usage = "A list of reads files to view, separated by commas (,)")
  var readsPaths: String = null

  @Args4jOption(required = false, name = "-var_files", usage = "A list of variants files to view, separated by commas (,)")
  var variantsPaths: String = null

  @Args4jOption(required = false, name = "-feat_files", usage = "The feature files to view, separated by commas (,)")
  var featurePaths: String = null

  @Args4jOption(required = false, name = "-port", usage = "The port to bind to for visualization. The default is 8080.")
  var port: Int = 8080

  @Args4jOption(required = false, name = "-test", usage = "For debugging purposes.")
  var testMode: Boolean = false

  @Args4jOption(required = false, name = "-discover", usage = "This turns on discovery mode on start up.")
  var discoveryMode: Boolean = false

  @Args4jOption(required = false, name = "-variantMode", usage = "This determines variant predicate for discovery mode.")
  var variantDiscoveryMode: Int = 0

  @Args4jOption(required = false, name = "-featureMode", usage = "This determines feature predicate for discovery mode.")
  var featureDiscoveryMode: Int = 0

  @Args4jOption(required = false, name = "-threshold", usage = "This threshold for density discovery mode.")
  var threshold: Int = 10
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
    // set initial referenceRegion so it is defined
    session("referenceRegion") = ReferenceRegion("chr", 1, 100)
    templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/overall.ssp",
      Map("dictionary" -> VizReads.formatDictionaryOpts(VizReads.globalDict),
        "regions" -> VizReads.formatReferenceRegions(VizReads.prefetchedRegions)))
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
      case e: Exception => session("referenceRegion") = ReferenceRegion(VizReads.globalDict.records.head.name, 0, 100)
    }

    val templateEngine = new TemplateEngine
    // set initial referenceRegion so it is defined
    val region = session("referenceRegion").asInstanceOf[ReferenceRegion]
    VizReads.readsRegion = region
    VizReads.variantsRegion = region
    VizReads.featuresRegion = region

    // generate file keys for front end
    val readsSamples = try {
      Some(VizReads.readsData.get.getFiles.map(r => LazyMaterialization.filterKeyFromFile(r)))
    } catch {
      case e: Exception => None
    }

    val variantsPaths = try {
      Some(VizReads.variantData.get.getFiles.map(r => LazyMaterialization.filterKeyFromFile(r)))
    } catch {
      case e: Exception => None
    }

    val featuresPaths = try {
      Some(VizReads.featureData.get.getFiles.map(r => LazyMaterialization.filterKeyFromFile(r)))
    } catch {
      case e: Exception => None
    }

    templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/browser.ssp",
      Map("dictionary" -> VizReads.formatDictionaryOpts(VizReads.globalDict),
        "genes" -> VizReads.genes,
        "readsPaths" -> readsSamples,
        "readsExist" -> VizReads.readsExist,
        "variantsPaths" -> variantsPaths,
        "variantsExist" -> VizReads.variantsExist,
        "featuresPaths" -> featuresPaths,
        "featuresExist" -> VizReads.featuresExist,
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

      if (!VizReads.readsExist) {
        VizReads.errors.notFound
      } else {
        val viewRegion = ReferenceRegion(params("ref"), params("start").toLong,
          VizUtils.getEnd(params("end").toLong, VizReads.globalDict(params("ref"))))
        val key: String = params("key")
        contentType = "json"

        val dictOpt = VizReads.globalDict(viewRegion.referenceName)
        if (dictOpt.isDefined) {
          var results: Option[String] = None
          VizReads.readsWait.synchronized {
            // region was already collected, grab from cache
            if (viewRegion != VizReads.readsRegion) {
              VizReads.readsCache = VizReads.readsData.get.getJson(viewRegion)
              VizReads.readsRegion = viewRegion
            }
            results = VizReads.readsCache.get(key)
          }
          if (results.isDefined) {
            Ok(results.get)
          } else VizReads.errors.notFound
        } else VizReads.errors.outOfBounds
      }
    }
  }

  get("/reads/coverage/:key/:ref") {
    VizTimers.ReadsRequest.time {

      if (!VizReads.readsExist) {
        VizReads.errors.notFound
      } else {
        val viewRegion = ReferenceRegion(params("ref"), params("start").toLong,
          VizUtils.getEnd(params("end").toLong, VizReads.globalDict(params("ref"))))
        val key: String = params("key")
        contentType = "json"

        val dictOpt = VizReads.globalDict(viewRegion.referenceName)
        if (dictOpt.isDefined) {
          var results: Option[String] = None
          VizReads.readsCoverageWait.synchronized {
            // region was already collected, grab from cache
            if (viewRegion != VizReads.readsCoverageRegion) {
              VizReads.readsCoverageCache = VizReads.readsData.get.getCoverage(viewRegion)
              VizReads.readsCoverageRegion = viewRegion
            }
            results = VizReads.readsCoverageCache.get(key)
          }
          if (results.isDefined) {
            Ok(results.get)
          } else VizReads.errors.notFound
        } else VizReads.errors.outOfBounds
      }
    }
  }

  get("/genotypes/:key/:ref") {
    VizTimers.VarRequest.time {
      if (!VizReads.variantsExist)
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
          VizReads.genotypesWait.synchronized {
            // region was already collected, grab from cache
            if (viewRegion != VizReads.genotypesRegion) {
              VizReads.genotypesCache = VizReads.genotypeData.get.getJson(viewRegion)
              VizReads.genotypesRegion = viewRegion
            }
            results = VizReads.genotypesCache.get(key)
          }
          if (results.isDefined) {
            Ok(results.get)
          } else ({}) // No data for this key
        } else VizReads.errors.outOfBounds
      }
    }
  }

  get("/variants/:key/:ref") {
    VizTimers.VarRequest.time {
      if (!VizReads.variantsExist)
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
            try
              params("binning").toInt
            catch {
              case e: Exception => 1
            }
          VizReads.variantsWait.synchronized {
            // region was already collected, grab from cache
            if (viewRegion != VizReads.variantsRegion) {
              VizReads.variantsCache = VizReads.variantData.get.getVariants(viewRegion, binning)
              VizReads.variantsRegion = viewRegion
            }
            results = VizReads.variantsCache.get(key)
          }
          if (results.isDefined) {
            // extract variants only and parse to stringified json
            Ok(results.get)
          } else Ok({}) // No data for this key
        } else VizReads.errors.outOfBounds
      }
    }
  }

  get("/features/:key/:ref") {
    VizTimers.FeatRequest.time {
      if (!VizReads.featuresExist)
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
          VizReads.featuresWait.synchronized {
            // region was already collected, grab from cache
            if (viewRegion != VizReads.featuresRegion) {
              VizReads.featuresCache = VizReads.featureData.get.getJson(viewRegion)
              VizReads.featuresRegion = viewRegion
            }
            results = VizReads.featuresCache.get(key)
          }
          if (results.isDefined) {
            Ok(results.get)
          } else Ok({}) // No data for this key
        } else VizReads.errors.outOfBounds
      }
    }
  }
}

class VizReads(protected val args: VizReadsArgs) extends BDGSparkCommand[VizReadsArgs] with Logging {
  val companion: BDGCommandCompanion = VizReads

  override def run(sc: SparkContext): Unit = {
    VizReads.sc = sc

    val partitionCount =
      if (args.partitionCount <= 0)
        VizReads.sc.defaultParallelism
      else
        args.partitionCount

    // initialize all datasets
    initAnnotations
    initAlignments
    initVariants
    initFeatures

    // run discovery mode if it is specified in the startup script
    if (args.discoveryMode) {
      VizReads.prefetchedRegions = discover(Option(args.variantDiscoveryMode), Option(args.variantDiscoveryMode))
      preprocess(VizReads.prefetchedRegions)
    }

    // check whether genePath was supplied
    if (args.genePath != null) {
      VizReads.genes = Some(args.genePath)
    }

    // start server
    if (!args.testMode) startServer()

    /*
     * Initialize required reference file
     */
    def initAnnotations() = {
      val referencePath = Option(args.referencePath).getOrElse({
        throw new FileNotFoundException("reference file not provided")
      })

      VizReads.annotationRDD = new AnnotationMaterialization(sc, referencePath)
      VizReads.globalDict = VizReads.annotationRDD.getSequenceDictionary
    }

    /*
     * Initialize loaded alignment files
     */
    def initAlignments = {
      if (Option(args.readsPaths).isDefined) {
        val readsPaths = args.readsPaths.split(",").toList
          .filter(path => path.endsWith(".bam") || path.endsWith(".adam"))

        // warn for incorrect file formats
        args.readsPaths.split(",").toList
          .filter(path => !path.endsWith(".bam") && !path.endsWith(".adam"))
          .foreach(file => log.warn(s"${file} does is not a valid variant file. Removing... "))

        if (!readsPaths.isEmpty) {
          VizReads.readsData = Some(new AlignmentRecordMaterialization(sc, readsPaths, VizReads.globalDict))
        }
      }
    }

    /**
     * Initialize loaded variant files
     */
    def initVariants() = {
      if (Option(args.variantsPaths).isDefined) {
        // filter out incorrect file formats
        val variantsPaths = args.variantsPaths.split(",").toList
          .filter(path => path.endsWith(".vcf") || path.endsWith(".adam"))

        // warn for incorrect file formats
        args.variantsPaths.split(",").toList
          .filter(path => !path.endsWith(".vcf") && !path.endsWith(".adam"))
          .foreach(file => log.warn(s"${file} does is not a valid variant file. Removing... "))

        if (!variantsPaths.isEmpty) {
          VizReads.variantData = Some(VariantMaterialization(sc, variantsPaths, VizReads.globalDict, partitionCount))
          VizReads.genotypeData = Some(GenotypeMaterialization(sc, variantsPaths, VizReads.globalDict, partitionCount))
        }
      }
    }

    /**
     * Initialize loaded feature files
     */
    def initFeatures() = {
      val featurePaths = Option(args.featurePaths)
      if (featurePaths.isDefined) {
        // filter out incorrect file formats
        val featurePaths = args.featurePaths.split(",").toList
          .filter(path => path.endsWith(".bed") || path.endsWith(".adam"))

        // warn for incorrect file formats
        args.featurePaths.split(",").toList
          .filter(path => !path.endsWith(".bed") && !path.endsWith(".adam"))
          .foreach(file => log.warn(s"${file} is not a valid feature file. Removing... "))

        if (!featurePaths.isEmpty) {
          VizReads.featureData = Some(new FeatureMaterialization(sc, featurePaths, VizReads.globalDict))
        }
      }
    }

    /**
     * Runs total data scan over all feature and variant files satisfying a certain predicate.
     *
     * @param variantFilter predicate to be satisfied during variant scan
     * @param featureFilter predicate to be satisfied during feature scan
     * @return Returns list of regions in the genome satisfying predicates
     */
    def discover(variantFilter: Option[Int], featureFilter: Option[Int]): List[ReferenceRegion] = {

      // filtering for variants
      val variantRegions: Option[RDD[(ReferenceRegion, Long)]] =
        if (variantFilter.isDefined) {
          if (!VizReads.variantsExist) {
            log.warn("specified discovery predicate for variants but no variant files were provided")
            None
          } else {
            var variants: RDD[Genotype] = VizReads.sc.parallelize[(Genotype)](Array[(Genotype)]())
            VizReads.variantData.get.files.foreach(fp => variants = variants.union(GenotypeMaterialization.load(sc, None, fp)))
            val threshold = args.threshold
            Some(GenotypeFilter.filter(variants, GenotypeFilterType(variantFilter.get), VizReads.chunkSize, threshold))
          }
        } else None

      // filtering for features
      val featureRegions: Option[RDD[(ReferenceRegion, Long)]] =
        if (featureFilter.isDefined) {
          if (!VizReads.featuresExist) {
            log.warn("specified discovery predicate for features but no variant files were provided")
            None
          } else {
            var features: RDD[Feature] = sc.parallelize[(Feature)](Array[(Feature)]())
            VizReads.featureData.get.files.foreach(fp => features = features.union(FeatureMaterialization.load(sc, None, fp).rdd))
            val threshold = args.threshold
            Some(FeatureFilter.filter(features, FeatureFilterType(featureFilter.get), VizReads.chunkSize, threshold))
          }
        } else None

      // collect and merge all regions together
      val emptyRDD = sc.parallelize[(ReferenceRegion, Long)](Array[(ReferenceRegion, Long)]())
      val regions = featureRegions.getOrElse(emptyRDD).union(variantRegions.getOrElse(emptyRDD)).map(_._1)
      Bookkeep.mergeRegions(regions.collect.toList.distinct)
    }

    /**
     * preprocesses data by loading specified regions into memory for reads, variants and features
     *
     * @param regions Regions to be preprocessed
     */
    def preprocess(regions: List[ReferenceRegion]) = {
      for (region <- regions) {
        if (VizReads.featureData.isDefined)
          VizReads.featureData.get.get(region)
        if (VizReads.readsData.isDefined)
          VizReads.readsData.get.get(region)
        if (VizReads.variantData.isDefined)
          VizReads.variantData.get.get(region)
        if (VizReads.genotypeData.isDefined)
          VizReads.genotypeData.get.get(region)
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
}
