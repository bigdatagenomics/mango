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
import htsjdk.tribble.index.IndexFactory
import htsjdk.tribble.index.tabix.TabixFormat
import htsjdk.tribble.readers.LineIterator
import htsjdk.tribble.util.LittleEndianOutputStream
import htsjdk.variant.variantcontext.{ VariantContext => HtsjdkVariantContext }
import htsjdk.variant.vcf.{ VCFCodec, VCFFileReader, VCFHeader, VCFHeaderLine }
import org.apache.spark.SparkContext
import org.bdgenomics.adam.converters.VariantContextConverter
import org.bdgenomics.adam.ds.variant.VariantContextDataset
import org.bdgenomics.adam.ds.ADAMContext._
import org.bdgenomics.adam.models.{ ReferenceRegion, VariantContext => ADAMVariantContext }
import org.bdgenomics.mango.models.LazyMaterialization
import grizzled.slf4j.Logging
import org.bdgenomics.formats.avro.Sample
import scala.collection.JavaConversions._
import htsjdk.tribble.AbstractFeatureReader

object VcfReader extends GenomicReader[VCFHeader, ADAMVariantContext, VariantContextDataset] with Logging {

  def suffixes = Array(".vcf", ".vcf.gz", "bgzf.gz", "vcf.bgz")

  def isCompressed(fp: String): Boolean = fp.endsWith(".gz") | fp.endsWith("vcf.bgz")

  /**
   * Loads file from local filesystem, http or hdfs.
   *
   * @param fp url to file
   * @param regions Iterable of ReferenceRegions
   * @param sc Optional SparkContext, only used for HDFS files
   * @return Array of Specific Records
   */
  def loadDataAndSamplesFromSource(fp: String, regions: Option[Iterable[ReferenceRegion]], sc: Option[SparkContext] = None): Tuple2[Seq[Sample], Array[ADAMVariantContext]] = {

    if (new java.io.File(fp).exists) { // if fp exists in local filesystem, load locally
      val results = load(fp, regions, true)

      val genotypeSamples = results._1.getGenotypeSamples.map(s => {
        Sample.newBuilder()
          .setName(s)
          .setId(s)
          .build()
      })

      (genotypeSamples, results._2)

    } else if (fp.startsWith("http")) { // TODO check correctly
      // http file
      val results = load(fp, regions, false)

      val genotypeSamples = results._1.getGenotypeSamples.map(s => {
        Sample.newBuilder()
          .setName(s)
          .setId(s)
          .build()
      })

      (genotypeSamples, results._2)

    } else {
      require(sc.isDefined, "HDFS requires a SparkContext to run")
      val vcDataset = loadHDFS(sc.get, fp, regions)
      (vcDataset._1.samples, vcDataset._2)
    }
  }

  /**
   * *
   * Loads records from vcf or compressed vcf files overlapping queried reference regions.
   *
   * @param fp filepath
   * @param regionsOpt Option of iterator of ReferenceRegions to query
   * @param local Boolean specifying whether file is local or remote.
   * @return Iterator of VariantContext
   */
  def load(fp: String, regionsOpt: Option[Iterable[ReferenceRegion]], local: Boolean): Tuple2[VCFHeader, Array[ADAMVariantContext]] = {

    val codec = new VCFCodec()

    // if not valid throw Exception
    if (!isValidSuffix(fp)) {
      invalidFileException(fp)
    }

    if (local) {
      // create index file
      createIndex(fp, codec)
    }

    val vcfReader: AbstractFeatureReader[HtsjdkVariantContext, LineIterator] =
      AbstractFeatureReader.getFeatureReader(fp, codec, true)

    val header: VCFHeader = vcfReader.getHeader().asInstanceOf[VCFHeader]

    val results =
      if (regionsOpt.isDefined) {
        val regions = regionsOpt.get

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
              logger.warn(s"File ${fp} did not have sequence header")
              regions
            }
          }

        if (isCompressed(fp))
          queries.map(r => vcfReader.query(r.referenceName, r.start.toInt + 1, r.end.toInt).toList).flatten // +1 because tabixReader subtracts 1
        else
          queries.map(r => vcfReader.query(r.referenceName, r.start.toInt, r.end.toInt).toList).flatten
      } else {
        val iter: Iterator[HtsjdkVariantContext] = vcfReader.iterator()
        vcfReader.close()
        iter
      }

    val converter = new VariantContextConverter(
      getHeaderLines(header),
      ValidationStringency.LENIENT, false)

    vcfReader.close()

    val tmp = results.toArray.map(r => converter.convert(r)).flatten

    (header, tmp)
  }

  /**
   * Loads data from bam files (indexed or unindexed) from s3.
   *
   * @param regionsOpt Iterable of ReferenceRegions to load
   * @param path filepath to load from
   * @return Alignment dataset from the file over specified ReferenceRegion
   */
  def loadS3(path: String, regionsOpt: Option[Iterable[ReferenceRegion]]): Tuple2[VCFHeader, Array[ADAMVariantContext]] = {
    throw new Exception("Not implemented")
  }

  /**
   * Loads data from bam files (indexed or unindexed) from HDFS.
   * @param sc SparkContext
   * @param regionsOpt Option of iterable of ReferenceRegions to load
   * @param fp filepath to load from
   * @return Alignment dataset from the file over specified ReferenceRegion
   */
  def loadHDFS(sc: SparkContext, fp: String, regionsOpt: Option[Iterable[ReferenceRegion]]): Tuple2[VariantContextDataset, Array[ADAMVariantContext]] = {
    val dataset =
      if (regionsOpt.isDefined) {
        val regions = regionsOpt.get
        val predicateRegions: Iterable[ReferenceRegion] = regions
          .flatMap(r => LazyMaterialization.getReferencePredicate(r))
        sc.loadIndexedVcf(fp, predicateRegions)
      } else {
        sc.loadVcf(fp) // No regions specified, return all regions
      }

    (dataset, dataset.rdd.collect)
  }

  /**
   * Generates an index for a VCF file.
   * @param fp path to VCF or compressed VCF file
   * @param codec VCFCodec
   * @return path to index file
   */
  private def createIndex(fp: String, codec: VCFCodec): String = {

    val file = new java.io.File(fp)

    val idxFile: java.io.File = if (isCompressed(fp)) new java.io.File(file + ".tbi") else new java.io.File(file + ".idx")

    // do not re-generate index file
    if (!idxFile.exists()) {
      logger.warn(s"No index file for ${file.getAbsolutePath} found. Generating ${idxFile.getAbsolutePath}...")

      // Create the index
      if (!isCompressed(fp)) {
        val idx = IndexFactory.createIntervalIndex(file, codec)

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
  def getHeaderLines(header: VCFHeader): Seq[VCFHeaderLine] = {
    (header.getFilterLines ++
      header.getFormatHeaderLines ++
      header.getInfoHeaderLines ++
      header.getOtherHeaderLines)
  }
}
