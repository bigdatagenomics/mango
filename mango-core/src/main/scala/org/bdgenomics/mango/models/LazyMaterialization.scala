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

abstract class LazyMaterialization[T: ClassTag] extends Serializable with Logging {

  def sc: SparkContext
  def sd: SequenceDictionary
  def chunkSize = 20000
  val prefetchSize = 10000
  val bookkeep = new Bookkeep(prefetchSize)

  def files: List[String]
  def getFiles: List[String] = files
  var intRDD: IntervalRDD[ReferenceRegion, (String, T)] = null

  /**
   * Used to generically load data from all file types
   * @return Generic RDD of data types from file
   */
  def load: (ReferenceRegion, String) => RDD[T]

  /**
   * Extracts reference region from data type T
   * @return extracted ReferenceRegion
   */
  def getReferenceRegion: (T) => ReferenceRegion

  /**
   * Stringify T classtag to json
   * @param rdd RDD of elements keyed by String
   * @return Map of (key, json) for the ReferenceRegion specified
   */
  def stringify(rdd: RDD[(String, T)]): Map[String, String]

  /**
   * Sets partitioner
   * @return partitioner
   */
  def setPartitioner: Partitioner = {
    GenomicRegionPartitioner(sc.defaultParallelism, sd)
  }

  /**
   * gets dictionary
   * @return
   */
  def getDictionary: SequenceDictionary = sd

  /**
   * Gets json data for all files.
   * Filters all alignment data already loaded into the corresponding RDD that overlap a region.
   * If data has yet been loaded, loads data within this region.
   *
   * @param region: ReferenceRegion to fetch
   * @return Map of sampleIds and corresponding JSON
   */
  def getJson(region: ReferenceRegion): Map[String, String] = {
    val seqRecord = sd(region.referenceName)
    seqRecord match {
      case Some(_) => {
        val regionsOpt = bookkeep.getMaterializedRegions(region, files)
        if (regionsOpt.isDefined) {
          for (r <- regionsOpt.get) {
            put(r)
          }
        }
        stringify(intRDD.filterByInterval(region).toRDD.map(_._2))
      } case None => {
        throw new Exception("Not found in dictionary")
      }
    }
  }

  /**
   * Gets raw data for all files.
   * Filters all alignment data already loaded into the corresponding RDD that overlap a region.
   * If data has yet been loaded, loads data within this region.
   *
   * @param region: ReferenceRegion to fetch
   * @return Map of sampleIds and corresponding JSON
   */
  def get(region: ReferenceRegion): RDD[(String, T)] = {
    val seqRecord = sd(region.referenceName)
    seqRecord match {
      case Some(_) => {
        val regionsOpt = bookkeep.getMaterializedRegions(region, files)
        if (regionsOpt.isDefined) {
          for (r <- regionsOpt.get) {
            put(r)
          }
        }
        intRDD.filterByInterval(region).toRDD.map(_._2)
      } case None => {
        throw new Exception("Not found in dictionary")
      }
    }
  }

  /**
   *  Transparent to the user, should only be called by get if IntervalRDD.get does not return data
   * Fetches the data from disk, using predicates and range filtering
   * Then puts fetched data in the IntervalRDD, and calls multiget again, now with the data existing
   *
   * @param region ReferenceRegion in which data is retreived
   */
  def put(region: ReferenceRegion) = {
    val seqRecord = sd(region.referenceName)
    if (seqRecord.isDefined) {
      var data: RDD[(String, T)] = sc.emptyRDD[(String, T)]

      // get alignment data for all samples
      files.map(fp => {
        val k = LazyMaterialization.filterKeyFromFile(fp)
        val d = load(region, fp).map(v => (k, v))
        data = data.union(d)
      })
      // insert into IntervalRDD
      if (intRDD == null) {
        intRDD = IntervalRDD(data.keyBy(r => getReferenceRegion(r._2)))
        intRDD.persist(StorageLevel.MEMORY_AND_DISK)
      } else {
        val t = intRDD
        intRDD = intRDD.multiput(data.keyBy(r => getReferenceRegion(r._2)))
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