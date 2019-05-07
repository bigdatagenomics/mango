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

import java.io.{ FileOutputStream, BufferedOutputStream, File }

import htsjdk.tribble.index.tabix.TabixFormat
import htsjdk.tribble.util.LittleEndianOutputStream
import htsjdk.tribble.{ TribbleException, AbstractFeatureReader }
import htsjdk.tribble.bed.{ BEDFeature, BEDCodec }
import htsjdk.tribble.readers.LineIterator
import org.apache.spark.SparkContext
import org.bdgenomics.adam.models.{ ReferenceRegion }
import org.bdgenomics.adam.rdd.feature.FeatureDataset
import org.bdgenomics.mango.models.LazyMaterialization
import org.bdgenomics.utils.misc.Logging
import htsjdk.tribble.index.{ IndexFactory, Index }
import scala.collection.JavaConversions._
import org.bdgenomics.formats.avro.Feature
import org.bdgenomics.adam.rdd.ADAMContext._

object BedReader extends GenomicReader[Feature, FeatureDataset] with Logging {

  def suffixes = Array(".bed", ".bed.gz", ".narrowPeak")

  val codec: BEDCodec = new BEDCodec()

  def load(fp: String, regions: Iterable[ReferenceRegion], local: Boolean = true): Iterator[Feature] = {

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

    val dictionary = reader.getSequenceNames()

    val queries =
      if (!dictionary.isEmpty) {

        // modify chr prefix, if this file uses chr prefixes.
        val hasChrPrefix = dictionary.get(0).startsWith("chr")

        regions.map(r => {
          LazyMaterialization.modifyChrPrefix(r, hasChrPrefix)
        })
      } else {
        regions.map(r => {
          Iterable(LazyMaterialization.modifyChrPrefix(r, true), LazyMaterialization.modifyChrPrefix(r, false))
        }).flatten
      }

    val results: Iterable[BEDFeature] =
      if (reader.isQueryable) {
        val tmp = queries.map(r => reader.query(r.referenceName, r.start.toInt, r.end.toInt).toList)
        reader.close()
        tmp.flatten
      } else {
        // in the case where file is remote and doesnt have an index, still want to get data
        // TODO: really inefficient. the data should be only pulled once
        val iter: Iterator[BEDFeature] = reader.iterator()
        reader.close()
        iter.map(r => {
          val overlaps = queries.filter(q => q.overlaps(ReferenceRegion(r.getContig, r.getStart, r.getEnd))).size > 0
          if (overlaps)
            Some(r)
          else None
        }).flatten.toIterable
      }

    // map results to ADAM features
    results.map(r => Feature.newBuilder()
      .setFeatureType(r.getType)
      .setReferenceName(r.getContig)
      .setStart(r.getStart.toLong)
      .setEnd(r.getEnd.toLong).build()).toIterator
  }

  def loadHttp(url: String, regions: Iterable[ReferenceRegion]): Iterator[Feature] = {
    load(url, regions, false)
  }

  /**
   * Loads data from bam files (indexed or unindexed) from s3.
   *
   * @param regions Iterable of ReferenceRegions to load
   * @param path filepath to load from
   * @return Alignment dataset from the file over specified ReferenceRegion
   */
  def loadS3(path: String, regions: Iterable[ReferenceRegion]): Iterator[Feature] = {
    throw new Exception("Not implemented")

  }

  /**
   * Loads data from bam files (indexed or unindexed) from HDFS.
   * @param sc SparkContext
   * @param regions Iterable of ReferenceRegions to load
   * @param fp filepath to load from
   * @return Alignment dataset from the file over specified ReferenceRegion
   */
  def loadHDFS(sc: SparkContext, fp: String, regions: Iterable[ReferenceRegion]): FeatureDataset = {
    // if regions are specified, specifically load regions. Otherwise, load all data
    val predicateRegions = regions
      .flatMap(r => LazyMaterialization.getReferencePredicate(r))
      .toArray

    sc.loadFeatures(fp)
      .transform(rdd => rdd.filter(g =>
        !predicateRegions.filter(r => ReferenceRegion.unstranded(g).overlaps(r)).isEmpty))
  }

  private def createIndex(fp: String, codec: BEDCodec) = {

    val file = new java.io.File(fp)
    val idxFile = new java.io.File(file + ".idx")

    // do not re-generate index file
    if (!idxFile.exists()) {

      log.warn(s"No index file for ${file.getAbsolutePath} found. Generating ${idxFile.getAbsolutePath}...")

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