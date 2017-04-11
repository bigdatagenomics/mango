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
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.bdgenomics.adam.models.{ Coverage, ReferenceRegion, SequenceDictionary }
import org.bdgenomics.adam.projections.{ Projection }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.adam.rdd.feature.CoverageRDD
import org.bdgenomics.mango.core.util.ResourceUtils
import org.bdgenomics.mango.layout.PositionCount
import org.bdgenomics.utils.misc.Logging

/**
 *
 * @param sc SparkContext
 * @param sd Sequence Dictionay calculated from reference
 * extends LazyMaterialization and KTiles
 * @see LazyMaterialization
 * @see KTiles
 */
class CoverageMaterialization(@transient sc: SparkContext,
                              files: List[String],
                              sd: SequenceDictionary,
                              prefetchSize: Option[Long] = None)
    extends LazyMaterialization[Coverage, PositionCount](CoverageMaterialization.name, sc, files, sd, prefetchSize)
    with Serializable with Logging {

  def load = (file: String, regions: Option[Iterable[ReferenceRegion]]) => CoverageMaterialization.load(sc, file, regions).rdd

  /**
   * Extracts ReferenceRegion from CoverageRecord
   *
   * @param c CoverageRecord
   * @return extracted ReferenceRegion
   */
  def getReferenceRegion = (c: Coverage) => ReferenceRegion(c)

  /**
   * Reset ReferenceName for Coverage
   *
   * @param c Coverage to be modified
   * @param contig to replace Coverage contigName
   * @return Coverage with new ReferenceRegion
   */
  def setContigName = (c: Coverage, contig: String) => {
    c.copy(contigName = contig)
  }

  /**
   * Formats raw data from RDD to JSON.
   *
   * @param region Region to obtain coverage for
   * @param binning Tells what granularity of coverage to return. Used for large regions
   * @return JSONified data map
   */
  override def getJson(region: ReferenceRegion, verbose: Boolean = false, binning: Int = 1): Map[String, Array[PositionCount]] = {
    val data: RDD[(String, Coverage)] = get(Some(region))

    val covCounts: RDD[(String, PositionCount)] =
      if (binning > 1) {
        bin(data, binning)
          .map(r => {
            // map to bin start, bin end
            val start = r._1._2.start
            val end = Math.max(r._2.end, start + binning)
            (r._1._1, PositionCount(region.referenceName, start, end, r._2.count.toInt))
          })
      } else {
        data.mapValues(r => PositionCount(region.referenceName, r.start, r.end, r.count.toInt))
      }

    covCounts.collect.groupBy(_._1) // group by sample Id
      .mapValues(r => r.map(_._2).sortBy(_.start)) // sort coverage
  }

  /**
   * Formats raw data from KLayeredTile to JSON. This is required by KTiles
   *
   * @param data RDD of (id, AlignmentRecord) tuples
   * @return JSONified data
   */
  def toJson(data: RDD[(String, Coverage)]): Map[String, Array[PositionCount]] = {
    data.collect
      .groupBy(_._1)
      .map(r => (r._1, r._2.map(_._2)))
      .mapValues(r => r.map(f => PositionCount(f.contigName, f.start, f.end, f.count.toInt)))
  }

}

object CoverageMaterialization {

  val name = "Coverage"

  /**
   * Loads alignment data from ADAM file formats
   *
   * @param sc SparkContext
   * @param fp filepath to load from
   * @return RDD of data from the file over specified ReferenceRegion
   */
  def load(sc: SparkContext, fp: String, regions: Option[Iterable[ReferenceRegion]]): CoverageRDD = {
    if (fp.endsWith(".adam")) loadAdam(sc, fp, regions)
    else {
      try {
        FeatureMaterialization.loadData(sc, fp, regions).toCoverage
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
   * @param fp filepath to load from
   * @param regions Iterable of  ReferenceRegions to load
   * @return CoverageRDD of data from the file over specified ReferenceRegion
   */
  def loadAdam(sc: SparkContext, fp: String, regions: Option[Iterable[ReferenceRegion]]): CoverageRDD = {
    val pred =
      if (regions.isDefined) {
        val prefixRegions: Iterable[ReferenceRegion] = regions.get.map(r => LazyMaterialization.getContigPredicate(r)).flatten
        Some(ResourceUtils.formReferenceRegionPredicate(prefixRegions))
      } else {
        None
      }
    sc.loadParquetCoverage(fp, predicate = pred).flatten()
  }

}