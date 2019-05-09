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

import net.liftweb.json.Serialization.write
import org.apache.spark._
import org.bdgenomics.adam.models.{ VariantContext, ReferenceRegion, SequenceDictionary }
import org.bdgenomics.adam.projections.{ Projection, VariantField }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.adam.rdd.variant.{ GenotypeDataset, VariantContextDataset }
import org.bdgenomics.formats.avro.{ Sample, Variant, GenotypeAllele }
import org.bdgenomics.mango.core.util.ResourceUtils
import org.bdgenomics.mango.io.VcfReader
import org.slf4j.LoggerFactory
import scala.collection.JavaConversions._
import org.bdgenomics.mango.converters.GA4GHutil._

/**
 * Handles loading and tracking of data from persistent storage into memory for Variant data.
 *
 * @param sc SparkContext
 * @param files list files to materialize
 * @param sd the sequence dictionary associated with the file records
 * @param prefetchSize the number of base pairs to prefetch in executors. Defaults to 1000000
 * @see LazyMaterialization.scala
 */
class VariantContextMaterialization(@transient sc: SparkContext,
                                    files: List[String],
                                    sd: SequenceDictionary,
                                    prefetchSize: Option[Long] = None)
    extends LazyMaterialization[VariantContext, ga4gh.Variants.Variant](VariantContextMaterialization.name, sc, files, sd, prefetchSize)
    with Serializable {

  // placeholder used for ref/alt positions to display in browser
  val variantPlaceholder = "N"

  // Map of filenames to Samples for that file
  @transient val samples: Map[String, Seq[Sample]] = getGenotypeSamples()

  /**
   * Extracts ReferenceRegion from Variant
   *
   * @return extracted ReferenceRegion
   */
  def getReferenceRegion = (g: VariantContext) => ReferenceRegion(g.position)

  /**
   * Reset ReferenceName for Feature
   *
   * @param f      VariantContext to be modified
   * @param contig to replace Feature contigName
   * @return Feature with new ReferenceRegion
   */
  def setReferenceName = (f: VariantContext, contig: String) => {
    val variant = Variant.newBuilder(f.variant.variant)
      .setReferenceName(contig).build()

    VariantContext(variant, f.genotypes)
  }

  /**
   * Loads VariantContext Data
   *
   * @return Generic RDD of data types from file
   */
  def load = (file: String, regions: Option[Iterable[ReferenceRegion]]) =>
    VariantContextMaterialization.load(sc, file, regions)._2

  /**
   * Stringifies data from variants to lists of variants over the requested regions
   *
   * @param data RDD of  filtered (key, GenotypeJson)
   * @return Map of (key, json) for the ReferenceRegion specified
   *
   */
  def toJson(data: Array[(String, VariantContext)]): Map[String, Array[ga4gh.Variants.Variant]] = {
    data.groupBy(_._1).mapValues(r => {
      r.map(a => variantContextToGAVariant(a._2))
    })
  }

  /**
   * Formats raw data from GA4GH Variants Response to JSON.
   *
   * @param data An array of GA4GH Variants
   * @return JSONified data
   */
  def stringify = (data: Array[ga4gh.Variants.Variant]) => {
    // write message
    val message = ga4gh.VariantServiceOuterClass
      .SearchVariantsResponse.newBuilder().addAllVariants(data.toList)
      .build()

    com.google.protobuf.util.JsonFormat.printer().includingDefaultValueFields().print(message)
  }

  /**
   * Gets all SampleIds for all genotypes in each file. If no genotypes are available, will return an empty sequence.
   *
   * @return List of filenames their corresponding Seq of SampleIds.
   */
  def getGenotypeSamples(): Map[String, List[Sample]] = {

    files.map(fp => (fp, VariantContextMaterialization.load(sc, fp, Some(Iterable()))._1.toList)).toMap
  }
}

/**
 * VariantContextMaterialization object, used to load VariantContext data into a VariantContextDataset. Supported file
 * formats are vcf and adam.
 */
object VariantContextMaterialization {

  val name = "VariantContext"
  val datasetCache = new collection.mutable.HashMap[String, GenotypeDataset]

  /**
   * Loads variant data from adam and vcf files into a VariantContextDataset
   *
   * @param sc SparkContext
   * @param fp filePath to load
   * @param regions Iterable of ReferenceRegions to predicate load
   * @return VariantContextDataset
   */
  def load(sc: SparkContext, fp: String, regions: Option[Iterable[ReferenceRegion]]): Tuple2[Seq[Sample], Array[VariantContext]] = {
    if (fp.endsWith(".adam")) {
      val results = loadAdam(sc, fp, regions)
      (results.samples, results.rdd.collect())
    } else {
      VcfReader.loadDataAndSamplesFromSource(fp, regions, Some(sc))
    }
  }

  /**
   * Loads adam variant files
   *
   * @param sc SparkContext
   * @param fp filePath to load variants from
   * @param regions Iterable of  ReferenceRegions to predicate load
   * @return VariantContextDataset
   */
  def loadAdam(sc: SparkContext, fp: String, regions: Option[Iterable[ReferenceRegion]]): VariantContextDataset = {

    val variantContext =
      if (regions.isDefined) {
        if (sc.isPartitioned(fp)) {

          // finalRegions includes references both with and without "chr" prefix
          val finalRegions: Iterable[ReferenceRegion] = regions.get ++ regions.get
            .map(x => ReferenceRegion(x.referenceName.replaceFirst("""^chr""", """"""),
              x.start,
              x.end,
              x.strand))

          // load new dataset or retrieve from cache
          val data: GenotypeDataset = datasetCache.get(fp) match {
            case Some(ds) => { // if dataset found in datasetCache
              ds
            }
            case _ => {
              // load dataset into cache and use use it
              datasetCache(fp) = sc.loadPartitionedParquetGenotypes(fp)
              datasetCache(fp)
            }
          }

          val maybeFiltered: GenotypeDataset = if (finalRegions.nonEmpty) {
            data.filterByOverlappingRegions(finalRegions)
          } else data

          maybeFiltered.toVariantContexts()

        } else {
          val pred = {
            val prefixRegions: Iterable[ReferenceRegion] = regions.get.map(r => LazyMaterialization.getReferencePredicate(r)).flatten
            Some(ResourceUtils.formReferenceRegionPredicate(prefixRegions))
          }
          sc.loadParquetGenotypes(fp, optPredicate = pred).toVariantContexts()

        }
      } else {
        if (sc.isPartitioned(fp)) {

          // load new dataset or retrieve from cache
          val data: GenotypeDataset = datasetCache.get(fp) match {
            case Some(ds) => { // if dataset found in datasetCache
              ds
            }
            case _ => {
              // load dataset into cache and use use it
              datasetCache(fp) = sc.loadPartitionedParquetGenotypes(fp)
              datasetCache(fp)
            }
          }

          data.toVariantContexts()

        } else {
          sc.loadGenotypes(fp).toVariantContexts()
        }
      }

    variantContext
  }
}
