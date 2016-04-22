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
import org.bdgenomics.utils.instrumentation.Metrics

import scala.collection.mutable.ListBuffer

object AlignmentLayoutTimers extends Metrics {
  val AlignmentLayout = timer("collect and filter alignment records")
}

object AlignmentRecordLayout extends Logging {

  /**
   * An implementation of AlignmentRecordLayout which takes in an RDD of (ReferenceRegion, AlignmentRecord) tuples, the reference String
   * over the region, the region viewed and samples viewed.
   *
   * @param rdd: RDD of (ReferenceRegion, AlignmentRecord) tuples
   * @param sampleIds: List of sample identifiers to be rendered
   * @return List of ReadJsons, which takes (AlignmentRecord, List[MisMatch]) tuples and picks the required information and mismatches
   */
  def apply(rdd: RDD[(ReferenceRegion, CalculatedAlignmentRecord)], sampleIds: List[String]): Map[String, Array[ReadJson]] = {
    val mappingTuples: Array[ReadJson] = {
      rdd.mapPartitions(AlignmentRecordLayout(_)).collect
    }
    val sampleMappedTuples = mappingTuples.groupBy(_.sampleId)
    sampleMappedTuples.filterKeys { sampleIds.contains(_) }
  }

  /**
   * An implementation of AlignmentRecordLayout which takes in an Iterator of (ReferenceRegion, AlignmentRecord) tuples, the reference String
   * over the region, and the region viewed.
   *
   * @param iter: Iterator of (ReferenceRegion, AlignmentRecord) tuples
   * @return Iterator of Read Tracks containing json for reads, mismatches and mate pairs
   */
  def apply(iter: Iterator[(ReferenceRegion, CalculatedAlignmentRecord)]): Iterator[ReadJson] = {
    new AlignmentRecordLayout(iter).collect
  }

}

object MergedAlignmentRecordLayout extends Logging {

  /**
   * An implementation of AlignmentRecordLayout which takes in an RDD of (ReferenceRegion, AlignmentRecord) tuples, the reference String
   * over the region, the region viewed and samples viewed.
   *
   * @param rdd: RDD of (ReferenceRegion, AlignmentRecord) tuples
   * @param referenceOpt: reference string used to calculate mismatches
   * @param region: ReferenceRegion to be viewed
   * @param sampleIds: List of sample identifiers to be rendered
   * @return List of Read Tracks containing json for reads, mismatches and mate pairs
   */
  def apply(rdd: RDD[(ReferenceRegion, AlignmentRecord)], referenceOpt: Option[String], region: ReferenceRegion, sampleIds: List[String], binSize: Int): Map[String, List[MutationCount]] = {

    // check for reference
    val reference = referenceOpt match {
      case Some(_) => referenceOpt.get
      case None => {
        log.error("Reference not provided")
        return Map.empty
      }
    }

    // collect and reduce mismatches for each sample
    val mismatches: RDD[(String, List[MisMatch])] = rdd.mapPartitions(MismatchLayout(_, reference, region))
      .reduceByKey(_ ++ _) // list of [sample, mismatches]

    // reduce point mismatches by start and end value
    mismatches.map(r => (r._1, PointMisMatch(r._2, binSize))).collect.toMap
  }

  /**
   * An implementation of AlignmentRecordLayout which takes in an RDD of (ReferenceRegion, AlignmentRecord) tuples, the reference String
   * over the region, the region viewed and samples viewed.
   *
   * @param rdd: RDD of (ReferenceRegion, (AlignmentRecord, List[MisMatch])) tuples
   * @return List of Read Tracks containing json for reads, mismatches and mate pairs
   */
  def apply(rdd: RDD[(ReferenceRegion, CalculatedAlignmentRecord)], binSize: Int): Map[String, List[MutationCount]] = {

    // collect and reduce mismatches for each sample
    val mismatches: RDD[(String, List[MisMatch])] = rdd
      .map(r => (r._2.record.getRecordGroupSample, r._2.mismatches))
      .reduceByKey(_ ++ _) // list of [sample, mismatches]

    // reduce point mismatches by start and end value
    mismatches.map(r => (r._1, PointMisMatch(r._2, binSize))).collect.toMap
  }

}

/**
 * An extension of TrackedLayout for AlignmentRecord data
 *
 * @param values The set of (Reference, AlignmentRecord) tuples to lay out in tracks
 */
class AlignmentRecordLayout(values: Iterator[(ReferenceRegion, CalculatedAlignmentRecord)]) extends Logging {
  val sequence = values.toList
  val sequenceGroupedBySample = groupBySample

  def groupBySample: List[ReadJson] = {
    sequence.map(rec => ReadJson(rec, rec._2.record.getRecordGroupSample))
  }

  def collect: Iterator[ReadJson] = {
    sequenceGroupedBySample.toIterator
  }

}

/**
 * An extension of TrackedLayout for AlignmentRecord data
 *
 * @param values The set of (Reference, AlignmentRecord) tuples to lay out in tracks
 */
class MergedAlignmentRecordLayout(values: Iterator[(ReferenceRegion, AlignmentRecord)]) extends Logging {

}

object ReadJson {
  /**
   * An implementation of ReadJson which converts AlignmentRecord data to ReadJson
   *
   * @param recs A tuple of reference region and alignment record
   * @param sampleID String that represents the sample ID of the person whose record this is
   * @param mismatches A List of MisMatch objects that go with the alignment record
   * @return Read Json class object
   */
  def apply(rec: (ReferenceRegion, CalculatedAlignmentRecord), sampleId: String): ReadJson = {
    new ReadJson(sampleId, rec._2.record.getReadName, rec._2.record.getStart, rec._2.record.getEnd, rec._2.record.getReadNegativeStrand, rec._2.record.getSequence, rec._2.record.getCigar, rec._2.record.getMapq, rec._2.mismatches) //removed track
  }
}

// json classes for alignmentrecord visual data
case class ReadJson(sampleId: String, readName: String, start: Long, end: Long, readNegativeStrand: Boolean, sequence: String, cigar: String, mapq: Int, mismatches: List[MisMatch]) //removed track
case class MatePair(val start: Long, val end: Long)

case class CalculatedAlignmentRecord(record: AlignmentRecord, mismatches: List[MisMatch])

