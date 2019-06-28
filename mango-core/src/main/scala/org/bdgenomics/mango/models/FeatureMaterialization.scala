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
import org.apache.spark.SparkContext
import org.bdgenomics.adam.models.{ ReferenceRegion, SequenceDictionary }
import org.bdgenomics.adam.projections.{ FeatureField, Projection }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.formats.avro.Feature
import org.bdgenomics.mango.converters.GA4GHutil
import org.bdgenomics.mango.core.util.ResourceUtils
import org.bdgenomics.mango.io.BedReader
import org.bdgenomics.utils.misc.Logging
import scala.collection.JavaConversions._

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
    extends LazyMaterialization[Feature, ga4gh.SequenceAnnotations.Feature](FeatureMaterialization.name, sc, files, sd, prefetchSize)
    with Serializable with Logging {

  /**
   * Extracts ReferenceRegion from Feature
   *
   * @param f Feature
   * @return extracted ReferenceRegion
   */
  def getReferenceRegion = (f: Feature) => ReferenceRegion.unstranded(f)

  def load = (file: String, regions: Option[Iterable[ReferenceRegion]]) => FeatureMaterialization.load(sc, file, regions)

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
  def toJson(data: Array[(String, Feature)]): Map[String, Array[ga4gh.SequenceAnnotations.Feature]] = {
    data.groupBy(_._1).mapValues(r =>
      {
        r.map(a => GA4GHutil.featureToGAFeature(a._2))
      })
  }

  /**
   * Formats raw data from GA4GH Variants Response to JSON.
   * @param data An array of GA4GH Variants
   * @return JSONified data
   */
  def stringify = (data: Array[ga4gh.SequenceAnnotations.Feature]) => FeatureMaterialization.stringify(data)

}

object FeatureMaterialization {

  val name = "Feature"

  /**
   * Formats raw data from GA4GH Variants Response to JSON.
   * @param data An array of GA4GH Variants
   * @return JSONified data
   */
  def stringify(data: Array[ga4gh.SequenceAnnotations.Feature]): String = {

    // write message
    val message = ga4gh.SequenceAnnotationServiceOuterClass
      .SearchFeaturesResponse.newBuilder().addAllFeatures(data.toList)
      .build()

    // do not call includingDefaultValueFields, because this includes all possible attribute types
    com.google.protobuf.util.JsonFormat.printer().print(message)
  }

  /**
   * Loads feature data from bam, sam and ADAM file formats
   *
   * @param sc SparkContext
   * @param fp filepath to load from
   * @param regions Iterable of ReferenceRegion to load
   * @return Feature dataset from the file over specified ReferenceRegion
   */
  def load(sc: SparkContext, fp: String, regions: Option[Iterable[ReferenceRegion]]): Array[Feature] = {
    if (fp.endsWith(".adam")) FeatureMaterialization.loadAdam(sc, fp, regions)
    else BedReader.loadFromSource(fp, regions, Some(sc))
  }

  /**
   * Loads ADAM data using predicate pushdowns
   *
   * @param sc SparkContext
   * @param regionsOpt Iterable of ReferenceRegion to load
   * @param fp filepath to load from
   * @return Feature dataset from the file over specified ReferenceRegion
   */
  def loadAdam(sc: SparkContext, fp: String, regionsOpt: Option[Iterable[ReferenceRegion]]): Array[Feature] = {
    if (regionsOpt.isDefined) {
      val regions = regionsOpt.get
      val pred = {
        val predicateRegions: Iterable[ReferenceRegion] = regions
          .flatMap(r => LazyMaterialization.getReferencePredicate(r))
        Some(ResourceUtils.formReferenceRegionPredicate(predicateRegions))
      }
      val proj = Projection(FeatureField.featureId, FeatureField.referenceName, FeatureField.start, FeatureField.end,
        FeatureField.score, FeatureField.featureType)
      sc.loadParquetFeatures(fp, optPredicate = pred, optProjection = Some(proj)).rdd.collect()
    } else {
      val proj = Projection(FeatureField.featureId, FeatureField.referenceName, FeatureField.start, FeatureField.end,
        FeatureField.score, FeatureField.featureType)
      sc.loadParquetFeatures(fp, optProjection = Some(proj)).rdd.collect()
    }
  }
}
