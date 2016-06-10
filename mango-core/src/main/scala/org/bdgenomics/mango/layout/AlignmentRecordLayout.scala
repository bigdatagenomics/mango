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

import org.apache.spark.rdd.RDD
import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.formats.avro.AlignmentRecord
import org.bdgenomics.utils.instrumentation.Metrics
import org.bdgenomics.utils.misc.Logging

import scala.collection.mutable.ListBuffer

object AlignmentLayoutTimers extends Metrics {
  val AlignmentLayout = timer("collect and filter alignment records")
}

object AlignmentRecordLayout extends Logging {

  /**
   * An implementation of AlignmentRecordLayout which takes in an RDD of (ReferenceRegion, AlignmentRecord) tuples, the reference String
   * over the region, the region viewed and samples viewed.
   *
   * @param data: Array of (ReferenceRegion, AlignmentRecord) tuples
   * @return List of Read Tracks containing json for reads, mismatches and mate pairs
   */
  def apply(data: Array[(ReferenceRegion, CalculatedAlignmentRecord)]): SampleTrack = {

    val track: List[ReadsTrack] = new AlignmentRecordLayout(data).collect

    val indexedTrack = track.zipWithIndex
    val matePairs = indexedTrack.flatMap(r => MatePairJson(r._1.matePairs, r._2))
    val mismatches = indexedTrack.flatMap(r => MisMatchJson(r._1.misMatches, r._2))
    val reads = indexedTrack.flatMap(r => ReadJson(r._1.records, r._2))
    new SampleTrack(reads.toList, matePairs.toList, mismatches.toList)
  }

  def apply(data: Map[String, Array[CalculatedAlignmentRecord]]): Map[String, SampleTrack] = {
    val regionData: Map[String, Array[(ReferenceRegion, CalculatedAlignmentRecord)]] = data.mapValues(r => r.map(c => (ReferenceRegion(c.record), c)))
    regionData.map(r => (r._1, AlignmentRecordLayout(r._2)))
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

  /**
   * An implementation of AlignmentRecordLayout which takes in an RDD of (ReferenceRegion, AlignmentRecord) tuples, the reference String
   * over the region, the region viewed and samples viewed.
   *
   * @param sampleIds    : List of sample identifiers to be rendered
   * @param alignmentData: Map of alignment data
   * @return Map of Read Tracks containing json for reads, mismatches and mate pairs
   */
  def diffRecords(sampleIds: List[String], alignmentData: Map[String, List[MutationCount]]): Map[String, List[MutationCount]] = {
    sampleIds.length match {
      case 2 => {
        val primarySampleIndels = alignmentData.get(sampleIds(0)).get.filter(f => f.op != "M")
          .asInstanceOf[List[IndelCount]]
          .map(f => SampleIndelCount(sampleIds(0), f))
        val secondarySampleIndels = alignmentData.get(sampleIds(1)).get.filter(f => f.op != "M")
          .asInstanceOf[List[IndelCount]]
          .map(f => SampleIndelCount(sampleIds(1), f))
        val mergedSampleIndels = primarySampleIndels ++ secondarySampleIndels
        val baseIndelPairings = mergedSampleIndels.groupBy(f => f.mutation.refCurr).values
        val singleIndelPairs = baseIndelPairings.filter(_.length == 1)
        val doubleIndelPairs = baseIndelPairings.filter(_.length > 1)
        val indelOutput = doubleIndelPairs.map(f => f(0).diffMisMatch(f(1)))
        //TODO: Figure out how to merge indels with variable lengths and sequences

        val primarySampleMismatch = alignmentData.get(sampleIds(0)).get.filter(f => f.op == "M")
          .asInstanceOf[List[MisMatchCount]]
          .map(f => SampleMisMatchCount(sampleIds(0), f))
        val secondarySampleMismatch = alignmentData.get(sampleIds(1)).get.filter(f => f.op == "M")
          .asInstanceOf[List[MisMatchCount]]
          .map(f => SampleMisMatchCount(sampleIds(1), f))

        val mergedSampleMismatches = primarySampleMismatch ++ secondarySampleMismatch
        //List of base pairings
        val basePairings = mergedSampleMismatches.groupBy(f => f.mutation.refCurr).values
        val singleOutputPairs: Iterable[SampleMisMatchCount] = basePairings.filter(_.length == 1).map(f => f(0))
        val doublePairs = basePairings.filter(_.length > 1)
        val doubleOutputPairs: Iterable[SampleMisMatchCount] = doublePairs.flatMap(f => f(0).diffMisMatch(f(1))).filter(f => f.mutation.count.nonEmpty)
        val mismatchPairs: Map[String, List[MutationCount]] = (singleOutputPairs ++ doubleOutputPairs)
          .groupBy(f => f.sample).map(f => (f._1, f._2.map(b => b.mutation).toList))
        mismatchPairs
      }
      case _ => {
        Map[String, List[MutationCount]]()
      }
    }
  }
}

/**
 * An extension of TrackedLayout for AlignmentRecord data
 *
 * @param values The set of (Reference, AlignmentRecord) tuples to lay out in tracks
 */
class AlignmentRecordLayout(values: Array[(ReferenceRegion, CalculatedAlignmentRecord)]) extends TrackedLayout[CalculatedAlignmentRecord, ReadsTrackBuffer] with Logging {
  val sequence = values
  var trackBuilder = new ListBuffer[ReadsTrackBuffer]()

