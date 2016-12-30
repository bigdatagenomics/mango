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

import org.apache.spark.SparkContext
import org.bdgenomics.adam.models._
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.adam.util.{ IndexedFastaFile, ReferenceFile }
import org.bdgenomics.mango.core.util.VizUtils
import org.bdgenomics.utils.misc.Logging

/**
 * Materializes reference files used to view reference in Genome Browser. Supported files
 * include 2bit, fasta, fa, and NucleotideContigFragmentRDDs in ADAM format. Note that
 * 2bit files must be saved locally, while other formats can be accessed through hdfs.
 * Note: paths must be absolute.
 *
 * @param sc SparkContext
 * @param referencePath path to reference
 */
class ReferenceMaterialization(@transient sc: SparkContext,
                               referencePath: String) extends Serializable with Logging {

  // required to parse json
  @transient implicit val formats = net.liftweb.json.DefaultFormats

  // keeps track of loaded chromsomes.
  var bookkeep = Array[String]()

  // length of reference strings to load.
  val fragmentLength = 10000

  // set and name interval rdd
  val reference: ReferenceFile =
    if (referencePath.endsWith(".2bit")) {
      sc.loadReferenceFile(referencePath, fragmentLength)
    } else if (referencePath.endsWith(".fa") || referencePath.endsWith(".fasta")) {
      IndexedFastaFile(sc, referencePath)
    } else {
      sc.loadSequences(referencePath, fragmentLength = fragmentLength)
    }

  def getSequenceDictionary: SequenceDictionary = reference.sequences

  def getReferenceString(region: ReferenceRegion): String = {
    try {
      val parsedRegion = ReferenceRegion(region.referenceName, VizUtils.getStart(region.start),
        VizUtils.getEnd(region.end, reference.sequences.apply(region.referenceName)))
      reference.extract(parsedRegion).toUpperCase()
    } catch {
      case e: Exception =>
        log.warn(s"requested reference region not found in sequence dictionary: ${e.getMessage}")
        ""
    }
  }
}