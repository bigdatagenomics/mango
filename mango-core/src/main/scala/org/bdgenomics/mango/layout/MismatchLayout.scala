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

import htsjdk.samtools.{ Cigar, CigarOperator, CigarElement, TextCigarCodec }
import org.apache.spark.Logging
import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.formats.avro.{ AlignmentRecord, Contig }
import scala.collection.JavaConversions._
import scala.collection.mutable
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

    if (rec.getReadNegativeStrand == true) {
      return misMatches.toList
    }
    
    val cigar = TextCigarCodec.decode(rec.getCigar).getCigarElements()

    var refIdx = 0L
    var recIdx = 0L

    // calculate start value
    if (rec.getStart >= region.start) {
      refIdx = rec.getStart + 1
      recIdx = rec.getStart
    } else {
      refIdx = region.start + 1
      recIdx = region.start
    }

    cigar.foreach {
      e =>
        {
          var misLen = 0
          var op: CigarOperator = null
          var refBase: Char = 'M'
          var recBase: Char = 'M'
          try {
            misLen = e.getLength
            op = e.getOperator
            recBase = rec.getSequence.charAt(getPosition(recIdx, rec.getStart))
            refBase = ref.charAt(getPosition(refIdx, region.start))
          } catch {
            case e: Exception => misMatches.toList
          }
          if (op == CigarOperator.X || op == CigarOperator.M) {
            try {
              for (i <- 0 to misLen - 1) {
                // if index position is not within region
                if (refIdx <= region.end && refIdx >= region.start) {
                  val recBase = rec.getSequence.charAt(getPosition(recIdx, rec.getStart))
                  val refBase = ref.charAt(getPosition(refIdx, region.start))
                  if (refBase != recBase) {
                    val start = recIdx
                    misMatches += new MisMatch(op.toString, refIdx, start, start + 1, recBase.toString, refBase.toString)
                  }
                }
                recIdx += 1
                refIdx += 1
              }
            } catch {
              case e: Exception => log.warn(e.toString)
            }
          } else if (op == CigarOperator.I) {
            val end = recIdx + misLen
            val stringStart = (recIdx - rec.getStart).toInt
            val indel = rec.getSequence.substring(stringStart, stringStart + misLen)
            misMatches += new MisMatch(op.toString, refIdx, recIdx, end, indel, null)
            recIdx += misLen
          } else if (op == CigarOperator.D || op == CigarOperator.N) {
            val end = recIdx + misLen
            val stringStart = getPosition(recIdx, rec.getStart)
            val indel = rec.getSequence.substring(stringStart, stringStart + misLen)
            misMatches += new MisMatch(op.toString, refIdx, recIdx, end, indel, null)
            refIdx += misLen
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
   * @param record: AlignmentRecord
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

  private def getPosition(idx: Long, start: Long): Int = (idx - start).toInt
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
   * @param recs The single MisMatch to lay out in json
   * @param track js track number
   * @return List of MisMatch Json objects
   */
  def apply(rec: MisMatch, track: Int): MisMatchJson = {
    new MisMatchJson(rec.op, rec.refCurr, rec.start, rec.end, rec.sequence, rec.refBase, track)
  }
}

// tracked MisMatch Json Object
case class MisMatchJson(op: String, refCurr: Long, start: Long, end: Long, sequence: String, refBase: String, track: Long)

// untracked Mismatch Json Object
case class MisMatch(op: String, refCurr: Long, start: Long, end: Long, sequence: String, refBase: String)
