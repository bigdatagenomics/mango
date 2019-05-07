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

import ga4gh.Reads
import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.mango.util.MangoFunSuite
import net.liftweb.json._

class BamReaderSuite extends MangoFunSuite {

  implicit val formats = DefaultFormats

  // test alignment data
  val bamFile = resourcePath("mouse_chrM.bam")
  val samFile = resourcePath("multi_chr.sam")

  test("Should load local bam file") {
    val region = new ReferenceRegion("chrM", 90L, 110L)
    val data = BamReader.load(bamFile, Iterable(region))
    assert(data.size == 1156)
  }

  test("Fails on files that are not bam or sam") {
    val region = new ReferenceRegion("chrM", 90L, 110L)

    val thrown = intercept[Exception] {
      BamReader.load("myfile.vcf", Iterable(region))
    }
    assert(thrown.getMessage.contains("myfile.vcf"))
  }

  test("Should return empty on invalid chromosome") {
    val region = new ReferenceRegion("chrT", 90L, 110L)
    val data = BamReader.load(bamFile, Iterable(region))
    assert(data.size == 0)
  }

  test("Should return no data on empty dictionary") {
    val invalidSamFile = resourcePath("nodictionary.sam")
    val region = new ReferenceRegion("1", 90L, 110L)

    val data = BamReader.load(invalidSamFile, Iterable(region))
    assert(data.size == 0)
  }

  test("Should load local sam file") {
    val region = new ReferenceRegion("1", 26472780L, 26472790L)
    val data = BamReader.load(samFile, Iterable(region))
    val x = data.toArray
    assert(x.length == 1)
  }

  test("Should load local bam files with incorrect prefixes file") {
    val region = new ReferenceRegion("M", 90L, 110L)
    val data = BamReader.load(bamFile, Iterable(region))
    assert(data.size == 1156)
  }

  sparkTest("Should load data from HDFS using Spark") {
    val region = new ReferenceRegion("chrM", 90L, 91L)
    val data = BamReader.loadHDFS(sc, bamFile, Iterable(region))

    val data2 = BamReader.load(bamFile, Iterable(region))

    assert(data.rdd.count == data2.size)
  }

  test("Should load http bam file") {
    val bamString = "http://1000genomes.s3.amazonaws.com/phase3/data/NA12878/exome_alignment/NA12878.chrom11.ILLUMINA.bwa.CEU.exome.20121211.bam"
    val region = new ReferenceRegion("chr11", 67401049L, 67401225L) // chr11 PPP1CA locus
    val count = BamReader.loadHttp(bamString, Iterable(region)).size

    assert(count == 14) // should have two reads (verified against original file)

  }
  //
  //  test("Should load s3 bam file") {
  //    val bamString = "s3://1000genomes/phase3/data/NA12878/exome_alignment/NA12878.chrom11.ILLUMINA.bwa.CEU.exome.20121211.bam"
  //
  //    val region = new ReferenceRegion("chr11", 67401049L, 67401225L) // chr11 PPP1CA locus
  //    val data = BamReader.loadS3(bamString, Iterable(region))
  //    assert(data.size == 2)
  //
  //  }

}
