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
import org.bdgenomics.mango.layout.FeatureJson
import org.bdgenomics.mango.util.MangoFunSuite

class FeatureMaterializationSuite extends MangoFunSuite {

  implicit val formats = DefaultFormats

  val bedFile = resourcePath("smalltest.bed")

  val dict = new SequenceDictionary(Vector(SequenceRecord("chrM", 16699L)))

  sparkTest("assert raw data returns from one block") {

    val data = new FeatureMaterialization(sc, List(bedFile), dict, 1000)

    val region = new ReferenceRegion("chrM", 1000L, 1200L)

    val json = data.get(region)
    val results = parse(json).extract[List[FeatureJson]]
    assert(results.size == 2)
  }

  sparkTest("assert raw data returns from multiple blocks") {
    val data = new FeatureMaterialization(sc, List(bedFile), dict, 1000)

    val region = new ReferenceRegion("chrM", 0L, 3000L)

    val json = data.get(region)
    val results = parse(json).extract[List[FeatureJson]]
    assert(results.size == 3)
  }

  sparkTest("assert tracks are correctly assigned for overlapping tracks") {
    val data = new FeatureMaterialization(sc, List(bedFile), dict, 1000)

    val region = new ReferenceRegion("chrM", 1000L, 1200L)

    val json = data.get(region)
    val results = parse(json).extract[List[FeatureJson]]

    val track0 = results.filter(_.track == 0)
    val track1 = results.filter(_.track == 1)
    assert(track0.length == 1)
    assert(track1.length == 1)
  }

  sparkTest("can handle regions out of bounds") {
    val data = new FeatureMaterialization(sc, List(bedFile), dict, 1000)

    val region = new ReferenceRegion("chrM", 16000L, 17000L)

    val json = data.get(region)
    val results = parse(json).extract[List[FeatureJson]]
    assert(results.size == 0)
  }

}
