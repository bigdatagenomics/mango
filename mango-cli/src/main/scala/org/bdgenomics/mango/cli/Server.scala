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
import org.bdgenomics.adam.models.{ Coverage, ReferenceRegion, SequenceDictionary }
import org.bdgenomics.mango.filters._
import org.bdgenomics.mango.models._
import org.bdgenomics.mango.cli.util.{ GenomicCache, ResolutionCache }
import org.bdgenomics.utils.cli._
import org.bdgenomics.utils.instrumentation.Metrics
import org.bdgenomics.utils.misc.Logging
import org.kohsuke.args4j.{ Argument, Option => Args4jOption }
import org.scalatra._

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

  @Args4jOption(required = false, name = "-test", usage = "For debugging purposes.")
  var testMode: Boolean = false

  @Args4jOption(required = false, name = "-discover", usage = "This turns on discovery mode on start up.")
  var discoveryMode: Boolean = false
}

case class Materializer(materializer: Seq[Any]) {

  def getObject = materializer

  /**
   * Access functions for materializer
   */
  def getReads(): Option[AlignmentRecordMaterialization] = {
    val x = materializer.flatMap(r =>
      r match {
        case m: AlignmentRecordMaterialization => Some(m)
        case _                                 => None
      })
    if (x.isEmpty) None
    else Some(x.head)
  }

  def getCoverage(): Option[CoverageMaterialization] = {
    val x = materializer.flatMap(r =>
      r match {
        case m: CoverageMaterialization => Some(m)
        case _                          => None
      })
    if (x.isEmpty) None
    else Some(x.head)
  }

  def getVariantContext(): Option[VariantContextMaterialization] = {
    val x = materializer.flatMap(r =>
      r match {
        case m: VariantContextMaterialization => Some(m)
        case _                                => None
      })
    if (x.isEmpty) None
    else Some(x.head)
  }

  def getFeatures(): Option[FeatureMaterialization] = {
    val x = materializer.flatMap(r =>
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
object MangoServer extends BDGCommandCompanion with Logging {

  val commandName: String = "Mango"
  val commandDescription: String = "Genomic visualization for ADAM"
  implicit val formats = net.liftweb.json.DefaultFormats

  var sc: SparkContext = null
  var server: org.eclipse.jetty.server.Server = null
  var globalDict: SequenceDictionary = null

  // Gene URL
  var genes: Option[String] = None

  // Structures storing data types. All but reference is optional
  var annotationRDD: AnnotationMaterialization = null
  var materializer: Materializer = null

  var showGenotypes: Boolean = false

  /**
   * Caching information for frontend
   */
  // stores synchonization objects
  var syncObject: Map[String, Object] = Map.empty[String, Object]

  // cache object
  var cache: GenomicCache = GenomicCache()

  // regions to prefetch during discovery. sent to front
  // end for visual processing
  var prefetchedRegions: List[(ReferenceRegion, Double)] = List()
  var cacheSize: Long = 0

  def apply(cmdLine: Array[String]): BDGCommand = {
    new MangoServer(Args4j[ServerArgs](cmdLine))
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

  def expand(region: ReferenceRegion, minLength: Long = MangoServer.cacheSize): ReferenceRegion = {
    require(minLength > 0, s"minimum length ${minLength} must be greater than 0")
    val start = region.start - (region.start % minLength)
    val end = Math.max(region.end, start + minLength)
    region.copy(start = start, end = end)
  }
}

/**
 * Initializes all data types to be served to frontend. Optionally runs discovery mode.
 *
 * @param args VizReadsArgs
 */
class MangoServer(protected val args: ServerArgs) extends BDGSparkCommand[ServerArgs] with Logging {
  val companion: BDGCommandCompanion = MangoServer

  override def run(sc: SparkContext): Unit = {
    MangoServer.sc = sc

    // choose prefetch size
    val prefetch =
      if (args.testMode) 1000 // for testing purposes, do not overload memory
      else if (sc.isLocal) 40000
      else 100000

    // initialize required annotation dataset
    initAnnotations

    // check whether genePath was supplied
    if (args.genePath != null) {
      MangoServer.genes = Some(args.genePath)
    }

    // start MangoServer
    if (!args.testMode) MangoServer.server = MangoRequest.startServer(args.port)

    /*
   * Initialize required reference file
   */
    def initAnnotations = {
      val referencePath = Option(args.referencePath).getOrElse({
        throw new FileNotFoundException("reference file not provided")
      })

      MangoServer.annotationRDD = new AnnotationMaterialization(sc, referencePath)
      MangoServer.globalDict = MangoServer.annotationRDD.getSequenceDictionary
    }

    // set materializer
    MangoServer.materializer = Materializer(Seq(initAlignments, initCoverages, initVariantContext, initFeatures).flatten)

    // run discovery mode if it is specified in the startup script
    if (args.discoveryMode) {
      discoverFrequencies() match {
        case (a, b) =>
          MangoServer.prefetchedRegions = a; MangoServer.cacheSize = b
        case _ =>
      }
    } else {
      MangoServer.cacheSize = 2 * prefetch
    }

    /*
   * Initialize loaded alignment files
   */
    def initAlignments: Option[AlignmentRecordMaterialization] = {
      if (Option(args.readsPaths).isDefined) {
        val readsPaths = args.readsPaths.split(",").toList

        if (readsPaths.nonEmpty) {
          object readsWait
          MangoServer.syncObject += (AlignmentRecordMaterialization.name -> readsWait)
          Some(AlignmentRecordMaterialization(sc, readsPaths, MangoServer.globalDict, Some(prefetch)))
        } else None
      } else None
    }

    /*
   * Initialize coverage files
   */
    def initCoverages: Option[CoverageMaterialization] = {
      if (Option(args.coveragePaths).isDefined) {
        val coveragePaths = args.coveragePaths.split(",").toList

        if (coveragePaths.nonEmpty) {
          object coverageWait
          MangoServer.syncObject += (CoverageMaterialization.name -> coverageWait)
          Some(new CoverageMaterialization(sc, coveragePaths, MangoServer.globalDict, Some(prefetch)))
        } else None
      } else None
    }

    /**
     * Initialize loaded variant files
     */
    def initVariantContext: Option[VariantContextMaterialization] = {
      // set flag for visualizing genotypes
      MangoServer.showGenotypes = args.showGenotypes

      if (Option(args.variantsPaths).isDefined) {
        val variantsPaths = args.variantsPaths.split(",").toList

        if (variantsPaths.nonEmpty) {
          object variantsWait
          MangoServer.syncObject += (VariantContextMaterialization.name -> variantsWait)
          Some(new VariantContextMaterialization(sc, variantsPaths, MangoServer.globalDict, Some(prefetch)))
        } else None
      } else None
    }

    /**
     * Initialize loaded feature files
     */
    def initFeatures: Option[FeatureMaterialization] = {
      val featurePaths = Option(args.featurePaths)
      if (featurePaths.isDefined) {
        val featurePaths = args.featurePaths.split(",").toList
        if (featurePaths.nonEmpty) {
          object featuresWait
          MangoServer.syncObject += (FeatureMaterialization.name -> featuresWait)
          Some(new FeatureMaterialization(sc, featurePaths, MangoServer.globalDict, Some(prefetch)))
        } else None
      } else None
    }

    /**
     * Runs total data scan over all feature, variant and coverage files, calculating the normalied frequency at all
     * windows in the genome.
     *
     * @return Returns list of windowed regions in the genome and their corresponding normalized frequencies
     */
    def discoverFrequencies(): (List[(ReferenceRegion, Double)], Long) = {

      val discovery = Discovery(MangoServer.annotationRDD.getSequenceDictionary)
      var regions: List[(ReferenceRegion, Double)] = List()

      // parse all materialization structures to calculate region frequencies
      MangoServer.materializer.getObject
        .foreach(m => m match {
          case vcm: VariantContextMaterialization => {
            regions = regions ++ discovery.getFrequencies(vcm.get()
              .map(r => ReferenceRegion(r._2.variant)))
          }
          case fm: FeatureMaterialization => {
            regions = regions ++ discovery.getFrequencies(fm.get()
              .map(r => ReferenceRegion.unstranded(r._2)))
          }
          case cm: CoverageMaterialization => {
            if (!sc.isLocal) {
              regions = regions ++ discovery.getFrequencies(cm.get()
                .map(r => ReferenceRegion(r._2)))
            }
          }
          case am: AlignmentRecordMaterialization => {
            if (!sc.isLocal) {
              am.get().count
            }
          }
          case _ => {
            // no op
          }
        })

      val cacheRange =
        if (!sc.isLocal) {
          // evaluate memory size
          val bps = MangoServer.annotationRDD.getSequenceDictionary.records.map(_.length).sum
          val mem = sc.getExecutorMemoryStatus
          // get max and remaining memory
          val temp = mem.map(_._2).reduce((e1, e2) => ((e1._1 + e2._1), (e1._2 + e2._2)))
          val bpSize = (temp._1 - temp._2) / bps
          // based on 0.15 driver allocation to cache, calculate the number of base pairs that can be stored
          val cacheRange = ((sc.getConf.getSizeAsBytes("spark.driver.memory") * 0.15) / bpSize).toLong
          (cacheRange * 1000) / 1000
        } else 2 * prefetch

      // group all regions together and reduce down for all data types
      regions = regions.groupBy(_._1).map(r => (r._1, r._2.map(a => a._2).sum)).toList

      // normalize and filter by regions with data
      val max = regions.map(_._2).reduceOption(_ max _).getOrElse(1.0)
      (regions.map(r => (r._1, r._2 / max))
        .filter(_._2 > 0.0), cacheRange)
    }
  }
}
