/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bdgenomics.mango.layout

import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.formats.avro.{ AlignmentRecord, Contig }
import org.scalatest.FunSuite

import scala.collection.mutable.ListBuffer

class VariantLayoutSuite extends FunSuite {

  test("correctly identify location of indels on read at beginning") {

    val read = AlignmentRecord.newBuilder()
      .setStart(1L)
      .setCigar("1I6M")
      .setSequence("GATCCAAA")
      .setReadMapped(true)
      .build

    val results = VariantLayout.alignIndelsToRead(read)
    assert(results.size == 1)
    assert(results.head.op == "I" && results.head.start == 1 && results.head.refCurr == 1 && results.head.end == 2 && results.head.sequence == "G")

  }

  test("correctly identify location of indels on read in middle") {

    val read = AlignmentRecord.newBuilder()
      .setStart(1L)
      .setCigar("4M1I3M")
      .setSequence("GATCCAAA")
      .setReadMapped(true)
      .build

    val results = VariantLayout.alignIndelsToRead(read)
    assert(results.size == 1)
    assert(results.head.op == "I" && results.head.start == 5 && results.head.refCurr == 5 && results.head.end == 6 && results.head.sequence == "C")

  }

  test("identify 1 mismatch on a read") {

    val read = AlignmentRecord.newBuilder()
      .setStart(1L)
      .setCigar("6M")
      .setSequence("ATCCAAA")
      .setReadMapped(true)
      .build

    val reference = "NATCAAAA"
    val region = new ReferenceRegion("chr", 1, 10)
    val results = VariantLayout.alignMatchesToRead(read, reference, region)
    assert(results.size == 1)
    assert(results.head.op == "M" && results.head.start == 4 && results.head.refCurr == 5 && results.head.end == 5 && results.head.sequence == "C" && results.head.refBase == "A")
  }

  test("identify 3 mismatches on a read") {

    val read = AlignmentRecord.newBuilder()
      .setStart(1L)
      .setCigar("9M")
      .setSequence("ATCCAAATG")
      .setReadMapped(true)
      .build

    val reference = "NATCAAAACC"
    val region = new ReferenceRegion("chr", 1, 10)
    val results = VariantLayout.alignMatchesToRead(read, reference, region)
    assert(results(0).op == "M" && results(0).start == 4 && results(0).refCurr == 5 && results(0).end == 5 && results(0).sequence == "C")
    assert(results(1).op == "M" && results(1).start == 8 && results(1).refCurr == 9 && results(1).end == 9 && results(1).sequence == "T")
    assert(results(2).op == "M" && results(2).start == 9 && results(2).refCurr == 10 && results(2).end == 10 && results(2).sequence == "G")
  }

  test("identify mismatches on a read with insertion") {
    val read = AlignmentRecord.newBuilder()
      .setStart(1L)
      .setCigar("1I6M")
      .setSequence("GATCCAAA")
      .setReadMapped(true)
      .build

    val reference = "NATCAAAA"
    val region = new ReferenceRegion("chr", 1, 10)
    val results = VariantLayout.alignMatchesToRead(read, reference, region)

    assert(results.size == 1)
    assert(results.head.op == "M" && results.head.start == 5 && results.head.refCurr == 5 && results.head.end == 6 && results.head.sequence == "C")
  }

  test("identify mismatches on a read with deletion") {
    val read = AlignmentRecord.newBuilder()
      .setStart(1L)
      .setCigar("1M1D6M")
      .setSequence("GTCCAAA")
      .setReadMapped(true)
      .build

    val reference = "NGATCAAAA"
    val region = new ReferenceRegion("chr", 1, 10)
    val results = VariantLayout.alignMatchesToRead(read, reference, region)

    assert(results.size == 1)
    assert(results.head.op == "M" && results.head.start == 4 && results.head.refCurr == 6 && results.head.end == 5 && results.head.sequence == "C")
  }

  test("find mismatches with overlapping reference") {

    val read = AlignmentRecord.newBuilder()
      .setStart(1L)
      .setCigar("9M")
      .setSequence("TTTTTTAAT")
      .setReadMapped(true)
      .build

    val reference = "AAAA"
    val region = new ReferenceRegion("chr", 8, 12)
    val results = VariantLayout.alignMatchesToRead(read, reference, region)

    assert(results.size == 1)
    assert(results.head.op == "M" && results.head.start == 9 && results.head.refCurr == 10 && results.head.end == 10 && results.head.sequence == "T")

  }

  test("get complement of reference") {

    val reference = "TGCTTTAAT"
    val complement = VariantLayout.complement(reference)
    assert(complement == "ACGAAATTA")

  }

  test("get complement of longer reference") {

    val reference = "GTTAATGTAGCTTAATAACAAAGCAAAGCACTGAAAATGCTTAGATGGATAATTGTATCCCATAAACACAAAGGTTTGGTCCTGGCCTTATAATTAATTA"
    val complement = VariantLayout.complement(reference)
    assert(complement == "CAATTACATCGAATTATTGTTTCGTTTCGTGACTTTTACGAATCTACCTATTAACATAGGGTATTTGTGTTTCCAAACCAGGACCGGAATATTAATTAAT")

  }

}
