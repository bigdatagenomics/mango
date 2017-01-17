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
package org.bdgenomics.mango.filters

import org.apache.spark.rdd.RDD
import org.bdgenomics.adam.models.{ SequenceDictionary, ReferenceRegion }
import org.bdgenomics.mango.core.util.VizUtils

/**
 * Aggregates RDDs to find frequency windows across whole RDD. These frequencies are aggregated together
 * by their corresponding ReferenceRegion. Window sizes are dependent on the user's browser. Here we use
 * VizUtils.screenSize as default for the browser.
 *
 * @param sd SequenceDictionary to match frequencies to. These are the chromosomes that are clickable in the browser.
 */
case class Discovery(sd: SequenceDictionary) extends Serializable {

  // Regions to aggregate frequency over. These are divied up by size VizUtils.screenSize.
  private val regions = {
    val windowSize = sd.records.map(_.length).max / VizUtils.screenSize
    sd.records.flatMap(r => {
      // window all possible regions of reference SequenceDictionary
      (0L until (r.length, windowSize)).map(n => ReferenceRegion(r.name, n, n + windowSize))
    })
  }

  /**
   * Gets aggregated, normalized frequencies at all locations of the genome. These regions are specified
   * by the regions variable.
   *
   * @param data data to calculate frequenies for
   * @return Array of windowed ReferenceRegions and their corresponding normalized frequencies.
   */
  def getFrequencies(data: RDD[ReferenceRegion]): Array[(ReferenceRegion, Double)] = {
    val regionsB = data.context.broadcast(regions)

    val frequencies = data.mapPartitions(iter => {
      // find all regions that overlap regions in sequence dictionary
      val arr = iter.flatMap(f => regionsB.value.filter(r => f.overlaps(r))).map((_, 1)).toArray
      arr.groupBy(_._1) // group by ReferenceRegion
        .map(r => (r._1, r._2.map(a => a._2).sum)).toIterator // reduce and sum by ReferenceRegion
    }).reduceByKey(_ + _).collect

    regionsB.unpersist()

    // normalize frequencies
    val max = frequencies.map(_._2).reduceOption(_ max _).getOrElse(1)
    frequencies.map(r => (r._1, r._2.toDouble / max))
  }

}
