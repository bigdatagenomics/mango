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

import org.bdgenomics.mango.layout.{ CalculatedAlignmentRecord, ConvolutionalSequence }

case class AlignmentRecordTile(alignments: Array[CalculatedAlignmentRecord],
                               layer1: Map[String, Array[Double]],
                               keys: List[String]) extends KLayeredTile[Array[CalculatedAlignmentRecord]] with Serializable {

  // TODO map to samples
  val rawData = alignments.groupBy(_.record.getRecordGroupSample)

  // TODO: verify layer sizes
  val layer2 = layer1.mapValues(r => ConvolutionalSequence.convolveArray(r, L1.patchSize, L1.stride))
  val layer3 = layer2.mapValues(r => ConvolutionalSequence.convolveArray(r, L1.patchSize, L1.stride))
  val layer4 = layer3.mapValues(r => ConvolutionalSequence.convolveArray(r, L1.patchSize, L1.stride))

  val layerMap = Map(1 -> layer1.mapValues(_.map(_.toByte)),
    2 -> layer2.mapValues(_.map(_.toByte)),
    3 -> layer3.mapValues(_.map(_.toByte)),
    4 -> layer4.mapValues(_.map(_.toByte)))
}

case class ReferenceTile(sequence: String) extends LayeredTile[String] with Serializable {
  val rawData = sequence
  val layerMap = ConvolutionalSequence.convolveToEnd(sequence, LayeredTile.layerCount)
}