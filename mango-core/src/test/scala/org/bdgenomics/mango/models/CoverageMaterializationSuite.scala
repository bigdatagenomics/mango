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
import org.bdgenomics.adam.models.{ ReferenceRegion, SequenceDictionary, SequenceRecord }
import org.bdgenomics.mango.layout.PositionCount
import org.bdgenomics.mango.util.MangoFunSuite
import org.bdgenomics.adam.rdd.ADAMContext._

class CoverageMaterializationSuite extends MangoFunSuite {

  implicit val formats = DefaultFormats

  val coverageFileName = "mouse_chrM.coverage.adam"
  val coverageFile = resourcePath(coverageFileName)
  val key = LazyMaterialization.filterKeyFromFile(coverageFileName)

  val dict = new SequenceDictionary(Vector(SequenceRecord("chrM", 16699L)))

  val files = List(coverageFile)

  sparkTest("create new CoverageRecordMaterialization") {
    val lazyMat = CoverageMaterialization(sc, files, dict)
  }

  sparkTest("return coverage from CoverageRecordMaterialization") {
    val data = CoverageMaterialization(sc, files, dict)
    val region = new ReferenceRegion("chrM", 0L, 20L)
    val coverage = data.getCoverage(region).get(key).get
    assert(coverage.length == region.length())
  }

  sparkTest("can parse coverage json") {
    val data = CoverageMaterialization(sc, files, dict)
    val region = new ReferenceRegion("chrM", 0L, 20L)
    val freq = data.stringify(data.getCoverage(region).get(key).get)
    val coverage = parse(freq).extract[Array[PositionCount]]
    assert(coverage.length == region.length())
  }

  sparkTest("return sampled coverage from CoverageRecordMaterialization over large regions") {
    val binning = 10
    val data = CoverageMaterialization(sc, files, dict)
    val region = new ReferenceRegion("chrM", 0L, 200L)
    val coverage = data.getCoverage(region, binning).get(key).get
    assert(coverage.length == region.length() / binning)
  }

  sparkTest("return coverage overlapping multiple materialized nodes") {
    val data = CoverageMaterialization(sc, files, dict)
    val region = new ReferenceRegion("chrM", 90L, 110L)
    val coverage = data.getCoverage(region).get(key).get
    assert(coverage.length == region.length())
  }

  sparkTest("Should handle chromosomes with different prefixes") {
    val dict = new SequenceDictionary(Vector(SequenceRecord("M", 16699L)))

    val data = CoverageMaterialization(sc, files, dict)
    val region = new ReferenceRegion("M", 90L, 110L)
    val coverage = data.getCoverage(region).get(key).get
    assert(coverage.length == region.length())
  }

}
