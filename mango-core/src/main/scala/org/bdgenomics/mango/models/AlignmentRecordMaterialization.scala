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

import java.io.{ PrintWriter, StringWriter }

import org.apache.hadoop.fs.Path
import org.apache.parquet.filter2.dsl.Dsl._
import org.apache.parquet.filter2.predicate.FilterPredicate
import org.apache.parquet.io.api.Binary
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.bdgenomics.adam.models.{ SequenceDictionary, ReferenceRegion }
import org.bdgenomics.adam.projections.{ AlignmentRecordField, Projection }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.adam.rdd.read.AlignmentRecordRDD
import org.bdgenomics.formats.avro.AlignmentRecord
import org.bdgenomics.mango.converters.GA4GHConverter
import org.bdgenomics.mango.layout.PositionCount
import org.bdgenomics.utils.misc.Logging
import org.ga4gh.{ GAReadAlignment, GASearchReadsResponse }
import net.liftweb.json.Serialization._
import org.seqdoop.hadoop_bam.util.SAMHeaderReader
import scala.collection.JavaConversions._
import org.bdgenomics.utils.instrumentation.Metrics

// metric variables
object AlignmentTimers extends Metrics {
  val loadADAMData = timer("LOAD alignments from parquet")
  val loadBAMData = timer("LOAD alignments from BAM files")
  val getCoverageData = timer("get coverage data from IntervalRDD")
  val getAlignmentData = timer("get alignment data from IntervalRDD")
  val convertToGaReads = timer("convert parquet alignments to GA4GH Reads")
}

/**
 *
 * @param sc SparkContext
 * @param sd Sequence Dictionay calculated from reference
 * extends LazyMaterialization and KTiles
 * @see LazyMaterialization
 * @see KTiles
 */
class AlignmentRecordMaterialization(@transient sc: SparkContext,
                                     files: List[String],
                                     sd: SequenceDictionary,
                                     prefetchSize: Option[Int] = None)
    extends LazyMaterialization[AlignmentRecord]("AlignmentRecordRDD", sc, files, sd, prefetchSize)
    with Serializable with Logging {

  @transient implicit val formats = net.liftweb.json.DefaultFormats

  def load = (file: String, region: Option[ReferenceRegion]) => AlignmentRecordMaterialization.load(sc, file, region).rdd

  /**
   * Extracts ReferenceRegion from AlignmentRecord
   * @param ar AlignmentRecord
   * @return extracted ReferenceRegion
   */
  def getReferenceRegion = (ar: AlignmentRecord) => ReferenceRegion.unstranded(ar)

  /**
   * Reset ReferenceName for AlignmentRecord
   *
   * @param ar AlignmentRecord to be modified
   * @param contig to replace AlignmentRecord contigName
   * @return AlignmentRecord with new ReferenceRegion
   */
  def setContigName = (ar: AlignmentRecord, contig: String) => {
    ar.setContigName(contig)
    ar
  }

  /*
   * Gets Frequency over a given region for each specified sample
   *
   * @param region: ReferenceRegion to query over
   *
   * @return Map[String, Iterable[FreqJson]] Map of [SampleId, Iterable[FreqJson]] which stores each base and its
   * cooresponding frequency.
   */
  def getCoverage(region: ReferenceRegion): Map[String, String] = {

    AlignmentTimers.getCoverageData.time {
      val covCounts: RDD[(String, PositionCount)] =
        get(region)
          .flatMap(r => {
            val t: List[Long] = List.range(r._2.getStart, r._2.getEnd)
            t.map(n => ((ReferenceRegion(r._2.getContigName, n, n + 1), r._1), 1))
              .filter(_._1._1.overlaps(region)) // filter out read fragments not overlapping region
          }).reduceByKey(_ + _) // reduce coverage by combining adjacent frequenct
          .map(r => (r._1._2, PositionCount(r._1._1.start, r._1._1.start + 1, r._2)))

      covCounts.collect.groupBy(_._1) // group by sample Id
        .map(r => (r._1, write(r._2.map(_._2))))
    }
  }

  /**
   * Formats raw data from KLayeredTile to JSON. This is requied by KTiles
   * @param data RDD of (id, AlignmentRecord) tuples
   * @return JSONified data
   */
  def stringify(data: RDD[(String, AlignmentRecord)]): Map[String, String] = {
    val flattened: Map[String, Array[AlignmentRecord]] =
      AlignmentTimers.getAlignmentData.time {
        data
          .filter(r => r._2.getMapq > 0)
          .collect
          .groupBy(_._1)
          .map(r => (r._1, r._2.map(_._2)))
      }

    AlignmentTimers.convertToGaReads.time {

      val gaReads: Map[String, List[GAReadAlignment]] = flattened.mapValues(l => l.map(r => GA4GHConverter.toGAReadAlignment(r)).toList)

      gaReads.mapValues(v => {
        GASearchReadsResponse.newBuilder()
          .setAlignments(v)
          .build().toString
      })
    }
  }
}

