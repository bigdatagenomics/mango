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

import org.bdgenomics.mango.converters.{ GA4GHutil, SearchFeaturesRequestGA4GH, SearchVariantsRequestGA4GH, SearchReadsRequestGA4GH }
import org.bdgenomics.mango.models.LazyMaterialization
import org.bdgenomics.mango.util.MangoFunSuite
import org.scalatra.{ NotFound, Ok }
import org.scalatra.test.scalatest.ScalatraSuite
import net.liftweb.json._

class VizReadsSuite extends MangoFunSuite with ScalatraSuite {

  implicit val formats = DefaultFormats

  val bamFile = resourcePath("mouse_chrM.bam")
  val referenceFile = resourcePath("mm10_chrM.fa")
  val vcfFile = resourcePath("truetest.genotypes.vcf")
  val featureFile = resourcePath("smalltest.bed")
  val coverageFile = resourcePath("mouse_chrM.coverage.bed")
  val chromSizesFile = resourcePath("hg19.chrom.sizes")

  // exampleFiles
  val chr17bam = examplePath("chr17.7500000-7515000.sam")
  val chr17Reference = examplePath("hg19.17.2bit")
  val chr17Vcf = examplePath("ALL.chr17.7500000-7515000.phase3_shapeit2_mvncall_integrated_v5a.20130502.genotypes.vcf")

  val bamKey = LazyMaterialization.filterKeyFromFile(bamFile)
  val featureKey = LazyMaterialization.filterKeyFromFile(featureFile)
  val vcfKey = LazyMaterialization.filterKeyFromFile(vcfFile)
  val coverageKey = LazyMaterialization.filterKeyFromFile(coverageFile)

  val args = new VizReadsArgs()
  args.readsPaths = bamFile
  args.chromSizesPath = chromSizesFile
  args.referencePath = referenceFile
  args.variantsPaths = vcfFile
  args.featurePaths = featureFile
  args.coveragePaths = coverageFile
  args.testMode = true

  // header for JSON POSTs
  val requestHeader = Map("Content-Type" -> "application/json")

  sparkTest("Should pass for discovery mode") {

    addServlet(classOf[VizServlet], "/*")
    val args = new VizReadsArgs()
    args.discoveryMode = true
    args.referencePath = referenceFile
    args.chromSizesPath = chromSizesFile
    args.featurePaths = featureFile
    args.variantsPaths = vcfFile
    args.coveragePaths = coverageFile
    args.testMode = true

    implicit val vizReads = runVizReads(args)

    val body = SearchFeaturesRequestGA4GH(featureKey, "null", 200, "chrM", 0, 2000).toByteArray()

    post("/features/search", body, requestHeader) {
      assert(status == Ok("").status.code)
    }

    get("/quit") {
      assert(status == Ok("").status.code)
    }
  }

  /** Reads tests **/
  sparkTest("should return reads") {
    implicit val vizReads = runVizReads(args)

    val body = SearchReadsRequestGA4GH("null", 200, Array(bamKey), "chrM", 1, 2).toByteArray()

    post("/reads/search", body, requestHeader) {
      assert(status == Ok("").status.code)

      val parsedData = GA4GHutil.stringToSearchReadsResponse(response.getContent())
        .getAlignmentsList

      assert(parsedData.size == 9)

    }

  }

  sparkTest("Should throw error when reads do not exist") {
    val newArgs = new VizReadsArgs()
    newArgs.referencePath = referenceFile
    newArgs.chromSizesPath = chromSizesFile
    newArgs.testMode = true
    implicit val vizReads = runVizReads(newArgs)

    val body = SearchReadsRequestGA4GH("null", 200, Array(bamKey), "chrM", 1, 100).toByteArray()

    post("/reads/search", body, requestHeader) {
      assert(status == NotFound().status.code)
    }
  }

  sparkTest("Reads should throw NotFound error on invalid reference") {
    implicit val VizReads = runVizReads(args)

    val body = SearchReadsRequestGA4GH("null", 200, Array(bamKey), "fakeChr", 1, 100).toByteArray()

    post("/reads/search", body, requestHeader) {
      assert(status == NotFound().status.code)
    }
  }

  sparkTest("should not return reads with invalid key") {
    implicit val vizReads = runVizReads(args)

    val body = SearchReadsRequestGA4GH("null", 200, Array("invalidKey"), "chrM", 1, 100).toByteArray()

    post("/reads/search", body, requestHeader) {
      assert(status == Ok("").status.code)
    }
  }

