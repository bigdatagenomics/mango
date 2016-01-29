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

import org.bdgenomics.formats.avro.Genotype
import org.apache.spark.Logging
import htsjdk.samtools.{ Cigar, CigarOperator, CigarElement, TextCigarCodec }
import scala.collection.mutable.ListBuffer
import org.bdgenomics.formats.avro.{ AlignmentRecord, Contig }
import org.bdgenomics.adam.models.ReferenceRegion
import scala.collection.JavaConversions._

object VariantLayout extends Logging {

  def findIndels(layout: OrderedTrackedLayout[AlignmentRecord]): List[TrackedIndelMismatch] =
    layout.trackAssignments.map(rec => new TrackedIndelMismatch(alignIndelsToRead(rec._1._2), rec._2))

  def findMismatches(layout: OrderedTrackedLayout[AlignmentRecord], reference: String, region: ReferenceRegion): List[TrackedIndelMismatch] =
    layout.trackAssignments.map(rec => new TrackedIndelMismatch(alignMatchesToRead(rec._1._2, reference, region), rec._2))

  /* gets all indels from an Alignment Read */
  def alignIndelsToRead(rec: AlignmentRecord): List[IndelMismatch] = {

    var misMatches: ListBuffer[IndelMismatch] = new ListBuffer[IndelMismatch]()
    val cigar = TextCigarCodec.decode(rec.cigar).getCigarElements()
    var recIdx = rec.start
    var refIdx = rec.start

    // Loop through each cigar in section
    cigar.foreach {
      e =>
        {
          var misLen = e.getLength
          var op: CigarOperator = e.getOperator
          if (op == CigarOperator.I) {
            val end = recIdx + misLen
            val stringStart = (recIdx - rec.start).toInt
            val indel = rec.sequence.substring(stringStart, stringStart + misLen)
            misMatches += new IndelMismatch(op.toString, refIdx, recIdx, end, indel, null)
            recIdx += misLen
          } else if (op == CigarOperator.D || op == CigarOperator.N) {
            val end = recIdx + misLen
            val stringStart = getPosition(recIdx, rec.start)
            val indel = rec.sequence.substring(stringStart, stringStart + misLen)
            misMatches += new IndelMismatch(op.toString, refIdx, recIdx, end, indel, null)
            refIdx += misLen
          } else if (op == CigarOperator.X || op == CigarOperator.M) {
            refIdx += misLen
            recIdx += misLen
          } else {
            // log.warn("Cigar operator " + op.toString + " not supported")
          }
        }
    }

    misMatches.toList
  }

  def alignMatchesToRead(rec: AlignmentRecord, reference: String, region: ReferenceRegion): List[IndelMismatch] = {
    var ref: String =
      if (rec.readNegativeStrand) {
        // get new reference sequence complementary to the given reference
        complement(reference)
      } else reference

    var misMatches: ListBuffer[IndelMismatch] = new ListBuffer[IndelMismatch]()
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
                    misMatches += new IndelMismatch(op.toString, refIdx, start, end, recBase.toString, refBase.toString)
                  }
                }
                recIdx += 1
                refIdx += 1
              }
            } catch {
              case iobe: StringIndexOutOfBoundsException => {
                log.warn("Record Sequence " + rec.sequence + " at index " + recIdx)
                log.warn(" Reference Sequence " + ref + " at index " + refIdx)
                log.warn("Cigar" + rec.cigar)
              }
              case e: Exception => log.warn(e.toString)
            }
          } else if (op == CigarOperator.I) {
            recIdx += misLen
          } else if (op == CigarOperator.D || op == CigarOperator.N) {
            refIdx += misLen
          } else {
            // log.warn("Cigar operator " + op.toString + " not supported")
          }

        }
    }

    misMatches.toList

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

case class IndelMismatch(val op: String, val refCurr: Long, val start: Long, val end: Long, val sequence: String, val refBase: String)
case class TrackedIndelMismatch(val indelMismatches: List[IndelMismatch], val track: Int)
