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
package org.bdgenomics.mango.tiling

import org.bdgenomics.utils.intervalrdd._
import org.apache.spark.Logging
import org.apache.spark.rdd.RDD
import org.bdgenomics.adam.models.ReferenceRegion

/**
 * Holds the IntervalRDD for the data tiles. Fetches and formats data
 * into json format.
 *
 * @tparam T: Tile Type (Alignment, Variant, Feature, etc.)
 */
trait KTiles[T <: KLayeredTile] extends Serializable {
  implicit val formats = net.liftweb.json.DefaultFormats

  /* Interval RDD holding data tiles. Each node in the tree is a tile spanning a region of size chunkSize */
  def intRDD: IntervalRDD[ReferenceRegion, T]

  /* chunk size of each node in interval tree */
  def chunkSize: Int

  /* Turns raw layer 0 data into JSON string */
  def stringifyL0(data: RDD[(String, Iterable[Any])], region: ReferenceRegion): String

  /* Turns aggregated layer 1 data into JSON string */
  def stringifyL1(data: RDD[(String, Iterable[Any])], region: ReferenceRegion): String

  /**
   * Gets the tiles overlapping a given region corresponding to the specified keys
   * @param region Region to retrieve data from
   * @param ks keys whose data to retrieve
   * @param isRaw Boolean specifying whether to fetch raw data or layer corresponding to region size
   * @return jsonified data
   */
  def getTiles(region: ReferenceRegion, ks: List[String], isRaw: Boolean = false): String = {

    val layerOpt = getLayer(region)
    val layer = layerOpt.getOrElse(L2)

    val data: RDD[(String, Iterable[Any])] = get(region, ks, isRaw)

    if (isRaw)
      stringifyL0(data, region)
    else
      layer match {
        case L0 => stringifyL0(data, region)
        case L1 => stringifyL1(data, region)
        case _  => ""
      }
  }

  /**
   * Fetches bytes from layers containing aggregated data
   *
   * @param region
   * @param ks: ks to processes
   * @param isRaw: Boolean to force data formatting to layer 0
   *
   * @return byte data from aggregated layers keyed by String id
   */
  def get(region: ReferenceRegion, ks: List[String], isRaw: Boolean = false): RDD[(String, Iterable[Any])] = {

    val x = intRDD.filterByInterval(region)
      .mapValues(r => r.get(region, ks, isRaw))

    x.toRDD.flatMap(_._2)

  }

  /**
   * Gets layer corresponding to the reference region.
   * @see LayeredTile
   * @param region ReferenceRegion whose size to compare
   * @return Option of layer. If region size exceeds specs in LayeredTile, no layer is returned
   */
  def getLayer(region: ReferenceRegion): Option[Layer] = {
    val size = region.length()

    size match {
      case x if (x < L1.range._1) => Some(L1)
      case _                      => None
    }
  }
}

/**
 * Abstract tile that is used by tile tiles (AlignmentRecordTile, VariantTile)
 * Provides methods to get data corresponding to the correct layer from that tile.
 */
abstract class KLayeredTile extends Serializable with Logging {

  /* Map storing (layer, map(key, data)) for each layer and key */
  def layerMap: Map[Int, Map[String, Iterable[Any]]]

  /**
   * Gets data corresponding to the layer tied to the region size specified. This gets data from layermap
   * and is called by KTiles
   * @param region Region to fetch data
   * @param ks keys whose data to fetch
   * @param isRaw Boolean to force data formatting to layer 0
   * @return Map of (k, Iterable(data))
   */
  def get(region: ReferenceRegion, ks: List[String], isRaw: Boolean): Map[String, Iterable[Any]] = {

    val size = region.length()

    val data =
      if (isRaw) layerMap.get(0)
      else
        size match {
          case x if (x < L1.range._1) => layerMap.get(1)
          case _                      => None
        }
    // if no data exists return empty map
    val m = data.getOrElse(Map.empty[String, Iterable[Any]])

    // filter out irrelevent keys
    m.filter(r => ks.contains(r._1))
  }
}

