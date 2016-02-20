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
import org.apache.spark.{ Logging, SparkContext }
import org.apache.spark.SparkContext._
import org.bdgenomics.formats.avro.{ AlignmentRecord, Contig }
import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.utils.instrumentation.Metrics
import scala.collection.JavaConversions._
import scala.collection.mutable
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
   * @param reference: reference string used to calculate mismatches
   * @param region: ReferenceRegion to be viewed
   * @param sampleIds: List of sample identifiers to be rendered
   * @return List of Read Tracks containing json for reads, mismatches and mate pairs
   */
  def apply(rdd: RDD[(ReferenceRegion, AlignmentRecord)], reference: Option[String], region: ReferenceRegion, sampleIds: List[String]): Map[String, SampleTrack] = {
    val sampleTracks = new ListBuffer[(String, SampleTrack)]()
    val highRes = region.end - region.start < 10000

    val tracks: Map[String, Array[ReadsTrack]] = {
      if (highRes) {
        rdd.mapPartitions(AlignmentRecordLayout(_, reference, region)).collect.groupBy(_.sample)
      } else {
        rdd.mapPartitions(AlignmentRecordLayout(_, reference, region)).filter(!_.misMatches.isEmpty).collect.groupBy(_.sample)
      }
    }

    tracks.foreach {
      case (sample, track) => {
        val indexedTrack = track.zipWithIndex
        val matePairs = indexedTrack.flatMap(r => MatePairJson(r._1.matePairs, r._2))
        val mismatches = indexedTrack.flatMap(r => MisMatchJson(r._1.misMatches, r._2))
        val reads = indexedTrack.flatMap(r => ReadJson(r._1.records, r._2))
        val sampleTrack = new SampleTrack(reads.toList, matePairs.toList, mismatches.toList)
        sampleTracks += Tuple2(sample, sampleTrack)
      }
    }
    sampleTracks.toMap
  }

  /**
   * An implementation of AlignmentRecordLayout which takes in an Iterator of (ReferenceRegion, AlignmentRecord) tuples, the reference String
   * over the region, and the region viewed.
   *
   * @param iter: Iterator of (ReferenceRegion, AlignmentRecord) tuples
   * @param reference: reference string used to calculate mismatches
   * @param region: ReferenceRegion to be viewed
   * @return Iterator of Read Tracks containing json for reads, mismatches and mate pairs
   */
  def apply(iter: Iterator[(ReferenceRegion, AlignmentRecord)], reference: Option[String], region: ReferenceRegion): Iterator[ReadsTrack] = {
    new AlignmentRecordLayout(iter).collect(reference, region)
  }
}

/**
 * An extension of TrackedLayout for AlignmentRecord data
 *
 * @param values The set of (Reference, AlignmentRecord) tuples to lay out in tracks
 */
class AlignmentRecordLayout(values: Iterator[(ReferenceRegion, AlignmentRecord)]) extends TrackedLayout[AlignmentRecord, ReadsTrackBuffer] with Logging {
  val sequence = values.toList
  var trackBuilder = new ListBuffer[ReadsTrackBuffer]()

  val readPairs: Map[String, List[(ReferenceRegion, AlignmentRecord)]] = sequence.groupBy(_._2.readName)
  addTracks
  trackBuilder = trackBuilder.filter(_.records.nonEmpty)

  def addTracks {
    readPairs.foreach {
      p =>
        {
          val recs: List[(ReferenceRegion, AlignmentRecord)] = p._2
          val track: Option[ReadsTrackBuffer] = TrackTimers.FindConflict.time {
            trackBuilder.find(track => !track.conflicts(recs))
          }
          track.map(trackval => {
            trackval.records ++= recs
            trackBuilder -= trackval
            trackBuilder += trackval
          }).getOrElse(addTrack(new ReadsTrackBuffer(recs)))
        }
    }
  }

  def collect(reference: Option[String], region: ReferenceRegion): Iterator[ReadsTrack] =
    trackBuilder.map(t => Track(t, reference, region)).toIterator
}

/**
 * An implementation of ReadJson which converts AlignmentRecord data to ReadJson
 *
 * @param recs The list of (Reference, AlignmentRecord) tuples to lay out in json
 * @param track js track number
 * @return List of Read Json objects
 */
object ReadJson {
  def apply(recs: List[(ReferenceRegion, AlignmentRecord)], track: Int): List[ReadJson] = {
    recs.map(rec => new ReadJson(rec._2.readName, rec._2.start, rec._2.end, rec._2.readNegativeStrand, rec._2.sequence, rec._2.cigar, track))
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
case class ReadJson(readName: String, start: Long, end: Long, readNegativeStrand: Boolean, sequence: String, cigar: String, track: Long)
case class MatePairJson(val start: Long, val end: Long, track: Long)

// complete json object of reads data containing matepairs and mismatches
case class SampleTrack(val records: List[ReadJson], val matePairs: List[MatePairJson], val mismatches: List[MisMatchJson])

// untracked json classes
case class MatePair(start: Long, end: Long)
