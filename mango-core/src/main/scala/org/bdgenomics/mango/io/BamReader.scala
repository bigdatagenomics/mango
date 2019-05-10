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

import java.io.File

import htsjdk.samtools.{ QueryInterval, SAMFileHeader, SAMRecord, SamInputResource, SamReaderFactory, ValidationStringency }
import org.apache.spark.SparkContext
import org.bdgenomics.adam.models.{ ReferenceRegion, SequenceDictionary }
import org.bdgenomics.formats.avro.AlignmentRecord
import org.bdgenomics.mango.converters.SAMRecordConverter
import org.seqdoop.hadoop_bam.util.SAMHeaderReader

import scala.collection.JavaConversions._
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.adam.rdd.read.AlignmentRecordDataset
import org.bdgenomics.utils.misc.Logging
import org.bdgenomics.mango.models.LazyMaterialization

object BamReader extends GenomicReader[SAMFileHeader, AlignmentRecord, AlignmentRecordDataset] with Logging {

  def suffixes = Array(".bam", ".sam")

  def getBamIndex(path: String): String = {
    path + ".bai"
  }

  /**
   * Loads records from bam or sam file overlapping queried reference regions.
   *
   * @param fp filepath
   * @param regionsOpt Option of iterator of ReferenceRegions to query
   * @param local Boolean specifying whether file is local or remote.
   * @return Iterator of AlignmentRecords
   */
  def load(fp: String, regionsOpt: Option[Iterable[ReferenceRegion]], local: Boolean): Tuple2[SAMFileHeader, Array[AlignmentRecord]] = {

    // if not valid throw Exception
    if (!isValidSuffix(fp)) {
      invalidFileException(fp)
    }

    val indexUrl = getBamIndex(fp)

    val reader = if (local) {
      if (fp.endsWith(".bam"))
        SamReaderFactory.makeDefault().open(SamInputResource.of(new File(fp)).index(new File(indexUrl)))
      else // sam file
        SamReaderFactory.makeDefault().open(SamInputResource.of(new File(fp)))
    } else {
      SamReaderFactory.makeDefault().open(
        SamInputResource.of(new java.net.URL(fp)).index(new java.net.URL(indexUrl)))
    }

    val header = reader.getFileHeader
    val dictionary = header.getSequenceDictionary

    // no valid dictionary. Cannot query or filter data.
    if (dictionary.getSequences.isEmpty) {
      reader.close()
      return (header, Array[AlignmentRecord]())
    }

    // modify chr prefix, if this file uses chr prefixes.
    val hasChrPrefix = dictionary.getSequences.head.getSequenceName.startsWith("chr")

    val samRecordConverter = new SAMRecordConverter

    if (regionsOpt.isDefined) {
      val regions = regionsOpt.get
      val queries: Array[QueryInterval] = regions.map(r => {
        val modified = LazyMaterialization.modifyChrPrefix(r, hasChrPrefix)

        val contig = dictionary.getSequence(modified.referenceName)
        // check if contig is in dictionary. If not in dictionary, corresponding records will not be in the file.
        if (contig != null)
          Some(new QueryInterval(dictionary.getSequence(modified.referenceName).getSequenceIndex,
            modified.start.toInt, modified.end.toInt))
        else
          None
      }).toArray.flatten

      if (reader.hasIndex) {

        val results = reader.query(queries, false).map(p =>
          try {
            Some(samRecordConverter.convert(p)) // filter out reads with conversion issues
          } catch {
            case e: Exception => None
          }).toArray.flatten

        reader.close()

        (header, results)

      } else {
        val iter: Iterator[SAMRecord] = reader.iterator()

        val samRecords: Array[SAMRecord] = iter.toArray.filter(!_.getReadUnmappedFlag) // read should be mapped
          .map(r => {
            val overlaps = queries.filter(q => q.overlaps(new QueryInterval(dictionary.getSequence(r.getContig).getSequenceIndex,
              r.getStart, r.getEnd))).size > 0
            if (overlaps)
              Some(r)
            else None
          }).flatten

        // close reader after records have been searched
        reader.close()

        // map SamRecords to ADAM
        (header, samRecords.map(p => samRecordConverter.convert(p)))
      }
    } else {
      val samRecords = reader.iterator().toArray
      reader.close

      (header, samRecords.map(p => samRecordConverter.convert(p)))
    }

  }

  /**
   * Loads data from bam files (indexed or unindexed) from s3.
   *
   * @param regionsOpt Option of iterable of ReferenceRegions to load
   * @param path filepath to load from
   * @return Alignment dataset from the file over specified ReferenceRegion
   */
  def loadS3(path: String, regionsOpt: Option[Iterable[ReferenceRegion]]): Tuple2[SAMFileHeader, Array[AlignmentRecord]] = {
    throw new Exception("Not implemented")
  }

  /**
   * Loads data from bam files (indexed or unindexed) from HDFS.
   *
   * @param sc SparkContext
   * @param regionsOpt Iterable of ReferenceRegions to load
   * @param fp filepath to load from
   * @return Alignment dataset from the file over specified ReferenceRegion
   */
  def loadHDFS(@transient sc: SparkContext, fp: String, regionsOpt: Option[Iterable[ReferenceRegion]]): Tuple2[AlignmentRecordDataset, Array[AlignmentRecord]] = {
    // hack to get around issue in hadoop_bam, which throws error if referenceName is not found in bam file
    val path = new org.apache.hadoop.fs.Path(fp)
    val fileSd = SequenceDictionary(SAMHeaderReader.readSAMHeaderFrom(path, sc.hadoopConfiguration))

    val dataset =
      if (regionsOpt.isDefined) {
        val regions = regionsOpt.get
        // start -1 because off by 1 error. IE. if fetching location 90-91, won't get alignments that start at 89.
        val predicateRegions: Iterable[ReferenceRegion] = regions.flatMap(r => {
          LazyMaterialization.getReferencePredicate(r).map(r => r.copy(start = Math.max(0, r.start - 1)))
        }).filter(r => fileSd.containsReferenceName(r.referenceName))

        try {
          sc.loadIndexedBam(fp, predicateRegions, stringency = ValidationStringency.SILENT)
        } catch {
          case e: Exception => { // IllegalArgumentException if local or FileNotFoundException if hdfs
            log.warn(e.getMessage)
            log.warn("No bam index detected. File loading will be slow...")
            sc.loadBam(fp, stringency = ValidationStringency.SILENT).filterByOverlappingRegions(predicateRegions)
          }
        }
      } else {
        sc.loadBam(fp, stringency = ValidationStringency.SILENT)
      }

    (dataset, dataset.rdd.collect)

  }
}
