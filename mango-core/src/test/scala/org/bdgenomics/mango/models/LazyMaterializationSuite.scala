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
import org.bdgenomics.mango.util.{ Bookkeep, MangoFunSuite }

class LazyMaterializationSuite extends MangoFunSuite {

  def getDataCountFromBamFile(file: String, viewRegion: ReferenceRegion): Long = {
    sc.loadIndexedBam(file, viewRegion).count
  }

  val sd = new SequenceDictionary(Vector(SequenceRecord("chr1", 2000L),
    SequenceRecord("chrM", 20000L),
    SequenceRecord("20", 90000L)))

  // test vcf data
  val vcfFile = resourcePath("truetest.vcf")

  // test reference data
  var referencePath = resourcePath("mm10_chrM.fa")

  sparkTest("Merge Regions") {
    val r1 = new ReferenceRegion("chr1", 0, 999)
    val r2 = new ReferenceRegion("chr1", 1000, 1999)
    val lazyMat = GenotypeMaterialization(sc, sd, 10)

    val merged = Bookkeep.mergeRegions(Option(List(r1, r2))).get
    assert(merged.size == 1)
    assert(merged.head.start == 0 && merged.head.end == 1999)
  }

  sparkTest("Merge Regions with gap") {
    val r1 = new ReferenceRegion("chr1", 0, 999)
    val r2 = new ReferenceRegion("chr1", 1000, 1999)
    val r3 = new ReferenceRegion("chr1", 3000, 3999)
    val lazyMat = GenotypeMaterialization(sc, sd, 10)

    val merged = Bookkeep.mergeRegions(Option(List(r1, r2, r3))).get
    assert(merged.size == 2)
    assert(merged.head.end == 1999 && merged.last.end == 3999)
  }

}
