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

object VizUtils {

  /*
   * Defualy bin size for screen resolution
   */
  val screenSize: Int = 1000

  /**
   * Returns the very last base in a given chromosome that
   * can be viewed relative to the reference
   *
   * @param end: end of referenceregion that has been queried
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

  /*
 * Trims level one strings to reference region
 * @param str: String to trim
 * @param region: ReferenceRegion to trim to
 *
 * @return trimmed string
 */
  def trimSequence(str: String, region: ReferenceRegion, size: Int): String = {
    val length = region.length.toInt
    val start = (region.start % size).toInt
    str.substring(start, start + length)
  }

}

/*
 * Calculates sample size used to calculate frequency. This number defines the fraction to sample from RDD[AlignmentRecord]
 * when calculating frequency
 *
 * @param partitions: number of partitions program is run with
 */
class SampleSize(partitions: Int) {

  /*
   * Normalizes the sample size by region. The larger the view region, the smaller the fraction of retrieved data should be.
   *
   * @param region: ReferenceRegion to be queried over
   */
  def normalizeByRegion(region: ReferenceRegion): Double = {
    val l = Math.max(1, Math.log(region.end - region.start))
    1 / l
  }
}

