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
import org.apache.spark.rdd.RDD
import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.formats.avro.{ AlignmentRecord, Feature }
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object FeatureLayout extends Logging {

  /**
   * An implementation of FeatureLayout
   *
   * @param rdd: RDD of features
   * @return List of FeatureJsons
   */
  def apply(rdd: RDD[Feature]): List[FeatureJson] = {
    val data = rdd.keyBy(ReferenceRegion(_))
    val trackedData = data.mapPartitions(FeatureLayout(_)).collect
    val featureData = trackedData.zipWithIndex
    featureData.flatMap(r => FeatureJson(r._1.records, r._2)).toList
  }

  /**
   * An implementation of FeatureLayout
   *
   * @param rdd: iterator of (ReferenceRegion, Features) tuples
   * @return List of Tracks of Feature Data
   */
  def apply(iter: Iterator[(ReferenceRegion, Feature)]): Iterator[GenericTrack[Feature]] = {
    new FeatureLayout(iter).collect
  }
}

/**
 * An implementation of TrackedLayout for Features
 *
 * @param values The set of values (i.e. reads, variants) to lay out in tracks
 */
class FeatureLayout(values: Iterator[(ReferenceRegion, Feature)]) extends TrackedLayout[Feature, GenericTrackBuffer[Feature]] with Logging {
  val sequence = values.toList
  var trackBuilder = new mutable.ListBuffer[GenericTrackBuffer[Feature]]()
  addTracks

  trackBuilder = trackBuilder.filter(_.records.nonEmpty)

  def addTracks {
    sequence.foreach {
      rec =>
        {
          val reg = rec._1
          if (reg != null) {
            val track: Option[GenericTrackBuffer[Feature]] = TrackTimers.FindConflict.time {
              trackBuilder.find(track => !track.conflicts(rec))
            }
            track.map(trackval => {
              trackval += rec
              trackBuilder -= trackval
              trackBuilder += trackval
            }).getOrElse(addTrack(new GenericTrackBuffer[Feature](rec)))
          }
        }
    }
  }

  def collect: Iterator[GenericTrack[Feature]] = trackBuilder.map(t => Track[Feature](t)).toIterator

}

object FeatureJson {

  /**
   * An implementation of FeatureJson that converts Features to tracked json Features
   *
   * @param recs: List of (ReferenceRegion, Feature) tuples
   * @param track: track to place features in
   * @return List of Feature Json Objects
   */
  def apply(recs: List[(ReferenceRegion, Feature)], track: Int): List[FeatureJson] = {
    recs.map(rec => new FeatureJson(rec._2.featureId, rec._2.featureType, rec._2.start, rec._2.end, track))
  }
}

// Tracked Json Features
case class FeatureJson(featureId: String, featureType: String, start: Long, end: Long, track: Long)
