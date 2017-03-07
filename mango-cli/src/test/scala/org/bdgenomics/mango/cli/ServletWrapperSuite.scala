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
package org.bdgenomics.mango.cli

import org.bdgenomics.adam.models.{ ReferenceRegion, SequenceDictionary, SequenceRecord }
import org.bdgenomics.mango.util.MangoFunSuite

import org.scalatra.test.scalatest.ScalatraSuite

class ServletWrapperSuite extends MangoFunSuite {

  test("formats dictionary ops") {
    val sd = new SequenceDictionary(Vector(SequenceRecord("chr2", 1000), SequenceRecord("chr1", 10000)))

    val ops = MangoServletWrapper.formatDictionaryOpts(sd)
    val ans = s"chr1:0-10000,chr2:0-1000"
    assert(ops == ans)
  }

  test("formats clickable regions for home page") {
    val region1 = (ReferenceRegion("chr1", 1, 10), 1.0)
    val region2 = (ReferenceRegion("chr2", 10, 12), 1.0)
    val regions = List(region1, region2)

    val formatted = MangoServletWrapper.formatClickableRegions(regions)
    val ans = s"${region1._1.referenceName}:${region1._1.start}-${region1._1.end}-1.0,${region2._1.referenceName}:${region2._1.start}-${region2._1.end}-1.0"
    assert(formatted == ans)
  }

  test("expands region") {
    val region = ReferenceRegion("chr1", 15, 32)
    val expanded = MangoServletWrapper.expand(region, minLength = 1000)
    assert(expanded.referenceName == region.referenceName)
    assert(expanded.start == 0)
    assert(expanded.end == 1000)
  }
}