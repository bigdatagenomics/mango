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
package org.bdgenomics.mango.filters

import org.apache.spark.rdd.RDD
import org.bdgenomics.adam.models.{ ReferencePosition, ReferenceRegion }
import org.bdgenomics.formats.avro.Genotype
import org.bdgenomics.mango.filters.GenotypeFilterType.GenotypeFilterType

object GenotypeFilterType extends Enumeration {
  type GenotypeFilterType = Value
  val highDensity = Value(0)
  val lowDensity = Value(1)
}

/**
 * Stores enumeration for available whole file scan predicates for genotype data
 * This is used for discovery mode in VizReads on startup
 */
object GenotypeFilter {

  def filter(rdd: RDD[Genotype], x: GenotypeFilterType, window: Long, threshold: Long): RDD[(ReferenceRegion, Long)] = {
    x match {
      case GenotypeFilterType.lowDensity  => GenericFilter.filterByDensity(window, threshold, rdd.map(r => (ReferenceRegion(ReferencePosition(r)), r)), false)
      case GenotypeFilterType.highDensity => GenericFilter.filterByDensity(window, threshold, rdd.map(r => (ReferenceRegion(ReferencePosition(r)), r)), true)
      case _                              => throw new Exception("Invalid filter for Genotypes")
    }
  }
}

