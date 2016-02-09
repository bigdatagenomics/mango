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

import org.apache.spark.{ Logging, SparkContext }
import org.apache.spark.rdd.RDD
import org.apache.spark.SparkContext._
import org.bdgenomics.formats.avro.{ AlignmentRecord, Contig }
import org.bdgenomics.adam.models.ReferenceRegion
import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object AlignmentRecordLayout extends Logging {
  //Prepares alignment information in Json format
  def apply(rdd: RDD[(ReferenceRegion, AlignmentRecord)], reference: String, region: ReferenceRegion, sampleIds: List[String]): List[ReadTrack] = {
    val readTracks = new mutable.ListBuffer[ReadTrack]()

    for (sample <- sampleIds) {
      val sampleData = rdd.filter(_._2.recordGroupSample == sample)
      val tracks = sampleData.mapPartitions(TrackedLayout(_)).collect.zipWithIndex
      // tracks is list(Track[T], idx)
      val matePairs = tracks.flatMap(r => MatePairJson(r._1, r._2))
      val mismatches = tracks.flatMap(r => MisMatchJson(r._1, r._2, reference, region))
      val reads = tracks.flatMap(r => ReadJson(r._1, r._2))
      readTracks += new ReadTrack(sample, reads.toList, matePairs.toList, mismatches.toList)
    }
    readTracks.toList
  }
}

// converts tracks of alignmentrecord data to read tracks
object ReadJson {
  def apply(recs: Track[AlignmentRecord], track: Int): List[ReadJson] = {
    recs.records.map(rec => new ReadJson(rec._2.readName, rec._2.start, rec._2.end, rec._2.readNegativeStrand, rec._2.sequence, rec._2.cigar, track))
  }
}

// converts tracks of alignmentrecord data to mismatches
object MisMatchJson {
  def apply(recs: Track[AlignmentRecord], track: Int, reference: String, region: ReferenceRegion): List[MisMatchJson] = {
    val mismatches = recs.records.flatMap(rec => MismatchLayout.alignMismatchesToRead(rec._2, reference, region))
    mismatches.map(rec => MisMatchJson(rec, track))
  }

  def apply(rec: MisMatch, track: Int): MisMatchJson = {
    new MisMatchJson(rec.op, rec.refCurr, rec.start, rec.end, rec.sequence, rec.refBase, track)
  }
}

// converts traks of alignmentrecord data to mate pairs
object MatePairJson {
  def apply(recs: Track[AlignmentRecord], track: Int): List[MatePairJson] = {
    val pairs = recs.records.groupBy(_._2.readName).filter(_._2.size == 2).map(_._2)
    val nonOverlap = pairs.filter(r => !(r(0)._1.overlaps(r(1)._1)))
    nonOverlap.map(p => MatePairJson(p.map(_._1.end).min, p.map(_._1.start).max, track)).toList
  }
}

// tracked json objects for alignmentrecord visual data
case class ReadJson(readName: String, start: Long, end: Long, readNegativeStrand: Boolean, sequence: String, cigar: String, track: Long)
case class MisMatchJson(op: String, refCurr: Long, start: Long, end: Long, sequence: String, refBase: String, track: Long)
case class MatePairJson(val start: Long, val end: Long, track: Long)
case class ReadTrack(val sample: String, val records: List[ReadJson], val matePairs: List[MatePairJson], val mismatches: List[MisMatchJson])
