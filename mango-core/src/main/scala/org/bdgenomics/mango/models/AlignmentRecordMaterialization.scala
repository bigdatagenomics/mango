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

import edu.berkeley.cs.amplab.spark.intervalrdd._
import org.apache.parquet.filter2.dsl.Dsl._
import org.apache.parquet.filter2.predicate.FilterPredicate
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel
import org.apache.spark.{ Logging, _ }
import org.bdgenomics.adam.models.{ ReferenceRegion, SequenceDictionary }
import org.bdgenomics.adam.projections.{ AlignmentRecordField, Projection }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.formats.avro.AlignmentRecord
import org.bdgenomics.mango.RDD._
import org.bdgenomics.mango.core.util.SampleSize
import org.bdgenomics.mango.layout.{ CalculatedAlignmentRecord, MismatchLayout }
import org.bdgenomics.mango.util.Bookkeep

import scala.reflect.ClassTag

/*
 * Handles loading and tracking of data from persistent storage into memory for Alignment Records, and the corresponding
 * frequency and mismatches.
 * @see LazyMaterialization.scala
 */
class AlignmentRecordMaterialization(s: SparkContext,
                                     d: SequenceDictionary,
                                     parts: Int, chunkS: Int,
                                     refRDD: ReferenceRDD) extends LazyMaterialization[AlignmentRecord, CalculatedAlignmentRecord] with Serializable with Logging {

  val sc = s
  val dict = d
  val partitions = parts
  val chunkSize = chunkS
  val partitioner = setPartitioner
  val bookkeep = new Bookkeep(chunkSize)


  //  var freqRDD = new FrequencyRDD(sc, chunkSize)
  val sqlContext = new org.apache.spark.sql.SQLContext(sc)

  var freq: FrequencyMaterialization = new FrequencyMaterialization(sc, dict, chunkSize)
  /*
   * Determines granularity of frequency calculations. Funtion of partitions and region to be viewed
   */
  val sampleSize = new SampleSize(partitions)

  /*
   * Gets Frequency over a given region for each specified sample
   *
   * @param region: ReferenceRegion to query over
   * @param sampleIds: List[String] all samples to fetch frequency
   * @param sampleSize: number of frequency values to return
   *
   * @return Map[String, Iterable[FreqJson]] Map of [SampleId, Iterable[FreqJson]] which stores each base and its
   * cooresponding frequency.
   */

  def getFrequency(region: ReferenceRegion, sampleIds: List[String], binSize: Int = 1000): Array[String] = {
    freq.multiget(region, sampleIds).collect
  }

  /*
   * Override files from LazyMaterialization
   */
  override def loadSample(filePath: String, sampleId: Option[String] = None) {
    val sample =
      sampleId match {
        case Some(_) => sampleId.get
        case None    => filePath
      }

    fileMap += ((sample, filePath))
    println(s"loading sample ${sample}")
    freq.loadSample(filePath, sample)

  }

  override def loadADAMSample(filePath: String): String = {
    val sample = getFileReference(filePath)
    fileMap += ((sample, filePath))
    freq.loadSample(filePath, sample)
    sample
  }

  override def getFileReference(fp: String): String = {
    sc.loadParquetAlignments(fp).recordGroups.recordGroups.head.sample
  }

  override def loadFromFile(region: ReferenceRegion, k: String): RDD[AlignmentRecord] = {
    if (!fileMap.containsKey(k)) {
      log.error("Key not in FileMap")
      null
    }
    val fp = fileMap(k)
    AlignmentRecordMaterialization.loadAlignmentData(sc: SparkContext, region, fp)
  }

  override def get(region: ReferenceRegion, k: String): Option[IntervalRDD[ReferenceRegion, CalculatedAlignmentRecord]] = {
    multiget(region, List(k))
  }

  /* If the RDD has not been initialized, initialize it to the first get request
	* Gets the data for an interval for the file loaded by checking in the bookkeeping tree.
	* If it exists, call get on the IntervalRDD
	* Otherwise call put on the sections of data that don't exist
	* Here, ks, is an option of list of personids (String)
	*/
  override def multiget(region: ReferenceRegion, ks: List[String]): Option[IntervalRDD[ReferenceRegion, CalculatedAlignmentRecord]] = {
    val seqRecord = dict(region.referenceName)
    val regionsOpt = bookkeep.getMaterializedRegions(region, ks)
    seqRecord match {
      case Some(_) => {
        regionsOpt match {
          case Some(_) => {
            for (r <- regionsOpt.get) {
              put(r, ks)
            }
          } case None => {
            // DO NOTHING
          }
        }
        // TODO: return data to multiget instead of making subsequent call to RDD
        Option(intRDD.filterByInterval(region))
      } case None => {
        None
      }
    }
  }
  /**
   *  Transparent to the user, should only be called by get if IntervalRDD.get does not return data
   * Fetches the data from disk, using predicates and range filtering
   * Then puts fetched data in the IntervalRDD, and calls multiget again, now with the data existing
   *
   * @param region ReferenceRegion in which data is retreived
   * @param ks to be retreived
   */
  override def put(region: ReferenceRegion, ks: List[String]) = {
    val seqRecord = dict(region.referenceName)
    seqRecord match {
      case Some(_) => {
        val (reg, ref) = refRDD.getPaddedReference(region)

        var alignments: RDD[AlignmentRecord] = sc.emptyRDD[AlignmentRecord]
        ks.map(k => {
          val data = loadFromFile(region, k)
          alignments = alignments.union(data)
        })

        // key data by ReferenceRegion
        val keyedAlignments = alignments
          .map(r => (ReferenceRegion(r), r))
          .partitionBy(partitioner)

        // map to CalculatedAlignmentRecord
        val data = keyedAlignments.map(r => (r._1, CalculatedAlignmentRecord(r._2, MismatchLayout(r._2, ref.get, reg))))
          .filter(r => r._2.mismatches.size > 0)

        // insert into IntervalRDD
        if (intRDD == null) {
          intRDD = IntervalRDD(data)
          intRDD.persist(StorageLevel.MEMORY_AND_DISK)
        } else {
          intRDD = intRDD.multiput(data)
          intRDD.persist(StorageLevel.MEMORY_AND_DISK)
        }
        bookkeep.rememberValues(region, ks)
      }
      case None => {
      }
    }
  }
}

