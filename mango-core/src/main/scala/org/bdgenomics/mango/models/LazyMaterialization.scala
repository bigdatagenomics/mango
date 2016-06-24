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
import org.bdgenomics.adam.models.{ ReferenceRegion, SequenceDictionary }
import org.bdgenomics.adam.rdd.GenomicRegionPartitioner
import org.bdgenomics.mango.util.Bookkeep
import org.bdgenomics.utils.intervalrdd._
import org.bdgenomics.utils.misc.Logging

import scala.collection.mutable
import scala.collection.mutable.HashMap
import scala.reflect.ClassTag

abstract class LazyMaterialization[T: ClassTag, S: ClassTag] extends Serializable with Logging {

  def sc: SparkContext
  def dict: SequenceDictionary
  def chunkSize: Int
  def partitioner: Partitioner
  def bookkeep: Bookkeep

  // Keeps track of sample ids and corresponding files
  var fileMap: HashMap[String, String] = new HashMap()

  def keys: List[String] = fileMap.keys.toList
  def files: List[String] = fileMap.values.toList

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

  // Stores location of sample at a given filepath
  def loadSample(filePath: String, sampleId: Option[String] = None) {
    sampleId match {
      case Some(_) => fileMap += ((sampleId.get, filePath))
      case None    => fileMap += ((filePath, filePath))

    }
  }

  def loadADAMSample(filePath: String): String = {
    val sample = getFileReference(filePath)
    fileMap += ((sample, filePath))
    sample
  }

  def getFileMap: mutable.HashMap[String, String] = fileMap

  var intRDD: IntervalRDD[ReferenceRegion, S] = null

  def getFileReference(fp: String): String

  def loadFromFile(region: ReferenceRegion, k: String): RDD[T]

  def put(region: ReferenceRegion, ks: List[String])

  /* If the RDD has not been initialized, initialize it to the first get request
  * Gets the data for an interval for the file loaded by checking in the bookkeeping tree.
  * If it exists, call get on the IntervalRDD
  * Otherwise call put on the sections of data that don't exist
  * Here, ks, is an option of list of personids (String)
  */
  def getTree(region: ReferenceRegion, ks: List[String]): Option[IntervalRDD[ReferenceRegion, S]] = {
    val seqRecord = dict(region.referenceName)
    val regionsOpt = bookkeep.getMaterializedRegions(region, ks)
    seqRecord match {
      case Some(_) =>
        regionsOpt match {
          case Some(_) =>
            for (r <- regionsOpt.get) {
              put(r, ks)
            }
          case None =>
          // DO NOTHING
        }
        Option(intRDD.filterByInterval(region))
      case None =>
        None
    }
  }

}

case class UnsupportedFileException(message: String) extends Exception(message)