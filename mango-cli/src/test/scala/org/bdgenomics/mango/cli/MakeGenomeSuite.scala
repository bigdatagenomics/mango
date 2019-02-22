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
package org.bdgenomics.mango.cli

import java.io.FileNotFoundException
import org.bdgenomics.mango.core.util.GenomeConfig
import org.bdgenomics.mango.util.MangoFunSuite

class MakeGenomeSuite extends MangoFunSuite {
  sparkTest("creates genome") {
    val outputPath = tmpFile("")

    val genome = "anoGam1"

    MakeGenome(Array(genome, outputPath)).run(sc)

    val config = GenomeConfig.loadZippedGenome(s"${outputPath}/${genome}.genome")
    assert(config.id == genome)
    assert(config.chromSizes.records.length == 7)
  }

  sparkTest("fails with invalid genome") {
    val outputPath = tmpFile("")

    val genome = "invalidBuild"

    val thrown = intercept[FileNotFoundException] {
      MakeGenome(Array(genome, outputPath)).run(sc)
    }
    assert(thrown.getMessage.contains(s"${genome}.2bit"))
  }

}
