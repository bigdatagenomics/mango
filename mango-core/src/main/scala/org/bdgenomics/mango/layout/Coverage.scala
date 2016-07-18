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
package org.bdgenomics.mango.layout

import net.liftweb.json.Serialization._
import org.apache.spark.rdd.RDD
import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.mango.core.util.VizUtils

/**
 * Coverage object handles all functionality related to position count data.
 */
object Coverage {
  /**
   * Formats coverage data from Iterable[Int] for each key
   * @param rdd RDD to compute from
   * @param region ReferenceRegion to calculate coverage over
   * @return
   */
  def stringifyCoverage(rdd: RDD[(String, Iterable[Any])], region: ReferenceRegion): Map[String, String] = {
    implicit val formats = net.liftweb.json.DefaultFormats

    // get bin size to mod by
    val binSize = VizUtils.getBinSize(region)
    val data = rdd
      .mapValues(_.asInstanceOf[Iterable[PositionCount]])
      .mapValues(r => r.filter(r => r.position <= region.end && r.position >= region.start && r.position % binSize == 0))
      .collect

    val flattened: Map[String, Iterable[PositionCount]] = data.groupBy(_._1) // group by key
      .map(r => {
        val grouped = r._2.flatMap(_._2)
          .groupBy(_.position) // merge duplicated positions over RDD partition borders
          .map(r => PositionCount(r._1, r._2.map(_.count).sum))
        (r._1, grouped)
      })

    flattened.mapValues(r => write(r))
  }
}

case class PositionCount(position: Long, count: Int)