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
import htsjdk.samtools.{ SAMRecord, SamReader, SamReaderFactory }
import net.liftweb.json.Serialization.write
import org.apache.parquet.filter2.dsl.Dsl._
import org.apache.parquet.filter2.predicate.FilterPredicate
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel
import org.apache.spark.{ Logging, _ }
import org.bdgenomics.adam.models.{ ReferenceRegion, SequenceDictionary }
import org.bdgenomics.adam.projections.{ AlignmentRecordField, Projection }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.formats.avro.AlignmentRecord
import org.bdgenomics.mango.core.util.{ ResourceUtils, VizUtils }
import org.bdgenomics.mango.layout.{ MutationCount, CalculatedAlignmentRecord }
import org.bdgenomics.mango.tiling.{ AlignmentRecordTile, KTiles }
import org.bdgenomics.mango.util.Bookkeep

import scala.reflect.ClassTag

/*
 * Handles loading and tracking of data from persistent storage into memory for Alignment Records, and the corresponding
 * frequency and mismatches.
 * @see LazyMaterialization.scala
 */
class AlignmentRecordMaterialization(s: SparkContext,
                                     d: SequenceDictionary,
                                     chunkS: Int,
                                     refRDD: ReferenceMaterialization) extends LazyMaterialization[AlignmentRecord, AlignmentRecordTile] with KTiles[AlignmentRecordTile] with Serializable with Logging {

  protected def tag = reflect.classTag[Iterable[AlignmentRecord]]
  val sc = s
  val dict = d
  val chunkSize = chunkS
  val partitioner = setPartitioner
  val bookkeep = new Bookkeep(chunkSize)

  //  var freqRDD = new FrequencyRDD(sc, chunkSize)
  val sqlContext = new org.apache.spark.sql.SQLContext(sc)

  var freq: FrequencyMaterialization = new FrequencyMaterialization(sc, dict, chunkSize)

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

  def getFrequency(region: ReferenceRegion, sampleIds: List[String]): String = {
    freq.get(region, sampleIds)
  }

  def init(readsPaths: List[String]): List[String] = {
    var sampNamesBuffer = new scala.collection.mutable.ListBuffer[String]
    for (readsPath <- readsPaths) {
      if (ResourceUtils.isLocal(readsPath, sc)) {
        if (readsPath.endsWith(".bam") || readsPath.endsWith(".sam")) {
          val srf: SamReaderFactory = SamReaderFactory.make()
          val samReader: SamReader = srf.open(new File(readsPath))
          val rec: SAMRecord = samReader.iterator().next()
          val sample = rec.getReadGroup.getSample
          sampNamesBuffer += sample
          loadSample(readsPath, Option(sample))
        } else if (readsPath.endsWith(".adam")) {
          sampNamesBuffer += loadADAMSample(readsPath)
        } else {
          log.info("WARNING: Invalid input for reads file on local fs")
          println("WARNING: Invalid input for reads file on local fs")
        }
      } else {
        if (readsPath.endsWith(".adam")) {
          sampNamesBuffer += loadADAMSample(readsPath)
        } else {
          log.info("WARNING: Invalid input for reads file on remote fs")
          println("WARNING: Invalid input for reads file on remote fs")
        }
      }
    }
    sampNamesBuffer.toList
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
    try {
      val fp = fileMap(k)
      AlignmentRecordMaterialization.loadAlignmentData(sc: SparkContext, region, fp)
    } catch {
      case e: NoSuchElementException => {
        throw e
      }
    }
  }

  def get(region: ReferenceRegion, k: String): String = {
    multiget(region, List(k)).get(k).get
  }

  /* If the RDD has not been initialized, initialize it to the first get request
    * Gets the data for an interval for the file loaded by checking in the bookkeeping tree.
    * If it exists, call get on the IntervalRDD
    * Otherwise call put on the sections of data that don't exist
    * Here, ks, is an option of list of personids (String)
    */
  def multiget(region: ReferenceRegion, ks: List[String], isRaw: Boolean = false): Map[String, String] = {
    val seqRecord = dict(region.referenceName)
    seqRecord match {
      case Some(_) => {
        val regionsOpt = bookkeep.getMaterializedRegions(region, ks)
        if (regionsOpt.isDefined) {
          for (r <- regionsOpt.get) {
            println(r)
            put(r, ks)
          }
        }
        getTiles(region, ks, isRaw)
      } case None => {
        throw new Exception("Not found in dictionary")
      }
    }
  }

  def stringifyL0(rdd: RDD[(String, Iterable[Any])], region: ReferenceRegion): Map[String, String] = {
    implicit val formats = net.liftweb.json.DefaultFormats

    val data = rdd
      .mapValues(_.asInstanceOf[Iterable[CalculatedAlignmentRecord]])
      .mapValues(r => r.filter(r => r.record.getStart <= region.end && r.record.getEnd >= region.start)).collect

    data.map(r => (r._1, write(r._2))).toMap
  }

  def stringifyL1(rdd: RDD[(String, Iterable[Any])], region: ReferenceRegion): Map[String, String] = {
    implicit val formats = net.liftweb.json.DefaultFormats
    val binSize = region.length / 1000

    val data = rdd
      .mapValues(_.asInstanceOf[Iterable[MutationCount]])
      .mapValues(r => r.filter(r => r.refCurr <= region.end && r.refCurr >= region.start))
      .map(r => (r._1, r._2.filter(r => r.refCurr % binSize == 0)))
      .collect

    data.map(r => (r._1, write(r._2))).toMap
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
    if (seqRecord.isDefined) {
      val trimmedRegion = ReferenceRegion(region.referenceName, region.start, VizUtils.getEnd(region.end, seqRecord))
      val (reg, ref) = refRDD.getPaddedReference(trimmedRegion)
      var data: RDD[AlignmentRecord] = sc.emptyRDD[AlignmentRecord]

      ks.map(k => {
        val kdata = loadFromFile(trimmedRegion, k)
        data = data.union(kdata)
        //        layer1 += ((k, ConvolutionalSequence.convolveCalculatedRDD(trimmedRegion, ref, data, L1.patchSize, L1.stride)))
      })

      // divide regions by chunksize
      val regions: List[ReferenceRegion] = Bookkeep.unmergeRegions(region, chunkSize)

      // for each chunk, filter groupedRecords by start,end and make into tile
      // TODO: this does not account for overlapping data crossing 2 borders of tiles
      val c = chunkSize
      val groupedRecords: RDD[(ReferenceRegion, Iterable[AlignmentRecord])] =
        data.map(r => (ReferenceRegion(r.record.getContigName, r.record.getStart / c * c, r.record.getStart / c * c + c - 1), r))
          .groupBy(_._1)
          .filter(r => regions.contains(r._1))
          .map(r => (r._1, r._2.map(_._2)))

      val tiles = groupedRecords.map(r => (r._1, new AlignmentRecordTile(r._2, ref, reg)))

      // insert into IntervalRDD
      if (intRDD == null) {
        intRDD = IntervalRDD(tiles)
        intRDD.persist(StorageLevel.MEMORY_AND_DISK)
      } else {
        val t = intRDD
        intRDD = intRDD.multiput(tiles)
        // TODO: can we do this incrementally instead?
        t.unpersist(true)
        intRDD.persist(StorageLevel.MEMORY_AND_DISK)
      }
      bookkeep.rememberValues(region, ks)
    }
  }
}

object AlignmentRecordMaterialization {

  def apply(sc: SparkContext, dict: SequenceDictionary, refRDD: ReferenceMaterialization): AlignmentRecordMaterialization = {
    new AlignmentRecordMaterialization(sc, dict, 1000, refRDD)
  }

  def apply[T: ClassTag, C: ClassTag](sc: SparkContext, dict: SequenceDictionary, chunkSize: Int, refRDD: ReferenceMaterialization): AlignmentRecordMaterialization = {
    new AlignmentRecordMaterialization(sc, dict, chunkSize, refRDD)
  }

  def loadAlignmentData(sc: SparkContext, region: ReferenceRegion, fp: String): RDD[AlignmentRecord] = {
    if (fp.endsWith(".adam")) loadAdam(sc, region, fp)
    else if (fp.endsWith(".sam") || fp.endsWith(".bam")) {
      AlignmentRecordMaterialization.loadFromBam(sc, region, fp)
    } else {
      throw UnsupportedFileException("File type not supported")
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