  val readPairs = sequence.groupBy(_._2.record.getReadName).values.toList.sortBy(r => r.map(_._2.mismatches.length).sum)

  addTracks
  trackBuilder = trackBuilder.filter(_.records.nonEmpty)

  def addTracks {
    readPairs.foreach {
      recs =>
        {
          val track: Option[ReadsTrackBuffer] =
            trackBuilder.find(track => !track.conflicts(recs))

          track.map(trackval => {
            trackval.records ++= recs
            trackBuilder -= trackval
            trackBuilder += trackval
          }).getOrElse(addTrack(new ReadsTrackBuffer(recs)))
        }
    }
  }

  def collect: List[ReadsTrack] =
    trackBuilder.reverse.map(t => Track(t)).toList
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
   * @param recs The list of (Reference, AlignmentRecord) tuples to lay out in json
   * @param track js track number
   * @return List of Read Json objects
   */
  def apply(recs: List[(ReferenceRegion, AlignmentRecord)], track: Int): List[ReadJson] = {
    recs.map(rec => new ReadJson(rec._2.getReadName, rec._2.getStart, rec._2.getEnd, rec._2.getReadNegativeStrand, rec._2.getSequence, rec._2.getCigar, rec._2.getMapq, track))
  }
}

object MatePairJson {

  /**
   * An implementation of MatePairJson which converts a list of MatePairs into MatePair Json
   *
   * @param recs The list of MatePairs to be layed out in json
   * @param track js track number
   * @return List of MatePair Json objects
   */
  def apply(recs: List[MatePair], track: Int): List[MatePairJson] = {
    recs.map(r => MatePairJson(r.start, r.end, track))
  }
}

// tracked json classes for alignmentrecord visual data
case class ReadJson(readName: String, start: Long, end: Long, readNegativeStrand: Boolean, sequence: String, cigar: String, mapq: Int, track: Long)
case class MatePairJson(val start: Long, val end: Long, track: Long)

// complete json object of reads data containing matepairs and mismatches
case class SampleTrack(val records: List[ReadJson], val matePairs: List[MatePairJson], val mismatches: List[MisMatchJson])

// untracked json classes
case class MatePair(start: Long, end: Long)

case class CalculatedAlignmentRecord(record: AlignmentRecord, mismatches: List[MisMatch]) extends Serializable
case class SampleCoverage(sample: String, referenceName: String, position: Long, count: Int)
