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

import java.io.{ PrintWriter, StringWriter }

import net.liftweb.json.Serialization.write
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.apache.parquet.filter2.dsl.Dsl._
import org.bdgenomics.adam.models.{ Coverage, ReferenceRegion, SequenceDictionary }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.adam.rdd.feature.CoverageRDD
import org.bdgenomics.mango.layout.PositionCount
import org.bdgenomics.utils.misc.Logging
import net.liftweb.json.Serialization._

/**
 *
 * @param s SparkContext
 * @param dict Sequence Dictionay calculated from reference
 * extends LazyMaterialization and KTiles
 * @see LazyMaterialization
 * @see KTiles
 */
class CoverageMaterialization(s: SparkContext,
                              filePaths: List[String],
                              dict: SequenceDictionary) extends LazyMaterialization[Coverage]("CoverageRDD")
    with Serializable with Logging {

  @transient implicit val formats = net.liftweb.json.DefaultFormats
  @transient val sc = s
  val sd = dict
  val files = filePaths

  def load = (region: ReferenceRegion, file: String) => CoverageMaterialization.load(sc, region, file).rdd

  /**
   * Extracts ReferenceRegion from CoverageRecord
   *
   * @param ar CoverageRecord
   * @return extracted ReferenceRegion
   */
  def getReferenceRegion = (ar: Coverage) => ReferenceRegion(ar)

  /**
   * Formats raw data from RDD to JSON.
   *
   * @param region Region to obtain coverage for
   * @param binning Tells what granularity of coverage to return. Used for large regions
   * @return JSONified data map
   */
  def getCoverage(region: ReferenceRegion, binning: Int = 1): Map[String, String] = {
    val covCounts: RDD[(String, PositionCount)] =
      if (binning > 1) {
        get(region)
          .map(r => {
            // map to bin start, bin end
            val pc = r._2
            val start = pc.start - (pc.start % binning)
            val end = start + binning
            ((r._1, start), PositionCount(start, end, pc.count.toInt))
          }).reduceByKey((a, b) => {
            // reduce by start
            PositionCount(a.start, a.end, (a.count + b.count) / 2)
          }).map(r => (r._1._1, r._2))
      } else {
        get(region).mapValues(r => PositionCount(r.start, r.end, r.count.toInt))
      }

    covCounts.collect.groupBy(_._1) // group by sample Id
      .mapValues(r => r.sortBy(_._2.start)) // sort coverage
      .map(r => (r._1, write(r._2.map(_._2))))
  }
  /**
   * Formats raw data from KLayeredTile to JSON. This is required by KTiles
   *
   * @param data RDD of (id, AlignmentRecord) tuples
   * @return JSONified data
   */
  def stringify(data: RDD[(String, Coverage)]): Map[String, String] = {
    val flattened: Map[String, Array[PositionCount]] = data
      .collect
      .groupBy(_._1)
      .map(r => (r._1, r._2.map(_._2)))
      .mapValues(r => r.map(f => PositionCount(f.start, f.end, f.count.toInt)))
    flattened.mapValues(r => write(r))
  }
}

object CoverageMaterialization {

  def apply(sc: SparkContext, files: List[String], sd: SequenceDictionary): CoverageMaterialization = {
    new CoverageMaterialization(sc, files, sd)
  }

  /**
   * Loads alignment data from ADAM file formats
   *
   * @param sc SparkContext
   * @param fp filepath to load from
   * @return RDD of data from the file over specified ReferenceRegion
   */
  def load(sc: SparkContext, region: ReferenceRegion, fp: String): CoverageRDD = {
    if (fp.endsWith(".adam")) loadAdam(sc, region, fp)
    else {
      try {
        FeatureMaterialization.loadData(sc, Some(region), fp).toCoverage
      } catch {
        case e: Exception => {
          val sw = new StringWriter
          e.printStackTrace(new PrintWriter(sw))
          throw UnsupportedFileException("File type not supported. Stack trace: " + sw.toString)
        }
      }
    }
  }
  /**
   * Loads ADAM data using predicate pushdowns
   *
   * @param sc SparkContext
   * @param region Region to load
   * @param fp filepath to load from
   * @return RDD of data from the file over specified ReferenceRegion
   */
  def loadAdam(sc: SparkContext, region: ReferenceRegion, fp: String): CoverageRDD = {
    val predicate = (LongColumn("end") <= region.end) && (LongColumn("start") >= region.start) && (BinaryColumn("contigName") === region.referenceName)
    sc.loadParquetCoverage(fp, Some(predicate)).flatten()
  }
}