  /** Variants tests **/
  sparkTest("/variants/:key/:ref") {
    val args = new VizReadsArgs()
    args.referencePath = referenceFile
    args.variantsPaths = vcfFile
    args.testMode = true
    args.chromSizesPath = chromSizesFile

    implicit val vizReads = runVizReads(args)

    val body = SearchVariantsRequestGA4GH(vcfKey, "null", 200, "chrM", Array(), 0, 100).toByteArray()

    post("/variants/search", body, requestHeader) {
      assert(status == Ok("").status.code)

      val json = GA4GHutil.stringToVariantServiceResponse(response.getContent())
        .getVariantsList

      assert(json.size == 7)
      assert(json.get(0).getStart == 9)
      assert(json.get(0).getCallsCount == 3)
    }
  }

  //  sparkTest("does not return genotypes when binned") {
  //    implicit val VizReads = runVizReads(args)
  //
  //    val body = SearchVariantsRequestGA4GH(vcfKey, "null", 200, "chrM", Array(), 0, 100).toByteArray()
  //
  //    post("/variants/search", body, requestHeader) {
  //      assert(status == Ok("").status.code)
  //
  //      val json = ga4gh.VariantServiceOuterClass.SearchVariantsResponse.parseFrom(response.getContentBytes())
  //        .getVariantsList
  //
  //      assert(json.size == 1)
  //      assert(json.get(0).getCallsCount == 0)
  //    }
  //  }

  sparkTest("should not return variants with invalid key") {
    implicit val VizReads = runVizReads(args)

    val body = SearchVariantsRequestGA4GH("invalidKey", "null", 200, "chrM", Array(), 0, 100).toByteArray()

    post("/variants/search", body, requestHeader) {
      assert(status == Ok("").status.code)

      val json = GA4GHutil.stringToVariantServiceResponse(response.getContent())
        .getVariantsList

      assert(json.size == 0)
    }
  }

  sparkTest("Should throw error when variants do not exist") {
    val newArgs = new VizReadsArgs()
    newArgs.referencePath = referenceFile
    newArgs.chromSizesPath = chromSizesFile
    newArgs.testMode = true
    implicit val VizReads = runVizReads(newArgs)

    val body = SearchVariantsRequestGA4GH("invalidKey", "null", 200, "chrM", Array(), 0, 100).toByteArray()

    post("/variants/search", body, requestHeader) {
      assert(status == NotFound().status.code)
    }
  }

  /** Feature Tests **/
  sparkTest("/features/:key/:ref") {
    implicit val vizReads = runVizReads(args)

    val body = SearchFeaturesRequestGA4GH(featureKey, "null", 200, "chrM", 0, 1200).toByteArray()

    post("/features/search", body, requestHeader) {
      assert(status == Ok("").status.code)

      val json = GA4GHutil.stringToSearchFeaturesResponse(response.getContent())
        .getFeaturesList

      assert(json.size == 2)
    }
  }

  sparkTest("should not return features with invalid key") {
    implicit val VizReads = runVizReads(args)

    val body = SearchFeaturesRequestGA4GH("invalidKey", "null", 200, "chrM", 0, 100).toByteArray()

    post("/features/search", body, requestHeader) {
      assert(status == Ok("").status.code)
      val json = GA4GHutil.stringToSearchFeaturesResponse(response.getContent())
        .getFeaturesList

      assert(json.size == 0)
    }
  }

  sparkTest("Should throw error when features do not exist") {
    val newArgs = new VizReadsArgs()
    newArgs.referencePath = referenceFile
    newArgs.chromSizesPath = chromSizesFile
    newArgs.testMode = true

    implicit val VizReads = runVizReads(newArgs)

    val body = SearchFeaturesRequestGA4GH("invalidKey", "null", 200, "chrM", 0, 100).toByteArray()

    post("/features/search", body, requestHeader) {
      assert(status == NotFound().status.code)
    }
  }

  sparkTest("Features should throw out of bounds error on invalid reference") {
    implicit val VizReads = runVizReads(args)

    val body = SearchFeaturesRequestGA4GH(featureKey, "null", 200, "fakeChr", 0, 100).toByteArray()

    post("/features/search", body, requestHeader) {
      assert(status == NotFound().status.code)
    }
  }

  /** Coverage Tests **/
  sparkTest("gets coverage from feature endpoint") {
    val newArgs = new VizReadsArgs()
    newArgs.referencePath = referenceFile
    newArgs.coveragePaths = coverageFile
    newArgs.chromSizesPath = chromSizesFile
    newArgs.testMode = true

    implicit val vizReads = runVizReads(newArgs)

    val body = SearchFeaturesRequestGA4GH(coverageKey, "null", 200, "chrM", 0, 1200).toByteArray()

    post("/features/search", body, requestHeader) {
      assert(status == Ok("").status.code)
      val json = GA4GHutil.stringToSearchFeaturesResponse(response.getContent())
        .getFeaturesList

      assert(json.size == 1200)
    }
  }

