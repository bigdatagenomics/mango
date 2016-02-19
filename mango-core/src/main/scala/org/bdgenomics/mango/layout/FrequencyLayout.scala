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

import org.apache.spark.Logging
import org.apache.spark.rdd.RDD
import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.formats.avro.AlignmentRecord
import scala.collection.mutable.ListBuffer

object FrequencyLayout extends Logging {

  def apply(rdd: RDD[AlignmentRecord], region: ReferenceRegion, sampleIds: List[String]): Map[String, List[FreqJson]] = {
    val jsonList = new ListBuffer[(String, List[FreqJson])]()

    val freqData: List[(String, Long, Long)] = rdd.mapPartitions(FrequencyLayout(_, region)).collect.toList
    for (sample <- sampleIds) {
      val sampleData: List[FreqJson] = freqData.filter(_._1 == sample).map(r => FreqJson(r._2, r._3))
      jsonList += Tuple2(sample, sampleData)
    }
    jsonList.toMap
  }

  def apply(iter: Iterator[AlignmentRecord], region: ReferenceRegion): Iterator[(String, Long, Long)] = {
    new FrequencyLayout(iter, region).collect
  }

}

class FrequencyLayout(array: Iterator[AlignmentRecord], region: ReferenceRegion) extends Logging {
  // Prepares frequency information in Json format
  // TODO: this algorithm can better perform using interval partitions
  val freqBuffer = new ListBuffer[(String, Long, Long)]
  val records = array.toList
  var i = region.start

  while (i <= region.end) {
    val currSubset = records.filter(value => ((value.getStart <= i) && (value.getEnd >= i))).groupBy(_.recordGroupSample)
    currSubset.foreach(p => freqBuffer += ((p._1, i, p._2.length)))
    i = i + 1
  }

  def collect(): Iterator[(String, Long, Long)] = freqBuffer.toIterator

}

case class FreqJson(base: Long, freq: Long)
