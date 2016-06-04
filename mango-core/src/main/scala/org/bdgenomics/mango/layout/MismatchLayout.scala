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

import htsjdk.samtools.{ CigarOperator, TextCigarCodec }
import org.apache.spark.Logging
import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.formats.avro.AlignmentRecord

import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer

object MismatchLayout extends Logging {
  /**
   * An implementation of MismatchLayout which takes in an alignmentRecord, reference and region
   * and finds all indels and mismatches
   *
   * @param record: AlignmentRecord
   * @param reference: reference string used to calculate mismatches
   * @param region: ReferenceRegion to be viewed
   * @return List of MisMatches
   */
  def apply(record: AlignmentRecord, reference: String, region: ReferenceRegion): List[MisMatch] = {
    alignMismatchesToRead(record, reference, region)
  }

  /**
   * An implementation of AlignmentRecordLayout which takes in an Iterator of (ReferenceRegion, AlignmentRecord) tuples, the reference String
   * over the region, and the region viewed.
   *
   * @param iter: Iterator of (ReferenceRegion, AlignmentRecord) tuples
   * @param reference: reference string used to calculate mismatches
   * @param region: ReferenceRegion to be viewed
   * @return Iterator of (sample, list of mismatch) pairs)
   */
  def apply(iter: Iterator[(ReferenceRegion, AlignmentRecord)], reference: String, region: ReferenceRegion): Iterator[(String, List[MisMatch])] = {
    val alignments: List[AlignmentRecord] = iter.toList.map(_._2)
    // get all mismatches for each read
    alignments.map(r => (r.getRecordGroupSample, MismatchLayout(r, reference, region))).toIterator
  }

  /**
   * Finds and returns all indels and mismatches of a given alignment record from an overlapping reference string.
   * Must take into account overlapping regions that are not covered by both the reference and record sequence.
   *
   * @param rec: AlignmentRecord
   * @param ref: reference string used to calculate mismatches
   * @param region: ReferenceRegion to be viewed
   * @return List of MisMatches
   */
  def alignMismatchesToRead(rec: AlignmentRecord, ref: String, region: ReferenceRegion): List[MisMatch] = {

    val regionSize = region.end - region.start

    var misMatches: ListBuffer[MisMatch] = new ListBuffer[MisMatch]()
    val refLength = ref.length - 1

    if (rec.getReadNegativeStrand == true) {
      return misMatches.toList
    }

    val cigar = TextCigarCodec.decode(rec.getCigar).getCigarElements()

    // string position
    var refIdx: Int = (rec.getStart - region.start).toInt
    var recIdx: Int = 0

    // actual position relative to reference region
    var refPos: Long = rec.getStart

    cigar.foreach {
      e =>
        {
          // required for reads that extend reference
          var misLen = 0
          var op: CigarOperator = null
          var refBase: Char = 'M'
          var recBase: Char = 'M'
          try {
            misLen = e.getLength
            op = e.getOperator
            recBase = rec.getSequence.charAt(recIdx)
            refBase = ref.charAt(refIdx)
          } catch {
            case e: Exception => {
              log.warn(e.getMessage)
              return misMatches.toList
            }
          }
          if (op == CigarOperator.X || op == CigarOperator.M) {
            try {
              for (i <- 0 to misLen - 1) {
                // required for reads that extend reference
                if (refIdx > refLength)
                  return misMatches.toList

                val recBase = rec.getSequence.charAt(recIdx)

                val refBase = ref.charAt(refIdx)
                if (refBase != recBase) {
                  misMatches += new MisMatch(op.toString, refPos, 1, recBase.toString, refBase.toString)
                }
                recIdx += 1
                refIdx += 1
                refPos += 1
              }
            } catch {
              case e: Exception => {
                log.warn(e.toString)
              }
            }
          } else if (op == CigarOperator.I) {
            try {
              val indel = rec.getSequence.substring(recIdx, recIdx + misLen)
              misMatches += new MisMatch(op.toString, refPos, misLen, indel, "")
              recIdx += misLen
            } catch {
              case e: Exception => {
                log.warn(e.toString)
              }
            }
          } else if (op == CigarOperator.D || op == CigarOperator.N) {
            try {
              val start = Math.min(refIdx, refLength)
              val end = Math.min(refIdx + misLen, refLength)
              val indel = ref.substring(start, end)
              misMatches += new MisMatch(op.toString, refPos, misLen, "", indel)
              refIdx += misLen
              refPos += misLen
            } catch {
              case e: Exception => {
                log.warn(e.toString)
              }
            }
          } else if (op == CigarOperator.S) {
            recIdx += misLen
          }

        }
    }
    misMatches.toList
  }

