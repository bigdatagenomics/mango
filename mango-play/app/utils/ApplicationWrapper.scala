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
package utils

import java.io.{ PrintWriter, FileNotFoundException }

import org.apache.spark.SparkContext
import org.bdgenomics.adam.models.{ ReferenceRegion, SequenceDictionary }
import org.bdgenomics.mango.filters._
import org.bdgenomics.mango.models._
import org.bdgenomics.utils.cli._
import org.bdgenomics.utils.instrumentation.{ MetricsListener, Metrics }
import org.bdgenomics.utils.misc.Logging
import org.kohsuke.args4j.{ Argument, Option => Args4jOption }

/**
 * Timers for fetching data to be returned as Json
 */
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

/**
 * Arguments for mango startup
 */
class ServerArgs extends Args4jBase with ParquetArgs {
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

  @Args4jOption(required = false, name = "-discover", usage = "This turns on discovery mode on start up.")
  var discoveryMode: Boolean = false

  @Args4jOption(required = false, name = "-prefetchSize", usage = "Bp to prefetch in executors.")
  var prefetchSize: Int = 10000

  @Args4jOption(required = false, name = "-cacheSize", usage = "Bp to cache on driver.")
  var cacheSize: Int = MangoServletWrapper.cacheSize

  @Args4jOption(required = false, name = "-preload", usage = "Chromosomes to prefetch, separated by commas (,).")
  var preload: String = null
}

/**
 * Contains caching, error and util function information for formatting and serving Json data
 */
object MangoServletWrapper extends BDGCommandCompanion with Logging {

  val commandName: String = "Mango"
  val commandDescription: String = "Genomic visualization for ADAM"

  var sc: SparkContext = null

  var materializer: Materializer = null

  var cacheSize: Int = 1000

  var metrics: Option[MetricsListener] = None

  var globalDict: SequenceDictionary = null

  // Structures storing data types. All but reference is optional
  var annotationRDD: AnnotationMaterialization = null

  // Gene URL
  var genes: Option[String] = None

  var showGenotypes: Boolean = false

  /**
   * Caching information for frontend
   */
  // stores synchonization objects
  var syncObject: Map[String, Object] = Map.empty[String, Object]

  // regions to prefetch during discovery. sent to front
  // end for visual processing
  var prefetchedRegions: List[(ReferenceRegion, Double)] = List()

  /**
   * Prints spark metrics to System.out
   */
  def printMetrics {
    if (metrics.isDefined) {
      // Set the output buffer size to 4KB by default
      val out = new PrintWriter(System.out)
      out.println("Metrics:")
      out.println("")
      Metrics.print(out, metrics.map(_.metrics.sparkMetrics.stageTimes))
      out.println()
      metrics.foreach(_.metrics.sparkMetrics.print(out))
      out.flush()
    }
  }

  /**
   * Populates MangoServletWrapper by using string parameters
   * @param cmdLine String of parameters (ie "reference.fa -reads reads.bam -discover"
   * @return MangoServletWrapper class
   */
  def apply(cmdLine: String): MangoServletWrapper = {
    val params = cmdLine.split("\\s").filter(r => !r.isEmpty)
    this.apply(params)
  }

  /**
   * Populates MangoServletWrapper by using list of string parameters
   * @param cmdLine List of command line parameters
   * @return MangoServletWrapper class
   */
  def apply(cmdLine: Array[String]): MangoServletWrapper = {
    new MangoServletWrapper(Args4j[ServerArgs](cmdLine))
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

  /**
   * Expands region to match the cach size. Region will be expanded by rounding down the start position
   * to be a factor of the cache size. If region is larger than cache size, the region size will
   * be a factor of the cache size.
   *
   * @param region ReferenceRegion to expand
   * @param minLength minimum length to expand the size of the region to
   * @return expanded region
   */
  def expand(region: ReferenceRegion, minLength: Long = MangoServletWrapper.cacheSize): Array[ReferenceRegion] = {
    require(minLength > 0, s"minimum length ${minLength} must be greater than 0")
    val finalStart = region.start - (region.start % minLength)
    val finalEnd = Math.max(region.end, finalStart + minLength)
    val blocks = ((finalEnd - finalStart) / minLength).toInt + 1
    (0 until blocks).map(r => {
      val start = finalStart + (minLength * r)
      val end = start + minLength
      region.copy(start = start, end = end)
    }).toArray
  }
}

/**
 * Initializes all data types to be served to frontend. Optionally runs discovery mode.
 *
 * @param args ServerArgs
 */
class MangoServletWrapper(val args: ServerArgs) extends BDGSparkCommand[ServerArgs] with Logging {
  val companion: BDGCommandCompanion = MangoServletWrapper

