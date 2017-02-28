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

import java.io.{ DataInputStream, ByteArrayInputStream }

import net.liftweb.json._
import org.apache.avro.Schema
import org.apache.avro.generic.{ GenericDatumReader, GenericData }
import org.apache.avro.io.{ DecoderFactory, DatumReader, JsonDecoder }
import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.mango.layout._
import org.bdgenomics.mango.models.LazyMaterialization
import org.bdgenomics.mango.util.MangoFunSuite
import org.ga4gh.{ GASearchReadsResponse, GAReadAlignment }
import org.scalatra.{ NotFound, RequestEntityTooLarge, Ok }
import org.scalatra.test.scalatest.ScalatraSuite

class RequestSuite extends MangoFunSuite with ScalatraSuite {

  implicit val formats = DefaultFormats
  addServlet(classOf[MangoRequest], "/*")

  val bamFile = ClassLoader.getSystemClassLoader.getResource("mouse_chrM.bam").getFile
  val referenceFile = ClassLoader.getSystemClassLoader.getResource("mm10_chrM.fa").getFile
  val vcfFile = ClassLoader.getSystemClassLoader.getResource("truetest.genotypes.vcf").getFile
  val featureFile = ClassLoader.getSystemClassLoader.getResource("smalltest.bed").getFile
  val coverageFile = ClassLoader.getSystemClassLoader.getResource("mouse_chrM.coverage.adam").getFile

  val bamKey = LazyMaterialization.filterKeyFromFile(bamFile)
  val featureKey = LazyMaterialization.filterKeyFromFile(featureFile)
  val vcfKey = LazyMaterialization.filterKeyFromFile(vcfFile)
  val coverageKey = LazyMaterialization.filterKeyFromFile(coverageFile)

  // empty args used for 'no content' tests
  val emptyArgs = new ServerArgs()
  emptyArgs.referencePath = referenceFile
  emptyArgs.testMode = true

  val args = new ServerArgs()
  args.readsPaths = bamFile
  args.referencePath = referenceFile
  args.variantsPaths = vcfFile
  args.coveragePaths = coverageFile
  args.featurePaths = featureFile
  args.showGenotypes = true
  args.testMode = true

  sparkTest("Should pass for discovery mode") {
    val args = new ServerArgs()
    args.discoveryMode = true
    args.referencePath = referenceFile
    args.featurePaths = featureFile
    args.variantsPaths = vcfFile
    args.testMode = true

    implicit val vizReads = runServer(args)
    get(s"/features/${featureKey}/chrM?start=0&end=2000") {
      assert(status == Ok("").status.code)
      val features = parse(response.getContent()).extract[Array[BedRowJson]]
      assert(features.length == 2)
    }
  }

  sparkTest("/reference/:ref") {
    implicit val VizReads = runServer(args)
    // should return data
    get("/reference/chrM?start=1&end=100") {
      assert(status == Ok("").status.code)
      val ref = parse(response.getContent()).extract[String]
      assert(ref.length == 99)
    }
  }

  sparkTest("Returns no content error when no data exists") {
    implicit val VizReads = runServer(emptyArgs)

    get(s"/reads/${bamKey}/chrM?start=0&end=100") {
      assert(status == HttpError.notFound.status.code)
    }

    get(s"/variants/${vcfKey}/chrM?start=0&end=100") {
      assert(status == HttpError.notFound.status.code)
    }

    get(s"/features/${featureKey}/chrM?start=0&end=100") {
      assert(status == HttpError.notFound.status.code)
    }

    get(s"/coverage/${coverageKey}/chrM?start=0&end=100") {
      assert(status == HttpError.notFound.status.code)
    }

    get(s"/reads/coverage/${bamKey}/chrM?start=0&end=100") {
      assert(status == HttpError.notFound.status.code)
    }
  }

  sparkTest("Key does not exist") {
    implicit val VizReads = runServer(args)
    val key = "fakeKey"

    val region = ReferenceRegion("chrM", 0, 2000)
    get(s"/reads/${key}/chrM?start=0&end=100") {
      assert(status == HttpError.keyNotFound(key).status.code)
    }

    get(s"/variants/${key}/chrM?start=0&end=100") {
      assert(status == HttpError.keyNotFound(key).status.code)
    }

    get(s"/features/${key}/chrM?start=0&end=100") {
      assert(status == HttpError.keyNotFound(key).status.code)
    }

    get(s"/coverage/${key}/chrM?start=0&end=100") {
      assert(status == HttpError.keyNotFound(key).status.code)
    }

    get(s"/reads/coverage/${key}/chrM?start=0&end=100") {
      assert(status == HttpError.keyNotFound(key).status.code)
    }
  }

