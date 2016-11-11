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
import org.bdgenomics.adam.projections.{ GenotypeField, Projection }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.adam.rdd.variation.GenotypeRDD
import org.bdgenomics.formats.avro.Genotype
import org.bdgenomics.mango.layout.{ GenotypeJson, VariantJson }

import scala.reflect.ClassTag

/*
 * Handles loading and tracking of data from persistent storage into memory for Genotype data.
 * @see LazyMaterialization.scala
 */
class GenotypeMaterialization(s: SparkContext,
                              filePaths: List[String],
                              dict: SequenceDictionary) extends LazyMaterialization[Genotype]("GenotypeRDD")
    with Serializable {

  @transient val sc = s
  @transient implicit val formats = net.liftweb.json.DefaultFormats
  val sd = dict
  val files = filePaths
  def getReferenceRegion = (g: Genotype) => ReferenceRegion(g.getContigName, g.getStart, g.getEnd)
  def load = (region: ReferenceRegion, file: String) => GenotypeMaterialization.load(sc, Some(region), file).rdd

  /**
   * Stringifies data from genotypes to lists of variants and genotypes over the requested regions
   *
   * @param data RDD of  filtered (sampleId, Genotype)
   * @return Map of (key, json) for the ReferenceRegion specified
   * N
   */
  def stringify(data: RDD[(String, Genotype)]): Map[String, String] = {

    val flattened: Map[String, Array[(String, VariantJson)]] = data
      .collect
      .groupBy(_._1)
      .mapValues(v => {
        v.map(r => {
          (r._2.getSampleId, VariantJson(r._2.getContigName, r._2.getStart,
            r._2.getVariant.getReferenceAllele, r._2.getVariant.getAlternateAllele, r._2.getEnd))
        })
      })

    // stringify genotypes and group
    val genotypes: Map[String, Array[GenotypeJson]] =
      flattened.mapValues(v => {
        v.groupBy(_._2).mapValues(r => r.map(_._1))
          .map(r => GenotypeJson(r._2, r._1)).toArray
      })

    // write variants and genotypes to json
    genotypes.mapValues(v => write(v))
  }
}

object GenotypeMaterialization {

  def apply[T: ClassTag, C: ClassTag](sc: SparkContext, files: List[String], dict: SequenceDictionary): GenotypeMaterialization = {
    new GenotypeMaterialization(sc, files, dict)
  }

  def load(sc: SparkContext, region: Option[ReferenceRegion], fp: String): GenotypeRDD = {
    val genotypes: GenotypeRDD =
      if (fp.endsWith(".adam")) {
        loadAdam(sc, region, fp)
      } else if (fp.endsWith(".vcf")) {
        region match {
          case Some(_) => sc.loadGenotypes(fp).transform(rdd =>
            rdd.filter(g => (g.getContigName == region.get.referenceName && g.getStart < region.get.end
              && g.getEnd > region.get.start)))
          case None => sc.loadGenotypes(fp)
        }
      } else {
        throw UnsupportedFileException("File type not supported")
      }

    val key = LazyMaterialization.filterKeyFromFile(fp)
    // map unique ids to features to be used in tiles
    genotypes.transform(rdd =>
      rdd.map(r => {
        if (r.getSampleId == null)
          r.setSampleId(key)
        r
      }))
  }

  def loadAdam(sc: SparkContext, region: Option[ReferenceRegion], fp: String): GenotypeRDD = {
    val pred: Option[FilterPredicate] =
      region match {
        case Some(_) => Some((LongColumn("variant.end") >= region.get.start) && (LongColumn("variant.start") <= region.get.end) && (BinaryColumn("variant.contig.contigName") === region.get.referenceName))
        case None    => None
      }
    val proj = Projection(GenotypeField.variant, GenotypeField.alleles, GenotypeField.sampleId)
    sc.loadParquetGenotypes(fp, predicate = pred, projection = Some(proj))
  }
}
