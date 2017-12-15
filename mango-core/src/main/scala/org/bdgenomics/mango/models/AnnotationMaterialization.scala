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
package org.bdgenomics.mango.models

import org.apache.spark.SparkContext
import org.bdgenomics.adam.models._
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.adam.util.ReferenceFile
import org.bdgenomics.mango.core.util.VizUtils
import org.bdgenomics.utils.misc.Logging

/**
 * Handles loading and tracking of data from persistent storage into memory for reference data.
 *
 * @param sc SparkContext
 * @param referencePath the file which contains the reference file
 */
class AnnotationMaterialization(@transient sc: SparkContext,
                                referencePath: String) extends Serializable with Logging {

  @transient implicit val formats = net.liftweb.json.DefaultFormats
  var bookkeep = Array[String]()
  val fragmentLength = 10000

  // set and name interval rdd
  val reference: ReferenceFile =
    sc.loadReferenceFile(referencePath, fragmentLength)

  def getSequenceDictionary: SequenceDictionary = reference.sequences

  /**
   * Extracts the Reference String from ReferenceRegion
   *
   * @param region ReferenceRegion
   * @return extracted Reference String
   */
  def getReferenceString(region: ReferenceRegion): String = {
    try {
      val parsedRegion = ReferenceRegion(region.referenceName, region.start,
        VizUtils.getEnd(region.end, reference.sequences.apply(region.referenceName)))
      reference.extract(parsedRegion).toUpperCase()
    } catch {
      case e: Exception =>
        log.warn("requested reference region not found in sequence dictionary")
        ""
    }
  }
}