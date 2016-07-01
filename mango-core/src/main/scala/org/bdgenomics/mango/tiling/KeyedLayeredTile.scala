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

  /* Interval RDD holding data tiles. Each node in the tree is a tile spanning a region of size chunkSize */
  def intRDD: IntervalRDD[ReferenceRegion, T]

  /* chunk size of each node in interval tree */
  def chunkSize: Int

  /**
   * Gets the tiles overlapping a given region corresponding to the specified keys
   *
   * @param region Region to retrieve data from
   * @param layerOpt: Option to force fetching of specific Layer
   * @return jsonified data
   */
  def getTiles(region: ReferenceRegion, layerOpt: Option[Layer] = None): RDD[(String, Iterable[Any])] = {

    // Filter IntervalRDD by region and requested layer
    val data: RDD[(String, Iterable[Any])] = intRDD.filterByInterval(region)
      .mapValues(r => r.get(region, layerOpt))
      .toRDD.flatMap(_._2)

    data
  }

  /**
   * Gets the tiles overlapping a given region corresponding to the specified keys
   *
   * @param region Region to retrieve data from
   * @param ks keys whose data to retrieve
   * @return jsonified data
   */
  def getRaw(region: ReferenceRegion, ks: List[String]): RDD[Any] = {
    // Filter IntervalRDD by region and requested layer
    val data: RDD[Any] = intRDD.filterByInterval(region)
      .mapValues(r => r.get(region, ks, Some(L0)))
      .toRDD.flatMap(_._2.flatMap(_._2))

    data
  }

  /**
   * Gets the tiles overlapping a given region corresponding to the specified keys
   * @param region Region to retrieve data from
   * @param layers: List of layers to fetch from tile
   * @return jsonified data
   */
  def getTiles(region: ReferenceRegion, layers: List[Layer]): RDD[(Int, Map[String, Iterable[Any]])] = {

    // Filter IntervalRDD by region and requested layer
    val data: RDD[(Int, Map[String, Iterable[Any]])] = intRDD.filterByInterval(region)
      .mapValues(r => r.get(region, layers))
      .toRDD.flatMap(_._2)
    // return JSONified data
    //    val json = layers.map(layer => (layer, stringify(data.filter(_._1 == layer.id).flatMap(_._2), region, layer))).toMap
    data
  }
  //
  //  /**
  //   * Gets layer corresponding to the reference region.
  //   *
  //   * @see LayeredTile
  //   * @param region ReferenceRegion whose size to compare
  //   * @return Option of layer. If region size exceeds specs in LayeredTile, no layer is returned
  //   */
  //  def getLayer(region: ReferenceRegion): Layer = {
  //    val size = region.length()
  //
  //    size match {
  //      case x if (x < L1.range._1) => L1
  //      case _                      => L4
  //    }
  //  }
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
   *
   * @param region Region to fetch data
   * @param layer: Option to force fetching of specific Layer
   * @return Map of (k, Iterable(data))
   */
  def get(region: ReferenceRegion, layer: Option[Layer] = None): Map[String, Iterable[Any]] = {

    val size = region.length()

    val data =
      if (layer.isDefined) layerMap.get(layer.get.id)
      else layerMap.get(0)

    // if no data exists return empty map
    data.getOrElse(Map.empty[String, Iterable[Any]])
  }

  /**
   * Gets data corresponding to the layer tied to the region size specified. This gets data from layermap
   * and is called by KTiles
   * @param region Region to fetch data
   * @param layers: List of layers to fetch
   * @return Map of (k, Iterable(data))
   */
  def get(region: ReferenceRegion, layers: List[Layer]): Map[Int, Map[String, Iterable[Any]]] = {
    layerMap.filterKeys(k => layers.map(_.id).contains(k))
  }
}

trait Layer extends Serializable {
  def id: Int
  def maxSize: Long
  def range: Tuple2[Long, Long]
  val finalSize = 1000

  def patchSize: Int
  def stride: Int

}

/* For raw data */
object L0 extends Layer {
  val id = 0
  val maxSize = 5000L
  val range = (0L, maxSize)
  val patchSize = 0
  val stride = 0
}

/* For objects 5000 to 10000 */
object L1 extends Layer {
  val id = 1
  val maxSize = 10000L
  val range = (5000L, maxSize)
  val patchSize = 10
  val stride = 10
}

/* For objects 10,000 to 100,000 */
object L2 extends Layer {
  val id = 2
  val maxSize = 100000L
  val range = (L1.maxSize, maxSize)
  val patchSize = 100
  val stride = patchSize
}