  /****************** Reads Tests *******************/
  sparkTest("/reads/:key/:ref") {
    implicit val VizReads = runServer(args)
    get(s"/reads/${bamKey}/chrM?start=0&end=100") {
      assert(status == Ok("").status.code)
    }
  }

  sparkTest("reads: region out of bounds error") {
    get(s"/reads/${bamKey}/chr1?start=0&end=100") {
      assert(status == HttpError.outOfBounds.status.code)
    }
  }

  sparkTest("/reads/coverage/:key/:ref") {
    implicit val VizReads = runServer(args)
    get(s"/reads/coverage/${bamKey}/chrM?start=1&end=100") {
      assert(status == Ok("").status.code)
      val json = parse(response.getContent()).extract[Array[PositionCount]]
      assert(json.length == 99)
    }
  }

  /************** Variants Tests ***************************/
  sparkTest("/variants/:key/:ref") {
    val args = new ServerArgs()
    args.referencePath = referenceFile
    args.variantsPaths = vcfFile
    args.testMode = true
    args.showGenotypes = true

    implicit val VizReads = runServer(args)
    get(s"/variants/${vcfKey}/chrM?start=0&end=100") {
      assert(status == Ok("").status.code)
      val json = parse(response.getContent()).extract[Array[String]].map(r => GenotypeJson(r))
        .sortBy(_.variant.getStart)
      assert(json.length == 3)
      assert(json.head.variant.getStart == 19)
      assert(json.head.sampleIds.length == 2)
    }
  }
  //
  //  sparkTest("variants: region out of bounds error") {
  //    get(s"/variants/${vcfKey}/chr1?start=0&end=100") {
  //      assert(status == HttpError.outOfBounds.status.code)
  //    }
  //  }
  //
  //  sparkTest("variants: no content error") {
  //    implicit val VizReads = runServer(args)
  //    val region = ReferenceRegion("chrM", 10000, 12000)
  //    get(s"/variants/${vcfKey}/${region.referenceName}?start=${region.start}&end=${region.end}") {
  //      assert(status == HttpError.noContent(region).status.code)
  //    }
  //  }
  //
  //  sparkTest("does not return genotypes when binned") {
  //    implicit val VizReads = runServer(args)
  //    get(s"/variants/${vcfKey}/chrM?start=0&end=100&binning=100") {
  //      assert(status == Ok("").status.code)
  //      val json = parse(response.getContent()).extract[Array[String]].map(r => GenotypeJson(r))
  //        .sortBy(_.variant.getStart)
  //      assert(json.length == 1)
  //      assert(json.head.sampleIds.length == 0)
  //    }
  //  }

  /************** Feature Tests ***************************/

  sparkTest("features: region out of bounds error") {
    get(s"/features/${featureKey}/chr1?start=0&end=100") {
      assert(status == HttpError.outOfBounds.status.code)
    }
  }

  sparkTest("features: no content error") {
    implicit val VizReads = runServer(args)
    val region = ReferenceRegion("chrM", 10000, 12000)
    get(s"/features/${featureKey}/${region.referenceName}?start=${region.start}&end=${region.end}") {
      assert(status == HttpError.noContent(region).status.code)
    }
  }

  sparkTest("/features/:key/:ref") {
    implicit val vizReads = runServer(args)
    get(s"/features/${featureKey}/chrM?start=0&end=1200") {
      assert(status == Ok("").status.code)
      val json = parse(response.getContent()).extract[Array[BedRowJson]]
      assert(json.length == 2)
    }
  }

  sparkTest("bins features at /features/:key/:ref:binning") {
    implicit val vizReads = runServer(args)
    get(s"/features/${featureKey}/chrM?start=0&end=1200&binning=10") {
      assert(status == Ok("").status.code)
      val json = parse(response.getContent()).extract[Array[BedRowJson]]
      assert(json.length == 2)
    }
  }

  /************** Coverage Tests ***************************/

  sparkTest("/coverage/:key/:ref") {
    val args = new ServerArgs()
    args.referencePath = referenceFile
    args.coveragePaths = coverageFile
    args.testMode = true

    implicit val vizReads = runServer(args)
    get(s"/coverage/${coverageKey}/chrM?start=0&end=1200") {
      assert(status == Ok("").status.code)
      val json = parse(response.getContent()).extract[Array[PositionCount]]
      assert(json.map(_.start).distinct.length == 1200)
    }
  }

  sparkTest("coverage: region out of bounds error") {
    val args = new ServerArgs()
    args.referencePath = referenceFile
    args.coveragePaths = coverageFile
    args.testMode = true

    get(s"/coverage/${coverageKey}/chr1?start=0&end=100") {
      assert(status == HttpError.outOfBounds.status.code)
    }
  }

}
