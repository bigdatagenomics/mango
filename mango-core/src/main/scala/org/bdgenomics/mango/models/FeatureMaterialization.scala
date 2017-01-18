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
import org.bdgenomics.mango.core.util.VizUtils
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

  /**
   * Extracts ReferenceRegion from Feature
   *
   * @param f Feature
   * @return extracted ReferenceRegion
   */
  def getReferenceRegion = (f: Feature) => ReferenceRegion.unstranded(f)

  def load = (file: String, region: Option[ReferenceRegion]) => FeatureMaterialization.load(sc, region, file).rdd

  /**
   * Reset ReferenceName for Feature
   *
   * @param f Feature to be modified
   * @param contig to replace Feature contigName
   * @return Feature with new ReferenceRegion
   */
  def setContigName = (f: Feature, contig: String) => {
    f.setContigName(contig)
    f
  }

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
      .mapValues(r =>
        r.map(f => {
          val score = Option(f.getScore)
            .getOrElse(VizUtils.defaultScore.toDouble)
            .asInstanceOf[Double].toInt
          BedRowJson(Option(f.getFeatureId).getOrElse("N/A"),
            Option(f.getFeatureType).getOrElse("N/A"),
            f.getContigName, f.getStart, f.getEnd,
            score)
        }))

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
    if (fp.endsWith(".adam")) FeatureMaterialization.loadAdam(sc, fp, region)
    else {
      try {
        FeatureMaterialization.loadData(sc, fp, region)
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
  def loadData(sc: SparkContext, fp: String, region: Option[ReferenceRegion]): FeatureRDD = {
    region match {
      case Some(_) =>
        val contigs = LazyMaterialization.getContigPredicate(region.get)
        val featureRdd = sc.loadFeatures(fp)
        featureRdd.transform(rdd => rdd.rdd.filter(g =>
          (g.getContigName == contigs._1.referenceName || g.getContigName == contigs._2.referenceName)
            && g.getStart < region.get.end
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
  def loadAdam(sc: SparkContext, fp: String, region: Option[ReferenceRegion]): FeatureRDD = {
    val pred: Option[FilterPredicate] =
      region match {
        case Some(_) =>
          val contigs = LazyMaterialization.getContigPredicate(region.get)
          Some((LongColumn("end") >= region.get.start) && (LongColumn("start") <= region.get.end) &&
            (BinaryColumn("contig.contigName") === contigs._1.referenceName) || BinaryColumn("contig.contigName") === contigs._2.referenceName)
        case None => None
      }

    val proj = Projection(FeatureField.featureId, FeatureField.contigName, FeatureField.start, FeatureField.end,
      FeatureField.score, FeatureField.featureType)
    sc.loadParquetFeatures(fp, predicate = pred, projection = Some(proj))
  }

}