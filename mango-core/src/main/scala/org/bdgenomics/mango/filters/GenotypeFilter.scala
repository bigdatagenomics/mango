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
import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.formats.avro.{ GenotypeAllele, Genotype }

/**
 * Stores enumeration for available whole file scan predicates for genotype data
 * This is used for discovery mode in VizReads on startup
 */
object GenotypeFilterEnumeration {
  def test(x: Genotype) = !x.getAlleles.contains(GenotypeAllele.Ref)

  def get(x: Int): Genotype => Boolean = {
    x match {
      case _ => test
    }
  }
}

object GenotypeFilter {

  /**
   * Filters variant RDD by a specified predicate
   * @param variants variants to filter
   * @param predicate predicate to filter variants with
   * @return list of regions satisfying the predicate
   */
  def getFilteredRegions(variants: RDD[Genotype], predicate: Genotype => Boolean): RDD[ReferenceRegion] = {
    val filtered = variants.filter(r => predicate(r))
    println(filtered.count)
    filtered.map(r => ReferenceRegion(r.getContigName, r.getStart, r.getEnd))
  }

}
