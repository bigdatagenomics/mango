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
package org.bdgenomics.mango.RDD

import java.io.File

import edu.berkeley.cs.amplab.spark.intervalrdd.IntervalRDD
import htsjdk.samtools.SAMSequenceDictionary
import net.liftweb.json.Serialization.write
import org.apache.spark.storage.StorageLevel
import org.apache.spark.{ Logging, _ }
import org.bdgenomics.adam.models._
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.formats.avro.NucleotideContigFragment
import org.bdgenomics.mango.core.util.{ ResourceUtils, VizUtils }
import org.bdgenomics.mango.layout.ConvolutionalSequence
import org.bdgenomics.mango.models.UnsupportedFileException
import org.bdgenomics.mango.tiling._
import picard.sam.CreateSequenceDictionary

class ReferenceRDD(sc: SparkContext, referencePath: String) extends LayeredTile with Serializable with Logging {

  val dict: SequenceDictionary = setSequenceDictionary(referencePath)
  val refRDD: IntervalRDD[ReferenceRegion, String] = init

  def getSequenceDictionary: SequenceDictionary = dict

  def init: IntervalRDD[ReferenceRegion, String] = {
    if (referencePath.endsWith(".fa") || referencePath.endsWith(".fasta") || referencePath.endsWith(".adam")) {
      val refRDD = IntervalRDD(sc.loadSequences(referencePath).map(r => (ReferenceRegion(r.getContig.getContigName, r.getFragmentStartPosition, r.getFragmentStartPosition + r.getFragmentLength), r.getFragmentSequence)))
      refRDD.persist(StorageLevel.MEMORY_AND_DISK)
      if (!ResourceUtils.isLocal(referencePath, sc)) {
        refRDD.persist(StorageLevel.MEMORY_AND_DISK)
        log.info("Loaded reference file, size: ", refRDD.count)
      }
      refRDD
    } else {
      log.info("WARNING: Invalid reference file")
      println("WARNING: Invalid reference file")
      null
    }
  }

  def setSequenceDictionary(filePath: String): SequenceDictionary = {
    if (ResourceUtils.isLocal(filePath, sc)) {
      if (filePath.endsWith(".fa") || filePath.endsWith(".fasta")) {
        val createObj = new CreateSequenceDictionary
        val dict: SAMSequenceDictionary = createObj.makeSequenceDictionary(new File(filePath))
        SequenceDictionary(dict)
      } else if (filePath.endsWith(".adam")) {
        sc.loadDictionary[NucleotideContigFragment](filePath)
      } else {
        throw UnsupportedFileException("File type not supported")
      }
    } else {
      require(filePath.endsWith(".adam"), "To generate SequenceDictionary on remote cluster, must use adam files")
      sc.loadDictionary[NucleotideContigFragment](filePath)
    }
  }

  /**
   * Returns reference region string that is padded to encompass all reads for
   * mismatch calculation
   *
   * @param region: ReferenceRegion to be viewed
   * @return Option of Padded Reference
   */
  def getPaddedReference(region: ReferenceRegion, isPlaceholder: Boolean = false): (ReferenceRegion, String) = {
    val padding = 200
    val start = Math.max(0, region.start - padding)
    val end = VizUtils.getEnd(region.end, dict(region.referenceName))
    val paddedRegion = ReferenceRegion(region.referenceName, start, end)
    if (isPlaceholder) {
      val n = (end - start).toInt
      (paddedRegion, List.fill(n)("N").mkString)
    } else {
      val reference = this.get(paddedRegion)
      (paddedRegion, reference)

    }
  }

  /**
   * Returns reference from reference RDD working set
   *
   * @param region: ReferenceRegion to be viewed
   * @return Option of Padded Reference
   */
  def getL0(region: ReferenceRegion, ids: Option[List[String]] = None): String = {
    val seqRecord = dict(region.referenceName)
    seqRecord match {
      case Some(_) => {
        val end: Long = VizUtils.getEnd(region.end, seqRecord)
        val newRegion = ReferenceRegion(region.referenceName, region.start, end)
        refRDD.filterByInterval(region).collect.map(_._2).reduce((s1, s2) => s1 + s2)
      }
      case None => {
        "N" * (region.end - region.start).toInt
      }
    }
  }

  def getConvolved(region: ReferenceRegion, ids: Option[List[String]] = None, patchSize: Int, stride: Int): String = {
    val str = getL0(ReferenceRegion(region.referenceName, region.start, region.end))
    println(str)
    ConvolutionalSequence.convolveSequence(str, patchSize, stride).foreach(r => print(r + " "))
    write(ConvolutionalSequence.convolveSequence(str, patchSize, stride))
  }

}