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

import java.io.{ FileNotFoundException, File }

import org.apache.parquet.filter2.dsl.Dsl._
import org.apache.parquet.filter2.predicate.FilterPredicate
import org.apache.parquet.io.api.Binary
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel
import org.bdgenomics.adam.models.{ ReferenceRegion, SequenceDictionary }
import org.bdgenomics.adam.projections.{ AlignmentRecordField, Projection }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.formats.avro.AlignmentRecord
import org.bdgenomics.mango.converters.GA4GHConverter
import org.bdgenomics.mango.core.util.VizUtils
import org.bdgenomics.mango.layout._
import org.bdgenomics.mango.tiling._
import org.bdgenomics.mango.util.Bookkeep
import org.bdgenomics.utils.intervalrdd.IntervalRDD
import org.bdgenomics.utils.misc.Logging
import org.ga4gh.{ GAReadAlignment, GASearchReadsResponse }

import scala.reflect.ClassTag

/**
 *
 * @param s SparkContext
 * @param chunkS chunkSize to size nodes in interval trees in IntervalRDD
 * @param d Sequence Dictionay calculated from reference
 * extends LazyMaterialization and KTiles
 * @see LazyMaterialization
 * @see KTiles
 */
class AlignmentRecordMaterialization(s: SparkContext,
                                     filePaths: List[String],
                                     d: SequenceDictionary,
                                     chunkS: Int) extends LazyMaterialization[AlignmentRecord, AlignmentRecordTile] with KTiles[AlignmentRecordTile] with Serializable with Logging {

  val dict = d
  @transient val sc = s
  @transient implicit val formats = net.liftweb.json.DefaultFormats

  val chunkSize = chunkS
  val prefetchSize = if (sc.isLocal) 3000 else 10000
  val bookkeep = new Bookkeep(prefetchSize)
  val files = filePaths
  def getStart = (ar: AlignmentRecord) => ar.getStart
  def toTile = (data: Iterable[(String, AlignmentRecord)], region: ReferenceRegion) => {
    AlignmentRecordTile(data, region)
  }
  def load = (region: ReferenceRegion, file: String) => AlignmentRecordMaterialization.load(sc, region, file)
  /**
   * Define layers and underlying data types
   */
  val rawLayer: Layer = L0
  val coverageLayer: Layer = L1

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
  def getCoverage(region: ReferenceRegion): Map[String, String] = {
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
  def get(region: ReferenceRegion, layerOpt: Option[Layer] = None): Map[String, String] = {
    val seqRecord = dict(region.referenceName)
    seqRecord match {
      case Some(_) => {
        val regionsOpt = bookkeep.getMaterializedRegions(region, files)
        if (regionsOpt.isDefined) {
          for (r <- regionsOpt.get) {
            put(r)
          }
        }
        val dataLayer: Layer = layerOpt.getOrElse(L0)
        val layers = List(dataLayer, coverageLayer)
        val data = getTiles(region, layers)
        val json = layers.map(layer => (layer, stringify(data.filter(_._1 == layer.id).flatMap(_._2), region, layer))).toMap
        json.get(dataLayer).get
      } case None => {
        throw new Exception("Not found in dictionary")
      }
    }
  }

  def stringify(data: RDD[(String, Iterable[Any])], region: ReferenceRegion, layer: Layer): Map[String, String] = {
    layer match {
      case `rawLayer`      => stringifyGA4GHAlignments(data, region)
      case `coverageLayer` => Coverage.stringifyCoverage(data, region)
      case _ => {
        log.warn("Layer for AlignmentRecordMaterialization not specified")
        Map.empty[String, String]
      }
    }
  }

  /**
   * Formats raw data from KLayeredTile to JSON. This is requied by KTiles
   * @param rdd RDD of (id, data) tuples
   * @param region region to futher filter by
   * @return JSONified data
   */
  def stringifyGA4GHAlignments(rdd: RDD[(String, Iterable[Any])], region: ReferenceRegion): Map[String, String] = {
    val data: Array[(String, Iterable[AlignmentRecord])] = rdd
      .mapValues(_.asInstanceOf[Iterable[AlignmentRecord]])
      .mapValues(r => {
        r.filter(ar => region.overlaps(ReferenceRegion(ar)))
      })
      .mapValues(r => r.filter(r => r.record.getStart <= region.end && r.record.getEnd >= region.start)).collect

    val flattened: Map[String, Array[AlignmentRecord]] = data.groupBy(_._1)
      .map(r => (r._1, r._2.flatMap(_._2)))

    val gaReads: Map[String, List[GAReadAlignment]] = flattened.mapValues(l => l.map(r => GA4GHConverter.toGAReadAlignment(r)).toList)

    gaReads.mapValues(v => {
      GASearchReadsResponse.newBuilder()
        .setAlignments(v)
        .build().toString
    })

  }

  /**
   *  Transparent to the user, should only be called by get if IntervalRDD.get does not return data
   * Fetches the data from disk, using predicates and range filtering
   * Then puts fetched data in the IntervalRDD, and calls multiget again, now with the data existing
   *
   * @param region ReferenceRegion in which data is retreived
   */
  override def put(region: ReferenceRegion) = {
    val seqRecord = dict(region.referenceName)
    if (seqRecord.isDefined) {
      val trimmedRegion = ReferenceRegion(region.referenceName, region.start, VizUtils.getEnd(region.end, seqRecord))
      var data: RDD[(String, AlignmentRecord)] = sc.emptyRDD[(String, AlignmentRecord)]

      // get alignment data for all samples
      files.map(fp => {
        // per sample data
        val k = LazyMaterialization.filterKeyFromFile(fp)
        val kdata = AlignmentRecordMaterialization.load(sc, trimmedRegion, fp).map(r => (k, r))
        data = data.union(kdata)
      })

      // group alignments by start w.r.t. chunksize
      val groupedRecords = data.groupBy(_._2.getStart / chunkSize)
        .map(r => {
          val max = r._2.map(_._2.getEnd).max
          val reg = ReferenceRegion(region.referenceName, r._1, max)
          (reg, r._2)
        })

      // map grouped alignments to tiles
      val tiles: RDD[(ReferenceRegion, AlignmentRecordTile)] =
        groupedRecords.map(r => (r._1, AlignmentRecordTile(r._2, r._1)))

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

  def apply(sc: SparkContext, files: List[String], dict: SequenceDictionary): AlignmentRecordMaterialization = {
    new AlignmentRecordMaterialization(sc, files, dict, 1000)
  }

  def apply[T: ClassTag, C: ClassTag](sc: SparkContext, files: List[String], dict: SequenceDictionary, chunkSize: Int): AlignmentRecordMaterialization = {
    new AlignmentRecordMaterialization(sc, files, dict, chunkSize)
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
    if (idxFile.exists()) {
      sc.loadIndexedBam(fp, region).rdd
    } else {
      throw new FileNotFoundException("bam index not provided")
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
    val proj = Projection(AlignmentRecordField.contigName, AlignmentRecordField.mapq, AlignmentRecordField.readName, AlignmentRecordField.start,
      AlignmentRecordField.end, AlignmentRecordField.sequence, AlignmentRecordField.cigar, AlignmentRecordField.readNegativeStrand, AlignmentRecordField.readPaired, AlignmentRecordField.recordGroupSample)
    sc.loadParquetAlignments(fp, predicate = Some(pred), projection = None).rdd
  }
}
