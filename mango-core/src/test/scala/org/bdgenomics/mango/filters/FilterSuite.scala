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
package org.bdgenomics.mango.filters

import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.mango.models.FeatureMaterialization
import org.bdgenomics.mango.util.MangoFunSuite

class FilterSuite extends MangoFunSuite {

  // load resource files
  val bedFile = resourcePath("smalltest.bed")
  val threshold = 2 // mark as high density if any bins have > 2 artifacts

  sparkTest("correctly filters features with high density regions") {
    val features = FeatureMaterialization.load(sc, None, bedFile)
    val filtered = FeatureFilter.filter(features.rdd, FeatureFilterType.highDensity, 100, threshold)

    val regions: List[(ReferenceRegion, Long)] = filtered.collect.toList
    assert(regions.length == 1)
    assert(regions.head._1 == ReferenceRegion("chrM", 1100, 1199))
    assert(regions.head._2 == 2)

  }

}
