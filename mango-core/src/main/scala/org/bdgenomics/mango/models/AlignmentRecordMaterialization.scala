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

import net.liftweb.json.Serialization.write
import org.apache.parquet.filter2.dsl.Dsl._
import org.apache.parquet.filter2.predicate.FilterPredicate
import org.apache.parquet.io.api.Binary
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel
import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.adam.projections.{ AlignmentRecordField, Projection }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.formats.avro.AlignmentRecord
import org.bdgenomics.mango.core.util.VizUtils
import org.bdgenomics.mango.layout._
import org.bdgenomics.mango.tiling._
import org.bdgenomics.mango.util.Bookkeep
import org.bdgenomics.utils.intervalrdd.IntervalRDD
import org.bdgenomics.utils.misc.Logging

import scala.reflect.ClassTag

/**
 *
 * @param s SparkContext
 * @param chunkS chunkSize to size nodes in interval trees in IntervalRDD
 * @param refRDD reference RDD to get reference string from for mismatch computations
 * extends LazyMaterialization and KTiles
 * @see LazyMaterialization
 * @see KTiles
 */
class AlignmentRecordMaterialization(s: SparkContext,
                                     filePaths: List[String],
                                     chunkS: Int,
                                     refRDD: ReferenceMaterialization) extends LazyMaterialization[AlignmentRecord, AlignmentRecordTile] with KTiles[AlignmentRecordTile] with Serializable with Logging {

  protected def tag = reflect.classTag[Iterable[AlignmentRecord]]
  val dict = refRDD.dict
  val sc = s
  val chunkSize = chunkS
  val prefetchSize = if (sc.isLocal) 3000 else 10000
  val bookkeep = new Bookkeep(prefetchSize)
  val files = filePaths

  /**
   * Define layers and underlying data types
   */
  val rawLayer: Layer = L0
  val mismatchLayer: Layer = L1
  val coverageLayer: Layer = L2

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
  def getFrequency(region: ReferenceRegion): String = {
    // TODO: check if in RDD
    get(region, Some(coverageLayer))
  }

  /**
   * Gets data for multiple keys.
   * If the RDD has not been initialized, initialize it to the first get request
   * Gets the data for an interval for the file loaded by checking in the bookkeeping tree.
   * If it exists, call get on the IntervalRDD
   * Otherwise call put on the sections of data that don't exist
   * Here, ks, is a list of personids (String)
   * @param region: ReferenceRegion to fetch
   * @param layerOpt: Option to force data retrieval from specific layer
   * @return JSONified data
   */
  def get(region: ReferenceRegion, layerOpt: Option[Layer] = None): String = {
    implicit val formats = net.liftweb.json.DefaultFormats
    val seqRecord = dict(region.referenceName)
    seqRecord match {
      case Some(_) => {
        val regionsOpt = bookkeep.getMaterializedRegions(region, files)
        if (regionsOpt.isDefined) {
          for (r <- regionsOpt.get) {
            put(r)
          }
        }
        val dataLayer: Layer = layerOpt.getOrElse(getLayer(region))
        val layers = getTiles(region, List(coverageLayer, dataLayer))
        val coverage = layers.get(coverageLayer).get
        val reads = layers.get(dataLayer).get
        write(Map("coverage" -> coverage, "reads" -> reads))
      } case None => {
        throw new Exception("Not found in dictionary")
      }
    }
  }

  def getAlignments(region: ReferenceRegion, ks: List[String]): RDD[AlignmentRecord] = {
    val seqRecord = dict(region.referenceName)
    seqRecord match {
      case Some(_) => {
        val regionsOpt = bookkeep.getMaterializedRegions(region, ks)
        if (regionsOpt.isDefined) {
          for (r <- regionsOpt.get) {
            put(r, ks)
          }
        }
        getRaw(region, ks).map(r => r.asInstanceOf[CalculatedAlignmentRecord])
          .map(_.record)
      } case None => {
        throw new Exception("Not found in dictionary")
      }
    }
  }

  def stringify(data: RDD[(String, Iterable[Any])], region: ReferenceRegion, layer: Layer): String = {
    layer match {
      case `rawLayer`      => stringifyRawAlignments(data, region)
      case `mismatchLayer` => stringifyPointMismatches(data, region)
      case `coverageLayer` => Coverage.stringifyCoverage(data, region, chunkSize)
      case _               => ""
    }
  }

  /**
   * Formats raw data from KLayeredTile to JSON. This is requied by KTiles
   * @param rdd RDD of (id, data) tuples
   * @param region region to futher filter by
   * @return JSONified data
   */
  def stringifyRawAlignments(rdd: RDD[(String, Iterable[Any])], region: ReferenceRegion): String = {
    implicit val formats = net.liftweb.json.DefaultFormats

    val data: Array[(String, Iterable[CalculatedAlignmentRecord])] = rdd
      .mapValues(_.asInstanceOf[Iterable[CalculatedAlignmentRecord]])
      .mapValues(r => r.filter(r => r.record.getStart <= region.end && r.record.getEnd >= region.start)).collect

    val flattened: Map[String, Array[CalculatedAlignmentRecord]] = data.groupBy(_._1)
      .map(r => (r._1, r._2.flatMap(_._2)))

    // write map of (key, data)
    write(AlignmentRecordLayout(flattened))
  }

  /**
   * Formats layer 1 data from KLayeredTile to JSON. This is requied by KTiles
   * @param rdd RDD of (id, data) tuples
   * @param region region to futher filter by
   * @return JSONified data
   */
  def stringifyPointMismatches(rdd: RDD[(String, Iterable[Any])], region: ReferenceRegion): String = {
    implicit val formats = net.liftweb.json.DefaultFormats
    val binSize = VizUtils.getBinSize(region)

    val data = rdd
      .mapValues(_.asInstanceOf[Iterable[MutationCount]])
      .mapValues(r => r.filter(r => r.refCurr <= region.end && r.refCurr >= region.start))
      .mapValues(r => r.toList.distinct)
      .map(r => (r._1, r._2.filter(r => r.refCurr % binSize == 0)))
      .collect

    val flattened: Map[String, Array[MutationCount]] = data.groupBy(_._1)
      .map(r => (r._1, r._2.flatMap(_._2)))

    write(flattened)
  }

  /**
   *  Transparent to the user, should only be called by get if IntervalRDD.get does not return data
   * Fetches the data from disk, using predicates and range filtering
   * Then puts fetched data in the IntervalRDD, and calls multiget again, now with the data existing
   *
   * @param region ReferenceRegion in which data is retreived
   */
  def put(region: ReferenceRegion) = {
    val seqRecord = dict(region.referenceName)
    if (seqRecord.isDefined) {
      val trimmedRegion = ReferenceRegion(region.referenceName, region.start, VizUtils.getEnd(region.end, seqRecord))
      val reference = refRDD.getReferenceString(trimmedRegion)
      var data: RDD[AlignmentRecord] = sc.emptyRDD[AlignmentRecord]

      // divide regions by chunksize
      val regions: List[ReferenceRegion] = Bookkeep.unmergeRegions(region, chunkSize)

      // get alignment data for all samples
      files.map(fp => {
        // per sample data
        val kdata = AlignmentRecordMaterialization.load(sc, trimmedRegion, fp)
        data = data.union(kdata)
      })

      var mappedRecords: RDD[(ReferenceRegion, AlignmentRecord)] = sc.emptyRDD[(ReferenceRegion, AlignmentRecord)]

      // for all regions, filter by that region and create AlignmentRecordTile
      regions.foreach(r => {
        val grouped = data.filter(ar => r.overlaps(ReferenceRegion(ar))).map(ar => (r, ar))
        mappedRecords = mappedRecords.union(grouped)
      })

      val groupedRecords: RDD[(ReferenceRegion, Iterable[AlignmentRecord])] =
        mappedRecords
          .groupBy(_._1)
          .map(r => (r._1, r._2.map(_._2)))
      val tiles: RDD[(ReferenceRegion, AlignmentRecordTile)] = groupedRecords.map(r => (r._1, AlignmentRecordTile(r._2, reference, r._1)))

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
      bookkeep.rememberValues(region, files)
    }
  }
}

