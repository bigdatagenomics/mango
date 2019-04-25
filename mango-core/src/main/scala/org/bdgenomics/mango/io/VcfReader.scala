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

import java.io.{ BufferedInputStream, FileOutputStream, BufferedOutputStream }
import java.nio.file.Path
import java.util.zip.GZIPInputStream

import com.google.common.io.Resources
import htsjdk.samtools.ValidationStringency
import htsjdk.samtools.seekablestream.{ ByteArraySeekableStream, SeekableStream }
import htsjdk.tribble.index.IndexFactory
import htsjdk.tribble.index.tabix.TabixFormat
import htsjdk.tribble.readers.LineIterator
import htsjdk.tribble.util.LittleEndianOutputStream
import htsjdk.variant.variantcontext.{ VariantContext => HtsjdkVariantContext }
import htsjdk.variant.vcf.{ VCFHeader, VCFHeaderLine, VCFCodec, VCFFileReader }
import org.apache.spark.SparkContext
import org.bdgenomics.adam.converters.VariantContextConverter
import org.bdgenomics.adam.rdd.variant.VariantContextDataset
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.adam.models.{ ReferenceRegion, VariantContext => ADAMVariantContext }
import org.bdgenomics.mango.models.LazyMaterialization
import org.bdgenomics.utils.misc.Logging
import scala.collection.JavaConversions._
import htsjdk.tribble.index.Index
import htsjdk.tribble.AbstractFeatureReader

object VcfReader extends GenomicReader[ADAMVariantContext, VariantContextDataset] with Logging {

  val codec = new VCFCodec()

  def loadLocal(fp: String, regions: Iterable[ReferenceRegion]): Iterator[ADAMVariantContext] = {

    val isGzipped = fp.endsWith(".gz")

    // create index file
    createIndex(fp, codec)

    val vcfReader: AbstractFeatureReader[HtsjdkVariantContext, LineIterator] = AbstractFeatureReader.getFeatureReader(fp, codec)

    val queries =
      try {
        val dictionary = vcfReader.getSequenceNames()

        // modify chr prefix, if this file uses chr prefixes.
        val hasChrPrefix = dictionary.head.startsWith("chr")

        regions.map(r => {
          LazyMaterialization.modifyChrPrefix(r, hasChrPrefix)
        })
      } catch {
        case e: NullPointerException => {
          log.warn(s"File ${fp} did not have sequence header")
          regions
        }
      }

    val results: Iterable[HtsjdkVariantContext] =
      if (isGzipped)
        queries.map(r => vcfReader.query(r.referenceName, r.start.toInt + 1, r.end.toInt).toList).flatten
      else
        queries.map(r => vcfReader.query(r.referenceName, r.start.toInt, r.end.toInt).toList).flatten // max because tabixReader subtracts 1

    val header: VCFHeader = vcfReader.getHeader().asInstanceOf[VCFHeader]

    val converter = new VariantContextConverter(
      getHeaderLines(header),
      ValidationStringency.LENIENT, false)

    vcfReader.close()

    results.map(r => converter.convert(r)).flatten.toIterator
  }

  def loadHttp(url: String, regions: Iterable[ReferenceRegion]): Iterator[ADAMVariantContext] = {

    // TODO
    val reader = new VCFFileReader(new java.io.File(url), false)

    val dictionary = reader.getFileHeader.getSequenceDictionary

    // modify chr prefix, if this file uses chr prefixes.
    val hasChrPrefix = dictionary.getSequences.head.getSequenceName.startsWith("chr")

    val queries = regions.map(r => {
      LazyMaterialization.modifyChrPrefix(r, hasChrPrefix)
    })

    val results = queries.map(r => reader.query(r.referenceName, r.start.toInt, r.end.toInt).toList)

    reader.close()

    val converter = new VariantContextConverter(
      getHeaderLines(reader.getFileHeader),
      ValidationStringency.LENIENT, false)

    results.flatten.map(r => converter.convert(r)).flatten.toIterator
  }

  /**
   * Loads data from bam files (indexed or unindexed) from s3.
   * @param regions Iterable of ReferenceRegions to load
   * @param fp filepath to load from
   * @return Alignment dataset from the file over specified ReferenceRegion
   */
  def loadS3(path: String, regions: Iterable[ReferenceRegion]): Iterator[ADAMVariantContext] = {
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
  def loadHDFS(sc: SparkContext, fp: String, regions: Iterable[ReferenceRegion]): VariantContextDataset = {
    val predicateRegions: Iterable[ReferenceRegion] = regions
      .flatMap(r => LazyMaterialization.getReferencePredicate(r))
    sc.loadIndexedVcf(fp, predicateRegions)
  }

  private def createIndex(fp: String, codec: VCFCodec) = {

    val file = new java.io.File(fp)
    val isGzipped = fp.endsWith(".gz")

    val idxFile: java.io.File = if (isGzipped) new java.io.File(file + ".tbi") else new java.io.File(file + ".idx")

    // do not re-generate index file
    if (!idxFile.exists()) {
      log.warn(s"No index file for ${file.getAbsolutePath} found. Generating ${idxFile.getAbsolutePath}...")

      // Create the index
      val idx: Index =
        if (!isGzipped) {
          IndexFactory.createIntervalIndex(file, codec)
        } else {
          IndexFactory.createTabixIndex(file, new VCFCodec(),
            TabixFormat.VCF,
            new VCFFileReader(file, false).getFileHeader().getSequenceDictionary())

        }

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

  // TODO already defined in ADAM in VariantContextConverter line 266
  private def getHeaderLines(header: VCFHeader): Seq[VCFHeaderLine] = {
    (header.getFilterLines ++
      header.getFormatHeaderLines ++
      header.getInfoHeaderLines ++
      header.getOtherHeaderLines).toSeq
  }

  private def seekableStream(resource: String): SeekableStream = {
    new ByteArraySeekableStream(Resources.toByteArray(ClassLoader.getSystemClassLoader().getResource(resource)))
  }

}