  /**
   * Determines weather a given AlignmentRecord contains indels using its cigar
   *
   * @param rec: AlignmentRecord
   * @return Boolean whether record contains any indels
   */
  def containsIndels(rec: AlignmentRecord): Boolean = {
    rec.getCigar.contains("I") || rec.getCigar.contains("D")
  }

  /**
   * Calculates the genetic complement of a strand
   *
   * @param sequence: genetic string
   * @return String: complement of sequence
   */
  def complement(sequence: String): String = {
    sequence.map {
      case 'A' => 'T'
      case 'T' => 'A'
      case 'C' => 'G'
      case 'G' => 'C'
      case 'W' | 'S' | 'Y' | 'R' | 'M' | 'K' | 'B' | 'D' | 'V' | 'H' | 'N' => 'N'
      case _ => 'N'
    }
  }

  //  private def getPosition(idx: Long, start: Long): Int = (idx - start).toInt
}

object MisMatchJson {

  /**
   * An implementation of MismatchJson which converts a list of Mismatches into MisMatch Json
   *
   * @param recs The list of MisMatches to lay out in json
   * @param track js track number
   * @return List of MisMatch Json objects
   */
  def apply(recs: List[MisMatch], track: Int): List[MisMatchJson] = {
    recs.map(rec => MisMatchJson(rec, track))
  }

  /**
   * An implementation of MismatchJson which converts a single Mismatch into MisMatch Json
   *
   * @param rec The single MisMatch to lay out in json
   * @param track js track number
   * @return List of MisMatch Json objects
   */
  def apply(rec: MisMatch, track: Int): MisMatchJson = {
    new MisMatchJson(rec.op, rec.refCurr, rec.length, rec.sequence, rec.refBase, track)
  }
}

object PointMisMatch {

  /**
   * aggregated point mismatch at a specific location
   *
   * @param mismatches: List of mismatches to be grouped by start value
   * @return List of aggregated mismatches and their corresponding counts
   */
  def apply(mismatches: List[MisMatch], binSize: Int): List[MutationCount] = {
    val grouped = mismatches.map(m => new MisMatch(m.op, (m.refCurr - (m.refCurr % binSize)), binSize, m.sequence, m.refBase)).groupBy(_.refCurr)
    val g = grouped.map(_._2).flatMap(reducePoints(_)).toList
    return g
  }

  /**
   * aggregated point mismatch at a specific location
   *
   * @param mismatches: List of mismatches to be grouped by start value
   * @return List of aggregated mismatches and their corresponding counts
   */
  def apply(mismatches: List[MisMatch]): List[MutationCount] = {
    val grouped = mismatches.groupBy(_.refCurr)
    val g = grouped.map(_._2).flatMap(reducePoints(_)).toList
    return g
  }

