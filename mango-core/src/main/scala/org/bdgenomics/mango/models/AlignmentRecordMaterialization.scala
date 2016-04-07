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
import org.bdgenomics.adam.models.{ RecordGroupDictionary, ReferenceRegion, SequenceDictionary }
import org.bdgenomics.adam.projections.{ AlignmentRecordField, Projection }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.adam.rdd.read.AlignmentRecordRDD
import org.bdgenomics.formats.avro.AlignmentRecord
import org.bdgenomics.mango.layout.{ CalculatedAlignmentRecord, MismatchLayout }

import scala.reflect.ClassTag

class AlignmentRecordMaterialization(s: SparkContext, d: SequenceDictionary, parts: Int, chunkS: Long, refRDD: ReferenceRDD) extends LazyMaterialization[AlignmentRecord, CalculatedAlignmentRecord] with Serializable with Logging {

  val sc = s
  val dict = d
  val partitions = parts
  val chunkSize = chunkS
  val partitioner = setPartitioner

  override def loadAdam(region: ReferenceRegion, fp: String): RDD[(ReferenceRegion, AlignmentRecord)] = {
    val pred: FilterPredicate = ((LongColumn("end") >= region.start) && (LongColumn("start") <= region.end) && (BinaryColumn("contigName") === (region.referenceName)))
    val proj = Projection(AlignmentRecordField.contigName, AlignmentRecordField.mapq, AlignmentRecordField.readName, AlignmentRecordField.start,
      AlignmentRecordField.end, AlignmentRecordField.sequence, AlignmentRecordField.cigar, AlignmentRecordField.readNegativeStrand, AlignmentRecordField.readPaired, AlignmentRecordField.recordGroupSample)
    val alignedReadRDD: AlignmentRecordRDD = sc.loadParquetAlignments(fp, predicate = Some(pred), projection = Some(proj))
    alignedReadRDD.rdd.map(r => (ReferenceRegion(r), r))
  }

  override def getFileReference(fp: String): String = {
    // TODO: most efficient predicate
    val pred: FilterPredicate = ((LongColumn("end") === 0L) && (LongColumn("start") === 0L))
    sc.loadParquetAlignments(fp, predicate = Some(pred)).recordGroups.recordGroups.head.sample
  }

  def loadFromBam(region: ReferenceRegion, fp: String): RDD[(ReferenceRegion, AlignmentRecord)] = {
    val idxFile: File = new File(fp + ".bai")
    if (!idxFile.exists()) {
      sc.loadBam(fp).rdd.filterByOverlappingRegion(region).map(r => (ReferenceRegion(r), r))
    } else {
      sc.loadIndexedBam(fp, region).map(r => (ReferenceRegion(r), r))
    }
  }

  override def loadFromFile(region: ReferenceRegion, k: String): RDD[(ReferenceRegion, AlignmentRecord)] = {
    if (!fileMap.containsKey(k)) {
      log.error("Key not in FileMap")
      null
    }
    val fp = fileMap(k)
    val file: File = new File(fp)
    val data: RDD[(ReferenceRegion, AlignmentRecord)] =
      if (fp.endsWith(".adam")) {
        loadAdam(region, fp)
      } else if (fp.endsWith(".sam") || fp.endsWith(".bam")) {
        loadFromBam(region, fp)
      } else {
        throw UnsupportedFileException("File type not supported")
        null
      }
    data.partitionBy(partitioner)
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
    val regionsOpt = getMaterializedRegions(region, ks)
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
        ks.map(k => {
          val data = loadFromFile(region, k).map(r => (r._1, CalculatedAlignmentRecord(r._2, MismatchLayout(r._2, ref.get, reg))))
          if (intRDD == null) {
            intRDD = IntervalRDD(data)
            intRDD.persist(StorageLevel.MEMORY_AND_DISK)
          } else {
            intRDD = intRDD.multiput(data)
            intRDD.persist(StorageLevel.MEMORY_AND_DISK)
          }
        })
        rememberValues(region, ks)
      }
      case None => {
      }
    }
  }
}

object AlignmentRecordMaterialization {

  def apply(sc: SparkContext, dict: SequenceDictionary, partitions: Int, refRDD: ReferenceRDD): AlignmentRecordMaterialization = {
    new AlignmentRecordMaterialization(sc, dict, partitions, 1000, refRDD)
  }

  def apply[T: ClassTag, C: ClassTag](sc: SparkContext, dict: SequenceDictionary, partitions: Int, chunkSize: Long, refRDD: ReferenceRDD): AlignmentRecordMaterialization = {
    new AlignmentRecordMaterialization(sc, dict, partitions, chunkSize, refRDD)
  }

}
