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
package org.bdgenomics.mango.layout

import org.apache.spark.Logging
import scala.collection.mutable.ListBuffer
import org.bdgenomics.formats.avro.{ AlignmentRecord, Feature }
import org.bdgenomics.adam.models.ReferenceRegion
import org.apache.spark.rdd.RDD

object FeatureLayout extends Logging {

  //Prepares feature information in Json format
  def apply(rdd: RDD[Feature]): List[FeatureJson] = {
    val data = rdd.keyBy(ReferenceRegion(_))
    val trackedData = data.mapPartitions(TrackedLayout(_)).collect
    val featureData = trackedData.zipWithIndex
    featureData.flatMap(r => FeatureJson(r._1.records, r._2)).toList
  }
}

object FeatureJson {
  // Transforms track data and corresponding track number to FeatureJsons
  def apply(recs: List[(ReferenceRegion, Feature)], track: Int): List[FeatureJson] = {
    recs.map(rec => new FeatureJson(rec._2.featureId, rec._2.featureType, rec._2.start, rec._2.end, track))
  }
}

case class FeatureJson(featureId: String, featureType: String, start: Long, end: Long, track: Long)
