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

import java.io.FileInputStream
import java.util.zip.GZIPInputStream

import org.bdgenomics.formats.avro.Feature
import org.bdgenomics.mango.converters.GA4GHutil
import scala.collection.JavaConversions._
import scala.io.Source

/**
 * Processes refSeq file, downloaded from http://hgdownload.soe.ucsc.edu/goldenPath/${GENOME}/database/refGene.txt.gz
 * for a given genome.
 */
object RefSeqFile {

  /**
   * Process iterator of tab delimited strings to gene features.
   *
   * @param lines Iterator of tab delimited strings
   * @return Array of gene features
   */
  def strToGenes(lines: Iterator[String]): Array[ga4gh.SequenceAnnotations.Feature] = {

    lines.map(nextLine => {

      try {
        val tokens = nextLine.split("\t")

        val geneId = tokens(1)
        val referenceName = tokens(2)
        val strand = tokens(3)
        val start = tokens(4).toLong
        val end = tokens(5).toLong
        val thickStart = tokens(6)
        val thickEnd = tokens(7)

        val blockCount = Integer.parseInt(tokens(8))

        val stok = tokens(9).split(",")
        val etok = tokens(10).split(",")

        val name = tokens(12)

        var blockStarts = ""
        var blockSizes = ""

        for (i <- 0 to blockCount - 1) {
          val bs = Integer.parseInt((stok(i)))
          blockStarts += (bs - start).toString
          blockSizes += (Integer.parseInt(etok(i)) - bs).toString
          if (i != blockCount - 1) {
            blockStarts += ","
            blockSizes += ","
          }
        }

        val attributes: Map[String, String] = Map("blockCount" -> blockCount.toString,
          "blockStarts" -> blockStarts,
          "thickStart" -> thickStart,
          "thickEnd" -> thickEnd,
          "blockSizes" -> blockSizes)

        val feature = Feature.newBuilder()
          .setReferenceName(referenceName)
          .setStart(start)
          .setEnd(end)
          .setName(name)
          .setFeatureId(geneId)
          .setGeneId(geneId)
          .setAttributes(attributes)
          .build()

        GA4GHutil.featureToGAFeature(feature)
      } catch {
        case e: Exception => {
          e.printStackTrace()
          throw new RuntimeException(s"${nextLine} is not properly formatted for gene conversion")
        }
      }
    }).toArray
  }

  /**
   * Read in tab delimited file and process to gene features.
   *
   * @param iFile absolute path to file
   * @return array of gene features
   */
  def refSeqFileToGenes(iFile: String, isGzipped: Boolean = false): Array[ga4gh.SequenceAnnotations.Feature] = {
    try {
      if (isGzipped) {
        val in = new GZIPInputStream(new FileInputStream(iFile))
        strToGenes(Source.fromInputStream(in).getLines())
      } else {
        strToGenes(Source.fromFile(iFile).getLines)
      }
    } catch {
      case e: Exception => {
        e.printStackTrace()
        throw new NullPointerException(s"${iFile} not found")
      }
    }
  }

}
