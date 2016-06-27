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
  val region = ReferenceRegion("chrM", 0, 500)

  sparkTest("test ReferenceRDD creation") {
    new ReferenceMaterialization(sc, referencePath)
  }

  sparkTest("test ReferenceRDD data retrieval at layer 0") {
    val refRDD = new ReferenceMaterialization(sc, referencePath)
    val response: String = refRDD.getReferenceString(region)
    assert(response.length == region.length)
  }

  sparkTest("assert chunk size specification correctly resizes fragments") {
    val chunkSize = 100
    val refRDD = new ReferenceMaterialization(sc, referencePath, chunkSize)
    val response: String = refRDD.getReferenceString(region)
    val first = refRDD.intRDD.toRDD.first._2
    assert(first.length == chunkSize)
  }

  sparkTest("assert Reference RDDs with different chunk sizes result in same output sequence") {
    val chunkSize1 = 100
    val chunkSize2 = 500

    val refRDD1 = new ReferenceMaterialization(sc, referencePath, chunkSize1)
    val refRDD2 = new ReferenceMaterialization(sc, referencePath, chunkSize2)
    val response1: String = refRDD1.getReferenceString(region)
    val response2: String = refRDD2.getReferenceString(region)
    assert(response1 == response2)
  }

  sparkTest("assert byte array and string is the sampe") {
    val chunkSize = 100

    val refRDD = new ReferenceMaterialization(sc, referencePath, chunkSize)
    val response1: String = new String(refRDD.getReferenceAsBytes(region).map(_.toChar))
    val response2: String = refRDD.getReferenceString(region)
    assert(response1 == response2)
  }
}
