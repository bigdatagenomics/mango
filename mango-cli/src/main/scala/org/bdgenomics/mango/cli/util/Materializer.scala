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

package org.bdgenomics.mango.cli.util

import org.bdgenomics.mango.models._

case class Materializer(objects: Seq[LazyMaterialization[_, _]]) {

  /**
   * Access functions for materializer
   */
  def getReads(): Option[AlignmentRecordMaterialization] = {
    val x = objects.flatMap(r =>
      r match {
        case m: AlignmentRecordMaterialization => Some(m)
        case _                                 => None
      })
    if (x.isEmpty) None
    else Some(x.head)
  }

  def getVariantContext(): Option[VariantContextMaterialization] = {
    val x = objects.flatMap(r =>
      r match {
        case m: VariantContextMaterialization => Some(m)
        case _                                => None
      })
    if (x.isEmpty) None
    else Some(x.head)
  }

  def getFeatures(): Option[FeatureMaterialization] = {
    val x = objects.flatMap(r =>
      r match {
        case m: FeatureMaterialization => Some(m)
        case _                         => None
      })
    if (x.isEmpty) None
    else Some(x.head)
  }

  /**
   * definitions tracking whether optional datatypes were loaded
   */
  def readsExist: Boolean = getReads().isDefined

  def variantContextExist: Boolean = getVariantContext().isDefined

  def featuresExist: Boolean = getFeatures().isDefined
}
