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

import ga4gh.SequenceAnnotationServiceOuterClass.SearchFeaturesResponse
import ga4gh.{ SequenceAnnotations, Variants }
import net.liftweb.json.Serialization.write
import org.apache.parquet.filter2.dsl.Dsl._
import org.apache.parquet.filter2.predicate.FilterPredicate
import org.apache.spark._
import org.apache.spark.rdd.RDD
import org.bdgenomics.adam.models.{ ReferenceRegion, SequenceDictionary }
import org.bdgenomics.adam.projections.{ Projection, VariantField }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.adam.rdd.variant.VariantContextRDD
import org.bdgenomics.formats.avro.{ Feature, Genotype, GenotypeAllele, Variant }
import org.bdgenomics.mango.layout.GenotypeJson
import org.bdgenomics.adam.models.VariantContext
import ga4gh.VariantServiceOuterClass.{ SearchCallSetsResponse, SearchVariantsResponse }
import org.bdgenomics.mango.converters.GA4GHConverter
import org.bdgenomics.mango.core.util.ResourceUtils

import scala.collection.JavaConverters._
import scala.collection.immutable

/*
 * Handles loading and tracking of data from persistent storage into memory for Variant data.
 * @see LazyMaterialization.scala
 */
class VariantContextMaterialization(@transient sc: SparkContext,
                                    files: List[String],
                                    sd: SequenceDictionary,
                                    prefetchSize: Option[Long] = None)
    extends LazyMaterialization[VariantContext, VariantContext]("VariantContext", sc, files, sd, prefetchSize)
    with Serializable {

  //@transient implicit val formats = net.liftweb.json.DefaultFormats
  // placeholder used for ref/alt positions to display in browser
  val variantPlaceholder = "N"

  /**
   * Extracts ReferenceRegion from Variant
   *
   * @return extracted ReferenceRegion
   */
  def getReferenceRegion = (g: VariantContext) => ReferenceRegion(g.variant.variant)

  /**
   * Loads VariantContext Data into GenotypeJson format
   *
   * @return Generic RDD of data types from file
   */
  def load = (file: String, regions: Option[Iterable[ReferenceRegion]]) =>
    VariantContextMaterialization.load(sc, file, regions).rdd

  /**
   * Reset ReferenceName for Variant
   *
   * @return Variant with new ReferenceRegion
   */
  def setContigName = (g: VariantContext, contig: String) => {

    g.variant.variant.setContigName(contig)
    g
  }

  def stringifyGA4GH(data: Array[VariantContext]): String = {

    val variants: Seq[Variants.Variant] = data.map(l => GA4GHConverter.toGA4GHVariant(l)).toList
    val result: SearchVariantsResponse = ga4gh.VariantServiceOuterClass.SearchVariantsResponse.newBuilder()
      .addAllVariants(variants.toList.asJava).build()
    val resultJSON: String = com.google.protobuf.util.JsonFormat.printer().print(result)
    resultJSON
  }

  def stringifyLegacy(data: Array[VariantContext], binning: Int = 1): String = {

    val variants: Array[GenotypeJson] =
      if (binning <= 1) {
        data.map(l => {
          val genotypes = l.genotypes.filter(_.getAlleles.toArray.filter(_ != GenotypeAllele.REF).length > 0)
          new GenotypeJson(l.variant.variant, genotypes.map(_.getSampleId).toArray)
        })
      } else {
        data.map(l => {
          val genotypes = l.genotypes.filter(_.getAlleles.toArray.filter(_ != GenotypeAllele.REF).length > 0)
          new GenotypeJson(l.variant.variant, Array.empty[String])
        })
      }
    write(variants.map(_.toString))
  }

  def toJson(rdd: RDD[(String, VariantContext)]): Map[String, Array[VariantContext]] = {

    rdd.collect
      .groupBy(_._1).map(r => (r._1, r._2.map(_._2)))
  }

  def stringifyFeatureGA4GH(data: collection.Seq[SequenceAnnotations.Feature]): String = {
    val result: SearchFeaturesResponse = ga4gh.SequenceAnnotationServiceOuterClass.SearchFeaturesResponse.newBuilder()
      .addAllFeatures(data.toList.asJava).build()

    val resultJSON: String = com.google.protobuf.util.JsonFormat.printer().print(result)
    resultJSON

  }

  def binRegionVars(r: ReferenceRegion, binning: Int): ReferenceRegion = {
    val start = r.start - (r.start % binning)
    r.copy(start = start, end = (start + binning))
  }

  def binVars(rdd: RDD[(String, VariantContext)], binning: Int): collection.Map[(String, ReferenceRegion), Long] = {
    val step1: RDD[((String, ReferenceRegion), VariantContext)] = rdd.map(r => ((r._1, binRegionVars(getReferenceRegion(r._2), binning)), r._2))
    val step2: collection.Map[(String, ReferenceRegion), Long] = step1.countByKey()
    step2

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
              binning: Int = 1): Map[String, Array[VariantContext]] = {

    val data: RDD[(String, VariantContext)] = get(Some(region))

    val binnedData: RDD[(String, VariantContext)] =
      if (binning <= 1) {
        if (!showGenotypes)
          data.map(r => (r._1, VariantContext(r._2.variant.variant)))
        else data
      } else {
        bin(data, binning)
          .map(r => {
            val start = r._1._2.start
            val binned: VariantContext = r.copy()._2
            binned.variant.variant.setStart(start)
            binned.variant.variant.setEnd(Math.max(r._2.variant.variant.getEnd, start + binning))
            binned.variant.variant.setReferenceAllele(variantPlaceholder)
            binned.variant.variant.setAlternateAllele(variantPlaceholder)
            (r._1._1, binned)
          })
      }

    //stringify(binnedData)
    binnedData.collect
      .groupBy(_._1).map(r => (r._1, r._2.map(_._2)))

  }

  def getJsonBinning(region: ReferenceRegion,
                     showGenotypes: Boolean,
                     binning: Int = 1): Array[Feature] = {

    val data: RDD[(String, VariantContext)] = get(Some(region))
    val featuresBinsStep1: collection.Map[(String, ReferenceRegion), Long] = binVars(data, binning)

    val featureBins: Map[(String, ReferenceRegion), Feature] = featuresBinsStep1
      .map(r => {
        val binned = new Feature()
        binned.setContigName(r._1._2.referenceName)
        binned.setStart(r._1._2.start)
        binned.setEnd(r._1._2.end)
        binned.setScore(r._2.toDouble)
        (r._1, binned)
      }).toMap

    val myFeatures: Array[Feature] = featureBins.values.toArray
    myFeatures
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

  val name = "VariantContext"
  /**
   * Loads variant data from adam and vcf files into a VariantContextRDD
   *
   * @param sc SparkContext
   * @param fp filePath to load
   * @param regions Region to predicate load
   * @return VariantContextRDD
   */
  def load(sc: SparkContext, fp: String, regions: Option[Iterable[ReferenceRegion]]): VariantContextRDD = {
    if (fp.endsWith(".adam")) {
      loadAdam(sc, fp, regions)
    } else {
      try {
        loadVariantContext(sc, fp, regions)
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
   * @param regions Region to predicate load
   * @return VariantContextRDD
   */
  def loadVariantContext(sc: SparkContext, fp: String, regions: Option[Iterable[ReferenceRegion]]): VariantContextRDD = {
    if (regions.isDefined) {
      val predicateRegions: Iterable[ReferenceRegion] = regions.get
        .flatMap(r => LazyMaterialization.getContigPredicate(r))
      sc.loadIndexedVcf(fp, predicateRegions)
    } else {
      sc.loadVcf(fp)
    }
  }

  /**
   * Loads adam variant files
   *
   * @param sc SparkContext
   * @param fp filePath to load variants from
   * @param regions Region to predicate load
   * @return VariantContextRDD
   */
  def loadAdam(sc: SparkContext, fp: String, regions: Option[Iterable[ReferenceRegion]]): VariantContextRDD = {
    val pred =
      if (regions.isDefined) {
        val prefixRegions: Iterable[ReferenceRegion] = regions.get.map(r => LazyMaterialization.getContigPredicate(r)).flatten
        Some(ResourceUtils.formReferenceRegionPredicate(prefixRegions))
      } else {
        None
      }
    sc.loadParquetGenotypes(fp, predicate = pred).toVariantContextRDD
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
