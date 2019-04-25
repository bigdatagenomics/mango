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

import htsjdk.samtools.{ SamInputResource, ValidationStringency, QueryInterval, SamReaderFactory }
import org.apache.spark.SparkContext
import org.bdgenomics.adam.models.{ SequenceDictionary, ReferenceRegion }
import org.bdgenomics.formats.avro.AlignmentRecord
import org.bdgenomics.mango.converters.SAMRecordConverter
import org.seqdoop.hadoop_bam.util.SAMHeaderReader
import scala.collection.JavaConversions._
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.adam.rdd.read.AlignmentRecordDataset
import org.bdgenomics.utils.misc.Logging
import org.bdgenomics.mango.models.LazyMaterialization

object BamReader extends GenomicReader[AlignmentRecord, AlignmentRecordDataset] with Logging {

  def getBamIndex(path: String): String = {
    path + ".bai"
  }

  def loadLocal(fp: String, regions: Iterable[ReferenceRegion]): Iterator[AlignmentRecord] = {

    val indexUrl = getBamIndex(fp)

    val reader = SamReaderFactory.makeDefault().open(SamInputResource.of(new File(fp)).index(new File(indexUrl)))

    val dictionary = reader.getFileHeader.getSequenceDictionary

    // modify chr prefix, if this file uses chr prefixes.
    val hasChrPrefix = dictionary.getSequences.head.getSequenceName.startsWith("chr") // TODO bad do not call .head

    val queries: Iterable[QueryInterval] = regions.map(r => {
      val modified = LazyMaterialization.modifyChrPrefix(r, hasChrPrefix)
      new QueryInterval(dictionary.getSequence(modified.referenceName).getSequenceIndex,
        modified.start.toInt, modified.end.toInt)
    })

    val t = reader.query(queries.toArray, false)

    val samRecordConverter = new SAMRecordConverter

    val results = t.map(p => samRecordConverter.convert(p)).toArray

    reader.close()

    results.toIterator //TODO this is pretty dumb. Remove after figuring out open/closing issue
  }

  def loadHttp(url: String, regions: Iterable[ReferenceRegion]): Iterator[AlignmentRecord] = {

    val indexUrl = getBamIndex(url)

    val reader = SamReaderFactory.makeDefault().open(
      SamInputResource.of(new java.net.URL(url)).index(new java.net.URL(indexUrl)))

    val dictionary = reader.getFileHeader.getSequenceDictionary

    // modify chr prefix, if this file uses chr prefixes.
    val hasChrPrefix = dictionary.getSequences.head.getSequenceName.startsWith("chr")

    val queries: Iterable[QueryInterval] = regions.map(r => {
      val modified = LazyMaterialization.modifyChrPrefix(r, hasChrPrefix)
      new QueryInterval(dictionary.getSequence(modified.referenceName).getSequenceIndex,
        modified.start.toInt, modified.end.toInt)
    })

    val t = reader.query(queries.toArray, false)

    val samRecordConverter = new SAMRecordConverter

    val results = t.map(p => samRecordConverter.convert(p)).toArray

    reader.close()

    results.toIterator //TODO this is pretty dumb. Remove after figuring out open/closing issue
  }

  /**
   * Loads data from bam files (indexed or unindexed) from s3.
   * @param regions Iterable of ReferenceRegions to load
   * @param fp filepath to load from
   * @return Alignment dataset from the file over specified ReferenceRegion
   */
  def loadS3(path: String, regions: Iterable[ReferenceRegion]): Iterator[AlignmentRecord] = {
    throw new Exception("Not implemented")
    // https://docs.aws.amazon.com/AmazonS3/latest/dev/RetrievingObjectUsingJava.html

    // https://www.programcreek.com/java-api-examples/?api=com.amazonaws.services.s3.AmazonS3URI

    // https://github.com/samtools/htsjdk/issues/635
  }

  /**
   * Loads data from bam files (indexed or unindexed) from HDFS.
   * @param sc SparkContext
   * @param regions Iterable of ReferenceRegions to load
   * @param fp filepath to load from
   * @return Alignment dataset from the file over specified ReferenceRegion
   */
  def loadHDFS(sc: SparkContext, fp: String, regions: Iterable[ReferenceRegion]): AlignmentRecordDataset = {
    // hack to get around issue in hadoop_bam, which throws error if referenceName is not found in bam file
    val path = new org.apache.hadoop.fs.Path(fp)
    val fileSd = SequenceDictionary(SAMHeaderReader.readSAMHeaderFrom(path, sc.hadoopConfiguration))

    // start -1 because off by 1 error. IE. if fetching location 90-91, won't get alignments that start at 89.
    val predicateRegions: Iterable[ReferenceRegion] = regions.flatMap(r => {
      LazyMaterialization.getReferencePredicate(r).map(r => r.copy(start = Math.max(0, r.start - 1)))
    }).filter(r => fileSd.containsReferenceName(r.referenceName))

    try {
      sc.loadIndexedBam(fp, predicateRegions, stringency = ValidationStringency.SILENT)
    } catch {
      case e: java.lang.IllegalArgumentException => {
        log.warn(e.getMessage)
        log.warn("No bam index detected. File loading will be slow...")
        sc.loadBam(fp, stringency = ValidationStringency.SILENT).filterByOverlappingRegions(predicateRegions)
      }
    }
  }
}