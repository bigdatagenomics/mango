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
package controllers

import javax.inject.Inject

import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.mango.core.util.VizUtils
import org.bdgenomics.mango.layout.{ BedRowJson, GenotypeJson, PositionCount }
import org.bdgenomics.mango.models._
import org.bdgenomics.mango.util.Bookkeep
import org.ga4gh.GAReadAlignment
import play.Logger
import play.api.Play
import play.api.Play.current
import play.api.cache._
import play.api.libs.json._
import play.api.mvc._
import utils.JsonImplicits._
import utils.{ MangoServletWrapper, VizTimers }

import scala.collection.mutable

class MangoApplication @Inject() (cache: CacheApi) extends Controller {

  def sequenceDictionary = Action {
    Ok(Json.toJson(MangoServletWrapper.globalDict))
  }

  def index = Action { request =>
    // set initial referenceRegion so it is defined. pick first chromosome to view
    val firstChr = MangoServletWrapper.globalDict.records.head.name
    Ok(views.html.index(MangoServletWrapper.formatClickableRegions(MangoServletWrapper.prefetchedRegions))).withSession(
      request.session + ("contig" -> firstChr) + ("start" -> "1") + ("end" -> "100"))
  }

  def browser = Action { request =>

    // if session variable for reference region is not yet set, set it to first available chromosome in sd
    val contig: String = request.session.get("contig").getOrElse(MangoServletWrapper.globalDict.records.head.name)
    val start = request.session.get("start").getOrElse("1").toLong
    val end = request.session.get("end").getOrElse("100").toLong

    // generate file keys for front end
    val readsSamples: Option[List[(String, Option[String])]] = try {
      val reads = MangoServletWrapper.materializer.getReads().get.getFiles.map(r => LazyMaterialization.filterKeyFromFile(r))

      // check if there are precomputed coverage files for reads. If so, send this information to the frontend
      // to avoid extra coverage computation
      if (MangoServletWrapper.materializer.getCoverage().isDefined) {
        Some(reads.map(r => {
          val coverage = MangoServletWrapper.materializer.getCoverage().get.getFiles.map(c => LazyMaterialization.filterKeyFromFile(c))
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
      val coverage = MangoServletWrapper.materializer.getCoverage().get.getFiles.map(r => LazyMaterialization.filterKeyFromFile(r))

      // filter out coverage samples that will be displayed with reads
      if (readsSamples.isDefined) {
        val readsCoverage = readsSamples.get.map(_._2).flatten
        Some(coverage.filter(c => !readsCoverage.contains(c)))
      } else Some(coverage)
    } catch {
      case e: Exception => None
    }

    val variantSamples = try {
      if (MangoServletWrapper.showGenotypes)
        Some(MangoServletWrapper.materializer.getVariantContext().get.getGenotypeSamples().map(r => (LazyMaterialization.filterKeyFromFile(r._1), r._2.mkString(","))))
      else Some(MangoServletWrapper.materializer.getVariantContext().get.getFiles.map(r => (LazyMaterialization.filterKeyFromFile(r), "")))
    } catch {
      case e: Exception => None
    }

    val featureSamples = try {
      Some(MangoServletWrapper.materializer.getFeatures().get.getFiles.map(r => LazyMaterialization.filterKeyFromFile(r)))
    } catch {
      case e: Exception => None
    }

    Ok(views.html.browser(contig, start, end,
      MangoServletWrapper.genes, coverageSamples, readsSamples,
      variantSamples, featureSamples))
  }

  def quit = Action {
    val thread = new Thread {
      override def run() {
        try {
          Logger.info("Shutting down the server")
          Play.application.stop()
          MangoServletWrapper.sc.stop()
          System.exit(0)
          Logger.info("Server has stopped")
        } catch {
          case e: Exception => {
            Logger.error("Error when stopping Jetty server: " + e.getMessage, e)
          }
        }
      }
    }
    thread.start()
    Ok("quit")
  }

  def features(key: String, contig: String, start: Long, end: Long, binning: Int) = Action {
    VizTimers.FeatRequest.time {
      if (!MangoServletWrapper.materializer.featuresExist)
        NotFound
      else {
        // if region is in bounds of reference, return data
        val dictOpt = MangoServletWrapper.globalDict(contig)
        if (dictOpt.isDefined) {
          val viewRegion = ReferenceRegion(contig, start,
            VizUtils.getEnd(end, dictOpt))

          val results = getFromCache[BedRowJson](viewRegion, key, FeatureMaterialization.name, Overlaps.overlapsFeature, binning = binning).toArray

          if (!results.isEmpty) {
            Ok(MangoServletWrapper.materializer.getFeatures().get.stringify(results))
          } else NoContent
        } else NotFound
      }
    }
  }

  def reads(key: String, contig: String, start: Long, end: Long) = Action {
    VizTimers.ReadsRequest.time {
      if (!MangoServletWrapper.materializer.readsExist) {
        NotFound
      } else {
        val dictOpt = MangoServletWrapper.globalDict(contig)
        if (dictOpt.isDefined) {
          val viewRegion = ReferenceRegion(contig, start,
            VizUtils.getEnd(end, dictOpt))

          val results = getFromCache[GAReadAlignment](viewRegion, key, AlignmentRecordMaterialization.name, Overlaps.overlapsRead).toArray
          if (!results.isEmpty) {
            // filter elements by region
            Ok(MangoServletWrapper.materializer.getReads().get.stringify(results))
          } else NoContent
        } else NotFound
      }
    }
  }

  def readCoverage(key: String, contig: String, start: Long, end: Long, binning: Int) = Action {
    VizTimers.ReadsRequest.time {
      if (!MangoServletWrapper.materializer.readsExist) {
        NotFound
      } else {
        val viewRegion = ReferenceRegion(contig, start,
          VizUtils.getEnd(end, MangoServletWrapper.globalDict(contig)))
        // get all coverage files that have been loaded
        val coverageFiles =
          if (MangoServletWrapper.materializer.coveragesExist) {
            Some(MangoServletWrapper.materializer.getCoverage().get.getFiles.map(f => LazyMaterialization.filterKeyFromFile(f)))
          } else None

        // check if there is a precomputed coverage file for this reads file
        if (coverageFiles.isDefined && coverageFiles.get.contains(key)) {

          getCoverage(viewRegion, key, binning)
        } else {
          // no precomputed coverage. compute from reads
          val dictOpt = MangoServletWrapper.globalDict(contig)
          if (dictOpt.isDefined) {

            val results = getFromCache[GAReadAlignment](viewRegion, key, AlignmentRecordMaterialization.name, Overlaps.overlapsRead).toArray

            if (!results.isEmpty) {
              // compute coverage from collected reads on the fly
              val coverage = MangoServletWrapper.materializer.getReads.get.toCoverage(results, viewRegion)
              Ok(MangoServletWrapper.materializer.getCoverage().get.stringify(coverage))
            } else NoContent
          } else NotFound
        }
      }
    }
  }

  def coverage(key: String, contig: String, start: Long, end: Long, binning: Int) = Action {
    val viewRegion = ReferenceRegion(contig, start,
      VizUtils.getEnd(end, MangoServletWrapper.globalDict(contig)))
    getCoverage(viewRegion, key, binning)
  }

  def variants(key: String, contig: String, start: Long, end: Long, binning: Int) = Action {
    VizTimers.VarRequest.time {
      if (!MangoServletWrapper.materializer.variantContextExist)
        NotFound
      else {
        val dictOpt = MangoServletWrapper.globalDict(contig)
        if (dictOpt.isDefined) {
          val viewRegion = ReferenceRegion(contig, start,
            VizUtils.getEnd(end, dictOpt))

          val results = getFromCache[GenotypeJson](viewRegion, key, VariantContextMaterialization.name, Overlaps.overlapsVariant,
            MangoServletWrapper.showGenotypes, binning).toArray

          if (!results.isEmpty) {
            // extract variants only and parse to stringified json
            Ok(MangoServletWrapper.materializer.getVariantContext().get.stringify(results))
          } else NoContent
        } else NotFound
      }
    }
  }

  def reference(contig: String, start: Long, end: Long) = Action {
    val viewRegion = ReferenceRegion(contig, start, end)
    val dictOpt = MangoServletWrapper.globalDict(viewRegion.referenceName)
    if (dictOpt.isDefined) {
      Ok(MangoServletWrapper.annotationRDD.getReferenceString(viewRegion))
    } else NotFound
  }

  def setContig(contig: String, start: Long, end: Long) = Action { request =>
    val dictOpt = MangoServletWrapper.globalDict(contig)
    val viewRegion = ReferenceRegion(contig, start,
      VizUtils.getEnd(end, dictOpt))
    Ok("").withSession(
      ("contig" -> contig),
      ("start" -> start.toString),
      ("end" -> end.toString))
  }

  /**
   * Gets Coverage for a get Request. This is used to get both Reads based coverage and generic coverage.
   *
   * @param viewRegion ReferenceRegion to view coverage over
   * @param key key for coverage file (see LazyMaterialization)
   * @return ActionResult of coverage json
   */
  private def getCoverage(viewRegion: ReferenceRegion, key: String, binning: Int = 1): Result = {
    VizTimers.CoverageRequest.time {
      if (!MangoServletWrapper.materializer.coveragesExist) {
        NotFound
      } else {
        val dictOpt = MangoServletWrapper.globalDict(viewRegion.referenceName)
        if (dictOpt.isDefined) {

          val results = getFromCache[PositionCount](viewRegion, key, CoverageMaterialization.name, Overlaps.overlapsCoverage,
            binning = binning).toArray

          if (!results.isEmpty) {
            Ok(MangoServletWrapper.materializer.getCoverage().get.stringify(results))
          } else NoContent
        } else NotFound
      }
    }
  }

  private def getFromCache[T](region: ReferenceRegion,
                              key: String,
                              name: String,
                              overlaps: (T, ReferenceRegion) => Boolean,
                              verbose: Boolean = true,
                              binning: Int = 1): mutable.ArraySeq[T] = {

    // expand region
    val expandedRegions = MangoServletWrapper.expand(region)

    // get cache keys based on expanded regions
    val cacheKeys = expandedRegions.map(r => (r, s"${name}_${r.toString}_${binning}"))

    // get keys that are not found in cache
    val keysNotFound = cacheKeys.filter(k => {
      !cache.get(k._2).isDefined
    })

    // synchronize to avoid duplicating items in cache
    MangoServletWrapper.syncObject(name).synchronized {
      // merge keys not in cache to reduce Spark calls
      val data: Map[String, Array[T]] = Bookkeep.mergeRegions(keysNotFound.map(_._1).toList).map(k => {
        MangoServletWrapper.materializer.get(name).get.getJson(k, verbose = verbose, binning = binning)
          .asInstanceOf[Map[String, Array[T]]]
      }).flatten.toMap

      // put missing keys back into cache, dividing data back up by cacheSize
      keysNotFound.map(k => {
        val filtered: Map[String, Array[T]] = data.mapValues(r => r.filter(t => overlaps(t, k._1)))
        cache.set(k._2, filtered)
      })
    }

    // finally, get results from cache
    cacheKeys.flatMap(k => cache.get[Map[String, Array[T]]](k._2))
      .flatMap(_.get(key)) // filter by key
      .flatten.distinct.filter(r => overlaps(r, region)) // remove elements not overlapping original region

  }

}

object Overlaps {

  val overlapsRead = (x: GAReadAlignment, r: ReferenceRegion) => {
    val ghRegion = ReferenceRegion(x.getAlignment.getPosition.getReferenceName, x.getAlignment.getPosition.getPosition,
      (x.getAlignment.getPosition.getPosition + 1))
    ghRegion.overlaps(r)
  }

  val overlapsFeature = (x: BedRowJson, r: ReferenceRegion) => x.overlaps(r)

  val overlapsVariant = (x: GenotypeJson, r: ReferenceRegion) => {
    x.overlaps(r)
  }

  val overlapsCoverage = (x: PositionCount, r: ReferenceRegion) => {
    x.overlaps(r)
  }

}
