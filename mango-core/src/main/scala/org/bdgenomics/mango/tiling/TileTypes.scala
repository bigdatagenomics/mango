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
import org.bdgenomics.formats.avro.AlignmentRecord
import org.bdgenomics.mango.layout.{ MismatchLayout, CalculatedAlignmentRecord, PointMisMatch }
import org.bdgenomics.mango.models.PositionCount

case class AlignmentRecordTile(layerMap: Map[Int, Map[String, Iterable[Any]]]) extends KLayeredTile with Serializable

case class ReferenceTile(sequence: String) extends LayeredTile[String] with Serializable {
  val rawData = sequence
  val layerMap = null
}

object AlignmentRecordTile {
  def apply(data: Iterable[AlignmentRecord],
            reference: String,
            region: ReferenceRegion): AlignmentRecordTile = {

    /* Calculate Coverage at each position */
    val grouped: Map[String, Iterable[AlignmentRecord]] = data.groupBy(_.getRecordGroupSample)
    val coverage: Map[String, Iterable[PositionCount]] = grouped.mapValues(v => {
      v.flatMap(r => (r.getStart.toLong to r.getEnd.toLong))
        .filter(r => (r >= region.start && r <= region.end))
        .map(r => (r, 1)).groupBy(_._1)
        .map { case (group, traversable) => traversable.reduce { (a, b) => (a._1, a._2 + b._2) } }
        .map(r => PositionCount(r._1, r._2))

    })

    // raw data is alignments and mismatches
    lazy val rawData = grouped.mapValues(iter => iter.map(r => CalculatedAlignmentRecord(r, MismatchLayout(r, reference, region)))
      .filter(r => !r.mismatches.isEmpty))

    // layer 1 is point mismatches
    lazy val layer1 = rawData.mapValues(rs => PointMisMatch(rs.flatMap(_.mismatches).toList))

    new AlignmentRecordTile(Map(0 -> rawData, 1 -> layer1, 2 -> coverage))

  }
}