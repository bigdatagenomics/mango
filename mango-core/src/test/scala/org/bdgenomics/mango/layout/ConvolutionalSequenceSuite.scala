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
import org.bdgenomics.formats.avro.AlignmentRecord

class ConvolutionalSequenceSuite extends ADAMFunSuite {

  // test alignment data
  val bamFile = resourcePath("mouse_chrM.bam")

  // test reference data
  var referencePath = resourcePath("mm10_chrM.fa")

  test("Calculates patch size correctly") {
    val p1 = ConvolutionalSequence.getPatchSize(100000, 1000, Some(100))._1

    assert(p1 == 100)

    def patchSize: Int = ConvolutionalSequence.getPatchSize(10000, 1000, Option(80))._1

  }

  test("Test convolution") {

    val sequence = "AAAAAATGAAAAATTTAAGG"
    val finalSize = 10
    val stride = 1

    val params = ConvolutionalSequence.getPatchSize(sequence.length, finalSize, Some(stride))
    val result = ConvolutionalSequence.convolveSequence(sequence, params._1, stride)
    assert(result.length == 10)
    assert(result(0) == result(1))
    assert(result(1) == result(2))
  }

  test("Compare convolved reference string to alignment record") {
    val reference = "AAAAAAAAAAAAAAAAAA" // 18 length
    val region = ReferenceRegion("chr1", 2, 2 + reference.length)
    val patchSize = 5
    val stride = 1

    val alignment = AlignmentRecord.newBuilder()
      .setContigName("chr1")
      .setStart(3L)
      .setEnd(10L)
      .setSequence("AATTAATT")
      .build()

    val convolvedReference = ConvolutionalSequence.convolveSequence(reference, patchSize, stride)

    val result = ConvolutionalSequence.convolveAlignmentRecord(region,
      convolvedReference,
      alignment,
      patchSize, stride)
    assert(result.length == 13)
  }

  test("Compare convolved reference string to alignment record at beginning") {
    val reference = "AAAAAAAAAAAAAAAAAA" // 18 length
    val region = ReferenceRegion("chr1", 2, 2 + reference.length)
    val patchSize = 5
    val stride = 2

    val alignment = AlignmentRecord.newBuilder()
      .setContigName("chr1")
      .setStart(2L)
      .setEnd(9L)
      .setSequence("AATTAATT")
      .build()

    val convolvedReference = ConvolutionalSequence.convolveSequence(reference, patchSize, stride)

    val result = ConvolutionalSequence.convolveAlignmentRecord(region,
      convolvedReference,
      alignment,
      patchSize, stride)
    assert(result.length == 7)
  }

  test("Compare convolved reference string to alignment record at end") {
    val reference = "AAAAAAAAAAAAAAAAAA" // 18 length
    val region = ReferenceRegion("chr1", 2, 2 + reference.length)
    val patchSize = 5
    val stride = 2

    val alignment = AlignmentRecord.newBuilder()
      .setContigName("chr1")
      .setStart(13L)
      .setEnd(20L)
      .setSequence("TTTTTTTT")
      .build()

    val convolvedReference = ConvolutionalSequence.convolveSequence(reference, patchSize, stride)

    val result = ConvolutionalSequence.convolveAlignmentRecord(region,
      convolvedReference,
      alignment,
      patchSize, stride)
    assert(result.sum == 38.0)

  }

}
