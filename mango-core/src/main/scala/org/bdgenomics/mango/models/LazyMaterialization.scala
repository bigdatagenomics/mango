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

import org.apache.spark.{ HashPartitioner, Partitioner, SparkContext }
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel
import org.bdgenomics.adam.models.{ ReferenceRegion, SequenceDictionary }
import org.bdgenomics.adam.rdd.GenomicRegionPartitioner
import org.bdgenomics.mango.util.Bookkeep
import org.bdgenomics.utils.interval.rdd.IntervalRDD
import org.bdgenomics.utils.misc.Logging

import scala.collection.mutable.ListBuffer
import scala.reflect.ClassTag

/**
 * Tracks regions of data already in memory and loads regions as needed.
 *
 * @param name Name of Materialization structure. Used for Spark UI.
 * @param prefetch prefetch size to lazily grab data. Defaults to 1000000
 */
abstract class LazyMaterialization[T: ClassTag](name: String,
                                                @transient sc: SparkContext,
                                                files: List[String],
                                                sd: SequenceDictionary,
                                                prefetch: Option[Int] = None) extends Serializable with Logging {

  val prefetchSize = prefetch.getOrElse(10000)

  val bookkeep = new Bookkeep(prefetchSize)
  var memoryFraction = 0.85 // default caching fraction

  def setMemoryFraction(fraction: Double) =
    memoryFraction = fraction

  def getFiles: List[String] = files
  var intRDD: IntervalRDD[ReferenceRegion, (String, T)] = null

  /**
   * Used to generically load data from all file types
   * @return Generic RDD of data types from file
   */
  def load: (String, Option[ReferenceRegion]) => RDD[T]

  /**
   * Extracts reference region from data type T
   * @return extracted ReferenceRegion
   */
  def getReferenceRegion: (T) => ReferenceRegion

  /**
   * Reassigns ReferenceRegion for ClassTag T
   * @return T with new ReferenceRegion
   */
  def setContigName: (T, String) => T

  /**
   * Stringify T classtag to json
   * @param rdd RDD of elements keyed by String
   * @return Map of (key, json) for the ReferenceRegion specified
   */
  def stringify(rdd: RDD[(String, T)]): Map[String, String]

  /**
   * Sets partitioner
   * @return partitioner
   */
  def setPartitioner: Partitioner = {
    GenomicRegionPartitioner(sc.defaultParallelism, sd)
  }

  /**
   * gets dictionary
   * @return
   */
  def getDictionary: SequenceDictionary = sd

  /**
   * Gets json data for all files.
   * Filters all alignment data already loaded into the corresponding RDD that overlap a region.
   * If data has yet been loaded, loads data within this region.
   *
   * @param region: ReferenceRegion to fetch
   * @return Map of sampleIds and corresponding JSON
   */
  def getJson(region: ReferenceRegion): Map[String, String] = {
    val seqRecord = sd(region.referenceName)
    seqRecord match {
      case Some(_) => {
        val regionsOpt = bookkeep.getMissingRegions(region, files)
        if (regionsOpt.isDefined) {
          for (r <- regionsOpt.get) {
            put(r)
          }
        }
        stringify(intRDD.filterByInterval(region).toRDD.map(_._2))
      } case None => {
        throw new Exception("Not found in dictionary")
      }
    }
  }

  /**
   * Bins region by binning size
   * @param r ReferenceRegion
   * @param binning binning size
   * @return binned ReferenceRegion
   */
  private def binRegion(r: ReferenceRegion, binning: Int): ReferenceRegion = {
    val start = r.start - (r.start % binning)
    r.copy(start = start, end = (start + binning))
  }

  /**
   * Bins elements into one record based on ReferenceRegion. Regions are set width bins, so
   * if elements can overflow a bin, the element with the longest region is chosen during
   * reduceByKey.
   *
   * @param rdd RDD to bin. (key, element) pairs.
   * @param binning Size to bin by
   * @return RDD of binned elements. Key contains modified binned ReferenceRegion
   */
  def bin(rdd: RDD[(String, T)], binning: Int): RDD[((String, ReferenceRegion), T)] = {
    rdd.map(r => ((r._1, binRegion(getReferenceRegion(r._2), binning)), r._2))
      .reduceByKey((a, b) => {
        if (getReferenceRegion((a)).end > getReferenceRegion(b).end) a
        else b
      })
  }

  /**
   * Gets raw data for all files.
   * Filters all alignment data already loaded into the corresponding RDD that overlap a region.
   * If data has yet been loaded, loads data within this region.
   *
   * @param region: ReferenceRegion to fetch
   * @return Map of sampleIds and corresponding JSON
   */
  def get(region: ReferenceRegion): RDD[(String, T)] = {
    val seqRecord = sd(region.referenceName)
    seqRecord match {
      case Some(_) => {
        val regionsOpt = bookkeep.getMissingRegions(region, files)
        if (regionsOpt.isDefined) {
          for (r <- regionsOpt.get) {
            put(r)
          }
        }
        intRDD.filterByInterval(region).toRDD.map(_._2)
      } case None => {
        throw new Exception(s"${region} not found in dictionary")
      }
    }
  }

  def getAll(): RDD[T] = {
    val hasChrPrefix = sd.records.head.name.startsWith("chr")
    files.map(fp => load(fp, None)).reduce(_ union _)
      .map(r => {
        val region = LazyMaterialization.modifyChrPrefix(getReferenceRegion(r), hasChrPrefix)
        setContigName(r, region.referenceName)
      })
  }

  /**
   *  Transparent to the user, should only be called by get if IntervalRDD.get does not return data
   * Fetches the data from disk, using predicates and range filtering
   * Then puts fetched data in the IntervalRDD, and calls multiget again, now with the data existing
   *
   * @param region ReferenceRegion in which data is retreived
   */
  def put(region: ReferenceRegion) = {
    checkMemory
    val seqRecord = sd(region.referenceName)
    if (seqRecord.isDefined) {

      // do we need to modify the chromosome prefix?
      val hasChrPrefix = seqRecord.get.name.startsWith("chr")

      val data =
        // get data for all samples
        files.map(fp => {
          val k = LazyMaterialization.filterKeyFromFile(fp)
          load(fp, Some(region)).map(v => (k, v))
        }).reduce(_ union _).map(r => {
          val region = LazyMaterialization.modifyChrPrefix(getReferenceRegion(r._2), hasChrPrefix)
          (region, (r._1, setContigName(r._2, region.referenceName)))
        })

      // tag regions as found, even if there is no data
      bookkeep.rememberValues(region, files)

      // insert into IntervalRDD if there is data
      if (intRDD == null) {
        // we must repartition in case the data we are adding has no partitioner (i.e., empty RDD)
        intRDD = IntervalRDD(data.partitionBy(new HashPartitioner(sc.defaultParallelism)))
        intRDD.persist(StorageLevel.MEMORY_AND_DISK)
      } else {
        val t = intRDD
        intRDD = intRDD.multiputRDD(data)
        t.unpersist(true)
        intRDD.persist(StorageLevel.MEMORY_AND_DISK)
      }
      intRDD.setName(name)
    }
  }

  /**
   * Checks memory across all executors
   * @return
   */
  private def checkMemory() = {
    val mem = sc.getExecutorMemoryStatus
    val (total, available) = mem.map(_._2)
      .reduce((e1, e2) => (e1._1 + e2._1, e1._2 + e2._2))
    val fraction: Double = (total - available).toFloat / total

    // if memory usage exceeds 85%, drop last viewed chromosome
    if (fraction > memoryFraction) {
      val dropped = bookkeep.dropValues()
      log.warn(s"memory limit exceeded. Dropping ${dropped} from cache")
      intRDD = intRDD.filter(_._1.referenceName != dropped)
    }
  }

}

