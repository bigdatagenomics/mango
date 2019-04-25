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

package org.bdgenomics.mango.io

import java.net.URL

import org.apache.spark.SparkContext
import org.bdgenomics.adam.models.ReferenceRegion

/**
 *
 * @tparam R SpecificRecord
 * @tparam D Genomic Dataset
 */
trait GenomicReader[R, D] {

  def loadLocal(path: String, regions: Iterable[ReferenceRegion]): Iterator[R]

  def loadHttp(path: String, regions: Iterable[ReferenceRegion]): Iterator[R]

  def loadS3(path: String, regions: Iterable[ReferenceRegion]): Iterator[R]

  def loadHDFS(sc: SparkContext, path: String, regions: Iterable[ReferenceRegion]): D

  // helper functions
  def createURL(urlString: String): URL = {

    return new URL(urlString.trim())

  }

}
