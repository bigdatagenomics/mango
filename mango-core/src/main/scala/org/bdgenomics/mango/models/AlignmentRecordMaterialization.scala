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

import htsjdk.samtools.ValidationStringency
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
import org.bdgenomics.mango.core.util.ResourceUtils
import org.bdgenomics.utils.misc.Logging
import org.bdgenomics.utils.instrumentation.Metrics
import org.ga4gh.{ GAReadAlignment, GASearchReadsResponse }
import net.liftweb.json.Serialization._
import org.seqdoop.hadoop_bam.util.SAMHeaderReader
import scala.collection.JavaConversions._
import scala.reflect._
import scala.collection.JavaConverters._

import scala.reflect.ClassTag

// metric variables
object AlignmentTimers extends Metrics {
  val loadADAMData = timer("load alignments from parquet")
  val loadBAMData = timer("load alignments from BAM files")
  val getCoverageData = timer("get coverage data from IntervalRDD")
  val getAlignmentData = timer("get alignment data from IntervalRDD")
  val convertToGaReads = timer("convert parquet alignments to GA4GH Reads")
  val collect = timer("collect alignments")
  val toJson = timer("convert alignments to json")
}

/**
 * Handles loading and tracking of data from persistent storage into memory for AlignmentRecord data.
 *
 * @param sc SparkContext
 * @param files list files to materialize
 * @param sd the sequence dictionary associated with the file records
 * @param repartition whether to repartition data to the default number of partitions
 * @param prefetchSize the number of base pairs to prefetch in executors. Defaults to 1000000
 * @see LazyMaterialization.scala
 */
class AlignmentRecordMaterialization(@transient sc: SparkContext,
                                     files: List[String],
                                     sd: SequenceDictionary,
                                     repartition: Boolean = false,
                                     prefetchSize: Option[Long] = None)
    extends LazyMaterialization[AlignmentRecord, GAReadAlignment](AlignmentRecordMaterialization.name, sc, files, sd, repartition, prefetchSize)
    with Serializable with Logging {

  def load = (file: String, regions: Option[Iterable[ReferenceRegion]]) => AlignmentRecordMaterialization.load(sc, file, regions).rdd

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
  def getCoverage(region: ReferenceRegion): Map[String, Array[PositionCount]] = {

    AlignmentTimers.getCoverageData.time {
      val covCounts: RDD[(String, PositionCount)] =
        get(Some(region))
          .flatMap(r => {
            val t: List[Long] = List.range(r._2.getStart, r._2.getEnd)
            t.map(n => ((ReferenceRegion(r._2.getContigName, n, n + 1), r._1), 1))
              .filter(_._1._1.overlaps(region)) // filter out read fragments not overlapping region
          }).reduceByKey(_ + _) // reduce coverage by combining adjacent frequenct
          .map(r => (r._1._2, PositionCount(r._1._1.referenceName, r._1._1.start, r._1._1.start + 1, r._2)))

      covCounts.collect.groupBy(_._1).mapValues(_.map(_._2)) // group by sample Id
    }
  }

  /**
   * Formats an RDD of keyed AlignmentRecords to a GAReadAlignments mapped by key
   * @param data RDD of (id, AlignmentRecord) tuples
   * @return GAReadAlignments mapped by key
   */
  override def toJson(data: RDD[(String, AlignmentRecord)]): Map[String, Array[GAReadAlignment]] = {
    AlignmentTimers.collect.time {
      AlignmentTimers.getAlignmentData.time {
        data.filter(r => Option(r._2.mapq).getOrElse(1).asInstanceOf[Int] > 0) // filter mapq 0 reads out 
          .mapValues(r => Array(GA4GHConverter.toGAReadAlignment(r, stringency = ValidationStringency.SILENT)))
          .reduceByKeyLocally(_ ++ _).toMap
      }
    }
  }

  /**
   * Formats raw data from GA4GH AlignmentRecords to JSON.
   * @param data An array of GAReadAlignments
   * @return JSONified data
   */
  override def stringify(data: Array[GAReadAlignment]): String = {
    GASearchReadsResponse.newBuilder()
      .setAlignments(data.toList)
      .build().toString
  }
}

object AlignmentRecordMaterialization extends Logging {

  val name = "AlignmentRecord"

  // caches the first steps of loading binned dataset from files to avoid repeating the
  // several minutes long initalization of these binned dataset
  val datasetCache = new collection.mutable.HashMap[String, AlignmentRecordRDD]

