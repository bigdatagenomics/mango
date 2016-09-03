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
import org.bdgenomics.mango.util.MangoFunSuite

class FeatureMaterializationSuite extends MangoFunSuite {

  implicit val formats = DefaultFormats

  val bedFileName = "smalltest.bed"
  val bedFile2Name = "smalltest2.bed"
  val bedFile = resourcePath(bedFileName)
  val bedFile2 = resourcePath(bedFile2Name)
  val key = LazyMaterialization.filterKeyFromFile(bedFileName)
  val key2 = LazyMaterialization.filterKeyFromFile(bedFile2Name)

  val dict = new SequenceDictionary(Vector(SequenceRecord("chrM", 16699L)))

  sparkTest("assert raw data returns from one block") {

    val data = new FeatureMaterialization(sc, List(bedFile), dict)

    val region = new ReferenceRegion("chrM", 1000L, 1200L)

    val json = data.getJson(region)
    assert(json.contains(key))
  }

  sparkTest("can fetch multiple files") {
    val data = new FeatureMaterialization(sc, List(bedFile, bedFile2), dict)
    val region = new ReferenceRegion("chrM", 1000L, 1200L)
    val json = data.getJson(region)

    assert(json.contains(key) && json.contains(key2))
  }
}
