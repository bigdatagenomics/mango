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

import edu.berkeley.cs.amplab.spark.intervalrdd.IntervalRDD
import net.liftweb.json.Serialization.write
import org.apache.spark.Logging
import org.apache.spark.rdd.RDD
import org.bdgenomics.adam.models.ReferenceRegion

import scala.reflect.ClassTag

trait KTiles[S, T <: KLayeredTile[S]] extends Serializable {
  implicit val formats = net.liftweb.json.DefaultFormats

  implicit protected def tag: ClassTag[S]

  def intRDD: IntervalRDD[ReferenceRegion, T]

  def chunkSize: Int

  def stringifyRaw(data: RDD[(String, S)], region: ReferenceRegion): Map[String, String]

  def stringifyBytes(data: RDD[(String, Array[Byte])], region: ReferenceRegion): Map[String, String] = {
    val layer = LayeredTile.getLayer(region)

    val x: Map[String, Array[Double]] = data.reduceByKey((x, y) => x ++ y)
      .mapValues(r => layer.fromDoubleBytes(r)).collect.toMap

    x.mapValues(r => write(r))
  }

  def getTiles(region: ReferenceRegion, ks: List[String]): Map[String, String] = {

    val layer = LayeredTile.getLayer(region)
    if (layer == L0) return getRaw(region, ks)

    // if not raw layer, fetch from other layers
    val data = getAggregated(region, ks)
    stringifyBytes(data, region)
  }

  def getRaw(region: ReferenceRegion, ks: List[String]): Map[String, String] = {
    val regionSize = region.length()

    val data: RDD[(String, S)] =
      if (chunkSize >= regionSize) {
        intRDD.filterByInterval(region)
          .toRDD.flatMap(r => (r._2.rawData.filter(r => ks.contains(r._1))))
      } else {
        intRDD.filterByInterval(region)
          .toRDD.sortBy(_._1.start)
          .flatMap(r => (r._2.rawData.filter(r => ks.contains(r._1))))
      }
    stringifyRaw(data, region)
  }

  /*
 * Fetches bytes from layers containing aggregated data
 *
 * @param region
 * @param layer: Optional layer to force data collect from. Defaults to reference size
 *
 * @return byte data from aggregated layers
 */
  def getAggregated(region: ReferenceRegion, ks: List[String]): RDD[(String, Array[Byte])] = {

    val regionSize = region.length()
    // type cast data on whether or not it was raw data from L0

    if (chunkSize >= regionSize) {
      intRDD.filterByInterval(region)
        .mapValues(r => (r._1, r._2.getAggregated(region, ks)))
        .toRDD.flatMap(_._2)
    } else {
      intRDD.filterByInterval(region)
        .mapValues(r => (r._1, r._2.getAggregated(region, ks)))
        .toRDD.sortBy(_._1.start).flatMap(_._2)
    }

  }
}

abstract class KLayeredTile[S: ClassTag] extends Serializable with Logging {
  def rawData: Map[String, S]
  def keys: List[String]
  def layerMap: Map[Int, Map[String, Array[Byte]]]

  def getAggregated(region: ReferenceRegion, ks: List[String]): Map[String, Array[Byte]] = {
    val size = region.length()

    val data =
      size match {
        case x if (x < L1.range._1) => throw new Exception(s"Should fetch raw data for regions < ${L1.range._1}")
        case x if (x >= L1.range._1 && x < L1.range._2) => layerMap(1)
        case x if (x >= L2.range._1 && x < L2.range._2) => layerMap(2)
        case x if (x >= L3.range._1 && x < L3.range._2) => layerMap(3)
        case _ => layerMap(4)
      }
    data.filter(r => ks.contains(r._1))
  }

}