object AlignmentRecordMaterialization extends Logging {

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
  def load(sc: SparkContext, fp: String, region: Option[ReferenceRegion]): AlignmentRecordRDD = {
    if (fp.endsWith(".adam")) loadAdam(sc, fp, region)
    else {
      try {
        AlignmentRecordMaterialization.loadFromBam(sc, fp, region)
          .transform(rdd => rdd.filter(_.getReadMapped))
      } catch {
        case e: Exception => {
          val sw = new StringWriter
          e.printStackTrace(new PrintWriter(sw))
          throw UnsupportedFileException("bam index not provided. Stack trace: " + sw.toString)
        }
      }
    }
  }

  /**
   * Loads data from bam files (indexed or unindexed) from persistent storage
   * @param sc SparkContext
   * @param region Region to load
   * @param fp filepath to load from
   * @return RDD of data from the file over specified ReferenceRegion
   */
  def loadFromBam(sc: SparkContext, fp: String, region: Option[ReferenceRegion]): AlignmentRecordRDD = {
    AlignmentTimers.loadBAMData.time {
      region match {
        case Some(_) =>
          val regions = LazyMaterialization.getContigPredicate(region.get)
          var alignments: AlignmentRecordRDD = null
          // hack to get around issue in hadoop_bam, which throws error if contigName is not found in bam file
          val path = new Path(fp)
          val fileSd = SequenceDictionary(SAMHeaderReader.readSAMHeaderFrom(path, sc.hadoopConfiguration))
          for (r <- List(regions._1, regions._2)) {
            if (fileSd.containsRefName(r.referenceName)) {
              val x = sc.loadIndexedBam(fp, r)
              if (alignments == null) alignments = x
              else alignments = alignments.transform(rdd => rdd.union(x.rdd))
            }
          }
          alignments
        case _ => sc.loadBam(fp)
      }
    }
  }

  /**
   * Loads ADAM data using predicate pushdowns
   * @param sc SparkContext
   * @param region Region to load
   * @param fp filepath to load from
   * @return RDD of data from the file over specified ReferenceRegion
   */
  def loadAdam(sc: SparkContext, fp: String, region: Option[ReferenceRegion]): AlignmentRecordRDD = {
    AlignmentTimers.loadADAMData.time {
      val pred: Option[FilterPredicate] =
        region match {
          case Some(_) => {
            val contigs = LazyMaterialization.getContigPredicate(region.get)
            Some((LongColumn("end") >= region.get.start) && (LongColumn("start") <= region.get.end) &&
              (BinaryColumn("contigName") === Binary.fromString(contigs._1.referenceName) ||
                BinaryColumn("contigName") === Binary.fromString(contigs._2.referenceName)) &&
                (BooleanColumn("readMapped") === true))
          } case None => None
        }
      val proj = Projection(AlignmentRecordField.contigName, AlignmentRecordField.mapq, AlignmentRecordField.readName,
        AlignmentRecordField.start, AlignmentRecordField.readMapped, AlignmentRecordField.recordGroupName,
        AlignmentRecordField.end, AlignmentRecordField.sequence, AlignmentRecordField.cigar, AlignmentRecordField.readNegativeStrand,
        AlignmentRecordField.readPaired, AlignmentRecordField.recordGroupSample)
      sc.loadParquetAlignments(fp, predicate = pred, projection = Some(proj))
    }
  }
}
