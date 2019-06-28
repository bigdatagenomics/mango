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

import java.io.FileNotFoundException
import java.net.URL

import org.apache.spark.SparkContext
import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.adam.rdd.GenomicDataset

/**
 * Trait for specifying functions for loading data from remote or local
 * genomic file formats (vcf, bed, bam, narrowPeak)
 *
 * @tparam H File header
 * @tparam R SpecificRecord
 * @tparam D GenomicDataset
 */
trait GenomicReader[H, R, D] {

  // Valid filename suffixes
  def suffixes: Array[String]

  /**
   * Loads file from local filesystem, http or hdfs.
   *
   * @param fp url to file
   * @param regions Iterable of ReferenceRegions
   * @param sc Optional SparkContext, only used for HDFS files
   * @return Array of Specific Records
   */
  def loadFromSource(fp: String, regions: Option[Iterable[ReferenceRegion]], sc: Option[SparkContext] = None): Array[R] = {

    if (new java.io.File(fp).exists) { // if fp exists in local filesystem, load locally
      load(fp, regions, true)._2
    } else if (fp.startsWith("http")) {
      // http file
      load(fp, regions, false)._2
    } else {
      require(sc.isDefined, "HDFS requires a SparkContext to run")
      loadHDFS(sc.get, fp, regions)._2
    }
  }

  /**
   * Checks if file has valid suffix.
   * @param path file path
   * @return boolean whether path is valid
   */
  def isValidSuffix(path: String): Boolean = {
    !suffixes.filter(s => path.endsWith(s)).isEmpty
  }

  def invalidFileException(fp: String): Exception = {
    throw new FileNotFoundException(s"${fp} has invalid extension for available extensions ${suffixes.mkString(", ")}")
  }

  def load(path: String, regions: Option[Iterable[ReferenceRegion]], local: Boolean): Tuple2[H, Array[R]]

  def loadS3(path: String, regions: Option[Iterable[ReferenceRegion]]): Tuple2[H, Array[R]]

  def loadHDFS(sc: SparkContext, path: String, regions: Option[Iterable[ReferenceRegion]]): Tuple2[D, Array[R]]

  /**
   * Helper function for generating URL from string
   * @param urlString URL string
   * @return URL
   */
  def createURL(urlString: String): URL = {

    return new URL(urlString.trim())

  }

}
