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

import net.liftweb.json.Serialization.write
import org.apache.parquet.filter2.dsl.Dsl._
import org.apache.parquet.filter2.predicate.FilterPredicate
import org.apache.spark._
import org.apache.spark.rdd.RDD
import org.bdgenomics.adam.models.{ ReferenceRegion, SequenceDictionary }
import org.bdgenomics.adam.projections.{ Projection, VariantField }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.adam.rdd.variant.{ VariantContextRDD }
import org.bdgenomics.formats.avro.{ Variant, GenotypeAllele }
import org.bdgenomics.mango.layout.GenotypeJson

/*
 * Handles loading and tracking of data from persistent storage into memory for Variant data.
 * @see LazyMaterialization.scala
 */
class VariantContextMaterialization(@transient sc: SparkContext,
                                    files: List[String],
                                    sd: SequenceDictionary,
                                    prefetchSize: Option[Int] = None)
    extends LazyMaterialization[GenotypeJson]("VariantContextRDD", sc, files, sd, prefetchSize)
    with Serializable {

  @transient implicit val formats = net.liftweb.json.DefaultFormats
  // placeholder used for ref/alt positions to display in browser
  val variantPlaceholder = "N"

  /**
   * Extracts ReferenceRegion from Variant
   *
   * @return extracted ReferenceRegion
   */
  def getReferenceRegion = (g: GenotypeJson) => ReferenceRegion(g.variant)

  /**
   * Loads VariantContext Data into GenotypeJson format
   *
   * @return Generic RDD of data types from file
   */
  def load = (file: String, region: Option[ReferenceRegion]) =>
    VariantContextMaterialization.toGenotypeJsonRDD(VariantContextMaterialization.load(sc, file, region))

  /**
   * Reset ReferenceName for Variant
   *
   * @return Variant with new ReferenceRegion
   */
  def setContigName = (g: GenotypeJson, contig: String) => {
    g.variant.setContigName(contig)
    g.copy()
  }

  /**
   * Stringifies data from variants to lists of variants over the requested regions
   *
   * @param data RDD of  filtered (key, GenotypeJson)
   * @return Map of (key, json) for the ReferenceRegion specified
   * N
   */
  def stringify(data: RDD[(String, GenotypeJson)]): Map[String, String] = {

    val flattened: Map[String, Array[String]] = data
      .collect
      .groupBy(_._1).map(r => (r._1, r._2.map(_._2.toString())))

    // write variants to json
    flattened.mapValues(write(_))
  }

  /**
   * Formats raw data from RDD to JSON.
   *
   * @param region Region to obtain coverage for
   * @param binning Tells what granularity of coverage to return. Used for large regions
   * @return JSONified data map;
   */
  def getJson(region: ReferenceRegion,
              showGenotypes: Boolean,
              binning: Int = 1): Map[String, String] = {
    val data: RDD[(String, GenotypeJson)] = get(region)

    val binnedData: RDD[(String, GenotypeJson)] =
      if (binning <= 1) {
        if (!showGenotypes)
          data.map(r => (r._1, GenotypeJson(r._2.variant, null)))
        else data
      } else {
        bin(data, binning)
          .map(r => {
            // Reset variant to match binned region
            val start = r._1._2.start
            val binned = Variant.newBuilder(r._2.variant)
              .setStart(start)
              .setEnd(Math.max(r._2.variant.getEnd, start + binning))
              .setReferenceAllele(variantPlaceholder)
              .setAlternateAllele(variantPlaceholder)
              .build()
            (r._1._1, GenotypeJson(binned))
          })
      }
    stringify(binnedData)
  }

  /**
   * Gets all SampleIds for all genotypes in each file. If no genotypes are available, will return an empty sequence.
   *
   * @return List of filenames their corresponding Seq of SampleIds.
   */
  def getGenotypeSamples(): List[(String, List[String])] = {
    files.map(fp => (fp, VariantContextMaterialization.load(sc, fp, None).samples.map(_.getSampleId).toList))
  }
}

/**
 * VariantContextMaterialization object, used to load VariantContext data into a VariantContextRDD. Supported file
 * formats are vcf and adam.
 */
object VariantContextMaterialization {

  /**
   * Loads variant data from adam and vcf files into a VariantContextRDD
   *
   * @param sc SparkContext
   * @param fp filePath to load
   * @param region Region to predicate load
   * @return VariantContextRDD
   */
  def load(sc: SparkContext, fp: String, region: Option[ReferenceRegion]): VariantContextRDD = {
    if (fp.endsWith(".adam")) {
      loadAdam(sc, fp, region)
    } else {
      try {
        loadVariantContext(sc, fp, region)
      } catch {
        case e: Exception => {
          val sw = new StringWriter
          e.printStackTrace(new PrintWriter(sw))
          throw UnsupportedFileException("File type not supported. Stack trace: " + sw.toString)
        }
      }
    }
  }

  /**
   * Loads VariantContextRDD from a vcf file. vcf tbi index is required.
   *
   * @param sc SparkContext
   * @param fp filePath to vcf file
   * @param region Region to predicate load
   * @return VariantContextRDD
   */
  def loadVariantContext(sc: SparkContext, fp: String, region: Option[ReferenceRegion]): VariantContextRDD = {
    region match {
      case Some(_) =>
        val regions = LazyMaterialization.getContigPredicate(region.get)
        sc.loadIndexedVcf(fp, Iterable(regions._1, regions._2))
      case None => sc.loadVcf(fp)
    }
  }

  /**
   * Loads adam variant files
   *
   * @param sc SparkContext
   * @param fp filePath to load variants from
   * @param region Region to predicate load
   * @return VariantContextRDD
   */
  def loadAdam(sc: SparkContext, fp: String, region: Option[ReferenceRegion]): VariantContextRDD = {
    val pred: Option[FilterPredicate] =
      region match {
        case Some(_) =>
          val contigs = LazyMaterialization.getContigPredicate(region.get)
          val contigPredicate = (BinaryColumn("variant.contig.contigName") === contigs._1.referenceName
            || BinaryColumn("variant.contig.contigName") === contigs._2.referenceName)
          Some((LongColumn("variant.end") >= region.get.start) && (LongColumn("variant.start") <= region.get.end) && contigPredicate)
        case None => None
      }
    val proj = Projection(VariantField.contigName, VariantField.start, VariantField.referenceAllele, VariantField.alternateAllele, VariantField.end)
    sc.loadParquetGenotypes(fp, predicate = pred, projection = Some(proj)).toVariantContextRDD
  }

  /**
   * Converts VariantContextRDD into RDD of Variants and Genotype SampleIds that can be directly converted to json
   *
   * @param v VariantContextRDD to Convert
   * @return Converted json RDD
   */
  private def toGenotypeJsonRDD(v: VariantContextRDD): RDD[GenotypeJson] = {
    v.rdd.map(r => {
      // filter out genotypes with only reference alleles
      val genotypes = r.genotypes.filter(_.getAlleles.toArray.filter(_ != GenotypeAllele.REF).length > 0)
      new GenotypeJson(r.variant.variant, genotypes.map(_.getSampleId).toArray)
    })
  }
}
