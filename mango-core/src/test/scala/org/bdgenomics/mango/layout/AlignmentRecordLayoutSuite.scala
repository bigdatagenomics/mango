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

import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.adam.util.ADAMFunSuite
import org.bdgenomics.formats.avro.{ AlignmentRecord, Contig }
import scala.collection.mutable.ListBuffer

class AlignmentRecordLayoutSuite extends ADAMFunSuite {

  sparkTest("test correct matePairs") {

    val read1 = AlignmentRecord.newBuilder
      .setContigName(Contig.newBuilder.setContigName("chrM").build().getContigName)
      .setCigar("5M")
      .setRecordGroupSample("Sample")
      .setStart(1L)
      .setEnd(6L)
      .setMapq(50)
      .setReadName("read")
      .setSequence("AAAAT")
      .build

    val read2 = AlignmentRecord.newBuilder
      .setContigName(Contig.newBuilder.setContigName("chrM").build().getContigName)
      .setCigar("5M")
      .setStart(7L)
      .setRecordGroupSample("Sample")
      .setEnd(11L)
      .setMapq(50)
      .setReadName("read")
      .setSequence("AAAAT")
      .build

    val mismatch1 = List(MisMatch("M", 1, 1, "A", "T"))
    val mismatch2 = List(MisMatch("M", 7, 1, "A", "T"))

    val region = new ReferenceRegion("chrM", 1, 5)
    val sampleIds: List[String] = List("Sample")
    val data =
      Array(CalculatedAlignmentRecord(read1, mismatch1),
        CalculatedAlignmentRecord(read2, mismatch2)).map(r => (ReferenceRegion(r.record), r))

    val alignmentData = AlignmentRecordLayout(data, sampleIds)
    assert(alignmentData.head._2.matePairs.length == 1)
  }

  sparkTest("test mate pairs do not overlap for multiple pairs") {

    val read1 = AlignmentRecord.newBuilder
      .setContigName(Contig.newBuilder.setContigName("chrM").build().getContigName)
      .setCigar("5M")
      .setRecordGroupSample("Sample")
      .setStart(1L)
      .setEnd(6L)
      .setMapq(50)
      .setReadName("read1")
      .setSequence("AAAAT")
      .build

    val read2 = AlignmentRecord.newBuilder
      .setContigName(Contig.newBuilder.setContigName("chrM").build().getContigName)
      .setCigar("10M")
      .setStart(30L)
      .setRecordGroupSample("Sample")
      .setEnd(40L)
      .setMapq(50)
      .setReadName("read1")
      .setSequence("AAAAAAAAAA")
      .build

    val read3 = AlignmentRecord.newBuilder
      .setContigName(Contig.newBuilder.setContigName("chrM").build().getContigName)
      .setCigar("5M")
      .setRecordGroupSample("Sample")
      .setStart(9L)
      .setEnd(14L)
      .setMapq(50)
      .setReadName("read2")
      .setSequence("AAAAT")
      .build

    val read4 = AlignmentRecord.newBuilder
      .setContigName(Contig.newBuilder.setContigName("chrM").build().getContigName)
      .setCigar("10M")
      .setStart(18L)
      .setMapq(50)
      .setRecordGroupSample("Sample")
      .setEnd(28L)
      .setReadName("read2")
      .setSequence("AAAAAAAAAA")
      .build

    val region = new ReferenceRegion("chrM", 1, 40)
    val sampleIds: List[String] = List("Sample")
    val d: Array[CalculatedAlignmentRecord] = Array(
      CalculatedAlignmentRecord(read1, List()),
      CalculatedAlignmentRecord(read2, List()),
      CalculatedAlignmentRecord(read3, List()),
      CalculatedAlignmentRecord(read4, List()))

    val data: Array[(ReferenceRegion, CalculatedAlignmentRecord)] = d.map(r => (ReferenceRegion(r.record), r))
    val alignmentData = AlignmentRecordLayout(data, sampleIds)
    val result = alignmentData.head
    assert(result._2.matePairs.length == 2)
    assert(result._2.matePairs.filter(_.track == 0).length == 1)
  }

