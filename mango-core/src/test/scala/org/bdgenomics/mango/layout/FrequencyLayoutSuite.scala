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
import org.bdgenomics.formats.avro.{ AlignmentRecord, Contig }
import org.scalatest.FunSuite
import org.bdgenomics.mango.layout._

import scala.collection.mutable.ListBuffer

class FrequencyLayoutSuite extends FunSuite {

  test("get frequency from reads of a 10 base pair long region") {

    val region = new ReferenceRegion("chr1", 0, 10)
    val records = new ListBuffer[AlignmentRecord]
    val sequence = "GATAAA"
    for (i <- 1 to 10) {
      records += AlignmentRecord.newBuilder()
        .setStart(i)
        .setEnd(i + sequence.length)
        .setSequence(sequence)
        .setRecordGroupSample("sample1")
        .build
    }

    val freq = FrequencyLayout(records.toIterator, region).toList
    assert(freq.contains(("sample1", 5, 5)))
    assert(freq.contains(("sample1", 9, 7)))

  }

  test("get frequency from reads of a 10 base pair long region for 2 samples") {

    val region = new ReferenceRegion("chr1", 1, 10)
    val records = new ListBuffer[AlignmentRecord]
    val sequence = "GATAAA"
    for (i <- 1 to 10) {
      records += AlignmentRecord.newBuilder()
        .setStart(i)
        .setEnd(i + sequence.length)
        .setSequence(sequence)
        .setRecordGroupSample("sample1")
        .build

      records += AlignmentRecord.newBuilder()
        .setStart(i)
        .setEnd(i + sequence.length)
        .setSequence(sequence)
        .setRecordGroupSample("sample2")
        .build
    }

    val freq = FrequencyLayout(records.toIterator, region).toList
    assert(freq.contains(("sample2", 5, 5)))
    assert(freq.contains(("sample1", 9, 7)))

  }
}
