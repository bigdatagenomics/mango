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
package org.bdgenomics.mango.RDD

import edu.berkeley.cs.amplab.spark.intervalrdd.IntervalRDD
import org.apache.spark.Logging
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel
import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.formats.avro.AlignmentRecord

import scala.collection.mutable.ListBuffer

class FrequencyRDD extends Serializable with Logging {

  var freqRDD: IntervalRDD[ReferenceRegion, SampleFreq] = null

  /*
   * Gets frequency data for samples over a given region.
   *
   * @param region: ReferenceRegion to query over
   * @param samples: List[String of all samples to query for
   *
   * @return Map of (Sample, and list of FreqJson) at every location over the queried region
   */
  def get(region: ReferenceRegion, samples: List[String], sampleSize: Option[Int] = None): Map[String, Iterable[FreqJson]] = {
    var data: RDD[(ReferenceRegion, SampleFreq)] = freqRDD.filterByInterval(region).toRDD
    data = data.filter(r => samples.contains(r._2.sample))

    val d = data.map(r => (r._1.start, r._2.sample, r._2.freq))
      .groupBy(_._2) // Group By Sample
      .map(r => (r._1, r._2.map(x => FreqJson(x._1, x._3))))

    sampleSize match {
      case Some(_) => d.takeSample(false, sampleSize.get).toMap
      case None    => d.collect.toMap
    }

  }

  /*
   * Loads Frequency data into intervalRDD.
   *
   * @param data: Frequency data to load
   */
  def loadToRDD(data: RDD[(ReferenceRegion, SampleFreq)]): Unit = {
    if (freqRDD == null) {
      freqRDD = IntervalRDD(data)
      freqRDD.persist(StorageLevel.MEMORY_AND_DISK)
    } else {
      freqRDD = freqRDD.multiput(data)
      freqRDD.persist(StorageLevel.MEMORY_AND_DISK)
    }
  }

  /*
   * Calculates and formats frequency data.
   *
   * @param rdd: AlignmentRecords to calculate frequency from
   * @param region: ReferenceRegion over which to extract frequency data
   * @param sampleSize: Option of int to scale resulting frequency by
   * @param stride: stride over which to space ReferenceRegions to calculate frequency data
   */
  def put(rdd: RDD[AlignmentRecord], region: ReferenceRegion, sampleSize: Option[Double] = None, stride: Long = 3L): Unit = {

    var regions: ListBuffer[ReferenceRegion] = ListBuffer.range(region.start, region.end, stride)
      .map(r => ReferenceRegion(region.referenceName, r, r + 1))

    val endRegion = ReferenceRegion(region.referenceName, region.end, region.end + 1)

    if (!regions.contains(endRegion))
      regions += endRegion

    val sz: Int = sampleSize match {
      case Some(_) => Math.round((1 / sampleSize.get)).toInt
      case None    => 1
    }

    val data: RDD[(ReferenceRegion, SampleFreq)] =
      rdd.mapPartitions(calculateFreq(_, regions.toList))
        .reduceByKey(_ + _).map(r => (r._1._1, SampleFreq(r._1._2, r._2 * sz)))

    loadToRDD(data)
  }

  /*
 * Maps Alignment records to frequency by finding length of Alignment Records at each location
 *
 * @param array: Iterator of alignment records
 * @param region: ReferenceRegion over which to calculate frequency
 */
  private def calculateFreq(array: Iterator[AlignmentRecord], regions: Iterable[ReferenceRegion]): Iterator[((ReferenceRegion, String), Int)] = {
    // map each regions to the number of alignment records at that point with its corresponding sample
    val records = array.toList
    val r = regions.map({
      r =>
        (r, records.filter(ar => ReferenceRegion(ar).overlaps(r))
          .map(r => r.getRecordGroupSample))
    }).flatMap(r => r._2.map(x => (r._1, x)))
      .map(r => (r, 1)).groupBy(_._1) // group by similar sampleId and ReferenceRegion
      .map(r => (r._1, r._2.size))
    r.toIterator
  }

}

case class SampleFreq(sample: String, freq: Int)

case class FreqJson(base: Long, freq: Int)
