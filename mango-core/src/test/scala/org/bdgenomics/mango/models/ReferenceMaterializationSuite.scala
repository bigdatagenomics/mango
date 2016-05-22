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

import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.mango.util.MangoFunSuite

class ReferenceMaterializationSuite extends MangoFunSuite {

  // test reference data
  var referencePath = resourcePath("mm10_chrM.fa")

  sparkTest("test ReferenceRDD creation") {
    val refRDD = new ReferenceMaterialization(sc, referencePath)
  }

  sparkTest("test ReferenceRDD data retrieval at layer 0") {
    val refRDD = new ReferenceMaterialization(sc, referencePath)
    val region = ReferenceRegion("chrM", 0, 1000)
    val response: String = refRDD.getReferenceString(region)
    assert(response.length == region.length)
  }

  sparkTest("test ReferenceRDD data retrieval at layer 1") {
    val refRDD = new ReferenceMaterialization(sc, referencePath)
    val response: String = refRDD.getReferenceString(ReferenceRegion("chrM", 0, 6000))
    println(response.length)
  }

  sparkTest("test ReferenceRDD data retrieval at layer 2") {
    val refRDD = new ReferenceMaterialization(sc, referencePath)
    val response: String = refRDD.getReferenceString(ReferenceRegion("chrM", 0, 12000))
    println(response.length)
  }
}
