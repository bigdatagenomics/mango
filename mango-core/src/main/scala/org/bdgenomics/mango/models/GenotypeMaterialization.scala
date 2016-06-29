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
import org.bdgenomics.formats.avro.Genotype
import org.bdgenomics.mango.tiling._
import org.bdgenomics.mango.util.Bookkeep
import org.bdgenomics.mango.layout.{ VariantJson, Coverage }

import scala.reflect.ClassTag

/*
 * Handles loading and tracking of data from persistent storage into memory for Genotype data.
 * @see LazyMaterialization.scala
 */
class GenotypeMaterialization(s: SparkContext,
                              filePaths: List[String],
                              d: SequenceDictionary,
                              parts: Int,
                              chunkS: Int) extends LazyMaterialization[Genotype, VariantTile]
    with KTiles[VariantTile] with Serializable {

  @transient val sc = s
  @transient implicit val formats = net.liftweb.json.DefaultFormats
  val dict = d
  val partitions = parts
  val chunkSize = chunkS
  val bookkeep = new Bookkeep(chunkSize)
  val files = filePaths
  def getStart = (g: Genotype) => g.getStart
  def toTile = (data: Iterable[(String, Genotype)], region: ReferenceRegion) => VariantTile(data, region)
  def load = (region: ReferenceRegion, file: String) => GenotypeMaterialization.load(sc, Some(region), file)

  /**
   * Define layers and underlying data types
   */
  val rawLayer: Layer = L0
  val freqLayer: Layer = L1

  /* If the RDD has not been initialized, initialize it to the first get request
    * Gets the data for an interval for the file loaded by checking in the bookkeeping tree.
    * If it exists, call get on the IntervalRDD
    * Otherwise call put on the sections of data that don't exist
    * Here, ks, is an option of list of personids (String)
    */
  def get(region: ReferenceRegion, layerOpt: Option[Layer] = None): Map[String, String] = {
    val seqRecord = dict(region.referenceName)
    seqRecord match {
      case Some(_) => {
        val regionsOpt = bookkeep.getMaterializedRegions(region, files, true)
        if (regionsOpt.isDefined) {
          for (r <- regionsOpt.get) {
            put(r)
          }
        }

        layerOpt match {
          case Some(_) => {
            val dataLayer: Layer = layerOpt.get

            // TODO: send back frequency for variants
            val layers = List(freqLayer, dataLayer)
            val data = getTiles(region, layers)
            val json = layers.map(layer => (layer, stringify(data.filter(_._1 == layer.id).flatMap(_._2), region, layer))).toMap
            json.get(dataLayer).get
          }
          case None => {
            val data = getTiles(region, Some(freqLayer))
            stringify(data, region, freqLayer)
          }
        }

      }
      case None => {
        throw new Exception("Not found in dictionary")
      }
    }
  }

  def stringify(data: RDD[(String, Iterable[Any])], region: ReferenceRegion, layer: Layer): Map[String, String] = {
    layer match {
      case `rawLayer`  => stringifyRawGenotypes(data, region)
      case `freqLayer` => Coverage.stringifyCoverage(data, region)
      case _           => Map.empty[String, String]
    }
  }

  /**
   * Stringifies raw genotypes to a string
   *
   * @param rdd
   * @param region
   * @return
   */
  def stringifyRawGenotypes(rdd: RDD[(String, Iterable[Any])], region: ReferenceRegion): Map[String, String] = {
    val data: Array[(String, Iterable[Genotype])] = rdd
      .mapValues(_.asInstanceOf[Iterable[Genotype]])
      .mapValues(r => r.filter(r => r.getStart <= region.end && r.getEnd >= region.start)).collect

    val flattened: Map[String, Array[VariantJson]] = data.groupBy(_._1)
      .map(r => (r._1, r._2.flatMap(_._2)))
      .mapValues(v => v.map(r => VariantJson(r.getContigName, r.getStart, r.getVariant.getReferenceAllele, r.getVariant.getAlternateAllele)))

    flattened.mapValues(v => write(v))
  }

}

object GenotypeMaterialization {

  def apply(sc: SparkContext, files: List[String], dict: SequenceDictionary, partitions: Int): GenotypeMaterialization = {
    new GenotypeMaterialization(sc, files, dict, partitions, 100)
  }

  def apply[T: ClassTag, C: ClassTag](sc: SparkContext, files: List[String], dict: SequenceDictionary, partitions: Int, chunkSize: Int): GenotypeMaterialization = {
    new GenotypeMaterialization(sc, files, dict, partitions, chunkSize)
  }

  def load(sc: SparkContext, region: Option[ReferenceRegion], fp: String): RDD[Genotype] = {
    val genotypes: RDD[Genotype] =
      if (fp.endsWith(".adam")) {
        loadAdam(sc, region, fp)
      } else if (fp.endsWith(".vcf")) {
        region match {
          case Some(_) => sc.loadGenotypes(fp).filterByOverlappingRegion(region.get)
          case None    => sc.loadGenotypes(fp)
        }
      } else {
        throw UnsupportedFileException("File type not supported")
      }

    val key = LazyMaterialization.filterKeyFromFile(fp)
    // map unique ids to features to be used in tiles
    genotypes.map(r => {
      if (r.getSampleId == null) new Genotype(r.getVariant, r.getContigName, r.getStart, r.getEnd, r.getVariantCallingAnnotations,
        key, r.getSampleDescription, r.getProcessingDescription, r.getAlleles, r.getExpectedAlleleDosage, r.getReferenceReadDepth,
        r.getAlternateReadDepth, r.getReadDepth, r.getMinReadDepth, r.getGenotypeQuality, r.getGenotypeLikelihoods, r.getNonReferenceLikelihoods, r.getStrandBiasComponents, r.getSplitFromMultiAllelic, r.getIsPhased, r.getPhaseSetId, r.getPhaseQuality)
      else r
    })
  }

  def loadAdam(sc: SparkContext, region: Option[ReferenceRegion], fp: String): RDD[Genotype] = {
    val pred: Option[FilterPredicate] =
      region match {
        case Some(_) => Some(((LongColumn("variant.end") >= region.get.start) && (LongColumn("variant.start") <= region.get.end) && (BinaryColumn("variant.contig.contigName") === region.get.referenceName)))
        case None    => None
      }
    val proj = Projection(GenotypeField.variant, GenotypeField.alleles, GenotypeField.sampleId)
    sc.loadParquetGenotypes(fp, predicate = pred, projection = Some(proj))
  }

}