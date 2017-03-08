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

import org.bdgenomics.adam.models.{ SequenceRecord, SequenceDictionary }
import org.bdgenomics.mango.util.MangoFunSuite

class MaterializerSuite extends MangoFunSuite {

  // test alignment data
  val bamFile = resourcePath("mouse_chrM.bam")
  val dict = new SequenceDictionary(Vector(SequenceRecord("chrM", 16699L)))

  sparkTest("verifies objects exist") {
    val alignmentMat = new AlignmentRecordMaterialization(sc, List(bamFile), dict)
    val coverageMat = new CoverageMaterialization(sc, List(bamFile), dict)

    val mat = Materializer(Seq(alignmentMat, coverageMat))

    assert(mat.readsExist)
    assert(mat.coveragesExist)
    assert(!mat.featuresExist)
    assert(!mat.variantContextExist)
  }

  sparkTest("Can fetch materialization objects") {
    val alignmentMat = new AlignmentRecordMaterialization(sc, List(bamFile), dict)
    val coverageMat = new CoverageMaterialization(sc, List(bamFile), dict)

    val mat = Materializer(Seq(alignmentMat, coverageMat))

    assert(mat.getReads.isDefined)
    assert(mat.getCoverage.isDefined)
    assert(!mat.getFeatures.isDefined)
    assert(!mat.getVariantContext.isDefined)
  }

  sparkTest("can fetch arbitrary object through get") {
    val alignmentMat = new AlignmentRecordMaterialization(sc, List(bamFile), dict)
    val coverageMat = new CoverageMaterialization(sc, List(bamFile), dict)

    val mat = Materializer(Seq(alignmentMat, coverageMat))

    assert(mat.get(AlignmentRecordMaterialization.name).isDefined)
    assert(mat.get(CoverageMaterialization.name).isDefined)
    assert(!mat.get(FeatureMaterialization.name).isDefined)
  }

}