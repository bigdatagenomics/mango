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

import java.io.{ File, PrintWriter, StringWriter }
import java.net.URL

import htsjdk.samtools.util.{ HttpUtils, SamRecordIntervalIteratorFactory }
import htsjdk.samtools._
import net.liftweb.json.Extraction._
import net.liftweb.json._
import org.apache.hadoop.io.LongWritable
import org.apache.parquet.filter2.dsl.Dsl._
import org.apache.parquet.filter2.predicate.FilterPredicate
import org.apache.parquet.hadoop.util.ContextUtil
import org.apache.parquet.io.api.Binary
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.bdgenomics.adam.models.{ SequenceDictionary, ReferenceRegion }
import org.bdgenomics.adam.projections.{ AlignmentRecordField, Projection }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.adam.rdd.read.AlignmentRecordDataset
import org.bdgenomics.formats.avro.AlignmentRecord
import org.bdgenomics.mango.converters.SAMRecordConverter
import org.bdgenomics.mango.core.util.ResourceUtils
import org.bdgenomics.mango.io.BamReader
import org.bdgenomics.utils.misc.{ HadoopUtil, Logging }
import org.bdgenomics.utils.instrumentation.Metrics
import ga4gh.Reads.ReadAlignment
import net.liftweb.json.Serialization._
import org.seqdoop.hadoop_bam.{ SAMRecordWritable, BAMInputFormat }
import org.seqdoop.hadoop_bam.util.SAMHeaderReader
import scala.collection.JavaConversions._
import scala.reflect._
import org.bdgenomics.mango.converters.GA4GHutil._

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
 * @param prefetchSize the number of base pairs to prefetch in executors. Defaults to 1000000
 * @see LazyMaterialization.scala
 */
class AlignmentRecordMaterialization(@transient sc: SparkContext,
                                     files: List[String],
                                     sd: SequenceDictionary,
                                     prefetchSize: Option[Long] = None)
    extends LazyMaterialization[AlignmentRecord, ReadAlignment](AlignmentRecordMaterialization.name, sc, files, sd, prefetchSize)
    with Serializable with Logging {

  def load = (file: String, regions: Option[Iterable[ReferenceRegion]]) => AlignmentRecordMaterialization.load(sc, file, regions)

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
   * @param referenceName to replace AlignmentRecord referenceName
   * @return AlignmentRecord with new ReferenceRegion
   */
  def setReferenceName = (ar: AlignmentRecord, referenceName: String) => {
    ar.setReferenceName(referenceName)
    ar
  }

  /**
   * Formats an RDD of keyed AlignmentRecords to a GAReadAlignments mapped by key
   * @param data RDD of (id, AlignmentRecord) tuples
   * @return GAReadAlignments mapped by key
   */
  override def toJson(data: Array[(String, AlignmentRecord)]): Map[String, Array[ReadAlignment]] = {
    data.filter(r => Option(r._2.getMappingQuality).getOrElse(1).asInstanceOf[Int] > 0) // filter mappingQuality 0 reads out
      .groupBy(_._1).mapValues(r => {
        r.map(a => alignmentRecordToGAReadAlignment(a._2))
      })
  }

  /**
   * Formats raw data from GA4GH AlignmentRecords to JSON.
   * @param data An array of GAReadAlignments
   * @return JSONified data
   */
  def stringify = (data: Array[ReadAlignment]) => {

    val message = ga4gh.ReadServiceOuterClass.SearchReadsResponse
      .newBuilder().addAllAlignments(data.toList).build()

    com.google.protobuf.util.JsonFormat.printer().includingDefaultValueFields().print(message)
  }
}

object AlignmentRecordMaterialization extends Logging {

  val name = "AlignmentRecord"

  // caches the first steps of loading binned dataset from files to avoid repeating the
  // several minutes long initalization of these binned dataset
  val datasetCache = new collection.mutable.HashMap[String, AlignmentRecordDataset]

  /**
   * Loads alignment data from bam, sam and ADAM file formats
   * @param sc SparkContext
   * @param regions Iterable of  ReferenceRegions to load
   * @param fp filepath to load from
   * @return Alignment dataset from the file over specified ReferenceRegion
   */
  def load(sc: SparkContext, fp: String, regions: Option[Iterable[ReferenceRegion]]): Array[AlignmentRecord] = {
    if (fp.endsWith(".adam")) loadAdam(sc, fp, regions)
    else BamReader.loadFromSource(fp, regions, Some(sc)).filter(_.getReadMapped)
  }

  /**
   * Loads ADAM data using predicate pushdowns
   * @param sc SparkContext
   * @param regions Iterable of ReferenceRegions to load
   * @param fp filepath to load from
   * @return Alignment dataset from the file over specified ReferenceRegion
   */
  def loadAdam(sc: SparkContext, fp: String, regions: Option[Iterable[ReferenceRegion]]): Array[AlignmentRecord] = {
    AlignmentTimers.loadADAMData.time {

      val proj = Projection(AlignmentRecordField.referenceName, AlignmentRecordField.mappingQuality, AlignmentRecordField.readName,
        AlignmentRecordField.start, AlignmentRecordField.readMapped, AlignmentRecordField.readGroupId,
        AlignmentRecordField.end, AlignmentRecordField.sequence, AlignmentRecordField.cigar, AlignmentRecordField.readNegativeStrand,
        AlignmentRecordField.readPaired, AlignmentRecordField.readGroupSampleId)

      val alignmentRecordDataset: AlignmentRecordDataset =
        if (regions.isDefined) {
          if (sc.isPartitioned(fp)) {

            // finalRegions includes references both with and without "chr" prefix
            val finalRegions: Iterable[ReferenceRegion] = regions.get ++ regions.get
              .map(x => ReferenceRegion(x.referenceName.replaceFirst("""^chr""", """"""),
                x.start,
                x.end,
                x.strand))

            // load new dataset or retrieve from cache
            val data: AlignmentRecordDataset = datasetCache.get(fp) match {
              case Some(ds) => { // if dataset found in datasetCache
                ds
              }
              case _ => {
                // load dataset into cache and use use it
                datasetCache(fp) = sc.loadPartitionedParquetAlignments(fp)
                datasetCache(fp)
              }
            }

            val maybeFiltered = if (finalRegions.nonEmpty) {
              data.filterByOverlappingRegions(finalRegions)
            } else data

            // remove unmapped reads
            maybeFiltered.transformDataset(d => {
              d.filter(x => (x.readMapped.getOrElse(false)) && x.mappingQuality.getOrElse(0) > 0)
            })

          } else { // data was not written as partitioned parquet
            val pred = {
              val prefixRegions: Iterable[ReferenceRegion] = regions.get.map(r => LazyMaterialization.getReferencePredicate(r)).flatten
              Some(ResourceUtils.formReferenceRegionPredicate(prefixRegions) && (BooleanColumn("readMapped") === true) && (IntColumn("mappingQuality") > 0))
            }
            sc.loadParquetAlignments(fp, optPredicate = pred, optProjection = Some(proj))
          }
        } else {
          sc.loadAlignments(fp, optProjection = Some(proj))
        }

      alignmentRecordDataset.rdd.collect()
    }
  }
}
