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
      .setEnd(5)
      .setSequence("AAAAT")
      .build

    val read2 = AlignmentRecord.newBuilder
      .setContig(Contig.newBuilder.setContigName("chrM").build)
      .setCigar("5M")
      .setStart(7)
      .setRecordGroupSample("Sample")
      .setEnd(11)
      .setReadName("read")
      .setEnd(10)
      .setSequence("AAAAT")
      .build

    val region = new ReferenceRegion("chrM", 1, 5)
    val sampleIds: List[String] = List("Sample")
    println()
    val data: RDD[(ReferenceRegion, AlignmentRecord)] = sc.parallelize(List(read1, read2), 1).keyBy(ReferenceRegion(_))
    val reference = "NAAAAA"

    val alignmentData = AlignmentRecordLayout(data, reference, region, sampleIds)
    assert(alignmentData.head.matePairs.length == 1)
  }

}
