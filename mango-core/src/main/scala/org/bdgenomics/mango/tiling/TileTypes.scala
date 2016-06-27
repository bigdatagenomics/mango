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
package org.bdgenomics.mango.tiling

import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.formats.avro.{ AlignmentRecord, Feature, Genotype }
import org.bdgenomics.mango.layout.{ CalculatedAlignmentRecord, MismatchLayout, PointMisMatch, PositionCount, VariantFreqLayout }

case class FeatureTile(layerMap: Map[Int, Map[String, Iterable[Any]]]) extends KLayeredTile with Serializable

object FeatureTile {
  def apply(data: Iterable[(String, Feature)],
            region: ReferenceRegion): FeatureTile = {

    // formats raw feature data
    val rawData = data.groupBy(_._1).mapValues(r => r.map(_._2))

    // Calculate coverage of features
    val layer1 = rawData.mapValues(v => {
      v.flatMap(r => (r.getStart.toLong to r.getEnd.toLong))
        .map(r => (r, 1)).groupBy(_._1)
        .map { case (group, traversable) => traversable.reduce { (a, b) => (a._1, a._2 + b._2) } }
        .filter(r => (r._1 >= region.start && r._1 <= region.end))
        .map(r => PositionCount(r._1, r._2))
    })

    val layerMap = Map(0 -> rawData, 1 -> layer1)
    new FeatureTile(layerMap)

  }
}

case class AlignmentRecordTile(layerMap: Map[Int, Map[String, Iterable[Any]]]) extends KLayeredTile with Serializable

object AlignmentRecordTile {
  def apply(data: Iterable[(String, AlignmentRecord)],
            region: ReferenceRegion,
            reference: String): AlignmentRecordTile = {

    /* Calculate Coverage at each position */
    val grouped: Map[String, Iterable[AlignmentRecord]] = data.groupBy(_._1).mapValues(r => r.map(_._2))
    val coverage: Map[String, Iterable[PositionCount]] = grouped.mapValues(v => {
      v.flatMap(r => (r.getStart.toLong to r.getEnd.toLong))
        .map(r => (r, 1)).groupBy(_._1)
        .map { case (group, traversable) => traversable.reduce { (a, b) => (a._1, a._2 + b._2) } }
        .filter(r => (r._1 >= region.start && r._1 <= region.end))
        .map(r => PositionCount(r._1, r._2))
    })
    // raw data is alignments and mismatches
    lazy val rawData = grouped.mapValues(iter => iter.map(r => CalculatedAlignmentRecord(r, MismatchLayout(r, reference, region)))
      .filter(r => !r.mismatches.isEmpty))

    // layer 1 is point mismatches
    lazy val layer1 = rawData.mapValues(
      rs => {
        PointMisMatch(rs.flatMap(_.mismatches).toList)
          .filter(m => m.refCurr >= region.start && m.refCurr <= region.end)
      })
    new AlignmentRecordTile(Map(0 -> rawData, 1 -> layer1, 2 -> coverage))

  }
}

case class VariantTile(layerMap: Map[Int, Map[String, Iterable[Any]]]) extends KLayeredTile with Serializable

object VariantTile {
  def apply(data: Iterable[(String, Genotype)]): VariantTile = {
    val rawData = data.groupBy(_._1).mapValues(r => r.map(_._2))
    val layer1 = rawData.mapValues(r => VariantFreqLayout(r))
    new VariantTile(Map(0 -> rawData, 1 -> layer1))
  }
}
