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

import htsjdk.samtools.reference.{ FastaSequenceIndex, IndexedFastaSequenceFile, ReferenceSequence }
import java.io.File
import org.apache.spark.rdd.RDD
import org.apache.spark._
import org.apache.spark.SparkContext
import org.apache.spark.TaskContext
import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.adam.util.ADAMFunSuite
import org.bdgenomics.formats.avro.{ AlignmentRecord, Contig }
import org.bdgenomics.mango.layout._
import org.scalatest.FunSuite

import scala.collection.mutable.ListBuffer

class MismatchLayoutSuite extends ADAMFunSuite {

  def getDataCountFromBamFile(file: String, viewRegion: ReferenceRegion): RDD[AlignmentRecord] = {
    sc.loadIndexedBam(file, viewRegion)
  }

  def getReference(region: ReferenceRegion): String = {
    val faidx: FastaSequenceIndex = new FastaSequenceIndex(new File(referencePath + ".fai"))
    val faWithIndex = new IndexedFastaSequenceFile(new File(referencePath), faidx)
    val bases = faWithIndex.getSubsequenceAt(region.referenceName, region.start, region.end).getBases
    return new String(bases)
  }

  val bamFile = "./src/test/resources/mouse_chrM.bam"
  val referencePath = "./src/test/resources/mouse_chrM.fasta"

  test("find 1 mismatch in read when read and reference are aligned") {
    val read = AlignmentRecord.newBuilder
      .setCigar("5M")
      .setStart(3)
      .setEnd(7)
      .setSequence("AAAAT")
      .build

    val reference = "AAAAG"
    val region = new ReferenceRegion("chr", 3, 7)

    val results = MismatchLayout.alignMismatchesToRead(read, reference, region)
    assert(results.size == 1)
    assert(results.head.op == "M")
    assert(results.head.refCurr == 7)
    assert(results.head.sequence == "T")
  }

  test("find 1 insertion in read") {
    val read = AlignmentRecord.newBuilder
      .setCigar("3M1I2M")
      .setStart(1)
      .setEnd(7)
      .setSequence("TAGGAT")
      .build

    val reference = "TAGAT"
    val region = new ReferenceRegion("chr", 1, 7)

    val results = MismatchLayout.alignMismatchesToRead(read, reference, region)
    assert(results.size == 1)
    assert(results.head.op == "I")
    assert(results.head.sequence == "G")
    assert(results.head.refCurr == 4)
  }

  test("find 1 deletion in read") {
    val read = AlignmentRecord.newBuilder
      .setCigar("4M1D1M")
      .setStart(3)
      .setEnd(8)
      .setSequence("TAGGT")
      .build

    val reference = "TAGGAT"
    val region = new ReferenceRegion("chr", 3, 8)

    val results = MismatchLayout.alignMismatchesToRead(read, reference, region)
    assert(results.size == 1)
    assert(results.head.op == "D")
    assert(results.head.refBase == "A")
    assert(results.head.refCurr == 7)
  }

  test("find 1 mismatch and 1 insertion in read") {
    val read = AlignmentRecord.newBuilder
      .setCigar("6M1I")
      .setStart(11)
      .setEnd(17)
      .setSequence("AAGGATT")
      .build

    val reference = "TAGGAT"
    val region = new ReferenceRegion("chr", 11, 17)

    val results = MismatchLayout.alignMismatchesToRead(read, reference, region)

    assert(results.size == 2)
    assert(results.head.op == "M")
    assert(results.head.refBase == "T")
    assert(results.head.sequence == "A")
    assert(results.head.refCurr == 11)

    assert(results.last.op == "I")
    assert(results.last.refCurr == 17)
    assert(results.last.sequence == "T")
  }

  test("find 1 insertion and 1 deletion") {
    val read = AlignmentRecord.newBuilder
      .setCigar("1M2I1M1D1M")
      .setStart(3)
      .setEnd(7)
      .setSequence("ATTAG")
      .build

    val reference = "AATA"
    val region = new ReferenceRegion("chr", 3, 7)

    val results = MismatchLayout.alignMismatchesToRead(read, reference, region)
    assert(results.size == 3)
    assert(results.head.op == "I")
    assert(results.head.sequence == "TT")
    assert(results.head.refCurr == 4)

    assert(results(1).op == "D")
    assert(results(1).refBase == "T")
    assert(results(1).refCurr == 5)

    assert(results(2).op == "M")
    assert(results(2).refBase == "A")
    assert(results(2).sequence == "G")
    assert(results(2).refCurr == 6)

  }

  test("find 1 insertion and 1 deletion with large reference") {
    val read = AlignmentRecord.newBuilder
      .setCigar("1M1I1M1D1M")
      .setStart(3)
      .setEnd(7)
      .setSequence("ATAG")
      .build

    val reference = "GGAATAGGGGGGGGGGGGG"
    val region = new ReferenceRegion("chr", 1, 20)

    val results = MismatchLayout.alignMismatchesToRead(read, reference, region)

    assert(results.size == 3)
    assert(results.head.op == "I")
    assert(results.head.sequence == "T")
    assert(results.head.refCurr == 4)

    assert(results(1).op == "D")
    assert(results(1).refBase == "T")
    assert(results(1).refCurr == 5)

    assert(results(2).op == "M")
    assert(results(2).refBase == "A")
    assert(results(2).sequence == "G")
    assert(results(2).refCurr == 6)

  }

}
