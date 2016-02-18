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
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.formats.avro.{ AlignmentRecord, Contig, Feature }
import org.scalatest.FunSuite
import org.bdgenomics.mango.layout._
import org.apache.spark.TaskContext
import org.bdgenomics.adam.util.ADAMFunSuite
import org.scalatest._
import org.apache.spark.{ SparkConf, Logging, SparkContext }
import org.apache.spark.TaskContext
import org.apache.spark.rdd.RDD
import org.scalatest.FunSuite
import org.bdgenomics.adam.util.ADAMFunSuite
import org.bdgenomics.adam.rdd.ADAMContext._
import scala.collection.mutable.ListBuffer

class AlignmentRecordLayoutSuite extends ADAMFunSuite {

  sparkTest("test correct matePairs") {

    val read1 = AlignmentRecord.newBuilder
      .setContig(Contig.newBuilder.setContigName("chrM").build)
      .setCigar("5M")
      .setRecordGroupSample("Sample")
      .setStart(1)
      .setEnd(6)
      .setReadName("read")
      .setSequence("AAAAT")
      .build

    val read2 = AlignmentRecord.newBuilder
      .setContig(Contig.newBuilder.setContigName("chrM").build)
      .setCigar("5M")
      .setStart(7)
      .setRecordGroupSample("Sample")
      .setEnd(11)
      .setReadName("read")
      .setSequence("AAAAT")
      .build

    val region = new ReferenceRegion("chrM", 1, 5)
    val sampleIds: List[String] = List("Sample")
    val data: RDD[(ReferenceRegion, AlignmentRecord)] = sc.parallelize(List(read1, read2), 1).keyBy(ReferenceRegion(_))
    val reference = "NAAAAA"

    val alignmentData = AlignmentRecordLayout(data, Option(reference), region, sampleIds)
    assert(alignmentData.head._2.matePairs.length == 1)
  }

  sparkTest("test mate pairs do not overlap for multiple pairs") {

    val read1 = AlignmentRecord.newBuilder
      .setContig(Contig.newBuilder.setContigName("chrM").build)
      .setCigar("5M")
      .setRecordGroupSample("Sample")
      .setStart(1)
      .setEnd(6)
      .setReadName("read1")
      .setSequence("AAAAT")
      .build

    val read2 = AlignmentRecord.newBuilder
      .setContig(Contig.newBuilder.setContigName("chrM").build)
      .setCigar("10M")
      .setStart(30)
      .setRecordGroupSample("Sample")
      .setEnd(40)
      .setReadName("read1")
      .setSequence("AAAAAAAAAA")
      .build

    val read3 = AlignmentRecord.newBuilder
      .setContig(Contig.newBuilder.setContigName("chrM").build)
      .setCigar("5M")
      .setRecordGroupSample("Sample")
      .setStart(9)
      .setEnd(14)
      .setReadName("read2")
      .setSequence("AAAAT")
      .build

    val read4 = AlignmentRecord.newBuilder
      .setContig(Contig.newBuilder.setContigName("chrM").build)
      .setCigar("10M")
      .setStart(18)
      .setRecordGroupSample("Sample")
      .setEnd(28)
      .setReadName("read2")
      .setSequence("AAAAAAAAAA")
      .build

    val region = new ReferenceRegion("chrM", 1, 40)
    val sampleIds: List[String] = List("Sample")
    val data: RDD[(ReferenceRegion, AlignmentRecord)] = sc.parallelize(List(read1, read3, read2, read4), 1).keyBy(ReferenceRegion(_))
    val reference = "NAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

    val alignmentData = AlignmentRecordLayout(data, Option(reference), region, sampleIds)
    val result = alignmentData.head
    assert(result._2.matePairs.length == 2)
    assert(result._2.matePairs.filter(_.track == 0).length == 1)
  }

  sparkTest("test mate pairs do not overlap in interspersed pattern") {

    val read1 = AlignmentRecord.newBuilder
      .setContig(Contig.newBuilder.setContigName("chrM").build)
      .setCigar("5M")
      .setRecordGroupSample("Sample")
      .setStart(1)
      .setEnd(6)
      .setReadName("read1")
      .setSequence("AAAAT")
      .build

    val read2 = AlignmentRecord.newBuilder
      .setContig(Contig.newBuilder.setContigName("chrM").build)
      .setCigar("10M")
      .setStart(30)
      .setRecordGroupSample("Sample")
      .setEnd(40)
      .setReadName("read1")
      .setSequence("AAAAAAAAAA")
      .build

    val read3 = AlignmentRecord.newBuilder
      .setContig(Contig.newBuilder.setContigName("chrM").build)
      .setCigar("5M")
      .setRecordGroupSample("Sample")
      .setStart(9)
      .setEnd(14)
      .setReadName("read2")
      .setSequence("AAAAT")
      .build

    val read4 = AlignmentRecord.newBuilder
      .setContig(Contig.newBuilder.setContigName("chrM").build)
      .setCigar("6M")
      .setStart(42)
      .setRecordGroupSample("Sample")
      .setEnd(48)
      .setReadName("read2")
      .setSequence("AAAAAA")
      .build

    val region = new ReferenceRegion("chrM", 1, 48)
    val sampleIds: List[String] = List("Sample")
    val data: RDD[(ReferenceRegion, AlignmentRecord)] = sc.parallelize(List(read1, read3, read2, read4), 1).keyBy(ReferenceRegion(_))
    val reference = "NAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"

    val alignmentData = AlignmentRecordLayout(data, Option(reference), region, sampleIds)
    val result = alignmentData.head
    assert(result._2.matePairs.length == 2)
    assert(result._2.matePairs.filter(_.track == 0).length == 1)
  }

}
