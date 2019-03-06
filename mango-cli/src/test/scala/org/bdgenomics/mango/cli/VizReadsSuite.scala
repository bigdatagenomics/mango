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

import net.liftweb.json._
import org.bdgenomics.mango.layout._
import org.bdgenomics.mango.models.LazyMaterialization
import org.bdgenomics.mango.util.MangoFunSuite
import org.ga4gh.GASearchReadsResponse
import org.scalatra.{ NotFound, Ok }
import org.scalatra.test.scalatest.ScalatraSuite

class VizReadsSuite extends MangoFunSuite with ScalatraSuite {

  implicit val formats = DefaultFormats
  addServlet(classOf[VizServlet], "/*")

  val emptyGASearchResponse = GASearchReadsResponse.newBuilder().build().toString

  val bamFile = resourcePath("mouse_chrM.bam")
  val referenceFile = resourcePath("mm10_chrM.fa")
  val vcfFile = resourcePath("truetest.genotypes.vcf")
  val featureFile = resourcePath("smalltest.bed")
  val coverageFile = resourcePath("mouse_chrM.coverage.bed")

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
  args.referencePath = referenceFile
  args.variantsPaths = vcfFile
  args.featurePaths = featureFile
  args.coveragePaths = coverageFile
  args.testMode = true

  sparkTest("Should pass for discovery mode") {
    val args = new VizReadsArgs()
    args.discoveryMode = true
    args.referencePath = referenceFile
    args.featurePaths = featureFile
    args.variantsPaths = vcfFile
    args.testMode = true

    implicit val vizReads = runVizReads(args)
    get(s"/features/${featureKey}/chrM?start=0&end=2000") {
      assert(status == Ok("").status.code)
    }
  }

  sparkTest("/reference/:ref") {
    implicit val VizReads = runVizReads(args)
    // should return data
    get("/reference/chrM?start=1&end=100") {
      assert(status == Ok("").status.code)
      val ref = parse(response.getContent()).extract[String]
      assert(ref.length == 99)
    }
  }

  sparkTest("Reference should throw NotFound error on invalid reference") {
    implicit val VizReads = runVizReads(args)
    // should return data
    get("/reference/fakeChr?start=1&end=100") {
      assert(status == NotFound().status.code)
    }
  }

  /** Reads tests **/
  sparkTest("should return reads") {
    implicit val VizReads = runVizReads(args)
    get(s"/reads/${bamKey}/chrM?start=0&end=2") {
      assert(status == Ok("").status.code)
      assert(response.getContent().length > emptyGASearchResponse.length)
    }
  }

  sparkTest("should return reads after first call returns no reads") {
    implicit val VizReads = runVizReads(args)
    get(s"/reads/${bamKey}/chrN?start=0&end=2") { // no reads
      assert(response.getContent().length == emptyGASearchResponse.length)
      get(s"/reads/${bamKey}/chrM?start=0&end=2") {
        assert(status == Ok("").status.code)
        assert(response.getContent().length > emptyGASearchResponse.length)
      }
    }
  }

  sparkTest("should return coverage from reads") {
    implicit val VizReads = runVizReads(args)
    get(s"/reads/coverage/${bamKey}/chrM?start=1&end=100") {
      assert(status == Ok("").status.code)
      val json = parse(response.getContent()).extract[Array[PositionCount]]
      assert(json.length == 99)
    }
  }

  sparkTest("Should throw error when reads do not exist") {
    val newArgs = new VizReadsArgs()
    newArgs.referencePath = referenceFile
    newArgs.testMode = true
    implicit val VizReads = runVizReads(newArgs)

    get(s"/reads/${bamKey}/chrM?start=1&end=100") {
      assert(status == NotFound().status.code)
    }
  }

  sparkTest("Reads should throw NotFound error on invalid reference") {
    implicit val VizReads = runVizReads(args)

    get(s"/reads/${bamKey}/fakeChr?start=1&end=100") {
      assert(status == NotFound().status.code)
    }
  }

  sparkTest("should not return reads with invalid key") {
    implicit val VizReads = runVizReads(args)
    get(s"/reads/invalidKey/chrM?start=0&end=100") {
      assert(status == Ok("").status.code)
    }
  }

  /** Variants tests **/
  sparkTest("/variants/:key/:ref") {
    val args = new VizReadsArgs()
    args.referencePath = referenceFile
    args.variantsPaths = vcfFile
    args.testMode = true
    args.showGenotypes = true

    implicit val VizReads = runVizReads(args)
    get(s"/variants/${vcfKey}/chrM?start=0&end=100") {
      assert(status == Ok("").status.code)
      val json = parse(response.getContent()).extract[Array[String]].map(r => GenotypeJson(r))
        .sortBy(_.variant.getStart)
      assert(json.length == 7)
      assert(json.head.variant.getStart == 9)
      assert(json.head.sampleIds.length == 3)
    }
  }

  sparkTest("does not return genotypes when binned") {
    implicit val VizReads = runVizReads(args)
    get(s"/variants/${vcfKey}/chrM?start=0&end=100&binning=100") {
      assert(status == Ok("").status.code)
      val json = parse(response.getContent()).extract[Array[String]].map(r => GenotypeJson(r))
        .sortBy(_.variant.getStart)
      assert(json.length == 1)
      assert(json.head.sampleIds.length == 0)
    }
  }

