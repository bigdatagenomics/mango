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
package org.bdgenomics.mango.core.util

import org.bdgenomics.adam.models.{ ReferenceRegion, SequenceRecord }

/**
 * Indicator used to determine whether current elements in cache match the region and resolution of the current cache
 *
 * @param region current ReferenceRegion
 * @param resolution current Resolution
 */
case class VizCacheIndicator(region: ReferenceRegion, resolution: Int)

/**
 * Grab bag of functions and constants used to interact with frontend
 */
object VizUtils {

  /*
   * Default score for feature files. Used for grey scale in browser.
   * For more information see https://genome.ucsc.edu/FAQ/FAQformat#format1 as reference.
   */
  val defaultScore: Int = 1000

  /*
   * Defualt bin size for screen resolution
   */
  val screenSize: Int = 1000

  /**
   * Returns the valid start for ReferenceRegion.
   *
   * @param start: start of ReferenceRegion that has been queried
   * @return First valid base in region being viewed
   */
  def getStart(start: Long, zeroBase: Boolean = false): Long = {
    if (zeroBase) Math.max(start, 0L)
    else Math.max(start, 1L)
  }

  /**
   * Returns the very last base in a given chromosome that
   * can be viewed relative to the reference
   *
   * @param end: end of ReferenceRegion that has been queried
   * @param rec: Option of sequence record that is being viewed
   * @return Last valid base in region being viewed
   */
  def getEnd(end: Long, rec: Option[SequenceRecord]): Long = {
    rec match {
      case Some(_) => Math.min(end, rec.get.length)
      case None    => end
    }
  }

  /**
   * Returns the bin size for calculation based on the client's screen
   * size
   * @param region Region to bin over
   * @return Bin size to calculate over
   */
  def getBinSize(region: ReferenceRegion, size: Int = screenSize): Int = {
    val binSize = ((region.end - region.start) / size).toInt
    return Math.max(1, binSize)
  }

  /**
   * Trims level one strings to reference region
   * @param str: String to trim
   * @param strRegion: string region
   * @param trimRegion: region to trim string by
   *
   * @return trimmed string
   */
  def trimSequence(str: String, strRegion: ReferenceRegion, trimRegion: ReferenceRegion): String = {
    // get length of string
    val length = trimRegion.length.toInt

    // get positive difference in start positions
    val start = Math.max(0, ((trimRegion.start - strRegion.start).toInt))

    // get length of parsed string bounded by string length
    val end = Math.min(str.length, start + length)
    str.substring(start, end)
  }

}
