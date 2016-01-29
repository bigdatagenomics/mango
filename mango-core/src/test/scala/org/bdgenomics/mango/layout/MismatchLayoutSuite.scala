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
import org.bdgenomics.mango.layout._

import scala.collection.mutable.ListBuffer

class MismatchLayoutSuite extends FunSuite {

  test("find 1 mismatch in read") {
    val read = AlignmentRecord.newBuilder
      .setCigar("5M")
      .setStart(1)
      .setEnd(5)
      .setSequence("AAAAT")
      .build

    val reference = "NAAAAA"
    val region = new ReferenceRegion("chr", 1, 6)

    val results = MismatchLayout.alignMismatchesToRead(read, reference, region)
    assert(results.size == 1)
    assert(results.head.op == "M")
    assert(results.head.sequence == "T")
  }

  test("find 1 insertion in read") {
    val read = AlignmentRecord.newBuilder
      .setCigar("3M1I2M")
      .setStart(1)
      .setEnd(6)
      .setSequence("TAGGAT")
      .build

    val reference = "NTAGAT"
    val region = new ReferenceRegion("chr", 1, 7)

    val results = MismatchLayout.alignMismatchesToRead(read, reference, region)
    assert(results.size == 1)
    assert(results.head.op == "I")
    assert(results.head.sequence == "G")
  }

  test("find 1 deletion in read") {
    val read = AlignmentRecord.newBuilder
      .setCigar("4M1D1M")
      .setStart(1)
      .setEnd(6)
      .setSequence("TAGGT")
      .build

    val reference = "NTAGGAT"
    val region = new ReferenceRegion("chr", 1, 7)

    val results = MismatchLayout.alignMismatchesToRead(read, reference, region)

    assert(results.size == 1)
    assert(results.head.op == "D")
  }

  test("find 1 mismatch and 1 insertion in read") {
    val read = AlignmentRecord.newBuilder
      .setCigar("6M1I")
      .setStart(1)
      .setEnd(7)
      .setSequence("AAGGATT")
      .build

    val reference = "NTAGGAT"
    val region = new ReferenceRegion("chr", 1, 7)

    val results = MismatchLayout.alignMismatchesToRead(read, reference, region)
    assert(results.size == 2)
  }

  test("find insertion in read overlapping at end of reference") {
    val read = AlignmentRecord.newBuilder
      .setCigar("7M")
      .setStart(1)
      .setEnd(7)
      .setSequence("AAGGATT")
      .build

    val reference = "GGCTTA"
    val region = new ReferenceRegion("chr", 4, 8)

    val results = MismatchLayout.alignMismatchesToRead(read, reference, region)
    assert(results.size == 1)
    assert(results.head.op == "M")
    assert(results.head.sequence == "A")
  }

}
