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
import org.bdgenomics.adam.rdd.feature.FeatureRDD
import org.bdgenomics.formats.avro.Feature
import org.bdgenomics.mango.layout.BedRowJson
import org.bdgenomics.utils.misc.Logging
import java.io.{ StringWriter, PrintWriter }

class FeatureMaterialization(s: SparkContext,
                             filePaths: List[String],
                             dict: SequenceDictionary) extends LazyMaterialization[Feature]("FeatureRDD") with Serializable with Logging {

  @transient implicit val formats = net.liftweb.json.DefaultFormats
  @transient val sc = s
  val sd = dict
  val files = filePaths

  def getReferenceRegion = (f: Feature) => ReferenceRegion.unstranded(f)
  def load = (region: ReferenceRegion, file: String) => FeatureMaterialization.load(sc, Some(region), file).rdd

  /**
   * Strinifies tuples of (sampleId, feature) to json
   *
   * @param data RDD (sampleId, Feature)
   * @return Map of (key, json) for the ReferenceRegion specified
   */
  def stringify(data: RDD[(String, Feature)]): Map[String, String] = {

    val flattened: Map[String, Array[BedRowJson]] = data
      .collect
      .groupBy(_._1)
      .map(r => (r._1, r._2.map(_._2)))
      .mapValues(r => r.map(f => BedRowJson(Option(f.getFeatureId).getOrElse("N/A"), Option(f.getFeatureType).getOrElse("N/A"), f.getContigName, f.getStart, f.getEnd)))

    flattened.mapValues(r => write(r))
  }
}

object FeatureMaterialization {

  /**
   * Loads feature data from bam, sam and ADAM file formats
   *
   * @param sc SparkContext
   * @param region Region to load
   * @param fp filepath to load from
   * @return RDD of data from the file over specified ReferenceRegion
   */
  def load(sc: SparkContext, region: Option[ReferenceRegion], fp: String): FeatureRDD = {
    if (fp.endsWith(".adam")) FeatureMaterialization.loadAdam(sc, region, fp)
    else {
      try {
        FeatureMaterialization.loadData(sc, region, fp)
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
   * Loads data from bam files (indexed or unindexed) from persistent storage
   *
   * @param sc SparkContext
   * @param region Region to load
   * @param fp filepath to load from
   * @return RDD of data from the file over specified ReferenceRegion
   */
  def loadData(sc: SparkContext, region: Option[ReferenceRegion], fp: String): FeatureRDD = {
    region match {
      case Some(_) =>
        val featureRdd = sc.loadFeatures(fp)
        featureRdd.transform(rdd => rdd.rdd.filter(g => g.getContigName == region.get.referenceName && g.getStart < region.get.end
          && g.getEnd > region.get.start))
      case None => sc.loadFeatures(fp)
    }
  }

  /**
   * Loads ADAM data using predicate pushdowns
   *
   * @param sc SparkContext
   * @param region Region to load
   * @param fp filepath to load from
   * @return RDD of data from the file over specified ReferenceRegion
   */
  def loadAdam(sc: SparkContext, region: Option[ReferenceRegion], fp: String): FeatureRDD = {
    val pred: Option[FilterPredicate] =
      region match {
        case Some(_) => Some((LongColumn("end") >= region.get.start) && (LongColumn("start") <= region.get.end) && (BinaryColumn("contig.contigName") === region.get.referenceName))
        case None    => None
      }

    val proj = Projection(FeatureField.featureId, FeatureField.source, FeatureField.featureType, FeatureField.start, FeatureField.end, FeatureField.contigName)
    sc.loadParquetFeatures(fp, predicate = pred, projection = Some(proj))
  }

}