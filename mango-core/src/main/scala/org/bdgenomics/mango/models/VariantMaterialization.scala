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
import org.apache.parquet.filter2.dsl.Dsl._
import org.apache.parquet.filter2.predicate.FilterPredicate
import org.apache.spark._
import org.apache.spark.rdd.RDD
import org.bdgenomics.adam.models.{ ReferenceRegion, SequenceDictionary }
import org.bdgenomics.adam.projections.{ Projection, VariantField }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.adam.rdd.variant.VariantRDD
import org.bdgenomics.formats.avro.Variant
import org.bdgenomics.mango.layout.VariantJson

import scala.reflect.ClassTag

/*
 * Handles loading and tracking of data from persistent storage into memory for Variant data.
 * @see LazyMaterialization.scala
 */
class VariantMaterialization(s: SparkContext,
                             filePaths: List[String],
                             dict: SequenceDictionary) extends LazyMaterialization[Variant]("VariantRDD")
    with Serializable {

  @transient val sc = s
  @transient implicit val formats = net.liftweb.json.DefaultFormats
  val sd = dict
  val files = filePaths
  val variantPlaceholder = "N"
  def getReferenceRegion = (v: Variant) => ReferenceRegion(v.getContigName, v.getStart, v.getEnd)
  def load = (region: ReferenceRegion, file: String) => VariantMaterialization.load(sc, Some(region), file).rdd

  /**
   * Stringifies data from variants to lists of variants over the requested regions
   *
   * @param data RDD of  filtered (sampleId, Variant)
   * @return Map of (key, json) for the ReferenceRegion specified
   * N
   */
  def stringify(data: RDD[(String, Variant)]): Map[String, String] = {

    val flattened: Map[String, Array[VariantJson]] = data
      .collect
      .groupBy(_._1)
      .mapValues(v => {
        v.map(r => {
          VariantJson(r._2.getContigName, r._2.getStart,
            r._2.getReferenceAllele, r._2.getAlternateAllele, r._2.getEnd)
        })
      })
    // write variants to json
    flattened.mapValues(v => write(v))
  }

  /**
   * Formats raw data from RDD to JSON.
   *
   * @param region Region to obtain coverage for
   * @param binning Tells what granularity of coverage to return. Used for large regions
   * @return JSONified data map;
   */
  def getVariants(region: ReferenceRegion, binning: Int = 1): Map[String, String] = {
    val data: RDD[(String, Variant)] = get(region)
    if (binning <= 1) {
      return stringify(data)
    } else {
      val binnedData = data
        .map(r => {
          // Add bin to key
          ((r._1, (r._2.getStart / binning).toInt), r._2)
        })
        .reduceByKey((a, b) => {
          if (a.getEnd < b.getEnd) {
            a.setEnd(b.getEnd)
          }
          if (a.getStart > b.getStart) {
            a.setStart(b.getStart)
          }
          // Determine if the ref alleles match and if the starting indices are the same (due to binning)
          if (a.getReferenceAllele != b.getReferenceAllele || a.getStart != b.getStart) {
            a.setReferenceAllele(variantPlaceholder)
          }
          // Determine if the alt alleles match and if the starting indices are the same (due to binning)
          if (a.getAlternateAllele != b.getAlternateAllele || a.getStart != b.getStart) {
            a.setAlternateAllele(variantPlaceholder)
          }
          a
        })
        .map(r => {
          // Remove bin from key
          (r._1._1, r._2)
        })
      stringify(binnedData)
    }
  }
}

object VariantMaterialization {

  def apply[T: ClassTag, C: ClassTag](sc: SparkContext, files: List[String], dict: SequenceDictionary): VariantMaterialization = {
    new VariantMaterialization(sc, files, dict)
  }

  def load(sc: SparkContext, region: Option[ReferenceRegion], fp: String): VariantRDD = {
    if (fp.endsWith(".adam")) {
      loadAdam(sc, region, fp)
    } else if (fp.endsWith(".vcf")) {
      region match {
        case Some(_) =>
          sc.loadVariants(fp).transform(rdd => rdd.filter(g =>
            (g.getContigName == region.get.referenceName && g.getStart < region.get.end
              && g.getEnd > region.get.start)))
        case None => sc.loadVariants(fp)
      }
    } else {
      throw UnsupportedFileException("File type not supported")
    }
  }

  def loadAdam(sc: SparkContext, region: Option[ReferenceRegion], fp: String): VariantRDD = {
    val pred: Option[FilterPredicate] =
      region match {
        case Some(_) => Some((LongColumn("variant.end") >= region.get.start) && (LongColumn("variant.start") <= region.get.end) && (BinaryColumn("variant.contig.contigName") === region.get.referenceName))
        case None    => None
      }
    val proj = Projection(VariantField.contigName, VariantField.start, VariantField.referenceAllele, VariantField.alternateAllele, VariantField.end)
    sc.loadParquetVariants(fp, predicate = pred, projection = Some(proj))
  }
}
