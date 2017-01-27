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

  // placeholder used for ref/alt positions to display in browser
  val variantPlaceholder = "N"

  /**
   * Extracts ReferenceRegion from Variant
   *
   * @param v Variant
   * @return extracted ReferenceRegion
   */
  def getReferenceRegion = (v: Variant) => ReferenceRegion(v.getContigName, v.getStart, v.getEnd)

  def load = (file: String, region: Option[ReferenceRegion]) => VariantMaterialization.load(sc, file, region).rdd

  /**
   * Reset ReferenceName for Variant
   *
   * @param v Variant to be modified
   * @param contig to replace Variant contigName
   * @return Variant with new ReferenceRegion
   */
  def setContigName = (v: Variant, contig: String) => {
    v.setContigName(contig)
    v
  }

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

  def load(sc: SparkContext, fp: String, region: Option[ReferenceRegion]): VariantRDD = {
    if (fp.endsWith(".adam")) {
      loadAdam(sc, fp, region)
    } else {
      try {
        region match {
          case Some(_) =>
            val regions = LazyMaterialization.getContigPredicate(region.get)
            sc.loadIndexedVcf(fp, Iterable(regions._1, regions._2)).toVariantRDD
          case None => sc.loadVariants(fp)
        }
      } catch {
        case e: Exception => {
          val sw = new StringWriter
          e.printStackTrace(new PrintWriter(sw))
          throw UnsupportedFileException("File type not supported. Stack trace: " + sw.toString)
        }
      }
    }
  }

  def loadAdam(sc: SparkContext, fp: String, region: Option[ReferenceRegion]): VariantRDD = {
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
    sc.loadParquetVariants(fp, predicate = pred, projection = Some(proj))
  }
}
