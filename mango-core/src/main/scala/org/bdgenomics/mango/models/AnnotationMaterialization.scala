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

import java.io.File

import htsjdk.samtools.SAMSequenceDictionary
import net.liftweb.json.Serialization._
import org.apache.spark.SparkContext
import org.bdgenomics.adam.models._
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.adam.rdd.contig.NucleotideContigFragmentRDD
import org.bdgenomics.adam.rdd.features.{ GeneRDD, FeatureRDD }
import org.bdgenomics.adam.util.{ ReferenceFile }
import org.bdgenomics.formats.avro.{ NucleotideContigFragment }
import org.bdgenomics.mango.core.util.{ VizUtils, Utils }
import org.bdgenomics.mango.layout.GeneJson
import org.bdgenomics.utils.misc.Logging
import picard.sam.CreateSequenceDictionary

class AnnotationMaterialization(@transient sc: SparkContext,
                                referencePath: String, genePath: String) extends Serializable with Logging {

  @transient implicit val formats = net.liftweb.json.DefaultFormats
  var bookkeep = Array[String]()

  // set and name interval rdd
  val (reference: NucleotideContigFragmentRDD, dict: SequenceDictionary) =
    if (referencePath.endsWith(".fa") || referencePath.endsWith(".fasta")) {
      val createObj = new CreateSequenceDictionary
      val dict: SAMSequenceDictionary = createObj.makeSequenceDictionary(new File(referencePath))
      val r: NucleotideContigFragmentRDD = sc.loadSequences(referencePath, fragmentLength = 10000)
      val d: SequenceDictionary = SequenceDictionary(dict)
      (sc.loadSequences(referencePath, fragmentLength = 10000), SequenceDictionary(dict))
    } else if (referencePath.endsWith(".adam")) {
      val reference = sc.loadParquetContigFragments(referencePath)
      (reference, sc.loadDictionary[NucleotideContigFragment](referencePath))
    } else
      throw new UnsupportedFileException("File Types supported for reference are fa, fasta and adam")

  val geneRDD: GeneRDD = loadGenes(genePath)
  geneRDD.rdd.setName("Gene RDD")

  /**
   * Loads genes from gtf file
   * @param filePath: filepath to gtf file
   * @return RDD of features formatted as genes
   */
  private def loadGenes(filePath: String): GeneRDD = {
    if (!filePath.endsWith(".gtf")) {
      throw new UnsupportedFileException(s"${filePath} is not a .gtf file")
    }
    try {
      val features: FeatureRDD = sc.loadFeatures(filePath)
      val fixedParentIds: FeatureRDD = features.reassignParentIds
      fixedParentIds.toGenes
    } catch {
      case e: Exception => {
        log.warn("No vaild gene file provided")
        GeneRDD(sc.emptyRDD[Gene], this.getSequenceDictionary)
      }
    }
  }

  /**
   * Collects all genes overlapping the specified region
   * @param region Region to filter genes by
   * @return JSON string of filtered genes
   */
  def getGeneArray(region: ReferenceRegion): Array[GeneJson] = {
    val genes = geneRDD.rdd.filter(g => !g.regions.filter(r => r.overlaps(region)).isEmpty)
    genes.flatMap(g => GeneJson(g)).collect
  }

  /**
   * Collects all genes overlapping the specified region
   * @param region Region to filter genes by
   * @return JSON string of filtered genes
   */
  def getGenes(region: ReferenceRegion): String = {
    write(getGeneArray(region))
  }

  def getSequenceDictionary: SequenceDictionary = dict

  def getReferenceString(region: ReferenceRegion): String = {
    try {
      val parsedRegion = ReferenceRegion(region.referenceName, region.start,
        VizUtils.getEnd(region.end, dict.apply(region.referenceName)))
      reference.getReferenceString(parsedRegion).toUpperCase()
    } catch {
      case e: Exception =>
        log.warn("requested reference region not found in sequence dictionary")
        ""
    }
  }
}