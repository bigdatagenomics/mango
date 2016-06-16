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
import org.bdgenomics.formats.avro.AlignmentRecord
import org.bdgenomics.mango.layout.FeatureJson
import org.bdgenomics.mango.util.MangoFunSuite
import org.scalatra.{ NotFound, RequestEntityTooLarge, Ok }
import org.scalatra.test.scalatest.ScalatraSuite

class VizReadsSuite extends MangoFunSuite with ScalatraSuite {

  implicit val formats = DefaultFormats

  val bamFile = ClassLoader.getSystemClassLoader.getResource("mouse_chrM.bam").getFile
  val referenceFile = ClassLoader.getSystemClassLoader.getResource("mm10_chrM.fa").getFile
  val vcfFile = ClassLoader.getSystemClassLoader.getResource("truetest.vcf").getFile
  val featureFile = ClassLoader.getSystemClassLoader.getResource("smalltest.bed").getFile

  var args = new VizReadsArgs()
  args.readsPaths = bamFile
  args.referencePath = referenceFile
  args.variantsPaths = vcfFile
  args.featurePaths = featureFile
  args.testMode = true
  addServlet(classOf[VizServlet], "/*")

  sparkTest("reference/:ref") {
    implicit val VizReads = runVizReads(args)
    // should return data
    get("/reference/chrM?start=1&end=100") {
      assert(status == Ok("").status.code)
      val ref = parse(response.getContent()).extract[String]
      assert(ref.length == 99)
    }

    // should return no data
    get("/reference/chrM?start=0&end=5000") {
      assert(status == RequestEntityTooLarge("").status.code)
    }
  }

  // test sequence dictionary retreival from Reference Materialization
  sparkTest("sequenceDictionary/") {
    get("/sequenceDictionary") {
      assert(status == Ok("").status.code)
    }
  }

  sparkTest("/reads/:ref raw data") {
    implicit val VizReads = runVizReads(args)
    get("/reads/chrM?start=0&end=100&sample=C57BL/6J&isRaw=true") {
      assert(status == Ok("").status.code)
    }
  }

  /*
   * Test for AlignmentRecord to GA4GH compliant json
   */
  sparkTest("/GA4GHreads/:ref raw data") {
    implicit val VizReads = runVizReads(args)
    get("/GA4GHreads/chrM?start=0&end=100&sample=C57BL/6J") {
      assert(status == Ok("").status.code)
      val alignments = parse(response.getContent()).extract[Array[AlignmentRecord]]
    }
  }

  sparkTest("/reads/:ref mismatch data") {
    implicit val VizReads = runVizReads(args)
    get("/reads/chrM?start=0&end=100&sample=C57BL/6J") {
      assert(status == Ok("").status.code)
    }
  }

  sparkTest("/features/chrM?start=0&end=2000") {
    implicit val vizReads = runVizReads(args)
    get("/features/chrM?start=0&end=1200") {
      assert(status == 200)
      val features = parse(response.getContent()).extract[List[FeatureJson]]
      assert(features.length == 2)
    }
  }

  sparkTest("Should not crash with incorrect feature file") {
    val args = new VizReadsArgs()
    args.referencePath = referenceFile
    args.featurePaths = vcfFile
    args.testMode = true

    implicit val vizReadNoFeatures = runVizReads(args)
    get("/features/chrM?start=0&end=2000") {
      println(status)
      assert(status == NotFound("").status.code)
    }
  }

}

