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
import org.apache.spark.SparkContext
import org.bdgenomics.adam.models._
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.adam.rdd.contig.NucleotideContigFragmentRDD
import org.bdgenomics.mango.core.util.{ VizUtils, Utils }
import org.bdgenomics.utils.misc.Logging
import picard.sam.CreateSequenceDictionary

class AnnotationMaterialization(@transient sc: SparkContext,
                                referencePath: String) extends Serializable with Logging {

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
      (reference, sc.loadReferenceFile(referencePath, fragmentLength = 10000).sequences)
    } else
      throw new UnsupportedFileException("File Types supported for reference are fa, fasta and adam")

  def getSequenceDictionary: SequenceDictionary = dict

  def getReferenceString(region: ReferenceRegion): String = {
    try {
      val parsedRegion = ReferenceRegion(region.referenceName, region.start,
        VizUtils.getEnd(region.end, dict.apply(region.referenceName)))
      reference.extract(parsedRegion).toUpperCase()
    } catch {
      case e: Exception =>
        log.warn("requested reference region not found in sequence dictionary")
        ""
    }
  }
}