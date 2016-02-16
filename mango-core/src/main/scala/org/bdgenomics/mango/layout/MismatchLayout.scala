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

  def apply(record: AlignmentRecord, reference: String, region: ReferenceRegion): List[MisMatch] = {
    alignMismatchesToRead(record, reference, region)
  }

  /*
   * Finds and returns all indels and mismatches of a given alignment record from an overlapping reference string.
   * Must take into account overlapping regions that are not covered by both the reference and record sequence.
  */
  def alignMismatchesToRead(rec: AlignmentRecord, reference: String, region: ReferenceRegion): List[MisMatch] = {
    var ref: String =
      if (rec.readNegativeStrand) {
        // get new reference sequence complementary to the given reference
        complement(reference)
      } else reference

    var misMatches: ListBuffer[MisMatch] = new ListBuffer[MisMatch]()
    val cigar = TextCigarCodec.decode(rec.cigar).getCigarElements()
    var refIdx = rec.start + 1
    var recIdx = rec.start

    cigar.foreach {
      e =>
        {
          var misLen = e.getLength
          var op: CigarOperator = e.getOperator
          if (op == CigarOperator.X || op == CigarOperator.M) {
            try {
              for (i <- 0 to misLen - 1) {
                // if index position is not within region
                if (refIdx <= region.end && refIdx >= region.start) {
                  val recBase = rec.sequence.charAt(getPosition(recIdx, rec.start))
                  val refBase = ref.charAt(getPosition(refIdx, region.start))
                  if (refBase != recBase) {
                    val start = recIdx
                    val end = start + 1
                    misMatches += new MisMatch(op.toString, refIdx, start, end, recBase.toString, refBase.toString)
                  }
                }
                recIdx += 1
                refIdx += 1
              }
            } catch {
              case iobe: StringIndexOutOfBoundsException => {
                // log.warn("Record Sequence " + rec.sequence + " at index " + recIdx)
                // log.warn(" Reference Sequence " + ref + " at index " + refIdx)
                // log.warn("Cigar" + rec.cigar)
              }
              case e: Exception => log.warn(e.toString)
            }
          } else if (op == CigarOperator.I) {
            val end = recIdx + misLen
            val stringStart = (recIdx - rec.start).toInt
            val indel = rec.sequence.substring(stringStart, stringStart + misLen)
            misMatches += new MisMatch(op.toString, refIdx, recIdx, end, indel, null)
            recIdx += misLen
          } else if (op == CigarOperator.D || op == CigarOperator.N) {
            val end = recIdx + misLen
            val stringStart = getPosition(recIdx, rec.start)
            val indel = rec.sequence.substring(stringStart, stringStart + misLen)
            misMatches += new MisMatch(op.toString, refIdx, recIdx, end, indel, null)
            refIdx += misLen
          }

        }
    }
    misMatches.toList
  }

  /*
   * From a given alignment record and an overlapping reference, determine whether the overlapping
   * sections of both sequences are the same. Must trim the start and end of either the record
   * sequence or reference string before comparing.
  */
  def matchesReference(rec: AlignmentRecord, reference: String, region: ReferenceRegion): Boolean = {
    val ref = {
      if (rec.readNegativeStrand)
        complement(reference)
      else
        reference
    }

    val seqLength = rec.sequence.length
    val modifiedEnd = rec.start + seqLength
    var recStart = 0
    var refStart = 0
    var recEnd = 0
    var refEnd = 0

    // get substring start value for reference and record
    if (rec.start < region.start) {
      refStart = 0
      recStart = (region.start - rec.start).toInt
    } else if (rec.start > region.start) {
      refStart = (rec.start - region.start).toInt
      recStart = 0
    } else {
      refStart = 0
      recStart = 0
    }

    // get substring end value for reference and record
    if (modifiedEnd < region.end) {
      val endDiff = (region.end - modifiedEnd).toInt
      refEnd = ref.length - endDiff
      recEnd = seqLength
    } else if (modifiedEnd > region.end) {
      val endDiff = (modifiedEnd - region.end).toInt
      refEnd = ref.length
      recEnd = seqLength - endDiff
    } else {
      refEnd = ref.length
      recEnd = seqLength
    }

    val refSegment: String = reference.substring(refStart + 1, refEnd)
    val sequence = rec.sequence.substring(recStart, recEnd)
    pairWiseCompare(refSegment, sequence)

  }

  def pairWiseCompare(s1: String, s2: String): Boolean = {
    if (s1.contains("N") || s2.contains("N")) {
      for (i <- 0 to s1.length - 1) {
        val c1 = s1(i)
        val c2 = s2(i)
        if (c1 != 'N' && c2 != 'N') {
          if (s1.charAt(i) != s2.charAt(i)) {
            return false
          }
        }
      }
      true
    } else {
      s1 == s2
    }
  }

  def containsIndels(rec: AlignmentRecord): Boolean = {
    rec.cigar.contains("I") || rec.cigar.contains("D")
  }

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

// temporary objects for alignmentrecord visual data
case class MisMatch(op: String, refCurr: Long, start: Long, end: Long, sequence: String, refBase: String)