object AlignmentRecordMaterialization {

  def apply(sc: SparkContext, files: List[String], refRDD: ReferenceMaterialization): AlignmentRecordMaterialization = {
    new AlignmentRecordMaterialization(sc, files, 1000, refRDD)
  }

  def apply[T: ClassTag, C: ClassTag](sc: SparkContext, files: List[String], chunkSize: Int, refRDD: ReferenceMaterialization): AlignmentRecordMaterialization = {
    new AlignmentRecordMaterialization(sc, files, chunkSize, refRDD)
  }

  /**
   * Loads alignment data from bam, sam and ADAM file formats
   * @param sc SparkContext
   * @param region Region to load
   * @param fp filepath to load from
   * @return RDD of data from the file over specified ReferenceRegion
   */
  def load(sc: SparkContext, region: ReferenceRegion, fp: String): RDD[AlignmentRecord] = {
    if (fp.endsWith(".adam")) loadAdam(sc, region, fp)
    else if (fp.endsWith(".sam") || fp.endsWith(".bam")) {
      AlignmentRecordMaterialization.loadFromBam(sc, region, fp)
    } else {
      throw UnsupportedFileException("File type not supported")
    }
  }

  /**
   * Loads data from bam files (indexed or unindexed) from persistent storage
   * @param sc SparkContext
   * @param region Region to load
   * @param fp filepath to load from
   * @return RDD of data from the file over specified ReferenceRegion
   */
  def loadFromBam(sc: SparkContext, region: ReferenceRegion, fp: String): RDD[AlignmentRecord] = {
    val idxFile: File = new File(fp + ".bai")
    if (!idxFile.exists()) {
      sc.loadBam(fp).rdd.filterByOverlappingRegion(region)
    } else {
      sc.loadIndexedBam(fp, region)
    }
  }

  /**
   * Loads ADAM data using predicate pushdowns
   * @param sc SparkContext
   * @param region Region to load
   * @param fp filepath to load from
   * @return RDD of data from the file over specified ReferenceRegion
   */
  def loadAdam(sc: SparkContext, region: ReferenceRegion, fp: String): RDD[AlignmentRecord] = {
    val name = Binary.fromString(region.referenceName)
    val pred: FilterPredicate = ((LongColumn("end") >= region.start) && (LongColumn("start") <= region.end) && (BinaryColumn("contigName") === name))
    // TODO: modify projection to contain group
    val proj = Projection(AlignmentRecordField.contigName, AlignmentRecordField.mapq, AlignmentRecordField.readName, AlignmentRecordField.start,
      AlignmentRecordField.end, AlignmentRecordField.sequence, AlignmentRecordField.cigar, AlignmentRecordField.readNegativeStrand, AlignmentRecordField.readPaired, AlignmentRecordField.recordGroupSample)
    sc.loadParquetAlignments(fp, predicate = Some(pred), projection = None)
  }

}
