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

import edu.berkeley.cs.amplab.spark.intervalrdd.IntervalRDD
import htsjdk.samtools.SAMSequenceDictionary
import org.apache.parquet.filter2.dsl.Dsl._
import org.apache.parquet.filter2.predicate.FilterPredicate
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel
import org.apache.spark.{ Logging, _ }
import org.bdgenomics.adam.models._
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.formats.avro.NucleotideContigFragment
import org.bdgenomics.mango.core.util.{ ResourceUtils, VizUtils }
import org.bdgenomics.mango.tiling._
import picard.sam.CreateSequenceDictionary

class ReferenceMaterialization(sc: SparkContext,
                               referencePath: String) extends Tiles[ReferenceTile] with Serializable with Logging {

  var bookkeep = Array[String]()
  val chunkSize = 10000L // TODO
  var intRDD: IntervalRDD[ReferenceRegion, ReferenceTile] = null
  val dict = init

  def getSequenceDictionary: SequenceDictionary = dict

  def put(region: ReferenceRegion) = {
    // TODO: check if query in dict
    val pred: FilterPredicate = (BinaryColumn("contig.contigName") === (region.referenceName))
    val sequences: RDD[NucleotideContigFragment] = sc.loadParquetContigFragments(referencePath, predicate = Some(pred))
    val refRDD: IntervalRDD[ReferenceRegion, ReferenceTile] = IntervalRDD(sequences.map(r => (ReferenceRegion(r.getContig.getContigName, r.getFragmentStartPosition, r.getFragmentStartPosition + r.getFragmentLength), r.getFragmentSequence.toUpperCase)))
      .mapValues(r => (r._1, ReferenceTile(r._2)))

    // insert whole chromosome in strucutre
    if (intRDD == null)
      intRDD = refRDD
    else intRDD = intRDD.multiput(refRDD)
    intRDD.persist(StorageLevel.MEMORY_AND_DISK)
    bookkeep ++= Array(region.referenceName)

  }

  def getReferenceString(region: ReferenceRegion): String = {
    if (!bookkeep.contains(region.referenceName)) {
      put(region)
    }
    get(region)
  }

  def init: SequenceDictionary = {
    if (!(referencePath.endsWith(".fa") || referencePath.endsWith(".fasta") || referencePath.endsWith(".adam"))) {
      throw new UnsupportedFileException("WARNING: Invalid reference file")
    }
    val dictionary = setSequenceDictionary(referencePath)
    if (referencePath.endsWith(".fa") || referencePath.endsWith(".fasta")) {
      // load whole reference file
      val sequences: RDD[NucleotideContigFragment] = sc.loadSequences(referencePath)
      intRDD = IntervalRDD(sequences.map(r => (ReferenceRegion(r.getContig.getContigName, r.getFragmentStartPosition, r.getFragmentStartPosition + r.getFragmentLength), r.getFragmentSequence.toUpperCase)))
        .mapValues(r => (r._1, ReferenceTile(r._2)))
      intRDD.persist(StorageLevel.MEMORY_AND_DISK)
      bookkeep = dictionary.records.map(_.name).toArray
    }
    dictionary
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
      (paddedRegion, getRaw(paddedRegion))
    }
  }
}