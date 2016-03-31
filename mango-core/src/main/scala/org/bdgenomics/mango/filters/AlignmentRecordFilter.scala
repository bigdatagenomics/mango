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
import org.bdgenomics.formats.avro.{ AlignmentRecord }
import org.bdgenomics.mango.layout.{ CalculatedAlignmentRecord, MisMatch }

object AlignmentRecordFilter {

  //Applies quality filter to (reference region, alignment records), returns the RDD
  def filterByRecordQuality(data: RDD[(ReferenceRegion, CalculatedAlignmentRecord)], quality: String): RDD[(ReferenceRegion, CalculatedAlignmentRecord)] = {
    val minimumQuality: Double = try { quality.toDouble } catch { case _ => 0 }
    data.filter(r => r._2.record.getMapq() >= minimumQuality && r._2.record.getMapq() > 0)
  }

}
