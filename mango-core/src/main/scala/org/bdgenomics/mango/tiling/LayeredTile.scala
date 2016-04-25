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

import org.apache.spark.Logging
import org.bdgenomics.adam.models.ReferenceRegion

abstract class LayeredTile extends Serializable with Logging {
  implicit val formats = net.liftweb.json.DefaultFormats

  val layers = 2

  def getL0(region: ReferenceRegion, args: Option[List[String]]): String
  def getConvolved(region: ReferenceRegion, args: Option[List[String]], patchSize: Int, stride: Int): String

  def get(region: ReferenceRegion, args: Option[List[String]] = None): String = {
    val size = region.end - region.start
    size match {
      case x if (x < L1.range._1) => getL0(region, args)
      case x if (x >= L1.range._1 && x < L1.range._2) => getConvolved(region, args, L1.patchSize, L1.stride)
      case x if (x >= L2.range._1 && x < L2.range._2) => getConvolved(region, args, L2.patchSize, L2.stride)
      case x if (x >= L3.range._1 && x < L3.range._2) => getConvolved(region, args, L3.patchSize, L3.stride)
      case _ => getConvolved(region, args, L4.patchSize, L4.stride)
    }

  }

}

trait Layer {

  def maxSize: Long
  def range: Tuple2[Long, Long]
  val finalSize = 1000

  def patchSize: Int
  def stride: Int

}

/* For objects 5000 to 10000 */
object L1 extends Layer {
  val maxSize = 10000L
  val range = (5000L, maxSize)
  val patchSize = 10
  val stride = 10

}

/* For objects 10,000 to 100,000 */
object L2 extends Layer {
  val maxSize = 100000L
  val range = (L1.maxSize, maxSize)
  val patchSize = 100
  val stride = patchSize
}

/* For objects 100,000 to 1,000,000 */
object L3 extends Layer {
  val maxSize = 1000000L
  val range = (L2.maxSize, maxSize)
  val patchSize = 1000
  val stride = patchSize

}

/* For objects 1000000 + */
object L4 extends Layer {
  val maxSize = 10000000L
  val range = (L3.maxSize, maxSize)
  val patchSize = 10000
  val stride = patchSize

}

