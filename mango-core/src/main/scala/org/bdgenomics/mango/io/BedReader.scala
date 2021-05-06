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

package org.bdgenomics.mango.io

import java.io.{ BufferedOutputStream, File, FileOutputStream }

import htsjdk.tribble.index.tabix.TabixFormat
import htsjdk.tribble.util.LittleEndianOutputStream
import htsjdk.tribble.{ AbstractFeatureReader, FeatureCodecHeader, TribbleException }
import htsjdk.tribble.bed.{ BEDCodec, BEDFeature }
import htsjdk.tribble.readers.LineIterator
import org.apache.spark.SparkContext
import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.mango.models.LazyMaterialization
import grizzled.slf4j.Logging
import htsjdk.tribble.index.{ Index, IndexFactory }

import scala.collection.JavaConversions._
import org.bdgenomics.formats.avro.Feature
import org.bdgenomics.adam.ds.ADAMContext._
import org.bdgenomics.adam.ds.feature.FeatureDataset
import org.apache.spark.rdd.RDD

object BedReader extends GenomicReader[FeatureCodecHeader, Feature, FeatureDataset] with Logging {

  def suffixes = Array(".bed", ".bed.gz", ".narrowPeak")

  def load(fp: String, regionsOpt: Option[Iterable[ReferenceRegion]], local: Boolean): Tuple2[FeatureCodecHeader, Array[Feature]] = {

    val codec: BEDCodec = new BEDCodec()

    // if not valid throw Exception
    if (!isValidSuffix(fp)) {
      invalidFileException(fp)
    }

    // reader must be called before createIndex is called
    val reader = AbstractFeatureReader.getFeatureReader(fp, codec, false)

    if (local) {
      // create index file, if it does not exist
      createIndex(fp, codec)
    }

    val header = reader.getHeader().asInstanceOf[FeatureCodecHeader]
    val dictionary = reader.getSequenceNames()

    val results =
      if (regionsOpt.isDefined) {
        val queries =
          if (!dictionary.isEmpty) {

            // modify chr prefix, if this file uses chr prefixes.
            val hasChrPrefix = dictionary.get(0).startsWith("chr")

            regionsOpt.get.map(r => {
              LazyMaterialization.modifyChrPrefix(r, hasChrPrefix)
            })
          } else {
            // take all possible combinations of ReferenceRegions
            regionsOpt.get.map(r => {
              Iterable(LazyMaterialization.modifyChrPrefix(r, true), LazyMaterialization.modifyChrPrefix(r, false))
            }).flatten
          }

        if (reader.isQueryable) {
          val tmp = queries.map(r => reader.query(r.referenceName, r.start.toInt, r.end.toInt).toList)
          reader.close()
          tmp.flatten
        } else {
          // in the case where file is remote and doesn't have an index, still want to get data
          val iter: Iterator[BEDFeature] = reader.iterator()
          reader.close()
          iter.map(r => {
            val overlaps = queries.filter(q => q.overlaps(ReferenceRegion(r.getContig, r.getStart, r.getEnd))).size > 0
            if (overlaps)
              Some(r)
            else None
          }).flatten
        }
      } else {
        // no regions specified, so return all records
        val iter: Iterator[BEDFeature] = reader.iterator()
        reader.close()
        iter
      }

    // map results to ADAM features
    val features = results.map(r => Feature.newBuilder()
      .setFeatureType(r.getType)
      .setReferenceName(r.getContig)
      .setStart(r.getStart.toLong - 1) // move to 0 indexed to match HDFS results
      .setEnd(r.getEnd.toLong).build()).toArray

    (header, features)
  }

  /**
   * Loads data from bam files (indexed or unindexed) from s3.
   *
   * @param regions Iterable of ReferenceRegions to load
   * @param path filepath to load from
   * @return Alignment dataset from the file over specified ReferenceRegion
   */
  def loadS3(path: String, regions: Option[Iterable[ReferenceRegion]]): Tuple2[FeatureCodecHeader, Array[Feature]] = {
    throw new Exception("Not implemented")
  }

  /**
   * Loads data from bam files (indexed or unindexed) from HDFS.
   * @param sc SparkContext
   * @param regionsOpt Option of Iterable of ReferenceRegions to load
   * @param fp filepath to load from
   * @return Alignment dataset from the file over specified ReferenceRegion
   */
  def loadHDFS(sc: SparkContext, fp: String, regionsOpt: Option[Iterable[ReferenceRegion]]): Tuple2[FeatureDataset, Array[Feature]] = {
    // if regions are specified, specifically load regions. Otherwise, load all data
    val dataset =
      if (regionsOpt.isDefined) {
        val regions = regionsOpt.get
        val predicateRegions = regions
          .flatMap(r => LazyMaterialization.getReferencePredicate(r))
          .toArray

        sc.loadFeatures(fp)
          .transform((rdd: RDD[Feature]) => rdd.filter(g =>
            !predicateRegions.filter(r => ReferenceRegion.unstranded(g).overlaps(r)).isEmpty))
      } else { // no regions defined. return all records.
        sc.loadFeatures(fp)
      }

    (dataset, dataset.rdd.collect)
  }

  private def createIndex(fp: String, codec: BEDCodec) = {

    val file = new java.io.File(fp)
    val idxFile = new java.io.File(file + ".idx")

    // do not re-generate index file
    if (!idxFile.exists()) {

      logger.warn(s"No index file for ${file.getAbsolutePath} found. Generating ${idxFile.getAbsolutePath}...")

      // Create the index
      val idx: Index = IndexFactory.createIntervalIndex(file, codec)

      var stream: LittleEndianOutputStream = null
      try {
        stream = new LittleEndianOutputStream(new BufferedOutputStream(new FileOutputStream(idxFile)))
        idx.write(stream)
      } finally {
        if (stream != null) {
          stream.close()
        }
      }
      idxFile.deleteOnExit()
    }
  }
}
