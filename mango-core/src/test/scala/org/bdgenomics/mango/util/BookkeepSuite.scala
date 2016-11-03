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
package org.bdgenomics.mango.util

import org.bdgenomics.adam.models.ReferenceRegion
import org.scalatest.FunSuite

class BookkeepSuite extends FunSuite {
  val prefetchSize = 100
  val sampleId = "id"

  val region1 = ReferenceRegion("chr1", 0, 100)
  val region2 = ReferenceRegion("chr1", 500, 900)

  test("remembers new regions") {
    val bookkeep = new Bookkeep(prefetchSize)
    bookkeep.rememberValues(region1, sampleId)
    val regions = bookkeep.getMissingRegions(ReferenceRegion("chr1", 0, 850), List(sampleId))
    assert(regions.get.length == 1)
    assert(regions.get.head.end == 900)

  }

  test("adds new regions to existing chromosomes") {
    val bookkeep = new Bookkeep(prefetchSize)
    bookkeep.rememberValues(region1, sampleId)
    bookkeep.rememberValues(region2, sampleId)
    val regions = bookkeep.getMissingRegions(ReferenceRegion("chr1", 0, 899), List(sampleId))
    assert(regions.get.length == 1)
    assert(regions.get.head.start == 100)
    assert(regions.get.head.end == 500)

  }

  test("adds new chromosome") {
    val bookkeep = new Bookkeep(prefetchSize)
    bookkeep.rememberValues(region1, sampleId)

    val newRegion = ReferenceRegion("chr2", 0, 100)
    bookkeep.rememberValues(newRegion, sampleId)
    val regions = bookkeep.getMissingRegions(ReferenceRegion("chr2", 0, 900), List(sampleId))
    assert(regions.get.length == 1)
    assert(regions.get.head.start == 100)
    assert(regions.get.head.end == 1000)
  }

  test("merges regions") {
    val newRegion = ReferenceRegion("chr1", 150, 500)
    val regions = List(region2, newRegion, region1)

    val merged = Bookkeep.mergeRegions(regions)
    assert(merged.length == 2)
  }

}
