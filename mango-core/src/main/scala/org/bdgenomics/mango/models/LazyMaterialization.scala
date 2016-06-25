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