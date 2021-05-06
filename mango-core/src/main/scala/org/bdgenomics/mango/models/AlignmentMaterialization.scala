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

import org.apache.parquet.filter2.dsl.Dsl._
import org.apache.spark.SparkContext
import org.bdgenomics.adam.models.{ SequenceDictionary, ReferenceRegion }
import org.bdgenomics.adam.projections.{ AlignmentField, Projection }
import org.bdgenomics.adam.ds.ADAMContext._
import org.bdgenomics.adam.ds.read.AlignmentDataset
import org.bdgenomics.formats.avro.Alignment
import org.bdgenomics.mango.core.util.ResourceUtils
import org.bdgenomics.mango.io.BamReader
import grizzled.slf4j.Logging
import ga4gh.Reads.ReadAlignment
import net.liftweb.json.Extraction._
import net.liftweb.json.Serialization._
import scala.collection.JavaConversions._
import org.bdgenomics.mango.converters.GA4GHutil._

/*
 * Handles loading and tracking of data from persistent storage into memory for Alignment data.
 *
 * @param sc SparkContext
 * @param files list files to materialize
 * @param sd the sequence dictionary associated with the file records
 * @param prefetchSize the number of base pairs to prefetch in executors. Defaults to 1000000
 * @see LazyMaterialization.scala
 */
class AlignmentMaterialization(@transient sc: SparkContext,
                               files: List[String],
                               sd: SequenceDictionary,
                               prefetchSize: Option[Long] = None)
    extends LazyMaterialization[Alignment, ReadAlignment](AlignmentMaterialization.name, sc, files, sd, prefetchSize)
    with Serializable with Logging {

  def load = (file: String, regions: Option[Iterable[ReferenceRegion]]) => AlignmentMaterialization.load(sc, file, regions)

  /**
   * Extracts ReferenceRegion from Alignment
   * @param ar Alignment
   * @return extracted ReferenceRegion
   */
  def getReferenceRegion = (ar: Alignment) => ReferenceRegion.unstranded(ar)

  /**
   * Reset ReferenceName for Alignment
   *
   * @param ar Alignment to be modified
   * @param referenceName to replace Alignment referenceName
   * @return Alignment with new ReferenceRegion
   */
  def setReferenceName = (ar: Alignment, referenceName: String) => {
    ar.setReferenceName(referenceName)
    ar
  }

  /**
   * Formats an RDD of keyed Alignments to a GAReadAlignments mapped by key
   * @param data RDD of (id, Alignment) tuples
   * @return GAReadAlignments mapped by key
   */
  override def toJson(data: Array[(String, Alignment)]): Map[String, Array[ReadAlignment]] = {
    data.filter(r => Option(r._2.getMappingQuality).getOrElse(1).asInstanceOf[Int] > 0) // filter mappingQuality 0 reads out
      .groupBy(_._1).mapValues(r => {
        r.map(a => alignmentToGAReadAlignment(a._2))
      })
  }

  /**
   * Formats raw data from GA4GH Alignments to JSON.
   * @param data An array of GAReadAlignments
   * @return JSONified data
   */
  def stringify = (data: Array[ReadAlignment]) => {

    val message = ga4gh.ReadServiceOuterClass.SearchReadsResponse
      .newBuilder().addAllAlignments(data.toList).build()

    com.google.protobuf.util.JsonFormat.printer().includingDefaultValueFields().print(message)
  }
}

object AlignmentMaterialization extends Logging {

  val name = "Alignment"

  // caches the first steps of loading binned dataset from files to avoid repeating the
  // several minutes long initalization of these binned dataset
  val datasetCache = new collection.mutable.HashMap[String, AlignmentDataset]

  /**
   * Loads alignment data from bam, sam and ADAM file formats
   * @param sc SparkContext
   * @param regions Iterable of  ReferenceRegions to load
   * @param fp filepath to load from
   * @return Alignment dataset from the file over specified ReferenceRegion
   */
  def load(sc: SparkContext, fp: String, regions: Option[Iterable[ReferenceRegion]]): Array[Alignment] = {
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
  def loadAdam(sc: SparkContext, fp: String, regions: Option[Iterable[ReferenceRegion]]): Array[Alignment] = {

    val proj = Projection(AlignmentField.referenceName, AlignmentField.mappingQuality, AlignmentField.readName,
      AlignmentField.start, AlignmentField.readMapped, AlignmentField.readGroupId,
      AlignmentField.end, AlignmentField.sequence, AlignmentField.cigar, AlignmentField.readNegativeStrand,
      AlignmentField.readPaired, AlignmentField.readGroupSampleId)

    val alignmentDataset: AlignmentDataset =
      if (regions.isDefined) {
        if (sc.isPartitioned(fp)) {

          // finalRegions includes references both with and without "chr" prefix
          val finalRegions: Iterable[ReferenceRegion] = regions.get ++ regions.get
            .map(x => ReferenceRegion(x.referenceName.replaceFirst("""^chr""", """"""),
              x.start,
              x.end,
              x.strand))

          // load new dataset or retrieve from cache
          val data: AlignmentDataset = datasetCache.get(fp) match {
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

    alignmentDataset.rdd.collect()
  }
}
