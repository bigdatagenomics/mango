
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

import org.bdgenomics.adam.models.{ Exon, Gene, ReferenceRegion }

/**
 * This file contains case classes for json conversions
 */

case class Interval(start: Long, end: Long)

case class VariantJson(contig: String, position: Long, ref: String, alt: String)

case class GenotypeJson(sampleIds: Array[String], variant: VariantJson)

case class BedRowJson(id: String, featureType: String, contig: String, start: Long, stop: Long)

object GeneJson {
  def apply(rf: Gene): Iterable[GeneJson] = {
    val transcripts = rf.transcripts
    transcripts.map(t => GeneJson(t.region, t.id, t.strand, Interval(t.region.start, t.region.end), t.exons, t.geneId, t.names.mkString(",")))
  }
}

case class GeneJson(position: ReferenceRegion, id: String, strand: Boolean, codingRegion: Interval,
                    exons: Iterable[Exon], geneId: String, name: String)

/**
 * Class for covertering adam coverage to coverage format readable by pileup.js
 * @param position Base pair on chromosome
 * @param count Coverage at the specified base pair
 */
case class PositionCount(position: Long, count: Int)