  sparkTest("should not return coverage with invalid key") {
    implicit val VizReads = runVizReads(args)

    val body = SearchFeaturesRequestGA4GH("invalidKey", "null", 200, "chrM", 0, 1200).toByteArray()

    post("/features/search", body, requestHeader) {
      assert(status == Ok("").status.code)
      val json = GA4GHutil.stringToSearchFeaturesResponse(response.getContent())
        .getFeaturesList

      assert(json.size == 0)
    }
  }

  sparkTest("Should return coverage and features") {
    val newArgs = new VizReadsArgs()
    newArgs.referencePath = referenceFile
    newArgs.chromSizesPath = chromSizesFile
    newArgs.coveragePaths = coverageFile
    newArgs.featurePaths = featureFile
    newArgs.testMode = true
    implicit val VizReads = runVizReads(newArgs)

    val coverageBody = SearchFeaturesRequestGA4GH(coverageKey, "null", 200, "chrM", 0, 1200).toByteArray()

    post("/features/search", coverageBody, requestHeader) {
      assert(status == Ok("").status.code)
      val json = GA4GHutil.stringToSearchFeaturesResponse(response.getContent())
        .getFeaturesList

      assert(json.size == 1200)
    }

    val featureBody = SearchFeaturesRequestGA4GH(featureKey, "null", 200, "chrM", 0, 1200).toByteArray()

    post("/features/search", featureBody, requestHeader) {
      assert(status == Ok("").status.code)
      val json = GA4GHutil.stringToSearchFeaturesResponse(response.getContent())
        .getFeaturesList

      assert(json.size == 2)
    }

  }

  sparkTest("Coverage should throw out of bounds error on invalid regerence") {
    implicit val VizReads = runVizReads(args)

    val coverageBody = SearchFeaturesRequestGA4GH(coverageKey, "null", 200, "fakeChr", 0, 1200).toByteArray()

    post("/features/search", coverageBody, requestHeader) {
      assert(status == NotFound().status.code)
    }
  }

  /** Example files **/
  sparkTest("should run example files") {

    val args = new VizReadsArgs()
    args.readsPaths = chr17bam
    args.referencePath = chr17Reference
    args.chromSizesPath = chromSizesFile
    args.variantsPaths = chr17Vcf
    args.testMode = true

    implicit val VizReads = runVizReads(args)
    val exBamKey = LazyMaterialization.filterKeyFromFile(chr17bam)
    val exVcfKey = LazyMaterialization.filterKeyFromFile(chr17Vcf)

    // no data
    val variantsBody = SearchVariantsRequestGA4GH(exVcfKey, "null", 200, "chr1", Array(), 7500000, 7510100).toByteArray()

    post("/variants/search", variantsBody, requestHeader) {
      assert(status == Ok("").status.code)

      val json = GA4GHutil.stringToVariantServiceResponse(response.getContent())
        .getVariantsList

      assert(json.size == 0)
    }

    // generate requests for regions not in data bounds
    val readsBody1 = SearchReadsRequestGA4GH("null", 200, Array(exBamKey), "chr17", 1, 100).toByteArray()
    val variantsBody1 = SearchVariantsRequestGA4GH(exVcfKey, "null", 200, "chr17", Array(), 1, 100).toByteArray()

    post("/reads/search", readsBody1, requestHeader) {
      assert(status == Ok("").status.code)
    }

    post("/variants/search", variantsBody1, requestHeader) {
      assert(status == Ok("").status.code)

      val json = GA4GHutil.stringToVariantServiceResponse(response.getContent())
        .getVariantsList

      assert(json.size == 0)

    }

    // form request bodies to send to post
    val readsBody2 = SearchReadsRequestGA4GH("null", 200, Array(exBamKey), "chr17", 7500000, 7510100).toByteArray()
    val variantsBody2 = SearchVariantsRequestGA4GH(exVcfKey, "null", 200, "chr17", Array(), 7500000, 7510100).toByteArray()

    post("/reads/search", readsBody2, requestHeader) {
      assert(status == Ok("").status.code)
    }

    post("/variants/search", variantsBody2, requestHeader) {
      assert(status == Ok("").status.code)

      val json = GA4GHutil.stringToVariantServiceResponse(response.getContent())
        .getVariantsList

      assert(json.size == 289)

    }

    val variantsBody3 = SearchVariantsRequestGA4GH(exVcfKey, "null", 400, "chr17",
      Array("HG00096", "HG00097", "HG00099", "HG00100", "HG00101"), 40603901, 40604000).toByteArray()

    post("/variants/search", variantsBody3, requestHeader) {
      assert(status == Ok("").status.code)

      val json = GA4GHutil.stringToVariantServiceResponse(response.getContent())
        .getVariantsList

      assert(json.size == 0)

    }
  }
}
