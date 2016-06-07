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

import net.liftweb.json._
import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.formats.avro.AlignmentRecord
import org.bdgenomics.mango.util.MangoFunSuite

class AlignmentRecordMaterializationSuite extends MangoFunSuite {

  implicit val formats = DefaultFormats

  def getDataCountFromBamFile(file: String, viewRegion: ReferenceRegion): Long = {
    sc.loadIndexedBam(file, viewRegion).count
  }

  def getFirstFromBamFile(file: String): AlignmentRecord = {
    sc.loadBam(file).first
  }

  // test alignment data
  val bamFile = resourcePath("mouse_chrM.bam")

  // test reference data
  var referencePath = resourcePath("mm10_chrM.fa")

  sparkTest("assert sample name is correct") {

    val reference = new ReferenceMaterialization(sc, referencePath)

    val lazyMat = AlignmentRecordMaterialization(sc, reference.chunkSize, reference)
    val samples = lazyMat.init(List(bamFile))
    val sampleName = getFirstFromBamFile(bamFile).getRecordGroupSample
    assert(samples.head == sampleName)
  }

  sparkTest("assert raw data returns from 1 sample") {

    val reference = new ReferenceMaterialization(sc, referencePath)

    val data = AlignmentRecordMaterialization(sc, reference.chunkSize, reference)
    val samples = data.init(List(bamFile))

    val region = new ReferenceRegion("chrM", 0L, 1000L)

    val results = data.multiget(region, samples)
    assert(results.size == 1)

  }

  sparkTest("assert convoluted data is calculated over large regions") {

    val reference = new ReferenceMaterialization(sc, referencePath, 100)

    val data = AlignmentRecordMaterialization(sc, 10, reference)
    val samples = data.init(List(bamFile))

    val region = new ReferenceRegion("chrM", 0L, 10000L)

    val results = data.multiget(region, samples)
    val json: Map[String, Array[Double]] = parse(results).extract[Map[String, Array[Double]]]
    println(json.head._2.length == 99)
  }

  sparkTest("Fetch region out of bounds") {
    val reference = new ReferenceMaterialization(sc, referencePath)
    val sample = getFirstFromBamFile(bamFile).getRecordGroupSample
    val data = AlignmentRecordMaterialization(sc, reference.chunkSize, reference)

    val bigRegion = new ReferenceRegion("chrM", 0L, 9000L)
    data.init(List(bamFile))
    val results1 = data.get(bigRegion, sample)
    println(results1)

    val smRegion = new ReferenceRegion("chrM", 0L, 16299L)
    val results2 = data.get(smRegion, sample)
    //    assert(results1.count == results2.count) // TODO: parse to CalculatedAlignmentRecord and compare

  }

  sparkTest("Fetch region whose name is not yet loaded") {
    val reference = new ReferenceMaterialization(sc, referencePath)
    val sample = "fakeSample"

    val data = AlignmentRecordMaterialization(sc, reference.chunkSize, reference)

    val bigRegion = new ReferenceRegion("chrM", 0L, 20000L)
    data.init(List(bamFile))
    try {
      val results = data.get(bigRegion, sample)
    } catch {
      case e: Exception =>
        assert(e.getMessage.contains(s"key not found: ${sample}"))
    }

  }

  sparkTest("Test frequency retrieval") {
    val reference = new ReferenceMaterialization(sc, referencePath)
    val sample = getFirstFromBamFile(bamFile).getRecordGroupSample
    val data = AlignmentRecordMaterialization(sc, reference.chunkSize, reference)

    val bigRegion = new ReferenceRegion("chrM", 0L, 20000L)
    data.init(List(bamFile))
    val results = data.get(bigRegion, sample)
    val freq = data.getFrequency(bigRegion, List(sample))

  }
}