  sparkTest("should not return variants with invalid key") {
    implicit val VizReads = runVizReads(args)
    get(s"/variants/invalidKey/chrM?start=0&end=100") {
      assert(status == Ok("").status.code)
      val json = parse(response.getContent()).extract[Array[String]].map(r => GenotypeJson(r))
      assert(json.map(_.variant).length == 0)
    }
  }

  sparkTest("Should throw error when variants do not exist") {
    val newArgs = new VizReadsArgs()
    newArgs.referencePath = referenceFile
    newArgs.testMode = true
    implicit val VizReads = runVizReads(newArgs)

    get(s"/variants/invalidKey/chrM?start=1&end=100") {
      assert(status == NotFound().status.code)
    }
  }

  /** Feature Tests **/
  sparkTest("/features/:key/:ref") {
    implicit val vizReads = runVizReads(args)
    get(s"/features/${featureKey}/chrM?start=0&end=1200") {
      assert(status == Ok("").status.code)
      val json = parse(response.getContent()).extract[Array[BedRowJson]]
      assert(json.length == 2)
    }
  }

  sparkTest("should not return features with invalid key") {
    implicit val VizReads = runVizReads(args)
    get(s"/features/invalidKey/chrM?start=0&end=100") {
      assert(status == Ok("").status.code)
      val json = parse(response.getContent()).extract[Array[BedRowJson]]
      assert(json.length == 0)
    }
  }

  sparkTest("Should throw error when features do not exist") {
    val newArgs = new VizReadsArgs()
    newArgs.referencePath = referenceFile
    newArgs.testMode = true
    implicit val VizReads = runVizReads(newArgs)

    get(s"/features/invalidKey/chrM?start=1&end=100") {
      assert(status == NotFound().status.code)
    }
  }

  sparkTest("Features should throw out of bounds error on invalid reference") {
    implicit val VizReads = runVizReads(args)

    get(s"/features/${featureKey}/fakeChr?start=1&end=100") {
      assert(status == NotFound().status.code)
    }
  }

  /** Coverage Tests **/
  sparkTest("/coverage/:key/:ref") {
    val args = new VizReadsArgs()
    args.referencePath = referenceFile
    args.coveragePaths = coverageFile
    args.testMode = true

    implicit val vizReads = runVizReads(args)
    get(s"/coverage/${coverageKey}/chrM?start=0&end=1200") {
      assert(status == Ok("").status.code)
      val json = parse(response.getContent()).extract[Array[PositionCount]]
      assert(json.map(_.start).distinct.length == 1200)
    }
  }

  sparkTest("should not return coverage with invalid key") {
    implicit val VizReads = runVizReads(args)
    get(s"/coverage/invalidKey/chrM?start=0&end=100") {
      assert(status == Ok("").status.code)
      val json = parse(response.getContent()).extract[Array[PositionCount]]
      assert(json.map(_.start).distinct.length == 0)
    }
  }

  sparkTest("Should throw error when coverage does not exist") {
    val newArgs = new VizReadsArgs()
    newArgs.referencePath = referenceFile
    newArgs.testMode = true
    implicit val VizReads = runVizReads(newArgs)

    get(s"/coverage/invalidKey/chrM?start=1&end=100") {
      assert(status == NotFound().status.code)
    }
  }

  sparkTest("Coverage should throw out of bounds error on invalid regerence") {
    implicit val VizReads = runVizReads(args)

    get(s"/reads/${coverageKey}/fakeChr?start=1&end=100") {
      assert(status == NotFound().status.code)
    }
  }

  /** Example files **/
  sparkTest("should run example files") {

    val args = new VizReadsArgs()
    args.readsPaths = chr17bam
    args.referencePath = chr17Reference
    args.variantsPaths = chr17Vcf
    args.testMode = true

    implicit val VizReads = runVizReads(args)
    val exBamKey = LazyMaterialization.filterKeyFromFile(chr17bam)
    val exVcfKey = LazyMaterialization.filterKeyFromFile(chr17Vcf)

    // get regions not in data bounds
    get(s"/reads/${exBamKey}/chr17?start=1&end=100") {
      assert(status == Ok("").status.code)
    }

    get(s"/variants/${exVcfKey}/chr17?start=1&end=100") {
      assert(status == Ok("").status.code)
      val json = parse(response.getContent()).extract[Array[String]].map(r => GenotypeJson(r))
        .sortBy(_.variant.getStart)
      assert(status == Ok("").status.code)
      assert(json.length == 0)

    }

    // get regions in data bounds
    get(s"/reads/${exBamKey}/chr17?start=7500000&end=7510100") {
      assert(status == Ok("").status.code)
    }

    get(s"/variants/${exVcfKey}/chr17?start=7500000&end=7510100") {
      assert(status == Ok("").status.code)
      val json = parse(response.getContent()).extract[Array[String]].map(r => GenotypeJson(r))
        .sortBy(_.variant.getStart)
      assert(status == Ok("").status.code)
      assert(json.length == 289)

    }
  }

}
