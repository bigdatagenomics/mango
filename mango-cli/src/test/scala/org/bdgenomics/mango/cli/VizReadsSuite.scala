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
import org.scalatra.Ok
import org.scalatra.test.scalatest.ScalatraSuite

class VizReadsSuite extends MangoFunSuite with ScalatraSuite {

  implicit val formats = DefaultFormats
  addServlet(classOf[VizServlet], "/*")

  val bamFile = ClassLoader.getSystemClassLoader.getResource("mouse_chrM.bam").getFile
  val referenceFile = ClassLoader.getSystemClassLoader.getResource("mm10_chrM.fa").getFile
  val vcfFile = ClassLoader.getSystemClassLoader.getResource("truetest.vcf").getFile
  val featureFile = ClassLoader.getSystemClassLoader.getResource("smalltest.bed").getFile
  val geneFile = ClassLoader.getSystemClassLoader.getResource("dvl1.200.gtf").getFile

  val bamKey = LazyMaterialization.filterKeyFromFile(bamFile)
  val featureKey = LazyMaterialization.filterKeyFromFile(featureFile)
  val vcfKey = LazyMaterialization.filterKeyFromFile(vcfFile)

  val args = new VizReadsArgs()
  args.readsPaths = bamFile
  args.referencePath = referenceFile
  args.variantsPaths = vcfFile
  args.featurePaths = featureFile
  args.testMode = true
  args.genotypesPaths = vcfFile

  sparkTest("reference/:ref") {
    implicit val VizReads = runVizReads(args)
    // should return data
    get("/reference/chrM?start=1&end=100") {
      assert(status == Ok("").status.code)
      val ref = parse(response.getContent()).extract[String]
      assert(ref.length == 99)
    }
  }

  sparkTest("/reads/:key/:ref") {
    implicit val VizReads = runVizReads(args)
    get(s"/reads/${bamKey}/chrM?start=0&end=100") {
      assert(status == Ok("").status.code)
    }
  }

  sparkTest("/reads/coverage/:key/:ref") {
    implicit val VizReads = runVizReads(args)
    get(s"/reads/coverage/${bamKey}/chrM?start=1&end=100") {
      assert(status == Ok("").status.code)
      val json = parse(response.getContent()).extract[Array[PositionCount]]
      assert(json.length == 99)
    }
  }

  sparkTest("/variants/:key/:ref") {
    implicit val VizReads = runVizReads(args)
    get(s"/variants/${vcfKey}/chrM?start=0&end=100") {
      assert(status == Ok("").status.code)
      val x = response.getContent()
      val json = parse(response.getContent()).extract[Array[VariantJson]]
      assert(json.length == 3)
    }
  }

  sparkTest("/genotypes/:key/:ref") {
    implicit val VizReads = runVizReads(args)
    get(s"/genotypes/${vcfKey}/chrM?start=0&end=100") {
      assert(status == Ok("").status.code)
      val json = parse(response.getContent()).extract[Array[GenotypeJson]]
      assert(json.length == 3)
    }
  }

  sparkTest("/features/:key/:ref") {
    implicit val vizReads = runVizReads(args)
    get(s"/features/${featureKey}/chrM?start=0&end=1200") {
      assert(status == Ok("").status.code)
      val json = parse(response.getContent()).extract[Array[BedRowJson]]
      assert(json.length == 2)
    }
  }

  sparkTest("Should pass for discovery mode") {
    val args = new VizReadsArgs()
    args.discoveryMode = true
    args.referencePath = referenceFile
    args.featurePaths = featureFile
    args.variantsPaths = vcfFile
    args.testMode = true

    implicit val vizReadDiscovery = runVizReads(args)
    get(s"/features/${featureKey}/chrM?start=0&end=2000") {
      assert(status == Ok("").status.code)
    }
  }

}