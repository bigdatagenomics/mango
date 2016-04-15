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

import com.github.erictu.intervaltree.IntervalTree
import org.bdgenomics.adam.models.ReferenceRegion

import scala.collection.mutable.{ HashMap, ListBuffer }

class Bookkeep(chunkSize: Int) {

  var bookkeep: HashMap[String, IntervalTree[ReferenceRegion, String]] = new HashMap()

  def rememberValues(region: ReferenceRegion, k: String): Unit = rememberValues(region, List(k))

  /*
  * Logs key and region values in a bookkeeping structure per chromosome
  *
  * @param region: ReferenceRegion to remember
  * @param ks: keys to remember
  */
  def rememberValues(region: ReferenceRegion, ks: List[String]): Unit = {
    if (bookkeep.contains(region.referenceName)) {
      bookkeep(region.referenceName).insert(region, ks.toIterator)
    } else {
      val newTree = new IntervalTree[ReferenceRegion, String]()
      newTree.insert(region, ks.toIterator)
      bookkeep += ((region.referenceName, newTree))
    }
  }

  /**
   * generates a list of reference regions that were not found in bookkeeping structure
   *
   * @param region that is divided into chunks and searched for in bookkeeping structure
   * @param ks in which region is searched over. these are sample IDs
   * @return List of reference regions not found in bookkeeping structure
   */
  def getMissingRegions(region: ReferenceRegion, ks: List[String]): Option[List[ReferenceRegion]] = {
    var regions: ListBuffer[ReferenceRegion] = new ListBuffer[ReferenceRegion]()
    var start = region.start / chunkSize * chunkSize
    var end = start + (chunkSize - 1)

    while (start <= region.end) {
      val r = new ReferenceRegion(region.referenceName, start, end)
      val size = {
        try {
          bookkeep(r.referenceName).search(r).length
        } catch {
          case ex: NoSuchElementException => 0
        }
      }
      if (size < ks.size) {
        regions += r
      }
      start += chunkSize
      end += chunkSize
    }

    if (regions.size < 1) {
      None
    } else {
      Bookkeep.mergeRegions(Option(regions.toList))
    }

  }

  /**
   * gets materialized regions that are not yet loaded into the bookkeeping structure
   *
   * @param region that to be searched over
   * @param ks in which region is searched over. these are sample IDs
   * @return List of materialied and merged reference regions not found in bookkeeping structure
   */
  def getMaterializedRegions(region: ReferenceRegion, ks: List[String]): Option[List[ReferenceRegion]] = {
    val start = region.start / chunkSize * chunkSize
    val end = region.end / chunkSize * chunkSize + (chunkSize - 1)
    getMissingRegions(new ReferenceRegion(region.referenceName, start, end), ks)
  }

}
object Bookkeep {

  /**
   * generates a list of closely overlapping regions, counting for gaps in the list
   *
   * @note For example, given a list of regions with ranges (0, 999), (1000, 1999) and (3000, 3999)
   * This function will consolidate adjacent regions and output (0, 1999), (3000, 3999)
   * @note Requires that list region is ordered
   * @param regionsOpt Option of list of regions to merge
   * @return Option of list of merged adjacent regions
   */
  def mergeRegions(regionsOpt: Option[List[ReferenceRegion]]): Option[List[ReferenceRegion]] = {
    regionsOpt match {
      case Some(_) =>
        val regions = regionsOpt.get
        var rmerged: ListBuffer[ReferenceRegion] = new ListBuffer[ReferenceRegion]()
        rmerged += regions.head
        for (r2 <- regions) {
          if (r2 != regions.head) {
            val r1 = rmerged.last
            if (r1.end == r2.start - 1) {
              rmerged -= r1
              rmerged += r1.hull(r2)
            } else {
              rmerged += r2
            }
          }
        }
        Option(rmerged.toList)
      case None => None
    }
  }

}