  override def run(sc: SparkContext) = {

    // Initialize Metrics
    MangoServletWrapper.metrics = initializeMetrics(sc)

    // Set SparkContext
    MangoServletWrapper.sc = sc

    // set driver cache size
    MangoServletWrapper.cacheSize = args.cacheSize

    // initialize required annotation dataset
    initAnnotations(sc)

    // check whether genePath was supplied
    if (args.genePath != null) {
      MangoServletWrapper.genes = Some(args.genePath)
    }

    // set materializer
    MangoServletWrapper.materializer = Materializer(Seq(initAlignments(sc, args.prefetchSize), initCoverages(sc, args.prefetchSize),
      initVariantContext(sc, args.prefetchSize), initFeatures(sc, args.prefetchSize)).flatten)

    val preload = Option(args.preload).getOrElse("").split(',').flatMap(r => LazyMaterialization.getContigPredicate(r))

    // run discovery mode if it is specified in the startup script
    if (!preload.isEmpty) {
      val preloaded = MangoServletWrapper.globalDict.records.filter(r => preload.contains(r.name))
        .map(r => ReferenceRegion(r.name, 0, r.length))
      preprocess(preloaded)
    }

    // run discovery mode if it is specified in the startup script
    if (args.discoveryMode) {
      MangoServletWrapper.prefetchedRegions = discoverFrequencies(sc)
    }
  }

  /*
  * Initialize required reference file
  */
  def initAnnotations(sc: SparkContext) = {
    val referencePath = Option(args.referencePath).getOrElse({
      throw new FileNotFoundException("reference file not provided")
    })

    MangoServletWrapper.annotationRDD = new AnnotationMaterialization(sc, referencePath)
    MangoServletWrapper.globalDict = MangoServletWrapper.annotationRDD.getSequenceDictionary
  }

  /*
 * Initialize loaded alignment files
 */
  def initAlignments(sc: SparkContext, prefetch: Int): Option[AlignmentRecordMaterialization] = {
    if (Option(args.readsPaths).isDefined) {
      val readsPaths = args.readsPaths.split(",").toList
      if (readsPaths.nonEmpty) {
        object readsWait
        MangoServletWrapper.syncObject += (AlignmentRecordMaterialization.name -> readsWait)
        Some(new AlignmentRecordMaterialization(sc, readsPaths, MangoServletWrapper.globalDict, Some(prefetch)))
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
        MangoServletWrapper.syncObject += (CoverageMaterialization.name -> coverageWait)
        Some(new CoverageMaterialization(sc, coveragePaths, MangoServletWrapper.globalDict, Some(prefetch)))
      } else None
    } else None
  }

  /**
   * Initialize loaded variant files
   */
  def initVariantContext(sc: SparkContext, prefetch: Int): Option[VariantContextMaterialization] = {
    // set flag for visualizing genotypes
    MangoServletWrapper.showGenotypes = args.showGenotypes

    if (Option(args.variantsPaths).isDefined) {
      val variantsPaths = args.variantsPaths.split(",").toList

      if (variantsPaths.nonEmpty) {
        object variantsWait
        MangoServletWrapper.syncObject += (VariantContextMaterialization.name -> variantsWait)
        Some(new VariantContextMaterialization(sc, variantsPaths, MangoServletWrapper.globalDict, Some(prefetch)))
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
        MangoServletWrapper.syncObject += (FeatureMaterialization.name -> featuresWait)
        Some(new FeatureMaterialization(sc, featurePaths, MangoServletWrapper.globalDict, Some(prefetch)))
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

    val discovery = Discovery(MangoServletWrapper.annotationRDD.getSequenceDictionary)
    var regions: List[(ReferenceRegion, Double)] = List()

    // get feature frequency
    if (MangoServletWrapper.materializer.featuresExist) {
      val featureRegions = MangoServletWrapper.materializer.getFeatures().get.get().map(r => ReferenceRegion.unstranded(r._2))
      regions = regions ++ discovery.getFrequencies(featureRegions)
    }

    // get variant frequency
    if (MangoServletWrapper.materializer.variantContextExist) {
      val variantRegions = MangoServletWrapper.materializer.getVariantContext().get.get().map(r => ReferenceRegion(r._2.variant))
      regions = regions ++ discovery.getFrequencies(variantRegions)
    }

    // get coverage frequency
    // Note: calculating coverage frequency is an expensive operation. Only perform if sc is not local.
    if (MangoServletWrapper.materializer.coveragesExist && !sc.isLocal) {
      val coverageRegions = MangoServletWrapper.materializer.getCoverage().get.get().map(r => ReferenceRegion(r._2))
      regions = regions ++ discovery.getFrequencies(coverageRegions)
    }

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
      if (MangoServletWrapper.materializer.featuresExist)
        MangoServletWrapper.materializer.getFeatures().get.get(Some(region)).count()
      if (MangoServletWrapper.materializer.readsExist)
        MangoServletWrapper.materializer.getReads().get.get(Some(region)).count()
      if (MangoServletWrapper.materializer.coveragesExist)
        MangoServletWrapper.materializer.getCoverage.get.get(Some(region)).count()
      if (MangoServletWrapper.materializer.variantContextExist)
        MangoServletWrapper.materializer.getVariantContext().get.get(Some(region)).count()
    }
  }

}

