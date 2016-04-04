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

package org.bdgenomics.mango.models

import org.bdgenomics.adam.models.{ ReferenceRegion, SequenceDictionary, SequenceRecord }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.adam.util.ADAMFunSuite

class LazyMaterializationSuite extends ADAMFunSuite {

  def getDataCountFromBamFile(file: String, viewRegion: ReferenceRegion): Long = {
    sc.loadIndexedBam(file, viewRegion).count
  }

  val sd = new SequenceDictionary(Vector(SequenceRecord("chr1", 2000L),
    SequenceRecord("chrM", 20000L),
    SequenceRecord("chr3", 2000L)))

  val bamFile = "./src/test/resources/mouse_chrM.bam"
  val vcfFile = "./src/test/resources/truetest.vcf"
  val vcfFile2 = "./src/test/resources/truetest2.vcf"
  val referencePath = "./src/test/resources/mm10_chrM.fa"

  sparkTest("assert the data pulled from a file is the same") {
    val refRDD = new ReferenceRDD(sc, referencePath)

    val sample = "sample1"
    val lazyMat = AlignmentRecordMaterialization(sc, sd, 10, refRDD)
    lazyMat.loadSample(bamFile, Option(sample))

    val region = new ReferenceRegion("chrM", 0L, 1000L)

    var results = lazyMat.get(region, sample).get
    var lazySize = results.count
    val dataSize = getDataCountFromBamFile(bamFile, region)
    assert(dataSize == lazySize)
  }

  sparkTest("Get data from different samples at the same region") {
    val refRDD = new ReferenceRDD(sc, referencePath)

    val sample1 = "person1"
    val sample2 = "person2"

    val lazyMat = AlignmentRecordMaterialization(sc, sd, 10, refRDD)
    val region = new ReferenceRegion("chrM", 0L, 100L)
    lazyMat.loadSample(bamFile, Option(sample1))
    lazyMat.loadSample(bamFile, Option(sample2))
    val results1 = lazyMat.get(region, sample1).get
    val lazySize1 = results1.count

    val results2 = lazyMat.get(region, sample2).get
    assert(lazySize1 == getDataCountFromBamFile(bamFile, region))
  }

  sparkTest("Fetch region out of bounds") {
    val refRDD = new ReferenceRDD(sc, referencePath)
    val sample1 = "person1"

    val lazyMat = AlignmentRecordMaterialization(sc, sd, 10, refRDD)
    val bigRegion = new ReferenceRegion("chrM", 0L, 20000L)
    lazyMat.loadSample(bamFile, Option(sample1))
    val results = lazyMat.get(bigRegion, sample1).get
    val lazySize = results.count

    val smRegion = new ReferenceRegion("chrM", 0L, 19299L)
    assert(lazySize == getDataCountFromBamFile(bamFile, smRegion))
  }

  sparkTest("Fetch region whose name is not yet loaded") {
    val refRDD = new ReferenceRDD(sc, referencePath)
    val sample1 = "person1"

    val lazyMat = AlignmentRecordMaterialization(sc, sd, 10, refRDD)
    val bigRegion = new ReferenceRegion("M", 0L, 20000L)
    lazyMat.loadSample(bamFile, Option(sample1))
    val results = lazyMat.get(bigRegion, sample1)

    assert(results == None)
  }

  sparkTest("Get data for variants") {
    val region = new ReferenceRegion("chrM", 0L, 100L)
    val lazyMat = GenotypeMaterialization(sc, sd, 10)
    lazyMat.loadSample(vcfFile)

    val results = lazyMat.get(region, vcfFile).get
    assert(results.count == 3)

  }

  sparkTest("Get data for 2 variant files") {
    val region = new ReferenceRegion("chrM", 0L, 100L)
    val lazyMat = GenotypeMaterialization(sc, sd, 10)
    lazyMat.loadSample(vcfFile)
    lazyMat.loadSample(vcfFile2)
    val vcfFiles = List(vcfFile, vcfFile2)
    val results1 = lazyMat.get(region, vcfFile).get
    val results2 = lazyMat.get(region, vcfFile2).get
    val recordCount = results1.count + results2.count

    val results = lazyMat.multiget(region, vcfFiles).get
    assert(results.count == recordCount)

  }

  sparkTest("Merge Regions") {
    val r1 = new ReferenceRegion("chr1", 0, 999)
    val r2 = new ReferenceRegion("chr1", 1000, 1999)
    val lazyMat = GenotypeMaterialization(sc, sd, 10)

    val merged = lazyMat.mergeRegions(Option(List(r1, r2))).get
    assert(merged.size == 1)
    assert(merged.head.start == 0 && merged.head.end == 1999)
  }

  sparkTest("Merge Regions with gap") {
    val r1 = new ReferenceRegion("chr1", 0, 999)
    val r2 = new ReferenceRegion("chr1", 1000, 1999)
    val r3 = new ReferenceRegion("chr1", 3000, 3999)
    val lazyMat = GenotypeMaterialization(sc, sd, 10)

    val merged = lazyMat.mergeRegions(Option(List(r1, r2, r3))).get
    assert(merged.size == 2)
    assert(merged.head.end == 1999 && merged.last.end == 3999)
  }

}
