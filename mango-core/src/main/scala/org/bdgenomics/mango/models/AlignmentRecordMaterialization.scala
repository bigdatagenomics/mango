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
import org.bdgenomics.adam.models.{ ReferenceRegion, SequenceDictionary }
import org.bdgenomics.adam.projections.{ AlignmentRecordField, Projection }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.adam.rdd.read.AlignmentRecordRDD
import org.bdgenomics.formats.avro.AlignmentRecord
import org.bdgenomics.mango.converters.GA4GHConverter
import org.bdgenomics.mango.layout.PositionCount
import org.bdgenomics.utils.misc.Logging
import org.ga4gh.{ GAReadAlignment, GASearchReadsResponse }
import net.liftweb.json.Serialization._
import scala.collection.JavaConversions._

/**
 *
 * @param s SparkContext
 * @param dict Sequence Dictionay calculated from reference
 * extends LazyMaterialization and KTiles
 * @see LazyMaterialization
 * @see KTiles
 */
class AlignmentRecordMaterialization(s: SparkContext,
                                     filePaths: List[String],
                                     dict: SequenceDictionary) extends LazyMaterialization[AlignmentRecord]
    with Serializable with Logging {

  @transient implicit val formats = net.liftweb.json.DefaultFormats
  @transient val sc = s
  val sd = dict
  val files = filePaths

  def load = (region: ReferenceRegion, file: String) => AlignmentRecordMaterialization.load(sc, region, file).rdd

  /**
   * Extracts ReferenceRegion from AlignmentRecord
   * @param ar AlignmentRecord
   * @return extracted ReferenceRegion
   */
  def getReferenceRegion = (ar: AlignmentRecord) => ReferenceRegion(ar)

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
    val covCounts: RDD[(String, PositionCount)] =
      get(region)
        .flatMap(r => {
          val t: List[Long] = List.range(r._2.getStart, r._2.getEnd)
          t.map(n => ((ReferenceRegion(r._2.getContigName, n, n + 1), r._1), 1))
            .filter(_._1._1.overlaps(region)) // filter out read fragments not overlapping region
        }).reduceByKey(_ + _) // reduce coverage by combining adjacent frequenct
        .map(r => (r._1._2, PositionCount(r._1._1.start, r._2)))

    covCounts.collect.groupBy(_._1) // group by sample Id
      .map(r => (r._1, write(r._2.map(_._2))))
  }

  /**
   * Formats raw data from KLayeredTile to JSON. This is requied by KTiles
   * @param data RDD of (id, AlignmentRecord) tuples
   * @return JSONified data
   */
  def stringify(data: RDD[(String, AlignmentRecord)]): Map[String, String] = {
    val flattened: Map[String, Array[AlignmentRecord]] = data
      .collect
      .groupBy(_._1)
      .map(r => (r._1, r._2.map(_._2)))

    val gaReads: Map[String, List[GAReadAlignment]] = flattened.mapValues(l => l.map(r => GA4GHConverter.toGAReadAlignment(r)).toList)

    gaReads.mapValues(v => {
      GASearchReadsResponse.newBuilder()
        .setAlignments(v)
        .build().toString
    })
  }
}

object AlignmentRecordMaterialization {

  def apply(sc: SparkContext, files: List[String], sd: SequenceDictionary): AlignmentRecordMaterialization = {
    new AlignmentRecordMaterialization(sc, files, sd)
  }

  /**
   * Loads alignment data from bam, sam and ADAM file formats
   * @param sc SparkContext
   * @param region Region to load
   * @param fp filepath to load from
   * @return RDD of data from the file over specified ReferenceRegion
   */
  def load(sc: SparkContext, region: ReferenceRegion, fp: String): AlignmentRecordRDD = {
    if (fp.endsWith(".adam")) loadAdam(sc, region, fp)
    else if (fp.endsWith(".sam") || fp.endsWith(".bam")) {
      AlignmentRecordMaterialization.loadFromBam(sc, region, fp)
        .transform(rdd => rdd.filter(_.getReadMapped))
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
  def loadFromBam(sc: SparkContext, region: ReferenceRegion, fp: String): AlignmentRecordRDD = {
    val idxFile: File = new File(fp + ".bai")
    if (idxFile.exists()) {
      sc.loadIndexedBam(fp, region)
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
  def loadAdam(sc: SparkContext, region: ReferenceRegion, fp: String): AlignmentRecordRDD = {
    val name = Binary.fromString(region.referenceName)
    val pred: FilterPredicate = ((LongColumn("end") >= region.start) && (LongColumn("start") <= region.end) && (BinaryColumn("contigName") === name) && (BooleanColumn("readMapped") === true))
    val proj = Projection(AlignmentRecordField.contigName, AlignmentRecordField.mapq, AlignmentRecordField.readName, AlignmentRecordField.start, AlignmentRecordField.readMapped,
      AlignmentRecordField.end, AlignmentRecordField.sequence, AlignmentRecordField.cigar, AlignmentRecordField.readNegativeStrand, AlignmentRecordField.readPaired, AlignmentRecordField.recordGroupSample)
    sc.loadParquetAlignments(fp, predicate = Some(pred), projection = None)
  }
}
