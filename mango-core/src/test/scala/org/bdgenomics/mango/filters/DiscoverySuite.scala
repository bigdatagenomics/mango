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

import org.bdgenomics.adam.models.{ SequenceRecord, SequenceDictionary, ReferenceRegion }
import org.bdgenomics.mango.models.FeatureMaterialization
import org.bdgenomics.mango.util.MangoFunSuite

class DiscoverySuite extends MangoFunSuite {

  // load resource files
  val bedFile = resourcePath("smalltest.bed")

  sparkTest("correctly gathers regions of occupancy from Feature file") {
    val sd = new SequenceDictionary(Vector(SequenceRecord("chrM", 1000000))) // length to ensure 1000 size windows
    val discovery = new Discovery(sd)

    val features = FeatureMaterialization.load(sc, bedFile, None).map(r => ReferenceRegion.unstranded(r))

    val mergedRegions = discovery.getFrequencies(features)
    assert(mergedRegions.length == 3)
    assert(mergedRegions.map(_._2).max == 1.0)
  }

  sparkTest("does not fail when no regions discovered") {
    val sd = new SequenceDictionary(Vector(SequenceRecord("chrN", 1000000))) // length to ensure 1000 size windows
    val discovery = new Discovery(sd)

    val features = FeatureMaterialization.load(sc, bedFile, None).map(r => ReferenceRegion.unstranded(r))

    val mergedRegions = discovery.getFrequencies(features)
    assert(mergedRegions.length == 0)
  }

}
