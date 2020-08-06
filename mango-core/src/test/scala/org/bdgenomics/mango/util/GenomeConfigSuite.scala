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
package org.bdgenomics.mango.core.util

import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.mango.util.MangoFunSuite

class GenomeConfigSuite extends MangoFunSuite {

  test("Should read in genome path") {

    val genomePath = resourcePath("mm10.genome")

    val genome = GenomeConfig.loadZippedGenome(genomePath)

    assert(genome.twoBitPath == "http://hgdownload.cse.ucsc.edu/goldenPath/mm10/bigZips/mm10.2bit")
  }

  test("Should parse chrom sizes file") {

    val genomePath = resourcePath("mm10.genome")

    val genome = GenomeConfig.loadZippedGenome(genomePath)

    val chromSizes = genome.chromSizes

    assert(chromSizes.size == 66)
    assert(chromSizes.records.head.name == "chr1")
    assert(chromSizes.records.head.length == 195471971)
  }

  test("Should parse refSeq.txt file") {

    val genomePath = resourcePath("mm10.genome")

    val genome = GenomeConfig.loadZippedGenome(genomePath)

    val genes = genome.genes.get

    assert(genes.length == 42586)
    assert(genes.get(ReferenceRegion("chr17", 24508849, 24527938)).toArray.length == 5)
  }

  test("Should fail when genome file does not exist") {

    val genomePath = java.nio.file.Files.createTempFile("mmbroken", ".genome").toAbsolutePath.toString

    val thrown = intercept[Exception] {
      GenomeConfig.loadZippedGenome(genomePath)
    }
    assert(thrown.getMessage === "zip file is empty")
  }

  test("should build genome") {

    val outputDir = java.nio.file.Files.createTempDirectory("genomes")

    // test genome, does not have genes or cytoband
    val genome = "hg19"

    GenomeConfig.saveZippedGenome(genome, outputDir.toAbsolutePath.toString)

    // check genome
    val config = GenomeConfig.loadZippedGenome(s"${outputDir}/${genome}.genome")
    assert(config.id == genome)
    assert(config.chromSizes.records.length == 93)
  }

  sparkTest("should not fail when initial RefSeq file is not available") {

    val outputDir = java.nio.file.Files.createTempDirectory("genomes")
    val genome = "anoGam1"

    GenomeConfig.saveZippedGenome(genome, outputDir.toAbsolutePath.toString)

    val config = GenomeConfig.loadZippedGenome(s"${outputDir}/${genome}.genome")
    assert(config.id == genome)
    assert(config.chromSizes.records.length == 7)
  }

}
