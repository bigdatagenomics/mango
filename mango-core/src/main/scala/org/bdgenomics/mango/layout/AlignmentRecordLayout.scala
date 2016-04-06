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
import scala.reflect.ClassTag

object AlignmentLayoutTimers extends Metrics {
  val AlignmentLayout = timer("collect and filter alignment records")
}

object AlignmentRecordLayout extends Logging {

  /**
   * An implementation of AlignmentRecordLayout which takes in an RDD of (ReferenceRegion, AlignmentRecord) tuples, the reference String
   * over the region, and the region viewed.
   *
   * @param rdd: RDD of (ReferenceRegion, AlignmentRecord) tuples
   * @param sampleIds: List of sample identifiers to be rendered
   * @return List of ReadJsons, which takes (AlignmentRecord, List[MisMatchJson]) tuples and picks the required information and mismatches
   */
  def apply(rdd: RDD[(ReferenceRegion, CalculatedAlignmentRecord)], sampleIds: List[String]): ReadJson = {
    val mappingTuples: List[(AlignmentRecord, List[MisMatchJson])] = {
        rdd.mapPartitions(AlignmentRecordLayout(_)).collect
    }
    ReadJson(mappingTuples)
  }

  /**
   * An implementation of AlignmentRecordLayout which takes in an Iterator of (ReferenceRegion, AlignmentRecord) tuples, the reference String
   * over the region, and the region viewed.
   *
   * @param iter: Iterator of (ReferenceRegion, AlignmentRecord) tuples
   * @return Iterator of tuples of alignment records to list of mismatches 
   */
  def apply(iter: Iterator[(ReferenceRegion, CalculatedAlignmentRecord)]): Iterator[CalculatedAlignmentRecord] = {
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
   * @return List of ReadJsons containing json for reads and corresponding mismatches
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
    val mismatches: RDD[(String, List[MisMatch])] = rdd.map(r => (r._2.record.getRecordGroupSample, r._2.mismatches))
      .reduceByKey(_ ++ _) // list of [sample, mismatches]

    // reduce point mismatches by start and end value
    mismatches.map(r => (r._1, PointMisMatch(r._2, binSize))).collect.toMap
  }

}

/**
 * AlignmentRecord data
 *
 * @param values The set of (Reference, AlignmentRecord) tuples
 */

class AlignmentRecordLayout(values: Iterator[(ReferenceRegion, CalculatedAlignmentRecord)]) with Logging {
  val sequence = values.toList

  def collect: Iterator[CalculatedAlignmentRecord] = {
    val records = new ListBuffer[CalculatedAlignmentRecord]
    sequence.foreach {
      p =>
        {
          records += p._2
        }
    }
    records.toIterator
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
   * @param recs The list of (AlignmentRecord, List[MisMatchJson]) tuples to lay out in json
   * @return List of Read Json objects
   */
  def apply(recs: List[(AlignmentRecord, List[MisMatchJson])]): List[ReadJson] = {
    recs.map(rec => new ReadJson(rec._1.getReadName, rec._1.getStart, rec._1.getEnd, rec._1.getReadNegativeStrand, rec._1.getSequence, rec._1.getCigar, rec._1.getMapq, rec._2)) //removed track
  }
}

object MatePairJson {

  /**
   * An implementation of MatePairJson which converts a list of MatePairs into MatePair Json
   *
   * @param recs The list of MatePairs to be layed out in json
   * @return List of MatePair Json objects
   */
  def apply(recs: List[MatePair]): List[MatePairJson] = {
    recs.map(r => MatePairJson(r.start, r.end))
  }
}

// json classes for alignmentrecord visual data
case class ReadJson(readName: String, start: Long, end: Long, readNegativeStrand: Boolean, sequence: String, cigar: String, mapq: Int, mismatches: List[MisMatchJson]) //removed track
case class MatePairJson(val start: Long, val end: Long)

case class CalculatedAlignmentRecord(record: AlignmentRecord, mismatches: List[MisMatch])
