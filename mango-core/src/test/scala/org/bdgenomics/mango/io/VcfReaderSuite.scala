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

package org.bdgenomics.mango.io

import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.mango.util.MangoFunSuite
import net.liftweb.json._

class VcfReaderSuite extends MangoFunSuite {

  implicit val formats = DefaultFormats

  // test vcf data
  val vcfFile = resourcePath("truetest.genotypes.vcf")
  val vcfFileGz = resourcePath("small.vcf.gz")

  test("Should load local vcf file") {
    val region = new ReferenceRegion("chrM", 0, 999)
    val data = VcfReader.load(vcfFile, Some(Iterable(region)), true)._2
    assert(data.size == 7)
  }

  test("Gets file header") {
    val region = new ReferenceRegion("chrM", 90L, 110L)
    val header = VcfReader.load(vcfFileGz, Some(Iterable(region)), true)._1
    assert(header.getSequenceDictionary.getSequences.toArray.length == 1)
  }

  test("Should load all records when region is not defined") {
    val data = VcfReader.load(vcfFile, None, true)._2
    assert(data.size == 7)
  }

  test("Fails on files that are not vcf or vcf.gz") {
    val region = new ReferenceRegion("chrM", 90L, 110L)

    val thrown = intercept[Exception] {
      VcfReader.load("myfile.badSuffix", Some(Iterable(region)), true)
    }
    assert(thrown.getMessage.contains("myfile.badSuffix"))
  }

  test("Should return empty on invalid chromosome") {
    val region = new ReferenceRegion("chrT", 90L, 110L)
    val data = VcfReader.load(vcfFile, Some(Iterable(region)), true)._2
    assert(data.size == 0)
  }

  test("Should load local vcf files with incorrect prefixes file") {
    val region = new ReferenceRegion("M", 0, 999)
    val data = VcfReader.load(vcfFile, Some(Iterable(region)), true)._2
    assert(data.size == 7)
  }

  test("Should load genotypes") {
    val region = new ReferenceRegion("chrM", 0L, 100L)
    val data = VcfReader.load(vcfFile, Some(Iterable(region)), true)._2

    assert(data.map(_.genotypes).flatten.size == 21)
  }

  test("loads gzipped vcfs") {
    val region = new ReferenceRegion("chr1", 0L, 63736L)
    val data = VcfReader.load(vcfFileGz, Some(Iterable(region)), true)._2

    assert(data.size == 4)
  }

  sparkTest("Should load data from HDFS using Spark") {
    val region = new ReferenceRegion("chrM", 90L, 91L)
    val data = VcfReader.loadHDFS(sc, vcfFile, Some(Iterable(region)))._2

    val data2 = VcfReader.load(vcfFile, Some(Iterable(region)), true)._2

    assert(data.length == data2.size)
  }

  test("Should load http vcf file") {
    val bamString = "http://1000genomes.s3.amazonaws.com/phase1/analysis_results/integrated_call_sets/ALL.chr22.integrated_phase1_v3.20101123.snps_indels_svs.genotypes.vcf.gz"
    val region = new ReferenceRegion("chr22", 25595080, 25603694)
    val count = VcfReader.load(bamString, Some(Iterable(region)), false)._2.size

    assert(count == 147)

  }

  //  TODO
  //  test("Should load s3 vcf file") {
  //    val bamString = "s3://1000genomes/phase1/analysis_results/integrated_call_sets/ALL.chr1.integrated_phase1_v3.20101123.snps_indels_svs.genotypes.vcf.gz"
  //
  //    val region = new ReferenceRegion("chr11", 67401049L, 67401225L) // chr11 PPP1CA locus
  //    val data = BamReader.loadS3(bamString, Iterable(region))
  //    assert(data.size == 2)
  //
  //  }

}
