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

import org.bdgenomics.formats.avro.AlignmentRecord
import org.bdgenomics.mango.util.MangoFunSuite

class AlignmentRecordMaterializationSuite extends MangoFunSuite {

  def getDataCountFromBamFile(file: String, viewRegion: ReferenceRegion): Long = {
    sc.loadIndexedBam(file, viewRegion).count
  }

  def getFirstFromBamFile(file: String): AlignmentRecord = {
    sc.loadBam(file).first
  }

  val sd = new SequenceDictionary(Vector(SequenceRecord("chr1", 2000L),
    SequenceRecord("chrM", 20000L),
    SequenceRecord("20", 90000L)))

  // test alignment data
  val bamFile = resourcePath("mouse_chrM.bam")

  // test reference data
  var referencePath = resourcePath("mm10_chrM.fa")

  sparkTest("assert sample name is correct") {

    val refRDD = new ReferenceMaterialization(sc, referencePath)

    val lazyMat = AlignmentRecordMaterialization(sc, sd, 10, refRDD, true)
    val samples = lazyMat.init(List(bamFile))
    val sampleName = getFirstFromBamFile(bamFile).getRecordGroupSample
    println(sampleName)

    assert(samples.head == sampleName)
  }

  sparkTest("assert the data pulled from a file is the same") {

    val refRDD = new ReferenceRDD(sc, referencePath)

    val data = AlignmentRecordMaterialization(sc, sd, 10, refRDD, true)
    val samples = data.init(List(bamFile))

    val region = new ReferenceRegion("chrM", 0L, 1000L)

    val results: String = data.g
    val lazySize = results.count
    val rawCount = getDataCountFromBamFile(bamFile, region)
    assert(lazySize == rawCount)
  }

    sparkTest("Get data from different samples at the same region") {
      val refRDD = new ReferenceRDD(sc, referencePath)

      val sample1 = "person1"
      val sample2 = "person2"

      val lazyMat = AlignmentRecordMaterialization(sc, sd, 10, refRDD, true)
      val region = new ReferenceRegion("chrM", 0L, 100L)
      lazyMat.loadSample(bamFile, Option(sample1))
      lazyMat.loadSample(bamFile, Option(sample2))
      val results1 = lazyMat.get(region, sample1).get
      val results2 = lazyMat.get(region, sample2).get
      assert(results1.count == results2.count)
    }

    sparkTest("Fetch region out of bounds") {
      val refRDD = new ReferenceRDD(sc, referencePath)
      val sample1 = "person1"

      val lazyMat = AlignmentRecordMaterialization(sc, sd, 10, refRDD, true)
      val bigRegion = new ReferenceRegion("chrM", 0L, 20000L)
      lazyMat.loadSample(bamFile, Option(sample1))
      val results1 = lazyMat.get(bigRegion, sample1).get
      val lazySize = results1.collect

      val smRegion = new ReferenceRegion("chrM", 0L, 16299L)
      val results2 = lazyMat.get(smRegion, sample1).get
      assert(results1.count == results2.count)

    }

    sparkTest("Test coverage and sampling") {
      val refRDD = new ReferenceRDD(sc, referencePath)
      val sample1 = "person1"

      val lazyMat = AlignmentRecordMaterialization(sc, sd, 10, refRDD)
      val bigRegion = new ReferenceRegion("chrM", 0L, 20000L)
      lazyMat.loadSample(bamFile, Option(sample1))
      val results = lazyMat.get(bigRegion, sample1).get
      val lazySize = results.count

      val smRegion = new ReferenceRegion("chrM", 0L, 16299L)
      val rawSize = getDataCountFromBamFile(bamFile, smRegion)
      assert(rawSize == lazySize)
    }

    sparkTest("Fetch region whose name is not yet loaded") {
      val refRDD = new ReferenceRDD(sc, referencePath)
      val sample1 = "person1"

      val lazyMat = AlignmentRecordMaterialization(sc, sd, 10, refRDD, true)
      val bigRegion = new ReferenceRegion("M", 0L, 20000L)
      lazyMat.loadSample(bamFile, Option(sample1))
      val results = lazyMat.get(bigRegion, sample1)

      assert(results == None)
    }

    sparkTest("Test frequency retrieval") {
      val refRDD = new ReferenceRDD(sc, referencePath)
      val sample1 = "person1"

      val lazyMat = AlignmentRecordMaterialization(sc, sd, 10, refRDD, true)
      val bigRegion = new ReferenceRegion("chrM", 0L, 20000L)
      lazyMat.loadSample(bamFile, Option(sample1))
      val results = lazyMat.get(bigRegion, sample1)
      val freq = lazyMat.getFrequency(bigRegion, List(sample1))

    }
}
