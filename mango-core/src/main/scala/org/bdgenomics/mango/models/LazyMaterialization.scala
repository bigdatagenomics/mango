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

import java.io.File

import org.apache.hadoop.fs.Path
import org.apache.spark.{ HashPartitioner, SparkContext }
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel
import org.bdgenomics.adam.models.{ ReferenceRegion, SequenceDictionary }
import org.bdgenomics.mango.core.util.ResourceUtils
import org.bdgenomics.mango.util.Bookkeep
import org.bdgenomics.utils.instrumentation.Metrics
import org.bdgenomics.utils.interval.array.IntervalArray
import org.bdgenomics.utils.misc.Logging
import jdk.nashorn.internal.ir.debug.ObjectSizeCalculator

import scala.reflect.ClassTag

// metric variables
object LazyMaterializationTimers extends Metrics {

  def put = timer("put data in lazy materialization")
  def get = timer("get data in lazy materialization")
  def checkMemory = timer("check memory in lazy materialization")
  def loadFiles = timer("load files in lazy materialization")

}

/**
 * Tracks regions of data already in memory and loads regions as needed.
 *
 * @param name Name of Materialization structure. Used for Spark UI.
 * @param sc SparkContext
 * @param files list files to materialize
 * @param sd the sequence dictionary associated with the file records
 * @param prefetchSize the number of base pairs to prefetch in executors. Defaults to 100000
 */
abstract class LazyMaterialization[T: ClassTag, S: ClassTag](name: String,
                                                             @transient sc: SparkContext,
                                                             files: List[String],
                                                             sd: SequenceDictionary,
                                                             prefetchSize: Option[Long] = None) extends Serializable with Logging {
  @transient implicit val formats = net.liftweb.json.DefaultFormats

  val bookkeep = new Bookkeep(prefetchSize.getOrElse(100000))
  var memoryFraction = 0.85 // default caching fraction

  def setMemoryFraction(fraction: Double) =
    memoryFraction = fraction

  /**
   * Gets files for this materialization structure.
   * @return List of Materialized files
   */
  def getFiles: List[String] = files

  var intArray: IntervalArray[ReferenceRegion, (String, T)] =
    IntervalArray[ReferenceRegion, (String, T)](Array[(ReferenceRegion, (String, T))](), 40000L, true)

  /**
   * Used to generically load data from all file types
   * @return Generic RDD of data types from file
   */
  def load: (String, Option[Iterable[ReferenceRegion]]) => Array[T]

  /**
   * Extracts reference region from data type T
   * @return extracted ReferenceRegion
   */
  def getReferenceRegion: (T) => ReferenceRegion

  /**
   * Reassigns ReferenceRegion for ClassTag T
   * @return T with new ReferenceRegion
   */
  def setReferenceName: (T, String) => T

  def stringify: (Array[S]) => String

  /**
   * Stringify T classtag to json
   * @param iter RDD of elements keyed by String
   * @return Map of (key, json) for the ReferenceRegion specified
   */
  def toJson(data: Array[(String, T)]): Map[String, Array[S]]

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
  def getJson(region: ReferenceRegion): Map[String, Array[S]] = toJson(get(Some(region)))

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
   * @param regionOpt: ReferenceRegion to fetch
   * @return Map of sampleIds and corresponding JSON
   */
  def get(regionOpt: Option[ReferenceRegion] = None): Array[(String, T)] = {
    LazyMaterializationTimers.get.time {
      regionOpt match {
        case Some(_) => {
          val region = regionOpt.get
          val seqRecord = sd(region.referenceName)
          seqRecord match {
            case Some(_) => {
              val missing = bookkeep.getMissingRegions(region, getFiles)
              if (!missing.isEmpty) {
                put(missing) // put data into interval array
              }
              intArray.get(region).toArray.sortBy(_._1).map(_._2)
            }
            case None => {
              throw new Exception(s"${region} not found in dictionary")
            }
          }
        }
        case None => {
          val data = loadAllFiles(None)

          // tag entire sequence dictionary
          bookkeep.rememberValues(sd, getFiles)

          intArray = intArray.insert(data.toIterator)
          data.map(_._2).toArray
        }
      }
    }
  }

  /**
   *  Transparent to the user, should only be called by get if IntervalRDD.get does not return data
   * Fetches the data from disk, using predicates and range filtering
   * Then puts fetched data in the IntervalRDD, and calls multiget again, now with the data existing
   *
   * @param regions ReferenceRegion in which data is retrieved
   */
  def put(regions: Iterable[ReferenceRegion]) = {
    checkMemory()

    //    LazyMaterializationTimers.put.time {

    // filter out regions that are not found in the sequence dictionary
    val filteredRegions = regions.filter(r => sd(r.referenceName).isDefined)

    val data = loadAllFiles(Some(regions))

    // tag regions as found, even if there is no data
    filteredRegions.foreach(r => bookkeep.rememberValues(r, getFiles))

    intArray = intArray.insert(data.toIterator)

    //    }
  }

  /**
   * Loads data from all files in materialization structure.
   *
   * @note: Modifies chromosome prefix depending on any discrepancies between the region requested and the
   * sequence dictionary.
   *
   * @param regions Optional region to fetch. If none, fetches all data
   * @return RDD of data. Primary index is ReferenceRegion and secondary index is filename.
   */
  private def loadAllFiles(regions: Option[Iterable[ReferenceRegion]]): List[(ReferenceRegion, (String, T))] = {
    // do we need to modify the chromosome prefix?
    val hasChrPrefix = sd.records.head.name.startsWith("chr")

    LazyMaterializationTimers.loadFiles.time {
      // get data for all files
      getFiles.map(fp => {
        val k = LazyMaterialization.filterKeyFromFile(fp)
        val t = load(fp, regions)
        t.map(v => (k, v))
      }).flatten.map(r => {
        val region = LazyMaterialization.modifyChrPrefix(getReferenceRegion(r._2), hasChrPrefix)
        (region, (r._1, setReferenceName(r._2, region.referenceName)))
      })
    }
  }

  /**
   * Checks memory
   * @return
   */
  private def checkMemory() = {
    LazyMaterializationTimers.checkMemory.time {

      val size = ObjectSizeCalculator.getObjectSize(intArray)
      val fraction = (size.toDouble) / Runtime.getRuntime().totalMemory()

      // if memory usage exceeds memoryFraction, drop last viewed chromosome
      if (fraction > memoryFraction) {
        val dropped = bookkeep.dropValues() // drop last chromosome
        log.warn(s"memory limit exceeded. Dropping ${dropped} from cache")
        intArray = intArray.filter(_._1.referenceName != dropped)
      }
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
   * @return Array of ReferenceRegions, with and without the "chr" prefix
   */
  def getReferencePredicate(region: ReferenceRegion): Array[ReferenceRegion] = {
    getReferencePredicate(region.referenceName).map(r => region.copy(referenceName = r))
  }

  /**
   * Gets predicate reference name options for chromosome name based on searchable region. For example, "chr20"
   * should also search "20", and "20" should also trigger the search of "chr20".
   *
   * @param referenceName referenceName
   * @return Array of of referencenames, with and without the "chr" prefix
   */
  def getReferencePredicate(referenceName: String): Array[String] = {
    if (referenceName.startsWith("chr")) {
      Array(referenceName, referenceName.drop(3))
    } else {
      Array(referenceName, ("chr").concat(referenceName))
    }
  }

}

case class UnsupportedFileException(message: String) extends Exception(message)