object AlignmentRecordMaterialization {

  def apply(sc: SparkContext, dict: SequenceDictionary, partitions: Int, refRDD: ReferenceRDD, filterEmptyMismatches: Boolean = true): AlignmentRecordMaterialization = {
    new AlignmentRecordMaterialization(sc, dict, partitions, 1000, refRDD)
  }

  def apply[T: ClassTag, C: ClassTag](sc: SparkContext, dict: SequenceDictionary, partitions: Int, chunkSize: Int, refRDD: ReferenceRDD, filterEmptyMismatches: Boolean = true): AlignmentRecordMaterialization = {
    new AlignmentRecordMaterialization(sc, dict, partitions, chunkSize, refRDD)
  }

  def loadAlignmentData(sc: SparkContext, region: ReferenceRegion, fp: String): RDD[AlignmentRecord] = {
    if (fp.endsWith(".adam")) loadAdam(sc, region, fp)
    else if (fp.endsWith(".sam") || fp.endsWith(".bam")) {
      AlignmentRecordMaterialization.loadFromBam(sc, region, fp)
    } else {
      throw UnsupportedFileException("File type not supported")
      null
    }
  }
  /*
 * Loads data from bam files (indexed or unindexed) from persistent storage
 *
 * @param region: ReferenceRegion to load AlignmentRecords from
 * @param fp: String bam file pointer to load records from
 *
 * @return RDD[AlignmentRecord] records overlapping region
 */
  def loadFromBam(sc: SparkContext, region: ReferenceRegion, fp: String): RDD[AlignmentRecord] = {
    val idxFile: File = new File(fp + ".bai")
    if (!idxFile.exists()) {
      sc.loadBam(fp).rdd.filterByOverlappingRegion(region)
    } else {
      sc.loadIndexedBam(fp, region)
    }
  }

  def loadAdam(sc: SparkContext, region: ReferenceRegion, fp: String): RDD[AlignmentRecord] = {
    val pred: FilterPredicate = ((LongColumn("end") >= region.start) && (LongColumn("start") <= region.end) && (BinaryColumn("contigName") === (region.referenceName)))
    val proj = Projection(AlignmentRecordField.contigName, AlignmentRecordField.mapq, AlignmentRecordField.readName, AlignmentRecordField.start,
      AlignmentRecordField.end, AlignmentRecordField.sequence, AlignmentRecordField.cigar, AlignmentRecordField.readNegativeStrand, AlignmentRecordField.readPaired, AlignmentRecordField.recordGroupSample)
    sc.loadParquetAlignments(fp, predicate = Some(pred), projection = Some(proj))
  }

}
