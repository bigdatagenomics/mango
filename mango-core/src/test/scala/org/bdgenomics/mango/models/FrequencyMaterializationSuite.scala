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

class FrequencyMaterializationSuite extends MangoFunSuite {

  implicit val formats = DefaultFormats

  val chrM = resourcePath("mouse_chrM.bam")
  val chunkSize = 1000
  val sd = new SequenceDictionary(Vector(SequenceRecord("chr1", 2000L),
    SequenceRecord("chrM", 20000L),
    SequenceRecord("20", 90000L)))

  sparkTest("get frequency from file that is not precomputed") {
    val sample = "C57BL/6J"
    val region = ReferenceRegion("chrM", 1, 1000)
    val freq = new FrequencyMaterialization(sc, sd, chunkSize)
    freq.loadSample(chrM, sample)

  }

}
