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
package org.bdgenomics.mango.converters

import org.bdgenomics.adam.rdd.read.AlignmentDataset
import org.bdgenomics.mango.util.MangoFunSuite
import org.scalatest.FunSuite
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.adam.rdd.feature.FeatureDataset
import org.bdgenomics.adam.rdd.variant.VariantDataset

class GA4GHutilSuite extends MangoFunSuite {

  val samPath = resourcePath("small.1.sam")

  sparkTest("create JSON from AlignmentDataset") {
    val rrdd: AlignmentDataset = sc.loadAlignments(samPath)

    val collected = rrdd.rdd.collect()

    val json = GA4GHutil.alignmentDatasetToJSON(rrdd).get("1")

    val response = GA4GHutil.stringToSearchReadsResponse(json)

    assert(response.getAlignmentsCount == collected.length)
  }

  sparkTest("create JSON from AlignmentDataset with 1 sample") {
    val rrdd: AlignmentDataset = sc.loadAlignments(samPath)

    val collected = rrdd.rdd.collect()

    // did the converters correctly pull the reaGroupMap as the id?
    val readGroupName = rrdd.readGroups.readGroupMap.keys.head
    val converted = GA4GHutil.alignmentDatasetToJSON(rrdd, multipleGroupNames = false).get("1")

    val json = converted.replaceAll("\\s", "")

    val builder = ga4gh.ReadServiceOuterClass.SearchReadsResponse.newBuilder()

    com.google.protobuf.util.JsonFormat.parser().merge(json,
      builder)

    val response = builder.build()

    assert(response.getAlignmentsCount == collected.length)
  }

  sparkTest("create JSON from AlignmentDataset with more than 1 sample") {

    val samPaths = globPath(samPath, "small*.sam")
    val rrdd: AlignmentDataset = sc.loadAlignments(samPaths)

    assert(GA4GHutil.alignmentDatasetToJSON(rrdd, multipleGroupNames = true).size == 2)
  }

  sparkTest("create JSON with genotypes from VCF using GenotypeDataset") {
    val inputPath = resourcePath("truetest.genotypes.vcf")
    val grdd = sc.loadGenotypes(inputPath)
    val collected = sc.loadVariants(inputPath).rdd.collect()

    val json = GA4GHutil.genotypeDatasetToJSON(grdd)

    val response = GA4GHutil.stringToVariantServiceResponse(json)

    assert(response.getVariantsCount == collected.length)
  }

  sparkTest("create JSON without genotypes from VCF using VariantDataset") {
    val inputPath = resourcePath("truetest.genotypes.vcf")
    val vrdd: VariantDataset = sc.loadVariants(inputPath)
    val collected = vrdd.rdd.collect()

    val json = GA4GHutil.variantDatasetToJSON(vrdd)

    val response = GA4GHutil.stringToVariantServiceResponse(json)

    assert(response.getVariantsCount == collected.length)
  }

  sparkTest("create JSON from FeatureDataset") {
    val inputPath = resourcePath("smalltest.bed")
    val frdd: FeatureDataset = sc.loadBed(inputPath)
    val collected = frdd.rdd.collect()

    val json = GA4GHutil.featureDatasetToJSON(frdd)

    val response = GA4GHutil.stringToSearchFeaturesResponse(json)

    assert(response.getFeaturesCount == collected.length)
  }
}
