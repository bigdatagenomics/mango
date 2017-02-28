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
package org.bdgenomics.mango.cli.util

import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.formats.avro.Variant
import org.bdgenomics.mango.layout.{ GenotypeJson, PositionCount, BedRowJson }
import org.ga4gh.{ GAPosition, GALinearAlignment, GAReadAlignment }
import org.scalatest.FunSuite

class CacheSuite extends FunSuite {

  // read test data
  val read1 = GAReadAlignment.newBuilder()
    .setId("id")
    .setNumberReads(2)
    .setReadGroupId("read1")
    .setFragmentName("frag1")
    .setProperPlacement(true)
    .setDuplicateFragment(false)
    .setFragmentLength(5)
    .setReadNumber(1)
    .setFailedVendorQualityChecks(false)
    .setSecondaryAlignment(false)
    .setSupplementaryAlignment(false)
    .setAlignment(GALinearAlignment.newBuilder().setPosition(GAPosition.newBuilder()
      .setReferenceName("chr1")
      .setReverseStrand(false)
      .setPosition(1)
      .build()).build())
    .build()

  val read2 = GAReadAlignment.newBuilder(read1).build()

  // feature test data
  val feature1 = BedRowJson("id1", "feature", "chr1", 1, 10, 100)
  val feature2 = BedRowJson("id1", "feature", "chr1", 2, 6, 100)

  // variant test data
  val variant = Variant.newBuilder()
    .setContigName("chr1")
    .setStart(2L)
    .setEnd(3L)
    .setReferenceAllele("A")
    .setAlternateAllele("G")
    .build()
  val variant1 = GenotypeJson(variant, Array("sample1", "sample2"))
  val variant2 = GenotypeJson(Variant.newBuilder(variant).setStart(5L).setEnd(6L).build(), Array("sample1", "sample2"))

  // coverage test data
  val coverage1 = PositionCount("chr1", 1, 5, 1)
  val coverage2 = PositionCount("chr1", 8, 10, 2)

  test("creates resolution cache") {
    val coveredRegion = ReferenceRegion("chr1", 10000, 11000)
    //    new GenomicCache(coveredRegion)
    val region = ReferenceRegion("chr1", 1, 5)
    val data = Array(1, 2, 3).map(r => (ReferenceRegion("chr1", r, r + 2), r))
    val x = Map(("key" -> data))
    new ResolutionCache(x, region, 1)
  }

  test("gets data in resolution cache") {
    val region = ReferenceRegion("chr1", 1, 20)
    val data = (1 until 20).map(r => (ReferenceRegion("chr1", r, r + 2), r)).toArray
    val cache = new ResolutionCache(Map(("key" -> data)), region, 1)
    val fetchRegion = ReferenceRegion("chr1", 10, 12)

    val response = cache.get(fetchRegion, "key")
    assert(response.get.length == 3)
  }

  test("returns none when key in cache has not been set") {
    val region = ReferenceRegion("chr1", 1, 20)
    val data = (1 until 20).map(r => (ReferenceRegion("chr1", r, r + 2), r)).toArray
    val cache = new ResolutionCache(Map(("key" -> data)), region, 1)
    val fetchRegion = ReferenceRegion("chr1", 10, 12)

    val response = cache.get(fetchRegion, "key2")
    assert(!response.isDefined)
  }

  test("sets alignment data in genomic cache") {
    val cache = GenomicCache()
    val region = ReferenceRegion("chr1", 1, 10)
    val map = Map("key" -> Array(read1, read2))

    cache.setReads(map, region)
  }

  test("returns none with empty key") {
    val cache = GenomicCache()
    val region = ReferenceRegion("chr1", 1, 10)
    cache.getReads(region, "key")
  }

  test("returns valid alignment data") {
    val cache = GenomicCache()
    val region = ReferenceRegion("chr1", 1, 10)
    val map = Map("key" -> Array(read1, read2))

    cache.setReads(map, region)

    val response = cache.getReads(region, "key")
    assert(response.get.length == 2)
  }

  test("returns valid coverage data") {
    val cache = GenomicCache()
    val region1 = ReferenceRegion("chr1", 1, 10)
    val map = Map("key" -> Array(coverage1, coverage2))

    cache.setCoverage(map, region1, 1)

    val response = cache.getCoverage(region1, "key")
    assert(response.get.length == 2)
  }

  test("returns valid feature data") {
    val cache = GenomicCache()
    val region1 = ReferenceRegion("chr1", 1, 10)
    val map = Map("key" -> Array(feature1, feature2))

    cache.setFeatures(map, region1, 1)

    val response1 = cache.getFeatures(region1, "key")
    assert(response1.get.length == 2)

    val region2 = ReferenceRegion("chr1", 8, 9)
    val response2 = cache.getFeatures(region2, "key")
    assert(response2.get.length == 1)
  }

  test("returns valid variant data") {
    val cache = GenomicCache()
    val region1 = ReferenceRegion("chr1", 1, 10)
    val map = Map("key" -> Array(variant1, variant2))

    cache.setVariants(map, region1, 1)

    val response1 = cache.getVariants(region1, "key")
    assert(response1.get.length == 2)

    val region2 = ReferenceRegion("chr1", 5, 7)
    val response2 = cache.getVariants(region2, "key")
    assert(response2.get.length == 1)
  }

  test("returns valid data when containing all type caches") {
    val cache = GenomicCache()
    val region = ReferenceRegion("chr1", 1, 10)

    val alignmentMap = Map("alignmentKey" -> Array(read1, read2))
    val featureMap = Map("featureKey" -> Array(feature1, feature2))
    val variantMap = Map("variantKey" -> Array(variant1, variant2))
    val coverageMap = Map("coverageKey" -> Array(coverage1, coverage2))

    cache.setReads(alignmentMap, region)
    cache.setFeatures(featureMap, region, 1)
    cache.setVariants(variantMap, region, 1)
    cache.setCoverage(coverageMap, region, 1)

    val alignmentResponse = cache.getReads(region, "alignmentKey")
    assert(alignmentResponse.get.length == 2)

    val featureResponse = cache.getFeatures(region, "featureKey")
    assert(featureResponse.get.length == 2)

    val variantResponse = cache.getVariants(region, "variantKey")
    assert(variantResponse.get.length == 2)

    val coverageResponse = cache.getCoverage(region, "coverageKey")
    assert(coverageResponse.get.length == 2)
  }

  test("deallocates and resets invalid ReferenceRegion") {
    val cache = GenomicCache()
    val region1 = ReferenceRegion("chr1", 2, 6)
    val map = Map("key" -> Array(feature2))

    cache.setFeatures(map, region1, 1)

    val response1 = cache.getFeatures(region1, "key")
    assert(response1.get.length == 1)

    val region2 = ReferenceRegion("chr1", 1, 10)

    cache.setFeatures(Map("key" -> Array(feature1, feature2)), region2, 1)
    val response2 = cache.getFeatures(region2, "key")
    assert(response2.get.length == 2)
  }
}
