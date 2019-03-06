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
import org.bdgenomics.adam.rdd.feature.FeatureDataset
import org.bdgenomics.formats.avro.Feature
import org.bdgenomics.mango.core.util.{ ResourceUtils, VizUtils }
import org.bdgenomics.mango.layout.BedRowJson
import org.bdgenomics.utils.misc.Logging
import java.io.{ StringWriter, PrintWriter }

/**
 * Handles loading and tracking of data from persistent storage into memory for Feature data.
 *
 * @param sc SparkContext
 * @param files list files to materialize
 * @param sd the sequence dictionary associated with the file records
 * @param repartition whether to repartition data to the default number of partitions
 * @param prefetchSize the number of base pairs to prefetch in executors. Defaults to 1000000
 * @see LazyMaterialization.scala
 */
class FeatureMaterialization(@transient sc: SparkContext,
                             files: List[String],
                             sd: SequenceDictionary,
                             repartition: Boolean = false,
                             prefetchSize: Option[Long] = None)
    extends LazyMaterialization[Feature, BedRowJson](FeatureMaterialization.name, sc, files, sd, repartition, prefetchSize)
    with Serializable with Logging {

  /**
   * Extracts ReferenceRegion from Feature
   *
   * @param f Feature
   * @return extracted ReferenceRegion
   */
  def getReferenceRegion = (f: Feature) => ReferenceRegion.unstranded(f)

  def load = (file: String, regions: Option[Iterable[ReferenceRegion]]) => FeatureMaterialization.load(sc, file, regions).rdd

  /**
   * Reset ReferenceName for Feature
   *
   * @param f Feature to be modified
   * @param referenceName to replace Feature referenceName
   * @return Feature with new ReferenceRegion
   */
  def setReferenceName = (f: Feature, referenceName: String) => {
    f.setReferenceName(referenceName)
    f
  }
  /**
   * Stringifies tuples of (sampleId, feature) to json
   *
   * @param data RDD (sampleId, Feature)
   * @return Map of (key, json) for the ReferenceRegion specified
   */
  def toJson(data: RDD[(String, Feature)]): Map[String, Array[BedRowJson]] = {

    data
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
            f.getReferenceName, f.getStart, f.getEnd,
            score)
        }))
  }

  /**
   * Formats raw data from RDD to JSON.
   *
   * @param region Region to obtain coverage for
   * @param binning Tells what granularity of coverage to return. Used for large regions
   * @return JSONified data map;
   */
  def getJson(region: ReferenceRegion, binning: Int = 1): Map[String, Array[BedRowJson]] = {
    val data = get(Some(region))

    val binnedData =
      if (binning > 1) {
        bin(data, binning)
          .map(r => {
            // map to bin start, bin end
            val start = r._1._2.start
            val binned = Feature.newBuilder(r._2)
              .setStart(start)
              .setEnd(Math.max(r._2.getEnd, start + binning))
              .setFeatureId("N/A")
              .setFeatureType("N/A")
              .build()
            (r._1._1, binned)
          })
      } else data
    toJson(binnedData)
  }

}

object FeatureMaterialization {

  val name = "Feature"

  /**
   * Loads feature data from bam, sam and ADAM file formats
   *
   * @param sc SparkContext
   * @param fp filepath to load from
   * @param regions Iterable of ReferenceRegion to load
   * @return Feature dataset from the file over specified ReferenceRegion
   */
  def load(sc: SparkContext, fp: String, regions: Option[Iterable[ReferenceRegion]]): FeatureDataset = {
    if (fp.endsWith(".adam")) FeatureMaterialization.loadAdam(sc, fp, regions)
    else {
      try {
        FeatureMaterialization.loadData(sc, fp, regions)
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
   * @param regions Iterable of ReferenceRegions to load
   * @param fp filepath to load from
   * @return Feature dataset from the file over specified ReferenceRegion
   */
  def loadData(sc: SparkContext, fp: String, regions: Option[Iterable[ReferenceRegion]]): FeatureDataset = {
    // if regions are specified, specifically load regions. Otherwise, load all data
    if (regions.isDefined) {
      val predicateRegions = regions.get
        .flatMap(r => LazyMaterialization.getReferencePredicate(r))
        .toArray

      sc.loadFeatures(fp)
        .transform(rdd => rdd.filter(g =>
          !predicateRegions.filter(r => ReferenceRegion.unstranded(g).overlaps(r)).isEmpty))

    } else {
      sc.loadFeatures(fp)
    }
  }

  /**
   * Loads ADAM data using predicate pushdowns
   *
   * @param sc SparkContext
   * @param regions Iterable of ReferenceRegion to load
   * @param fp filepath to load from
   * @return Feature dataset from the file over specified ReferenceRegion
   */
  def loadAdam(sc: SparkContext, fp: String, regions: Option[Iterable[ReferenceRegion]]): FeatureDataset = {
    val pred =
      if (regions.isDefined) {
        val predicateRegions: Iterable[ReferenceRegion] = regions.get
          .flatMap(r => LazyMaterialization.getReferencePredicate(r))
        Some(ResourceUtils.formReferenceRegionPredicate(predicateRegions))
      } else {
        None
      }

    val proj = Projection(FeatureField.featureId, FeatureField.referenceName, FeatureField.start, FeatureField.end,
      FeatureField.score, FeatureField.featureType)
    sc.loadParquetFeatures(fp, optPredicate = pred, optProjection = Some(proj))
  }
}
