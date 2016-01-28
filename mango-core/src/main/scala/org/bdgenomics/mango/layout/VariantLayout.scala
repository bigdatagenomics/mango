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
  //
  // def findIndels(layout: OrderedTrackedLayout[AlignmentRecord]): List[MisMatch] = layout.trackAssignments.map(rec => alignIndelsToRead(rec)).reduce(_ ++ _)


  /* gets all indels from an Alignment Read */
  def alignIndelsToRead(rec: AlignmentRecord): List[indelMismatch] = {

    var misMatches: ListBuffer[indelMismatch] = new ListBuffer[indelMismatch]()
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
            misMatches += new indelMismatch(op.toString, refIdx, recIdx, end, indel)
            recIdx += misLen
          } else if (op == CigarOperator.D || op == CigarOperator.N) {
            val end = recIdx + misLen
            val stringStart = getPosition(recIdx, rec.start)
            val indel = rec.sequence.substring(stringStart, stringStart + misLen)
            misMatches += new indelMismatch(op.toString, refIdx, recIdx, end, indel)
            refIdx += misLen
          } else if (op == CigarOperator.X || op == CigarOperator.M) {
            refIdx += misLen
            recIdx += misLen
          } else {
            log.warn("Cigar operator " + op.toString + " not supported")
          }
        }
    }

    misMatches.toList
  }

  def alignMatchesToRead(rec: AlignmentRecord, ref: String): List[indelMismatch] = {

    var misMatches: ListBuffer[indelMismatch] = new ListBuffer[indelMismatch]()
    val cigar = TextCigarCodec.decode(rec.cigar).getCigarElements()
    var refIdx = rec.start
    var recIdx = rec.start

    cigar.foreach {
      e =>
        {
          var misLen = e.getLength
          var op: CigarOperator = e.getOperator
          println(op.toString)
          println(misLen)
          if (op == CigarOperator.X || op == CigarOperator.M) {
            for (i <- 0 to misLen - 1) {
              val recBase = rec.sequence.charAt(getPosition(recIdx, rec.start) + i)
              val refBase = ref.charAt(getPosition(refIdx, rec.start) + i)
              println(recBase, refBase)
              if (refBase != recBase) {
                val start = recIdx + i
                val end = start + 1
                misMatches += new indelMismatch(op.toString, refIdx + i, start, end, recBase.toString)
              }
            }
            recIdx += misLen
            refIdx += misLen
          } else if (op == CigarOperator.I) {
            recIdx += misLen
          } else if (op == CigarOperator.D || op == CigarOperator.N) {
            refIdx += misLen
          } else {
            log.warn("Cigar operator " + op.toString + " not supported")
          }

        }
    }

    misMatches.toList

  }

  private def getPosition(idx: Long, start: Long): Int = (idx - start).toInt
}

case class indelMismatch(val op: String, val refCurr: Long, val start: Long, val end: Long, val sequence: String)
case class MisMatch(val op: String, val refCurr: Long, val start: Long, val end: Long, val sequence: String, val track: Int)
