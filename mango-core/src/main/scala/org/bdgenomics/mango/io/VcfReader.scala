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

import java.io.{ BufferedOutputStream, FileOutputStream }

import htsjdk.samtools.ValidationStringency
import htsjdk.samtools.util.BlockCompressedInputStream
import htsjdk.tribble.index.IndexFactory
import htsjdk.tribble.index.tabix.TabixFormat
import htsjdk.tribble.readers.LineIterator
import htsjdk.tribble.util.LittleEndianOutputStream
import htsjdk.variant.variantcontext.{ VariantContext => HtsjdkVariantContext }
import htsjdk.variant.vcf.{ VCFCodec, VCFFileReader, VCFHeader, VCFHeaderLine }
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

  def suffixes = Array(".vcf", ".vcf.gz")

  val codec = new VCFCodec()

  /**
   * *
   * Loads records from vcf or compressed vcf files overlapping queried reference regions.
   *
   * @param fp filepath
   * @param regions Iterator of ReferenceRegions to query
   * @param local Boolean specifying whether file is local or remote.
   * @return Iterator of VariantContext
   */
  def load(fp: String, regions: Iterable[ReferenceRegion], local: Boolean = true): Iterator[ADAMVariantContext] = {

    // if not valid throw Exception
    if (!isValidSuffix(fp)) {
      invalidFileException(fp)
    }

    val isGzipped = fp.endsWith(".gz")

    if (local) {
      // create index file
      createIndex(fp, codec)
    }

    val vcfReader: AbstractFeatureReader[HtsjdkVariantContext, LineIterator] =
      AbstractFeatureReader.getFeatureReader(fp, codec, true)

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
        queries.map(r => vcfReader.query(r.referenceName, r.start.toInt + 1, r.end.toInt).toList).flatten // +1 because tabixReader subtracts 1
      else
        queries.map(r => vcfReader.query(r.referenceName, r.start.toInt, r.end.toInt).toList).flatten

    val header: VCFHeader = vcfReader.getHeader().asInstanceOf[VCFHeader]

    val converter = new VariantContextConverter(
      getHeaderLines(header),
      ValidationStringency.LENIENT, false)

    vcfReader.close()

    results.map(r => converter.convert(r)).flatten.toIterator
  }

  /**
   * *
   * Loads remote http file.
   *
   * @param url remote path to file
   * @param regions regions to filter file
   * @return Iterator of filtered VariantContext
   */
  def loadHttp(url: String, regions: Iterable[ReferenceRegion]): Iterator[ADAMVariantContext] = {
    load(url, regions, local = false)
  }

  /**
   * Loads data from bam files (indexed or unindexed) from s3.
   *
   * @param regions Iterable of ReferenceRegions to load
   * @param path filepath to load from
   * @return Alignment dataset from the file over specified ReferenceRegion
   */
  def loadS3(path: String, regions: Iterable[ReferenceRegion]): Iterator[ADAMVariantContext] = {
    throw new Exception("Not implemented")
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

  /**
   * Generates an index for a VCF file.
   * @param fp path to VCF or compressed VCF file
   * @param codec VCFCodec
   * @return path to index file
   */
  private def createIndex(fp: String, codec: VCFCodec): String = {

    val file = new java.io.File(fp)
    val isGzipped = fp.endsWith(".gz")

    val idxFile: java.io.File = if (isGzipped) new java.io.File(file + ".tbi") else new java.io.File(file + ".idx")

    // do not re-generate index file
    if (!idxFile.exists()) {
      log.warn(s"No index file for ${file.getAbsolutePath} found. Generating ${idxFile.getAbsolutePath}...")

      // Create the index
      if (!isGzipped) {
        val idx = IndexFactory.createIntervalIndex(file, codec)

        // TODO maybe not writing correctly
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

      } else {

        // get sequences from header for index file
        val tmpReader = new VCFFileReader(file, false)
        val sequences = tmpReader.getFileHeader().getSequenceDictionary()
        tmpReader.close()

        val idx = IndexFactory.createTabixIndex(file, new VCFCodec(),
          TabixFormat.VCF, sequences)

        idx.write(idxFile)

        idxFile.deleteOnExit()
      }

    }
    idxFile.getAbsolutePath
  }

  // TODO already defined in ADAM in VariantContextConverter line 266
  private def getHeaderLines(header: VCFHeader): Seq[VCFHeaderLine] = {
    (header.getFilterLines ++
      header.getFormatHeaderLines ++
      header.getInfoHeaderLines ++
      header.getOtherHeaderLines)
  }
}