  /**
   * Loads alignment data from bam, sam and ADAM file formats
   * @param sc SparkContext
   * @param regions Iterable of  ReferenceRegions to load
   * @param fp filepath to load from
   * @return RDD of data from the file over specified ReferenceRegion
   */
  def load(sc: SparkContext, fp: String, regions: Option[Iterable[ReferenceRegion]]): AlignmentRecordRDD = {
    if (fp.endsWith(".adam")) loadAdam(sc, fp, regions)
    else {
      try {
        AlignmentRecordMaterialization.loadFromBam(sc, fp, regions)
          .transform(rdd => rdd.filter(_.getReadMapped))
      } catch {
        case e: Exception => {
          val sw = new StringWriter
          e.printStackTrace(new PrintWriter(sw))
          throw UnsupportedFileException(s"bam index not provided for file ${fp}. Stack trace: " + sw.toString)
        }
      }
    }
  }

  /**
   * Loads data from bam files (indexed or unindexed) from persistent storage
   * @param sc SparkContext
   * @param regions Iterable of ReferenceRegions to load
   * @param fp filepath to load from
   * @return RDD of data from the file over specified ReferenceRegion
   */
  def loadFromBam(sc: SparkContext, fp: String, regions: Option[Iterable[ReferenceRegion]]): AlignmentRecordRDD = {
    AlignmentTimers.loadBAMData.time {
      if (regions.isDefined) {
        // hack to get around issue in hadoop_bam, which throws error if contigName is not found in bam file
        val path = new Path(fp)
        val fileSd = SequenceDictionary(SAMHeaderReader.readSAMHeaderFrom(path, sc.hadoopConfiguration))
        val predicateRegions: Iterable[ReferenceRegion] = regions.get
          .flatMap(r => {
            LazyMaterialization.getContigPredicate(r)
          }).filter(r => fileSd.containsRefName(r.referenceName))
        try {
          sc.loadIndexedBam(fp, predicateRegions, stringency = ValidationStringency.SILENT)
        } catch {
          case e: java.lang.IllegalArgumentException => {
            log.warn(e.getMessage)
            log.warn("No bam index detected. File loading will be slow...")
            sc.loadBam(fp, stringency = ValidationStringency.SILENT).filterByOverlappingRegions(regions.get)
          }
        }
      } else {
        sc.loadBam(fp, stringency = ValidationStringency.SILENT)
      }
    }
  }

  /**
   * Loads ADAM data using predicate pushdowns
   * @param sc SparkContext
   * @param regions Iterable of ReferenceRegions to load
   * @param fp filepath to load from
   * @return RDD of data from the file over specified ReferenceRegion
   */
  def loadAdam(sc: SparkContext, fp: String, regions: Option[Iterable[ReferenceRegion]]): AlignmentRecordRDD = {

    AlignmentTimers.loadADAMData.time {
      if (sc.isPartitioned(fp)) {

        // finalRegions includes contigs both with and without "chr" prefix
        val finalRegions: Iterable[ReferenceRegion] = regions.get ++ regions.get
          .map(x => ReferenceRegion(x.referenceName.replaceFirst("""^chr""", """"""),
            x.start,
            x.end,
            x.strand))

        val x: AlignmentRecordRDD = datasetCache.get(fp) match {
          case Some(x) => x.transformDataset(d => regions match {
            case Some(regions) => {
              d.filter(sc.referenceRegionsToDatasetQueryString(finalRegions))
                .filter(x => (x.readMapped.getOrElse(false)) && x.mapq.getOrElse(0) > 0)
            }
            case _ => d
          })
          case _ => {
            val loadedDataset = sc.loadPartitionedParquetAlignments(fp)
            datasetCache(fp) = loadedDataset
            loadedDataset.transformDataset(d => regions match {
              case Some(regions) => {
                d.filter(sc.referenceRegionsToDatasetQueryString(finalRegions))
                  .filter(x => (x.readMapped.getOrElse(false)) && x.mapq.getOrElse(0) > 0)
              }
              case _ => d
            })
          }
        }

        return x
      } else {
        val pred =
          if (regions.isDefined) {
            val prefixRegions: Iterable[ReferenceRegion] = regions.get.map(r => LazyMaterialization.getContigPredicate(r)).flatten
            Some(ResourceUtils.formReferenceRegionPredicate(prefixRegions) && (BooleanColumn("readMapped") === true) && (IntColumn("mapq") > 0))
          } else {
            Some((BooleanColumn("readMapped") === true) && (IntColumn("mapq") > 0))
          }

        val proj = Projection(AlignmentRecordField.contigName, AlignmentRecordField.mapq, AlignmentRecordField.readName,
          AlignmentRecordField.start, AlignmentRecordField.readMapped, AlignmentRecordField.recordGroupName,
          AlignmentRecordField.end, AlignmentRecordField.sequence, AlignmentRecordField.cigar, AlignmentRecordField.readNegativeStrand,
          AlignmentRecordField.readPaired, AlignmentRecordField.recordGroupSample)

        sc.loadParquetAlignments(fp, optPredicate = pred, optProjection = Some(proj))

      }
    }
  }
}
