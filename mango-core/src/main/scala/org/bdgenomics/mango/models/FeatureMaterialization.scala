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
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.bdgenomics.adam.models.{ ReferenceRegion, SequenceDictionary }
import org.bdgenomics.adam.projections.{ FeatureField, Projection }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.formats.avro.Feature
import org.bdgenomics.mango.layout.{ BedRowJson, Coverage }
import org.bdgenomics.mango.tiling._
import org.bdgenomics.mango.util.Bookkeep
import org.bdgenomics.utils.misc.Logging

class FeatureMaterialization(s: SparkContext,
                             filePaths: List[String],
                             d: SequenceDictionary,
                             chunkS: Int) extends LazyMaterialization[Feature, FeatureTile]
    with KTiles[FeatureTile] with Serializable with Logging {

  @transient val sc = s
  @transient implicit val formats = net.liftweb.json.DefaultFormats
  val chunkSize = chunkS
  val dict = d
  val bookkeep = new Bookkeep(chunkSize)
  val files = filePaths
  def getStart = (f: Feature) => f.getStart
  def toTile = (data: Iterable[(String, Feature)], region: ReferenceRegion) => FeatureTile(data, region)
  def load = (region: ReferenceRegion, file: String) => FeatureMaterialization.load(sc, Some(region), file)

  /**
   * Gets data for multiple keys.
   * If the RDD has not been initialized, initialize it to the first get request
   * Gets the data for an interval for the file loaded by checking in the bookkeeping tree.
   * If it exists, call get on the IntervalRDD
   * Otherwise call put on the sections of data that don't exist
   * Here, ks, is a list of personids (String)
   * @param region: ReferenceRegion to fetch
   * @return JSONified data
   */
  def get(region: ReferenceRegion): Map[String, String] = {
    val seqRecord = dict(region.referenceName)
    seqRecord match {
      case Some(_) => {
        val regionsOpt = bookkeep.getMaterializedRegions(region, files, true)
        if (regionsOpt.isDefined) {
          for (r <- regionsOpt.get) {
            put(r)
          }
        }

        val layers = getTiles(region, Some(L0))
        stringify(layers, region, L0)
      } case None => {
        throw new Exception("Not found in dictionary")
      }
    }
  }

  def stringify(rdd: RDD[(String, Iterable[Any])], region: ReferenceRegion, layer: Layer): Map[String, String] = {
    layer match {
      case L0 => stringifyRaw(rdd, region)
      case L1 => Coverage.stringifyCoverage(rdd, region)
    }
  }

  def stringifyRaw(rdd: RDD[(String, Iterable[Any])], region: ReferenceRegion): Map[String, String] = {
    val data: Array[(String, Iterable[Feature])] = rdd
      .mapValues(_.asInstanceOf[Iterable[Feature]])
      .mapValues(r => r.filter(r => r.getStart <= region.end && r.getEnd >= region.start)).collect

    val flattened: Map[String, Array[BedRowJson]] = data.groupBy(_._1)
      .map(r => (r._1, r._2.flatMap(_._2)))
      .mapValues(r => r.map(f => BedRowJson(Option(f.getFeatureId).getOrElse("N/A"), Option(f.getFeatureType).getOrElse("N/A"), f.getContigName, f.getStart, f.getEnd)))

    flattened.mapValues(r => write(r))
  }
}

object FeatureMaterialization {

  /**
   * Loads feature data from bam, sam and ADAM file formats
   * @param sc SparkContext
   * @param region Region to load
   * @param fp filepath to load from
   * @return RDD of data from the file over specified ReferenceRegion
   */
  def load(sc: SparkContext, region: Option[ReferenceRegion], fp: String): RDD[Feature] = {
    if (fp.endsWith(".adam")) FeatureMaterialization.loadAdam(sc, region, fp)
    else if (fp.endsWith(".bed")) {
      FeatureMaterialization.loadFromBed(sc, region, fp)
    } else {
      throw UnsupportedFileException("File type not supported")
    }
  }

  /**
   * Loads data from bam files (indexed or unindexed) from persistent storage
   * @param sc SparkContext
   * @param region Region to load
   * @param fp filepath to load from
   * @return RDD of data from the file over specified ReferenceRegion
   */
  def loadFromBed(sc: SparkContext, region: Option[ReferenceRegion], fp: String): RDD[Feature] = {
    region match {
      case Some(_) => sc.loadFeatures(fp).rdd.filter(g => (g.getContigName == region.get.referenceName && g.getStart < region.get.end
        && g.getEnd > region.get.start))
      case None => sc.loadFeatures(fp).rdd
    }
  }

  /**
   * Loads ADAM data using predicate pushdowns
   * @param sc SparkContext
   * @param region Region to load
   * @param fp filepath to load from
   * @return RDD of data from the file over specified ReferenceRegion
   */
  def loadAdam(sc: SparkContext, region: Option[ReferenceRegion], fp: String): RDD[Feature] = {
    val pred: Option[FilterPredicate] =
      region match {
        case Some(_) => Some(((LongColumn("end") >= region.get.start) && (LongColumn("start") <= region.get.end) && (BinaryColumn("contig.contigName") === region.get.referenceName)))
        case None    => None
      }

    val proj = Projection(FeatureField.featureId, FeatureField.source, FeatureField.featureType, FeatureField.start, FeatureField.end, FeatureField.contigName)
    sc.loadParquetFeatures(fp, predicate = pred, projection = Some(proj)).rdd
  }

}