  sparkTest("test mate pairs do not overlap in interspersed pattern") {

    val read1 = AlignmentRecord.newBuilder
      .setContigName(Contig.newBuilder.setContigName("chrM").build().getContigName)
      .setCigar("5M")
      .setRecordGroupSample("Sample")
      .setStart(1L)
      .setMapq(50)
      .setEnd(6L)
      .setReadName("read1")
      .setSequence("AAAAT")
      .build

    val read2 = AlignmentRecord.newBuilder
      .setContigName(Contig.newBuilder.setContigName("chrM").build().getContigName)
      .setCigar("10M")
      .setStart(30L)
      .setMapq(50)
      .setRecordGroupSample("Sample")
      .setEnd(40L)
      .setReadName("read1")
      .setSequence("AAAAAAAAAA")
      .build

    val read3 = AlignmentRecord.newBuilder
      .setContigName(Contig.newBuilder.setContigName("chrM").build().getContigName)
      .setCigar("5M")
      .setRecordGroupSample("Sample")
      .setStart(9L)
      .setMapq(50)
      .setEnd(14L)
      .setReadName("read2")
      .setSequence("AAAAT")
      .build

    val read4 = AlignmentRecord.newBuilder
      .setContigName(Contig.newBuilder.setContigName("chrM").build().getContigName)
      .setCigar("6M")
      .setStart(42L)
      .setRecordGroupSample("Sample")
      .setEnd(48L)
      .setMapq(50)
      .setReadName("read2")
      .setSequence("AAAAAA")
      .build

    val region = new ReferenceRegion("chrM", 1, 48)
    val sampleIds: List[String] = List("Sample")

    val d: Array[CalculatedAlignmentRecord] = Array(
      CalculatedAlignmentRecord(read1, List()),
      CalculatedAlignmentRecord(read2, List()),
      CalculatedAlignmentRecord(read3, List()),
      CalculatedAlignmentRecord(read4, List()))

    val data = d.map(r => (ReferenceRegion(r.record), r))

    val alignmentData = AlignmentRecordLayout(data, sampleIds)
    val result = alignmentData.head
    assert(result._2.matePairs.length == 2)
    assert(result._2.matePairs.filter(_.track == 0).length == 1)
  }

  sparkTest("test diffing reads works correctly") {

    val read1 = AlignmentRecord.newBuilder
      .setContigName(Contig.newBuilder.setContigName("chrM").build().getContigName)
      .setCigar("5M")
      .setRecordGroupSample("Sample")
      .setStart(1L)
      .setMapq(50)
      .setEnd(5000000L)
      .setReadName("read1")
      .setSequence("AAAAA")
      .build

    val read1_2 = AlignmentRecord.newBuilder
      .setContigName(Contig.newBuilder.setContigName("chrM").build().getContigName)
      .setCigar("5M")
      .setRecordGroupSample("Sample2")
      .setStart(1L)
      .setMapq(50)
      .setEnd(5000000L)
      .setReadName("read1")
      .setSequence("TTTTT")
      .build

    val region = new ReferenceRegion("chrM", 1, 48)
    val sampleIds: List[String] = List("Sample", "Sample2")

    var mutation1b = new ListBuffer[MisMatch]()
    var mutation2b = new ListBuffer[MisMatch]()
    val readLength = 100000
    for(x <- 1 to readLength) {
      mutation1b += MisMatch("M", x, 1, "A", "C")
      mutation2b += MisMatch("M", x, 1, "T", "C")
    }
    var mutation1 = mutation1b.toList
    var mutation2 = mutation2b.toList

    val d: List[CalculatedAlignmentRecord] = List(
      CalculatedAlignmentRecord(read1, mutation1),
      CalculatedAlignmentRecord(read1_2, mutation2))

    val data: RDD[(ReferenceRegion, CalculatedAlignmentRecord)] = sc.parallelize(d, 1).keyBy(r => ReferenceRegion(r.record))

    val alignmentData: Map[String, List[MutationCount]] = MergedAlignmentRecordLayout(data, 1)
    val t0 = System.nanoTime()
    val diffs = MergedAlignmentRecordLayout.diffRecords(sampleIds, alignmentData)
    print("Elapsed time: " + (System.nanoTime - t0)/1000000000 + "s")
    assert(diffs.keySet.contains(sampleIds(0)))
    assert(diffs.keySet.contains(sampleIds(1)))
    assert(diffs.getOrElse(sampleIds(0), List()).length == readLength)
    assert(diffs.getOrElse(sampleIds(1), List()).length == readLength)
  }

}
