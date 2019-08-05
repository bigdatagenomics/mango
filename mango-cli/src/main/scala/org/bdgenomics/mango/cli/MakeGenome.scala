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

import grizzled.slf4j.Logging
import org.apache.spark.SparkContext
import org.bdgenomics.mango.core.util.GenomeConfig
import org.bdgenomics.utils.cli._
import org.kohsuke.args4j.Argument

/**
 * Command line accessible class to download and save a genome.
 */
class MakeGenomeArgs extends Args4jBase with ParquetArgs {

  @Argument(required = true, metaVar = "genome", usage = "Genome name in UCSC (ie. hg19, mm10, etc).", index = 0)
  var genome: String = null

  @Argument(required = true, metaVar = "outputPath", usage = "Output path to save .genome file", index = 1)
  var outputPath: String = null
}

object MakeGenome extends BDGCommandCompanion {

  val commandName = "MakeGenome"
  val commandDescription = "Create a zipped .genome file containing 2bit link, gene file, chrom.sizes file and cytoband file."

  def apply(cmdLine: Array[String]): MakeGenome = {
    val args = Args4j[MakeGenomeArgs](cmdLine)
    new MakeGenome(args)
  }
}

class MakeGenome(protected val args: MakeGenomeArgs) extends BDGSparkCommand[MakeGenomeArgs] with Logging {
  val companion: BDGCommandCompanion = MakeGenome

  override def run(sc: SparkContext): Unit = {
    GenomeConfig.saveZippedGenome(args.genome, args.outputPath)
    info(s"Saved genome ${args.genome} to ${args.outputPath}")
  }
}
