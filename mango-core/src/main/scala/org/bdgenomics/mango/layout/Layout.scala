
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

import net.liftweb.json.Serialization.write
import net.liftweb.json._
import org.bdgenomics.formats.avro.Variant

/**
 * This file contains case classes for json conversions
 */

/**
 * class for printing intervals to pileup.js
 * @param start start of region
 * @param end end of region
 */
case class Interval(start: Long, end: Long)

/**
 * Class for printing variants to pileup.js
 * @param contig contig name (reference name)
 * @param position start of variant
 * @param ref reference
 * @param alt alternate
 * @param end end of variant
 */
case class VariantJson(contig: String, position: Long, end: Long, ref: String, alt: String)

object VariantJson {
  def apply(variant: Variant): VariantJson = {
    VariantJson(variant.getContigName, variant.getStart, variant.getEnd, variant.getReferenceAllele, variant.getAlternateAllele)
  }
}

case class GenotypeString(variant: VariantJson, sampleIds: Array[String])

/**
 * Class for printing json genotypes to pileup.js
 * @param variant variant of this genotype
 * @param sampleIds sample Ids for this genotype
 */
case class GenotypeJson(variant: Variant, sampleIds: Array[String]) {

  override def toString(): String = {

    // required for writing json
    @transient implicit val formats = net.liftweb.json.DefaultFormats

    write(GenotypeString(VariantJson(variant), sampleIds))(formats)
  }

}

/**
 * Object used to convert json strings into GenotypeJson classes
 */
object GenotypeJson {

  // required for json extraction
  @transient implicit val formats = net.liftweb.json.DefaultFormats

  /**
   * Rebuilds GenotypeJson and its Variant from VariantJson. Some Variant components are lost when rebuilding,
   * but are not used for the final visualization.
   * @param str String to convert to GenotypeJson
   * @return final GenotypeJson
   */
  def apply(str: String): GenotypeJson = {
    val tuple = parse(str).extract[GenotypeString]
    val variant = Variant.newBuilder()
      .setContigName(tuple.variant.contig)
      .setStart(tuple.variant.position)
      .setEnd(tuple.variant.end)
      .setReferenceAllele(tuple.variant.ref)
      .setAlternateAllele(tuple.variant.alt)
      .build()

    new GenotypeJson(variant, tuple.sampleIds)
  }

  /**
   * Makes genotype json without genotype sample names
   * @param variant Variant
   * @return GenotypeJson
   */
  def apply(variant: Variant): GenotypeJson = new GenotypeJson(variant, null)

}

/**
 * Class for printing json features to pileup.js
 * @param id feature Id
 * @param featureType feature type
 * @param contig contig name (reference name)
 * @param start start of feature region
 * @param stop end of feature region
 */
case class BedRowJson(id: String, featureType: String, contig: String, start: Long, stop: Long, score: Int)

/**
 * Class for covertering adam coverage to coverage format readable by pileup.js
 * @param start Base pair start
 * @param end Base pair end chromosome
 * @param count Coverage at the specified base pair
 */
case class PositionCount(contig: String, start: Long, end: Long, count: Int)

