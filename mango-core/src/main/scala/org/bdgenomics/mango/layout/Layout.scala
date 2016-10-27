
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
case class VariantJson(contig: String, position: Long, ref: String, alt: String, end: Long)

/**
 * Class for printing json genotypes to pileup.js
 * @param sampleIds sample Ids for this genotype
 * @param variant variant of this genotype
 */
case class GenotypeJson(sampleIds: Array[String], variant: VariantJson)

/**
 * Class for printing json features to pileup.js
 * @param id feature Id
 * @param featureType feature type
 * @param contig contig name (reference name)
 * @param start start of feature region
 * @param stop end of feature region
 */
case class BedRowJson(id: String, featureType: String, contig: String, start: Long, stop: Long)

/**
 * Class for coverting adam coverage to coverage format readable by pileup.js
 * @param position Base pair on chromosome
 * @param count Coverage at the specified base pair
 */
case class PositionCount(position: Long, count: Int)

