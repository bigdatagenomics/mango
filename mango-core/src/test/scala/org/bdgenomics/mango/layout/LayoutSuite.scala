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

import org.bdgenomics.formats.avro.Variant
import org.bdgenomics.mango.util.MangoFunSuite

class LayoutSuite extends MangoFunSuite {

  test("Can create VariantJson with missing TranscriptEffects") {
    val variant = Variant.newBuilder()
      .setStart(100L)
      .setEnd(101L)
      .setContigName("chr1")
      .setReferenceAllele("T")
      .setAlternateAllele("A")
      .build()

    val json = VariantJson(variant)
    assert(json.position == variant.getStart)
    assert(json.contig == variant.getContigName)
  }
}