  /**
   * aggregated point mismatch at a specific location
   *
   * @param mismatches: List of mismatches to be grouped by start value
   * @return aggregated mismatches and their corresponding counts
   */
  private def reducePoints(mismatches: List[MisMatch]): List[MutationCount] = {

    var mutationCounts: ListBuffer[MutationCount] = new ListBuffer[MutationCount]
    // process mismatches
    val ms = mismatches.filter(_.op == "M")
    val indels = mismatches.filter(_.op != "M")

    if (ms.nonEmpty) {
      val length = ms.head.length
      val refCurr = ms.head.refCurr
      val refBase = ms.head.refBase

      // count each occurrence of a mismatch
      val mappedMs: Map[String, Long] = ms.map(r => (r.sequence, 1L))
        .groupBy(_._1)
        .map { case (group: String, traversable) => traversable.reduce { (a, b) => (a._1, a._2 + b._2) } }

      if (!mappedMs.isEmpty) {
        mutationCounts += MisMatchCount("M", refCurr, length, refBase, mappedMs)
      }
    }

    // process indels
    if (indels.nonEmpty) {
      val length = indels.head.length
      val refCurr = indels.head.refCurr

      val insertions: Map[String, Long] = indels.filter(_.op == "I").groupBy(_.sequence).mapValues(v => v.length)
      val deletions: Map[String, Long] = indels.filter(_.op == "D").groupBy(_.length.toString).mapValues(v => v.length)

      mutationCounts += IndelCount("indel", refCurr, Map("I" -> insertions, "D" -> deletions))

    }
    mutationCounts.toList
  }
}

// tracked MisMatch Json Object
case class MisMatchJson(op: String, refCurr: Long, length: Long, sequence: String, refBase: String, track: Long)

/**
 * aggregated point mismatch at a specific location
 *
 * @param refCurr: location of reference corresponding to mismatch
 * @param refBase: base at reference corresponding to mismatch
 * @param length: length of mismatch or indel
 * @param mismatches: Map of either [String, Long] for I,D or N or [String, (sequence, Long)] for M
 */
case class PointMisMatch(refCurr: Long, refBase: String, length: Long, indels: Map[String, Long], mismatches: Map[String, Long])

// untracked Mismatch Json Object
case class MisMatch(op: String, refCurr: Long, length: Long, sequence: String, refBase: String)

//  count = Map[Base, Count]
case class MisMatchCount(op: String, refCurr: Long, length: Long, refBase: String, count: Map[String, Long]) extends MutationCount
// count = Map[indel (I or D), (Sequence, Count)
case class IndelCount(op: String, refCurr: Long, count: Map[String, Any]) extends MutationCount

trait MutationCount {
  def op: String
  def refCurr: Long
  def count: Map[String, Any]
}

case class SampleIndelCount(sample: String, mutation: IndelCount) extends SampleCount {
  def diffMisMatch(other: SampleIndelCount): List[MisMatchCount] = {
    //TODO: Diff indels
    val primaryCount = mutation.count
    val secondaryCount = other.mutation.count
    List[MisMatchCount]()
  }
}

case class SampleMisMatchCount(sample: String, mutation: MisMatchCount) extends SampleCount {
  def diffMisMatch(other: SampleMisMatchCount): List[SampleMisMatchCount] = {
    val primaryCount = mutation.count
    val secondaryCount = other.mutation.count
    val diffCount = (primaryCount.toSet diff secondaryCount.toSet).toMap.filter(_._2 > 0)
    val diffCount2 = (secondaryCount.toSet diff primaryCount.toSet).toMap.filter(_._2 > 0)
    val primaryMutation = MisMatchCount(mutation.op, mutation.refCurr, mutation.length,
      mutation.refBase, diffCount.filter(_._2 > 0))

    val secondaryMutation = MisMatchCount(other.mutation.op, other.mutation.refCurr, other.mutation.length,
      other.mutation.refBase, diffCount2.filter(_._2 > 0))
    List(SampleMisMatchCount(sample, primaryMutation), SampleMisMatchCount(other.sample, secondaryMutation))
  }
}

case class SampleMutationCount(sample: String, mutation: MutationCount)

trait SampleCount {
  def sample: String
  def mutation: MutationCount
}
