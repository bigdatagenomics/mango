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

import org.apache.spark.{ Logging, SparkContext }
import org.apache.spark.rdd.RDD
import org.apache.spark.SparkContext._
import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.adam.models.VariantContext
import org.bdgenomics.formats.avro.{ AlignmentRecord, Feature, Variant, Genotype }
import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer

object VariantLayout extends Logging {

  /**
   * An implementation of Variant Layout
   *
   * @param rdd: RDD of (ReferenceRegion, Genotype) tuples
   * @return List of VariantJsons
   */
  def apply(rdd: RDD[(ReferenceRegion, Genotype)]): List[VariantJson] = {
    val trackedData = rdd.mapPartitions(VariantLayout(_)).collect
    val variantData = trackedData.zipWithIndex
    variantData.flatMap(r => VariantJson(r._1.records, r._2)).toList
  }

  /**
   * An implementation of Variant Layout
   *
   * @param iter: Iterator of (ReferenceRegion, Genotype) tuples
   * @return List of Genotype Tracks
   */
  def apply(iter: Iterator[(ReferenceRegion, Genotype)]): Iterator[GenericTrack[Genotype]] = {
    new VariantLayout(iter).collect
  }
}

object VariantFreqLayout extends Logging {

  /**
   * An implementation of VariantFreqLayout
   *
   * @param rdd: RDD of (ReferenceRegion, Genotype) tuples
   * @return List of VariantFreqJsons
   */
  def apply(rdd: RDD[(ReferenceRegion, Genotype)]): List[VariantFreqJson] = {
    val variantFreq = rdd.countByKey
    var freqJson = new ListBuffer[VariantFreqJson]
    for (rec <- variantFreq) {
      freqJson += VariantFreqJson(rec._1.referenceName, rec._1.start, rec._1.end, rec._2)
    }
    freqJson.toList
  }

}

/**
 * An implementation of TrackedLayout for Genotype Data
 *
 * @param values Iterator of (ReferenceRegion, Genotype) tuples
 */
class VariantLayout(values: Iterator[(ReferenceRegion, Genotype)]) extends TrackedLayout[Genotype, GenericTrackBuffer[Genotype]] with Logging {
  val sequence = values.toList
  var trackBuilder = new ListBuffer[GenericTrackBuffer[Genotype]]()
  val data = sequence.groupBy(_._2.sampleId)
  addTracks
  trackBuilder = trackBuilder.filter(_.records.nonEmpty)

  def addTracks {
    for (rec <- data) {
      trackBuilder += GenericTrackBuffer[Genotype](rec._2)
    }
  }
  def collect: Iterator[GenericTrack[Genotype]] = trackBuilder.map(t => Track[Genotype](t)).toIterator
}

object VariantJson {

  /**
   * An implementation of VariantJson
   *
   * @param recs: List of (ReferenceRegion, Genotype) tuples
   * @return List of VariantJsons
   */
  def apply(recs: List[(ReferenceRegion, Genotype)], track: Int): List[VariantJson] = {
    recs.map(rec => new VariantJson(rec._2.variant.contig.contigName, rec._2.alleles.map(_.toString).mkString(" / "), rec._2.variant.start, rec._2.variant.end, track))
  }
}

// tracked json objects for genotype visual data
case class VariantJson(contigName: String, alleles: String, start: Long, end: Long, track: Long)
case class VariantFreqJson(contigName: String, start: Long, end: Long, count: Long)
