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

import org.apache.spark.{ Logging, SparkContext }
import org.apache.spark.rdd.RDD
import org.bdgenomics.adam.models.ReferenceRegion
import net.liftweb.json.Serialization.write
import org.bdgenomics.formats.avro.AlignmentRecord
import org.bdgenomics.mango.layout.{ MismatchLayout, CalculatedAlignmentRecord, ConvolutionalSequence }

class AlignmentRecordTile(sc: SparkContext,
                          alignments: RDD[AlignmentRecord],
                          reference: String,
                          region: ReferenceRegion,
                          ks: List[String]) extends LayeredTile with Logging {

  // TODO map to samples
  implicit val formats = net.liftweb.json.DefaultFormats

  // map to CalculatedAlignmentRecord alignment records and
  val layer0 = alignments.map(r => CalculatedAlignmentRecord(r, MismatchLayout(r, reference, region))).map(write(_)).collect.map(_.toByte)
  val layerMap = Map(0 -> layer0)

  // TODO: get convolutions

  //  val c = ConvolutionalSequence.convolveRDD(region, ref, alignments.get.toRDD.map(_._2).filter(r => r.getRecordGroupSample == k), layerType.patchSize, layerType.stride)
  // format to data
  // put in layermap sca

}

case class ReferenceTile(sequence: String) extends LayeredTile with Serializable {
  val layerMap = ConvolutionalSequence.convolveToEnd(sequence, LayeredTile.layerCount)
}
