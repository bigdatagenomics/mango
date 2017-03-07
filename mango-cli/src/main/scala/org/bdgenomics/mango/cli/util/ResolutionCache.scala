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
package org.bdgenomics.mango.cli.util

import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.mango.layout.{ GenotypeJson, PositionCount, BedRowJson }
import org.bdgenomics.mango.models.{ CoverageMaterialization, AlignmentRecordMaterialization, FeatureMaterialization, VariantContextMaterialization }
import org.bdgenomics.utils.interval.array.IntervalArray
import org.bdgenomics.utils.misc.Logging
import org.ga4gh.{ GAReadAlignment }
import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

case class GenomicCache() {

  // seq of Resolution caches for all genomic datatypes
  var cache: Seq[ResolutionCache[_]] = Seq.empty[ResolutionCache[_]]

  /**
   * Gets json from the resolution cache specified by type T
   *
   * @param region region to filter by
   * @param key key that represents a file
   * @param resolutionOpt Optional resolution. Currently used for variants and features
   * @tparam T Genomic json type
   * @return Optional array of json
   */
  private def get[T: TypeTag](region: ReferenceRegion, key: String, resolutionOpt: Option[Int] = None): Option[Array[T]] = {

    // get ResolutionCache associated with type T
    val tCache = cache.find(m => m match {
      case r if (r.selfTypeTag.tpe =:= typeOf[T]) => true
      case _                                      => false
    })

    if (tCache.isDefined) {
      val d = tCache.get.asInstanceOf[ResolutionCache[T]]

      // if binning was specified, verify bin sizes are the same
      if (d.coversRange(region))
        d.get(region, key, resolutionOpt)
      else None
    } else None
  }

  /**
   * Sets data for type T in cache by creating a ResolutionCache[T] and inserting it into the cache object
   *
   * @param data Data to insert in cache. Map has filenames as keys
   * @param region ReferenceRegion that data covers
   * @param regionConverter function that converts object into ReferenceRegion
   * @param resolution Resolution to fetch
   * @tparam T Type to fetch cache
   */
  private def set[T: TypeTag: ClassTag](data: Map[String, Array[T]], region: ReferenceRegion, regionConverter: (T) => ReferenceRegion, resolution: Int = 1) = {
    // map data to regions
    val mappedData = data.mapValues(v => v.map(r => {
      val region = regionConverter(r)
      (region, r)
    }))
    // create ResolutionCache from mapped data
    val resCache = new ResolutionCache[T](mappedData, region, resolution)
    cache = cache.filter(m => m match {
      case r if (r.selfTypeTag.tpe =:= typeOf[T]) => false
      case _                                      => true
    }) :+ resCache
  }

  /**
   * Setters for specific genomic types
   */

  def setReads(data: Map[String, Array[GAReadAlignment]], coveredRegion: ReferenceRegion) =
    set[GAReadAlignment](data, coveredRegion, RegionConverters.readsConverter)

  def setVariants(data: Map[String, Array[GenotypeJson]], coveredRegion: ReferenceRegion, resolution: Int) =
    set[GenotypeJson](data, coveredRegion, RegionConverters.variantConverter, resolution)

  def setFeatures(data: Map[String, Array[BedRowJson]], coveredRegion: ReferenceRegion, resolution: Int) =
    set[BedRowJson](data, coveredRegion, RegionConverters.featureConverter, resolution: Int)

  def setCoverage(data: Map[String, Array[PositionCount]], coveredRegion: ReferenceRegion, resolution: Int) =
    set[PositionCount](data, coveredRegion, RegionConverters.coverageConverter, resolution)

  /**
   * Getters for specific genomic types
   */

  def getReads(region: ReferenceRegion, key: String, resolutionOpt: Option[Int] = None): Option[Array[GAReadAlignment]] =
    get[GAReadAlignment](region, key, resolutionOpt)

  def getVariants(region: ReferenceRegion, key: String, resolutionOpt: Option[Int] = None): Option[Array[GenotypeJson]] =
    get[GenotypeJson](region, key, resolutionOpt)

  def getFeatures(region: ReferenceRegion, key: String, resolutionOpt: Option[Int] = None): Option[Array[BedRowJson]] =
    get[BedRowJson](region, key, resolutionOpt)

  def getCoverage(region: ReferenceRegion, key: String, resolutionOpt: Option[Int] = None): Option[Array[PositionCount]] =
    get[PositionCount](region, key, resolutionOpt)
}

/**
 * Object of Converters which take a genomic type and output the ReferenceRegion that object overlaps.
 * Support for Alignments, Features, Coverage and Variants
 */
object RegionConverters {

  def readsConverter(r: GAReadAlignment): ReferenceRegion = {
    ReferenceRegion(r.getAlignment.getPosition.getReferenceName, r.getAlignment.getPosition.getPosition,
      r.getAlignment.getPosition.getPosition + 1)
  }

  def featureConverter(r: BedRowJson): ReferenceRegion = {
    ReferenceRegion(r.contig, r.start, r.stop)
  }

  def coverageConverter(r: PositionCount): ReferenceRegion = {
    ReferenceRegion(r.contig, r.start, r.end)
  }

  def variantConverter(r: GenotypeJson): ReferenceRegion = {
    ReferenceRegion(r.variant)
  }
}

/**
 * Meant to temporarily store data on driver for fast access.
 * @param cache Map of resolution and corresponding json data
 * @param coveredRegion Region that cache covers
 * @tparam T data type stored in cache
 */
case class ResolutionCache[T: TypeTag: ClassTag](cache: Map[Int, Map[String, IntervalArray[ReferenceRegion, T]]], coveredRegion: ReferenceRegion)
    extends Serializable with Logging {

  // Used for type reflection in GenomicCache
  val selfTypeTag = typeTag[T]

  def this(data: Map[String, Array[(ReferenceRegion, T)]], region: ReferenceRegion, resolution: Int) = {
    this(Map((resolution -> data.mapValues(r => IntervalArray(r, 200, true)))), region)
  }

  /**
   * Gets overlapping data in cache
   *
   * @param region Range to filter by
   * @param resolutionOpt Resolution to get data for
   * @return Array of T
   */
  def get(region: ReferenceRegion, key: String, resolutionOpt: Option[Int] = None): Option[Array[T]] = {

    val resolution = resolutionOpt.getOrElse(1)
    val intArray = cache.get(resolution)

    if (!intArray.isDefined)
      None
    else {
      val arr = intArray.get.get(key)
      if (!arr.isDefined) {
        log.warn(s"${key} not found in resolution cache")
        None
      } else {
        Some(arr.get.get(region).toArray.map(_._2))
      }
    }
  }

  /**
   * Returns whether the cache has data for a given ReferenceRegion
   *
   * @param range ReferenceRegion
   * @return Returns true if cache covers range, false otherwise
   */
  def coversRange(range: ReferenceRegion): Boolean = coveredRegion.contains(range)

}
