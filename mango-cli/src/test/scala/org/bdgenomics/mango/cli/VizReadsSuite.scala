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
import org.bdgenomics.mango.models.LazyMaterialization
import org.bdgenomics.mango.util.MangoFunSuite
import org.scalatra.Ok
import org.scalatra.test.scalatest.ScalatraSuite

class VizReadsSuite extends MangoFunSuite with ScalatraSuite {

  implicit val formats = DefaultFormats
  addServlet(classOf[VizServlet], "/*")

  val bamFile = ClassLoader.getSystemClassLoader.getResource("mouse_chrM.bam").getFile
  val referenceFile = ClassLoader.getSystemClassLoader.getResource("mm10_chrM.fa").getFile
  val vcfFile = ClassLoader.getSystemClassLoader.getResource("truetest.vcf").getFile
  val featureFile = ClassLoader.getSystemClassLoader.getResource("smalltest.bed").getFile

  val bamKey = LazyMaterialization.filterKeyFromFile(bamFile)
  val featureKey = LazyMaterialization.filterKeyFromFile(featureFile)
  val vcfKey = LazyMaterialization.filterKeyFromFile(vcfFile)

  val args = new VizReadsArgs()
  args.readsPaths = bamFile
  args.referencePath = referenceFile
  args.variantsPaths = vcfFile
  args.featurePaths = featureFile
  args.testMode = true

  sparkTest("reference/:ref") {
    implicit val VizReads = runVizReads(args)
    // should return data
    get("/reference/chrM?start=1&end=100") {
      assert(status == Ok("").status.code)
      val ref = parse(response.getContent()).extract[String]
      assert(ref.length == 99)
    }
  }

  sparkTest("/reads/:ref") {
    implicit val VizReads = runVizReads(args)
    get("/reads/chrM?start=0&end=100&key=" + bamKey) {
      assert(status == Ok("").status.code)
    }
  }

  sparkTest("/reads/coverage/:ref") {
    implicit val VizReads = runVizReads(args)
    get("/reads/coverage/chrM?start=0&end=100&key=" + bamKey) {
      assert(status == Ok("").status.code)
    }
  }

  sparkTest("/features/:ref") {
    implicit val vizReads = runVizReads(args)
    get("/features/chrM?start=0&end=1200&key=" + featureKey) {
      assert(status == Ok("").status.code)
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
    get("/features/chrM?start=0&end=2000&key=" + featureKey) {
      assert(status == Ok("").status.code)
    }
  }

}