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
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel
import org.apache.spark.{ Logging, _ }
import org.bdgenomics.adam.models._
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.formats.avro.NucleotideContigFragment
import org.bdgenomics.mango.core.util.{ ResourceUtils, VizUtils }
import picard.sam.CreateSequenceDictionary

class ReferenceRDD(sc: SparkContext, referencePath: String) extends Serializable with Logging {

  var refRDD: RDD[NucleotideContigFragment] = null

  if (referencePath.endsWith(".fa") || referencePath.endsWith(".fasta") || referencePath.endsWith(".adam")) {
    setSequenceDictionary(referencePath)
    refRDD = sc.loadSequence(referencePath)
    if (!ResourceUtils.isLocal(referencePath, sc)) {
      refRDD.persist(StorageLevel.MEMORY_AND_DISK)
      log.info("Loaded reference file, size: ", refRDD.count)
    }
  } else {
    log.info("WARNING: Invalid reference file")
    println("WARNING: Invalid reference file")
  }

  val dict: SequenceDictionary = setSequenceDictionary(referencePath)

  def getSequenceDictionary: SequenceDictionary = dict

  def setSequenceDictionary(filePath: String): SequenceDictionary = {
    if (ResourceUtils.isLocal(filePath, sc)) {
      if (filePath.endsWith(".fa") || filePath.endsWith(".fasta")) {
        val createObj = new CreateSequenceDictionary
        val dict: SAMSequenceDictionary = createObj.makeSequenceDictionary(new File(filePath))
        SequenceDictionary(dict)
      } else if (filePath.endsWith(".adam")) {
        sc.adamDictionaryLoad[NucleotideContigFragment](filePath)
      } else {
        throw UnsupportedFileException("File type not supported")
      }
    } else {
      require(filePath.endsWith(".adam"), "To generate SequenceDictionary on remote cluster, must use adam files")
      sc.adamDictionaryLoad[NucleotideContigFragment](filePath)
    }
  }

  /**
   * Returns reference region string that is padded to encompass all reads for
   * mismatch calculation
   *
   * @param region: ReferenceRegion to be viewed
   * @return Option of Padded Reference
   */
  def getPaddedReference(region: ReferenceRegion, isPlaceholder: Boolean = false): (ReferenceRegion, Option[String]) = {
    val padding = 200
    val start = Math.max(0, region.start - padding)
    val end = VizUtils.getEnd(region.end, dict(region.referenceName))
    val paddedRegion = ReferenceRegion(region.referenceName, start, end)
    if (isPlaceholder) {
      val n = (end - start).toInt
      (paddedRegion, Option(List.fill(n)("N").mkString))
    } else {
      val reference = getReference(paddedRegion)
      (paddedRegion, reference)

    }
  }

  /**
   * Returns reference from reference RDD working set
   *
   * @param region: ReferenceRegion to be viewed
   * @return Option of Padded Reference
   */
  def getReference(region: ReferenceRegion): Option[String] = {
    val seqRecord = dict(region.referenceName)
    seqRecord match {
      case Some(_) => {
        val end: Long = VizUtils.getEnd(region.end, seqRecord)
        val newRegion = ReferenceRegion(region.referenceName, region.start, end)
        Option(refRDD.adamGetReferenceString(region).toUpperCase)
      } case None => {
        None
      }
    }
  }
}

