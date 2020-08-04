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
package org.bdgenomics.mango.util

import org.bdgenomics.adam.models.{ SequenceDictionary, ReferenceRegion }
import org.bdgenomics.utils.interval.array.{ IntervalArray, ConcreteIntervalArray }
import grizzled.slf4j.Logging
import scala.collection.mutable
import scala.collection.mutable.{ HashMap, ListBuffer }

/**
 * Bookkeep keeps track of what chunks of data have been loaded into memory. This is
 * used for all Materialization structures. It is build from an IntervalTree
 * which stores the regions that have been loaded for each id (which is a string)
 * @param chunkSize Chunk size is the size at which data is loaded into memory
 */
class Bookkeep(chunkSize: Long) extends Serializable with Logging {

  /*
   * The maximum width across all intervals in this bookkeeping structure.
   */
  private val maxIntervalWidth = 200

  /*
   * Holds hash of ReferenceName pointing to IntervalTree of (Region, ID)
   */
  var intArray: IntervalArray[ReferenceRegion, List[String]] = new ConcreteIntervalArray(Array.empty[(ReferenceRegion, List[String])], maxIntervalWidth)

  /**
   * Keeps track of ordering of most recently viewed chromosomes
   */
  var queue = new mutable.Queue[String]()

  def rememberValues(region: ReferenceRegion, k: String): Unit = rememberValues(region, List(k))

  def rememberValues(sd: SequenceDictionary, ks: List[String]): Unit = {
    sd.records.map(r => ReferenceRegion(r.name, 0, Bookkeep.roundDown(r.length + chunkSize - 1, chunkSize)))
      .foreach(r => rememberValues(r, ks))
  }

  /**
   * Drops all values from a given sequence record
   */
  def dropValues(): String = {
    try {
      val droppedChr = queue.dequeue()
      intArray = intArray.filter(_._1.referenceName != droppedChr)
      droppedChr
    } catch {
      case e: NoSuchElementException =>
        logger.warn("bookkeeping queue is empty")
        null
    }
  }

  /**
   * Logs key and region values in a bookkeeping structure per chromosome
   *
   * @param region: ReferenceRegion to remember
   * @param ks: keys to remember
   */
  def rememberValues(region: ReferenceRegion, ks: List[String]): Unit = {
    if (!queue.contains(region.referenceName))
      queue.enqueue(region.referenceName)
    intArray = intArray.insert(Iterator((region, ks)))
  }

  /**
   * Generates a list of reference regions that were not found in bookkeeping structure.
   *
   * @param region that is divided into chunks and searched for in bookkeeping structure
   * @param ks in which region is searched over. these are sample IDs
   * @return List of reference regions not found in bookkeeping structure
   */
  def getMissingRegions(region: ReferenceRegion, ks: List[String]): List[ReferenceRegion] = {
    // get number of chunks in region
    val roundedStart = Bookkeep.roundDown(region.start, chunkSize)
    val roundedEnd = Bookkeep.roundDown(region.end + chunkSize - 1, chunkSize)

    val chunkCount = Math.max(1, ((roundedEnd - roundedStart) / chunkSize).toInt)

    // get chunked reference regions
    val chunks = (0 until chunkCount)
      .map(c => {
        val start = c * chunkSize + roundedStart
        val end = start + chunkSize
        region.copy(start = start, end = end)
      })

    // filter out chunks that are covered by intArray
    val missing = chunks.filter(c => intArray.filter(elem => elem._1.contains(c)).length == 0)
    Bookkeep.mergeRegions(missing.toList)
  }

}
object Bookkeep {

  /**
   * Rounds numbers down to the neares 'rounded'
   *
   * @param number number to round
   * @param rounded number rounded to
   * @return new rounded number
   */

  def roundDown(number: Long, rounded: Long): Long = {
    number - (number % rounded)
  }

  /**
   * Merges together overlapping ReferenceRegions in a list of ReferenceRegions.
   *
   * @note For example, given a list of regions with ranges (0, 999), (1000, 1999) and (3000, 3999)
   * This function will consolidate adjacent regions and output (0, 1999), (3000, 3999)
   *
   * @param regions list of regions to merge
   * @return Option of list of merged adjacent regions
   */
  def mergeRegions(regions: List[ReferenceRegion]): List[ReferenceRegion] = {
    val sorted = regions.sortBy(_.start)

    sorted.foldLeft(List[ReferenceRegion]()) {
      (curList, r) =>
        {
          curList match {
            case Nil => List(r)
            case head :: rest => {
              if (r.overlaps(head) || r.isAdjacent(head))
                r.merge(head) :: rest
              else r :: curList
            }
          }
        }
    }.reverse
  }

}
