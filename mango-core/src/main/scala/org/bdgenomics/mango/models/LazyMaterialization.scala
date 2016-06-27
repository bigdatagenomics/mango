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
package org.bdgenomics.mango.models

import org.apache.spark._
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel
import org.bdgenomics.adam.models.{ ReferenceRegion, SequenceDictionary }
import org.bdgenomics.adam.rdd.GenomicRegionPartitioner
import org.bdgenomics.mango.util.Bookkeep
import org.bdgenomics.utils.intervalrdd._
import org.bdgenomics.utils.misc.Logging

import scala.reflect.ClassTag

abstract class LazyMaterialization[T: ClassTag, S: ClassTag] extends Serializable with Logging {

  def sc: SparkContext
  def dict: SequenceDictionary
  def chunkSize: Int
  def bookkeep: Bookkeep
  def files: List[String]
  def getFiles: List[String] = files

  /**
   * Used to generically load data from all file types
   * @param region Region specifying predicate to load from file
   * @param file location of file
   * @return Generic RDD of data types from file
   */
  def load: (ReferenceRegion, String) => RDD[T]

  def getStart: T => Long

  /**
   * Tiles data
   * @param data data to format into tile
   * @param region region to tile over
   * @return new Data Tile
   * @see TileTypes.scala
   */
  def toTile: (Iterable[(String, T)], ReferenceRegion, Option[String]) => S

  /**
   * Sets partitioner
   * @return partitioner
   */
  def setPartitioner: Partitioner = {
    GenomicRegionPartitioner(sc.defaultParallelism, dict)
  }

  /**
   * gets dictionary
   * @return
   */
  def getDictionary: SequenceDictionary = {
    dict
  }

  var intRDD: IntervalRDD[ReferenceRegion, S] = null

  /**
   *  Transparent to the user, should only be called by get if IntervalRDD.get does not return data
   * Fetches the data from disk, using predicates and range filtering
   * Then puts fetched data in the IntervalRDD, and calls multiget again, now with the data existing
   *
   * @param region ReferenceRegion in which data is retreived
   */
  def put(region: ReferenceRegion, reference: Option[String] = None) = {
    val seqRecord = dict(region.referenceName)
    if (seqRecord.isDefined) {
      var data: RDD[(String, T)] = sc.emptyRDD[(String, T)]

      // get alignment data for all samples
      files.map(fp => {
        val k = LazyMaterialization.filterKeyFromFile(fp)
        val d = load(region, fp).map(v => (k, v))
        data = data.union(d)
      })

      val c = chunkSize // required for Spark closure

      val groupedRecords: RDD[(Long, Iterable[(String, T)])] =
        data.groupBy(r => getStart(r._2) / c * c) // divide up into tile sizes by start value

      val tiles: RDD[(ReferenceRegion, S)] = groupedRecords.map(f => {
        val r = ReferenceRegion(region.referenceName, f._1, f._1 + c - 1) // map to chunk size Reference Regions
        (r, toTile(f._2, r, reference))
      })

      // insert into IntervalRDD
      if (intRDD == null) {
        intRDD = IntervalRDD(tiles)
        intRDD.persist(StorageLevel.MEMORY_AND_DISK)
      } else {
        val t = intRDD
        intRDD = intRDD.multiput(tiles)
        // TODO: can we do this incrementally instead?
        t.unpersist(true)
        intRDD.persist(StorageLevel.MEMORY_AND_DISK)
      }
      bookkeep.rememberValues(region, files)
    }
  }

}

object LazyMaterialization {

  /**
   * Extracts location agnostic key form file
   * @param file file to extract key from
   * @return memoryless key representing file
   */
  def filterKeyFromFile(file: String): String = {
    val slash = file.split("/")
    val fileName = slash.last
    fileName.replace(".", "_")
  }
}

case class UnsupportedFileException(message: String) extends Exception(message)