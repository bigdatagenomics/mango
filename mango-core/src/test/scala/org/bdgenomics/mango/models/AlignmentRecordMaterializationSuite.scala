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
import org.bdgenomics.adam.models.{ SequenceRecord, SequenceDictionary, ReferenceRegion }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.mango.layout.PositionCount
import org.bdgenomics.mango.util.MangoFunSuite

class AlignmentRecordMaterializationSuite extends MangoFunSuite {

  implicit val formats = DefaultFormats

  val dict = new SequenceDictionary(Vector(SequenceRecord("chrM", 16699L)))
  val chunkSize = 100

  def getDataCountFromBamFile(file: String, viewRegion: ReferenceRegion): Long = {
    sc.loadIndexedBam(file, viewRegion).rdd.count
  }

  // test alignment data
  val bamFile = resourcePath("mouse_chrM.bam")
  val key = LazyMaterialization.filterKeyFromFile(bamFile)

  // test reference data
  var referencePath = resourcePath("mm10_chrM.fa")
  val files = List(bamFile)

  sparkTest("create new AlignmentRecordMaterialization") {
    val lazyMat = AlignmentRecordMaterialization(sc, files, dict)
  }

  sparkTest("return raw data from AlignmentRecordMaterialization") {

    val data = AlignmentRecordMaterialization(sc, files, dict)

    val region = new ReferenceRegion("chrM", 0L, 900L)
    val results = data.getJson(region).get(key).get
  }

  sparkTest("return coverage from AlignmentRecordMaterialization") {
    val data = AlignmentRecordMaterialization(sc, files, dict)
    val region = new ReferenceRegion("chrM", 0L, 20L)
    val freq = data.getCoverage(region).get(key).get
    val coverage = parse(freq).extract[Array[PositionCount]]

    assert(coverage.length == region.length())
  }

  sparkTest("return coverage overlapping multiple materialized nodes") {
    val data = AlignmentRecordMaterialization(sc, files, dict)
    val region = new ReferenceRegion("chrM", 90L, 110L)
    val freq = data.getCoverage(region).get(key).get
    val coverage = parse(freq).extract[Array[PositionCount]].sortBy(_.start)
    assert(coverage.length == region.length())
  }
}