object LazyMaterialization {

  /**
   * Extracts location agnostic key form file
   * @param file file to extract key from
   * @return memoryless key representing file
   */
  def filterKeyFromFile(file: String): String = {
    val slash = file.split("/")
    val fileName = slash.last
    fileName.replace(".", "_")
  }

  /**
   * Either strips, maintains or adds the prefix "chr" depending on the expected chr name.
   *
   * @param region Region to modify
   * @param chrPrefix Whether or not the prefix requires "chr" as a prefix
   * @return Modified chrPrefix
   */
  def modifyChrPrefix(region: ReferenceRegion, chrPrefix: Boolean): ReferenceRegion = {
    region.copy(referenceName = modifyChrPrefix(region.referenceName, chrPrefix))
  }

  /**
   * Either strips, maintains or adds the prefix "chr" depending on the expected chr name.
   *
   * @param name String to modify
   * @param chrPrefix Whether or not the prefix requires "chr" as a prefix
   * @return Modified chrPrefix
   */
  def modifyChrPrefix(name: String, chrPrefix: Boolean): String = {
    val hasPrefix = name.contains("chr")
    // case 1: expected and actual prefixes match, do nothing
    if ((hasPrefix && chrPrefix) || (!hasPrefix && !chrPrefix)) name
    // case 2: we must add the prefix
    else if (hasPrefix) name.drop(3)
    // case 3: we must strip the prefix
    else ("chr").concat(name)
  }

  /**
   * Gets predicate ReferenceRegion options for chromosome name based on searchable region. For example, "chr20"
   * should also search "20", and "20" should also trigger the search of "chr20".
   *
   * @param region ReferenceRegion to modify referenceName
   * @return Tuple2 of ReferenceRegions, with and without the "chr" prefix
   */
  def getContigPredicate(region: ReferenceRegion): Tuple2[ReferenceRegion, ReferenceRegion] = {
    if (region.referenceName.startsWith("chr")) {
      val modifiedRegion = ReferenceRegion(region.referenceName.drop(3), region.start, region.end, region.strand)
      Tuple2(region, modifiedRegion)
    } else {
      val modifiedRegion = ReferenceRegion(("chr").concat(region.referenceName), region.start, region.end, region.strand)
      Tuple2(region, modifiedRegion)
    }
  }
}

case class UnsupportedFileException(message: String) extends Exception(message)
