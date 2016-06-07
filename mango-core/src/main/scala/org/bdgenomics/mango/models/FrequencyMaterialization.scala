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

import org.apache.hadoop.fs.{ FileSystem, Path }
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.DataFrame
import org.apache.spark.storage.StorageLevel
import org.apache.spark.SparkContext
import org.bdgenomics.adam.models.{ ReferenceRegion, SequenceDictionary }
import org.bdgenomics.mango.core.util.VizUtils
import org.bdgenomics.mango.util.Bookkeep
import org.bdgenomics.utils.misc.Logging

class FrequencyMaterialization(s: SparkContext,
                               d: SequenceDictionary,
                               chunkS: Int, maxBins: Int = 1000) extends Serializable with Logging {

  implicit val formats = net.liftweb.json.DefaultFormats

  val sc = s
  val dict = d
  val chunkSize = chunkS
  val bookkeep = new Bookkeep(chunkSize)

  val sqlContext = new org.apache.spark.sql.SQLContext(sc)

  var fileMap: Map[String, CoverageFile] = Map[String, CoverageFile]()
  var coverage: DataFrame = sqlContext.createDataFrame(sc.emptyRDD[SampleCoverage])

  /*
   * Adds coverage file to fileMap. If coverage file is found, file is
   * added as coverage. Otherwise, file is added as raw alignment data.
   *
   * @parameter filePath: String
   * @parameter sampleId: String
   */
  def loadSample(filePath: String, sampleId: String): Unit = {
    val coverageFile = filePath + ".coverage.adam"
    val coveragePath = new Path(filePath + ".coverage.adam")
    val fs: FileSystem = coveragePath.getFileSystem(sc.hadoopConfiguration)
    if (fs.exists(coveragePath)) {
      // load all coverage immediately
      fileMap += (sampleId -> CoverageFile(coverageFile, true))
      coverage = coverage.unionAll(loadCoverage(sampleId))
    } else {
      fileMap += (sampleId -> CoverageFile(filePath, false))
    }
  }

  /*
   * Loads coverage from precomputed adam file or from scratch from an alignment record file
   *
   * @param filePath: String where file is located
   * @param region: ReferenceRegion to take from
   */
  def loadCoverage(sampleId: String, regionOpt: Option[ReferenceRegion] = None): DataFrame = {
    val file = fileMap(sampleId)
    val coverage: RDD[SampleCoverage] =
      if (file.containsCoverage) {
        val df = sqlContext.read.load(file.filePath)
        df.rdd.map(r => SampleCoverage(sampleId, r.getString(0), r.getLong(1), r.getInt(2)))
      } else {
        val region = regionOpt.getOrElse(throw new Exception)
        val rdd = AlignmentRecordMaterialization.loadAlignmentData(sc, region, file.filePath)

        rdd.flatMap(r => (r.getStart.toLong to r.getEnd.toLong))
          .filter(r => (r >= region.start && r <= region.end))
          .map(r => (r, 1)).reduceByKey((k, v) => k + v)
          .map(r => SampleCoverage(sampleId, region.referenceName, r._1, r._2))
      }
    sqlContext.createDataFrame(coverage)
  }

  def get(region: ReferenceRegion, ks: List[String]): String = {
    val binSize = VizUtils.getBinSize(region, maxBins)
    val seqRecord = dict(region.referenceName)
    val regionsOpt = bookkeep.getMaterializedRegions(region, ks)
    seqRecord match {
      case Some(_) => {
        regionsOpt match {
          case Some(_) => {
            for (r <- regionsOpt.get) {
              put(r, ks)
            }
          } case None => {
            // DO NOTHING
          }
        }

        val json = coverage.filter((coverage("position") % binSize === 0) && (coverage("position") <= region.end) && (coverage("position") >= region.start)
          && (coverage("referenceName") === region.referenceName) && (coverage("sample") isin (ks: _*))).toJSON.collect
        "[" + json.mkString(",") + "]"
      } case None => {
        ""
      }
    }
  }

  /**
   *  Transparent to the user, should only be called by get if IntervalRDD.get does not return data
   * Fetches the data from disk, using predicates and range filtering
   * Then puts fetched data in the IntervalRDD, and calls multiget again, now with the data existing
   *
   * @param region ReferenceRegion in which data is retreived
   * @param ks to be retreived
   */
  def put(region: ReferenceRegion, ks: List[String]) = {
    // only load data in from files that are not precomputed
    val keys = ks.filter(k => !fileMap(k).containsCoverage)
    val seqRecord = dict(region.referenceName)
    seqRecord match {
      case Some(_) => {

        var depths = sc.emptyRDD[SampleCoverage]
        keys.map(k => {
          val data = loadCoverage(k, Some(region))
          coverage = coverage.unionAll(data)
        })

        coverage.persist(StorageLevel.MEMORY_AND_DISK)
        bookkeep.rememberValues(region, ks)
      }
      case None => {
      }
    }
  }

}

case class CoverageFile(filePath: String, containsCoverage: Boolean)
case class SampleCoverage(sample: String, referenceName: String, position: Long, count: Int)

