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
import org.bdgenomics.adam.rdd.ADAMContext._

class CoverageMaterializationSuite extends MangoFunSuite {

  implicit val formats = DefaultFormats

  val coverageFileName = "mouse.bed"
  val coverageFile = resourcePath(coverageFileName)
  val key = LazyMaterialization.filterKeyFromFile(coverageFileName)

  val dict = new SequenceDictionary(Vector(SequenceRecord("chrM", 16699L)))

  sparkTest("test") {
    val rdd = sc.loadCoverage(coverageFile)
    println(rdd.rdd.count)
    // TODO: Complete coverage tests once issues with loadParquetAlignments are resolved
  }
}
