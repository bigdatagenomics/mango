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

  def getSampleName: String = {
    "C57BL/6J"
  }

  // test alignment data
  val bamFile = resourcePath("mouse_chrM.bam")

  // test reference data
  var referencePath = resourcePath("mm10_chrM.fa")

  sparkTest("assert sample name is correct") {
    val sample = getSampleName
    val reference = new ReferenceMaterialization(sc, referencePath, 100)

    val lazyMat = AlignmentRecordMaterialization(sc, reference.chunkSize, reference)
    val samples = lazyMat.init(List(bamFile))
    assert(samples.head == sample)
  }

  sparkTest("assert raw data returns from 1 sample") {

    val reference = new ReferenceMaterialization(sc, referencePath, 100)

    val data = AlignmentRecordMaterialization(sc, reference.chunkSize, reference)
    val samples = data.init(List(bamFile))

    val region = new ReferenceRegion("chrM", 0L, 1000L)

    val results = data.multiget(region, samples)

  }

  sparkTest("Fetch region whose name is not yet loaded") {
    val reference = new ReferenceMaterialization(sc, referencePath, 100)
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
    val reference = new ReferenceMaterialization(sc, referencePath, 100)
    val data = AlignmentRecordMaterialization(sc, reference.chunkSize, reference)
    val sample = getSampleName
    val region = new ReferenceRegion("chrM", 0L, 20L)
    data.init(List(bamFile))
    val freq = data.getFrequency(region, List(sample))
    val coverageJson = parse(freq).extract[Map[String, String]].get("coverage").get

    // extract number of positions in string ('position' => 'p')
    val count = coverageJson.count(p => p == 'p')
    assert(count == 21)

  }
}
