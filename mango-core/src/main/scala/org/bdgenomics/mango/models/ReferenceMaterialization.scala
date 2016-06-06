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
import org.apache.parquet.filter2.dsl.Dsl.{ BinaryColumn, _ }
import org.apache.parquet.filter2.predicate.FilterPredicate
import org.apache.spark._
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel
import org.bdgenomics.adam.models.{ ReferenceRegion, SequenceDictionary }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.formats.avro.NucleotideContigFragment
import org.bdgenomics.mango.core.util.ResourceUtils
import org.bdgenomics.mango.tiling.{ ReferenceTile, Tiles }
import org.bdgenomics.utils.intervalrdd.IntervalRDD
import picard.sam.CreateSequenceDictionary

class ReferenceMaterialization(sc: SparkContext,
                               referencePath: String,
                               chunkS: Int = 10000) extends Tiles[String, ReferenceTile] with Serializable with Logging {

  //Regex for splitting fragments to the chunk size specified above

  protected def tag = reflect.classTag[String]

  var bookkeep = Array[String]()
  val chunkSize = chunkS
  var intRDD: IntervalRDD[ReferenceRegion, ReferenceTile] = null
  val dict = init

  def getSequenceDictionary: SequenceDictionary = dict

  /*
   * Puts data into reference RDD
   *
   * @param region: ReferenceRegion. Loads chromosome from the specified region into reference RDD
   */
  def put(region: ReferenceRegion): Unit = {
    put(Some(region))
    bookkeep ++= Array(region.referenceName)
  }

  /*
   * Puts data into reference RDD
   *
   * @param region: Option[ReferenceRegion] if region is none,
   * loads whole reference file into rdd. Otherwise loads whole chromosome from region.
   */
  def put(region: Option[ReferenceRegion]): Unit = {
    // TODO: check if query in dict
    val pred: Option[FilterPredicate] =
      region match {
        case Some(_) => Some((BinaryColumn("contig.contigName") === (region.get.referenceName)))
        case None    => None
      }
    val sequences: RDD[NucleotideContigFragment] =
      if (referencePath.endsWith(".fa") || referencePath.endsWith(".fasta"))
        sc.loadSequences(referencePath)
      else if (referencePath.endsWith(".adam"))
        sc.loadParquetContigFragments(referencePath, predicate = pred)
      else
        throw new UnsupportedFileException("File Types supported for reference are fa, fasta and adam")

    val splitRegex = "(?<=\\G.{" + chunkSize + "})"
    val c = chunkSize

    // map sequences and divy fragment lengths by chunk size
    val fragments: RDD[(ReferenceRegion, Array[(String, Int)])] = sequences.map(r => (ReferenceRegion(r.getContig.getContigName, r.getFragmentStartPosition, r.getFragmentStartPosition + r.getFragmentLength),
      r.getFragmentSequence.toUpperCase.split(splitRegex).zipWithIndex))

    // map fragmented sequences to smaller referenceregions the size of chunksize
    val splitFragments: RDD[(ReferenceRegion, String)] = fragments.flatMap(r => r._2.map(x =>
      (ReferenceRegion(r._1.referenceName, r._1.start + x._2 * c, r._1.start + x._2 * c + x._1.length), x._1)))

    // convert to interval RDD
    val refRDD: IntervalRDD[ReferenceRegion, ReferenceTile] =
      IntervalRDD(splitFragments)
        .mapValues(r => ReferenceTile(r))

    // insert whole chromosome in structure
    if (intRDD == null)
      intRDD = refRDD
    else intRDD = intRDD.multiput(refRDD)
    intRDD.persist(StorageLevel.MEMORY_AND_DISK)

  }

  def getReferenceString(region: ReferenceRegion): String = {
    if (!bookkeep.contains(region.referenceName)) {
      put(region)
    }
    getTiles(region, true)
  }

  def init: SequenceDictionary = {
    if (!(referencePath.endsWith(".fa") || referencePath.endsWith(".fasta") || referencePath.endsWith(".adam"))) {
      throw new UnsupportedFileException("WARNING: Invalid reference file")
    }
    val dictionary = setSequenceDictionary(referencePath)

    // because fastas do not support predicate pushdown, must load all data into index
    if (referencePath.endsWith(".fa") || referencePath.endsWith(".fasta") || !sc.isLocal) {
      // load whole reference file
      put(None)
      bookkeep ++= dictionary.records.map(_.name)
    }
    dictionary
  }

  def stringifyRaw(data: RDD[String], region: ReferenceRegion): String = {
    val str = data.reduce(_ + _)
    trimSequence(str, region)
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

}