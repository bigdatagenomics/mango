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
import org.bdgenomics.mango.util.MangoFunSuite

class AnnotationMaterializationSuite extends MangoFunSuite {

  implicit val formats = DefaultFormats

  // test reference data
  var referencePath = resourcePath("mm10_chrM.fa")
  val referencePath_2bit = resourcePath("mm10_chrM.2bit")
  val region = ReferenceRegion("chrM", 0, 500)

  sparkTest("test ReferenceRDD creation") {
    new AnnotationMaterialization(sc, referencePath)
  }

  sparkTest("assert reference string is correctly extracted") {
    val refRDD = new AnnotationMaterialization(sc, referencePath)
    val response: String = refRDD.getReferenceString(region)
    assert(response.length == region.length)
    assert(response.take(50) == "GTTAATGTAGCTTAATAACAAAGCAAAGCACTGAAAATGCTTAGATGGAT")
  }

  sparkTest("return empty string in region out of bounds") {
    val refRDD = new AnnotationMaterialization(sc, referencePath)
    val response: String = refRDD.getReferenceString(region)
    assert(response.length == region.length)
    assert(response.take(50) == "GTTAATGTAGCTTAATAACAAAGCAAAGCACTGAAAATGCTTAGATGGAT")
  }

  sparkTest("can load 2bit file") {
    val refRDD = new AnnotationMaterialization(sc, referencePath_2bit)
    val response: String = refRDD.getReferenceString(region)
    assert(response.length == region.length)
    assert(response.take(50) == "GTTAATGTAGCTTAATAACAAAGCAAAGCACTGAAAATGCTTAGATGGAT")
  }
}
