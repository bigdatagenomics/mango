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
  var cache: Map[String, Object] = Map.empty[String, Object]

  private def get[T](name: String, region: ReferenceRegion, key: String, resolutionOpt: Option[Int] = None): Option[Array[T]] = {
    val tCache = cache.get(name)
    if (tCache.isDefined) {
      val d = tCache.get.asInstanceOf[ResolutionCache[T]]

      // if binning was specified, verify bin sizes are the same
      if (d.coversRange(region))
        d.get(region, key, resolutionOpt)
      else None
    } else None
  }

  private def set[T: ClassTag](name: String, data: Map[String, Array[T]], region: ReferenceRegion, regionConverter: (T) => ReferenceRegion, resolution: Int = 1) = {
    // map data to regions
    val mappedData = data.mapValues(v => v.map(r => {
      val region = regionConverter(r)
      (region, r)
    }))
    // create ResolutionCache from mapped data
    val resCache = new ResolutionCache[T](mappedData, region, resolution)
    cache += (name -> resCache)
  }

  def getKey(t: Any): String = {
    t match {
      case a: GAReadAlignment => AlignmentRecordMaterialization.name
      case v: GenotypeJson    => VariantContextMaterialization.name
      case f: BedRowJson      => FeatureMaterialization.name
      case c: PositionCount   => CoverageMaterialization.name
    }
  }

  def setReads(data: Map[String, Array[GAReadAlignment]], coveredRegion: ReferenceRegion) =
    set[GAReadAlignment](AlignmentRecordMaterialization.name, data, coveredRegion, RegionConverters.readsConverter)

  def setVariants(data: Map[String, Array[GenotypeJson]], coveredRegion: ReferenceRegion, resolution: Int) =
    set[GenotypeJson](VariantContextMaterialization.name, data, coveredRegion, RegionConverters.variantConverter, resolution)

  def setFeatures(data: Map[String, Array[BedRowJson]], coveredRegion: ReferenceRegion, resolution: Int) =
    set[BedRowJson](FeatureMaterialization.name, data, coveredRegion, RegionConverters.featureConverter, resolution: Int)

  def setCoverage(data: Map[String, Array[PositionCount]], coveredRegion: ReferenceRegion, resolution: Int) =
    set[PositionCount](CoverageMaterialization.name, data, coveredRegion, RegionConverters.coverageConverter, resolution)

  def getReads(region: ReferenceRegion, key: String, resolutionOpt: Option[Int] = None): Option[Array[GAReadAlignment]] =
    get[GAReadAlignment](AlignmentRecordMaterialization.name, region, key, resolutionOpt)

  def getVariants(region: ReferenceRegion, key: String, resolutionOpt: Option[Int] = None): Option[Array[GenotypeJson]] =
    get[GenotypeJson](VariantContextMaterialization.name, region, key, resolutionOpt)

  def getFeatures(region: ReferenceRegion, key: String, resolutionOpt: Option[Int] = None): Option[Array[BedRowJson]] =
    get[BedRowJson](FeatureMaterialization.name, region, key, resolutionOpt)

  def getCoverage(region: ReferenceRegion, key: String, resolutionOpt: Option[Int] = None): Option[Array[PositionCount]] =
    get[PositionCount](CoverageMaterialization.name, region, key, resolutionOpt)
}

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
case class ResolutionCache[T: ClassTag](cache: Map[Int, Map[String, IntervalArray[ReferenceRegion, T]]], coveredRegion: ReferenceRegion)
    extends Serializable with Logging {

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

  def coversRange(range: ReferenceRegion): Boolean = coveredRegion.contains(range)

}
