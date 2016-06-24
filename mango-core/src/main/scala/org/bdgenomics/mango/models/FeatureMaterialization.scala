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

import net.liftweb.json.Serialization.write
import org.apache.parquet.filter2.dsl.Dsl._
import org.apache.parquet.filter2.predicate.FilterPredicate
import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel
import org.bdgenomics.adam.models.{ ReferenceRegion, SequenceDictionary }
import org.bdgenomics.adam.projections.{ FeatureField, Projection }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.formats.avro.Feature
import org.bdgenomics.mango.layout.FeatureLayout
import org.bdgenomics.mango.tiling.{ FeatureTile, Tiles }
import org.bdgenomics.mango.util.Bookkeep
import org.bdgenomics.utils.intervalrdd.IntervalRDD
import org.bdgenomics.utils.misc.Logging

class FeatureMaterialization(sc: SparkContext,
                             filePaths: List[String],
                             dict: SequenceDictionary,
                             chunkS: Int) extends Tiles[Iterable[Feature], FeatureTile] with Serializable with Logging {

  protected def tag = reflect.classTag[Iterable[Feature]]

  init
  val chunkSize = chunkS
  val files = filePaths
  val bookkeep = new Bookkeep(chunkSize)
  var intRDD: IntervalRDD[ReferenceRegion, FeatureTile] = null

  /**
   * Gets data for multiple keys.
   * If the RDD has not been initialized, initialize it to the first get request
   * Gets the data for an interval for the file loaded by checking in the bookkeeping tree.
   * If it exists, call get on the IntervalRDD
   * Otherwise call put on the sections of data that don't exist
   * Here, ks, is a list of personids (String)
   * @param region: ReferenceRegion to fetch
   * @return JSONified data
   */
  def get(region: ReferenceRegion): String = {
    val seqRecord = dict(region.referenceName)
    seqRecord match {
      case Some(_) => {
        val regionsOpt = bookkeep.getMaterializedRegions(region, filePaths)
        if (regionsOpt.isDefined) {
          for (r <- regionsOpt.get) {
            put(r)
          }
        }
        getRaw(region)
      } case None => {
        throw new Exception("Not found in dictionary")
      }
    }
  }

  /**
   *  Transparent to the user, should only be called by get if IntervalRDD.get does not return data
   * Fetches the data from disk, using predicates and range filtering
   * Then puts fetched data in the IntervalRDD, and calls multiget again, now with the data existing
   *
   * @param region ReferenceRegion in which data is retreived
   */
  def put(region: ReferenceRegion) = {
    val seqRecord = dict(region.referenceName)
    if (seqRecord.isDefined) {
      var data: RDD[Feature] = sc.emptyRDD[Feature]

      // get alignment data for all samples
      filePaths.map(path => {
        val fileData = FeatureMaterialization.load(sc, Some(region), path)
        data = data.union(fileData)
      })

      var mappedRecords: RDD[(ReferenceRegion, Feature)] = sc.emptyRDD[(ReferenceRegion, Feature)]

      // divide regions by chunksize
      val regions: List[ReferenceRegion] = Bookkeep.unmergeRegions(region, chunkSize)

      // for all regions, filter by that region and create AlignmentRecordTile
      regions.foreach(r => {
        val grouped = data.filter(ar => r.overlaps(ReferenceRegion(ar))).map(ar => (r, ar))
        mappedRecords = mappedRecords.union(grouped)
      })

      val groupedRecords: RDD[(ReferenceRegion, Iterable[Feature])] =
        mappedRecords
          .groupBy(_._1)
          .map(r => (r._1, r._2.map(_._2)))
      val tiles: RDD[(ReferenceRegion, FeatureTile)] = groupedRecords.map(r => (r._1, new FeatureTile(r._2)))

      // insert into IntervalRDD
      if (intRDD == null) {
        intRDD = IntervalRDD(tiles)
        intRDD.persist(StorageLevel.MEMORY_AND_DISK)
      } else {
        val t = intRDD
        intRDD = intRDD.multiput(tiles)
        // TODO: can we do this incrementally instead?
        t.unpersist(true)
        intRDD.persist(StorageLevel.MEMORY_AND_DISK)
      }
      bookkeep.rememberValues(region, filePaths)
    }
  }

  def init = {
    // verify file types
    filePaths.foreach(path => {
      if (!(path.endsWith(".bed") || path.endsWith(".adam"))) {
        throw new UnsupportedFileException(s"WARNING: ${path} is not a valid feature file")
      }
    })
  }

  def stringifyRaw(data: RDD[(ReferenceRegion, Iterable[Feature])], region: ReferenceRegion): String = stringify(data.flatMap(_._2), region)

  def stringify(data: RDD[Feature], region: ReferenceRegion): String = {
    implicit val formats = net.liftweb.json.DefaultFormats
    val filteredFeatures = data.filter(f => f.getStart <= region.end && f.getEnd >= region.start)
    write(FeatureLayout(filteredFeatures)) // TODO: distinct
  }

}

object FeatureMaterialization {

  /**
   * Loads feature data from bam, sam and ADAM file formats
   * @param sc SparkContext
   * @param region Region to load
   * @param fp filepath to load from
   * @return RDD of data from the file over specified ReferenceRegion
   */
  def load(sc: SparkContext, region: Option[ReferenceRegion], fp: String): RDD[Feature] = {
    if (fp.endsWith(".adam")) FeatureMaterialization.loadAdam(sc, region, fp)
    else if (fp.endsWith(".bed")) {
      FeatureMaterialization.loadFromBed(sc, region, fp)
    } else {
      throw UnsupportedFileException("File type not supported")
    }
  }

  /**
   * Loads data from bam files (indexed or unindexed) from persistent storage
   * @param sc SparkContext
   * @param region Region to load
   * @param fp filepath to load from
   * @return RDD of data from the file over specified ReferenceRegion
   */
  def loadFromBed(sc: SparkContext, region: Option[ReferenceRegion], fp: String): RDD[Feature] = {
    region match {
      case Some(_) => sc.loadFeatures(fp).filterByOverlappingRegion(region.get)
      case None    => sc.loadFeatures(fp)
    }
  }

  /**
   * Loads ADAM data using predicate pushdowns
   * @param sc SparkContext
   * @param region Region to load
   * @param fp filepath to load from
   * @return RDD of data from the file over specified ReferenceRegion
   */
  def loadAdam(sc: SparkContext, region: Option[ReferenceRegion], fp: String): RDD[Feature] = {
    val pred: Option[FilterPredicate] =
      region match {
        case Some(_) => Some(((LongColumn("end") >= region.get.start) && (LongColumn("start") <= region.get.end) && (BinaryColumn("contig.contigName") === region.get.referenceName)))
        case None    => None
      }

    val proj = Projection(FeatureField.featureId, FeatureField.featureType, FeatureField.start, FeatureField.end, FeatureField.contigName)
    sc.loadParquetFeatures(fp, predicate = pred, projection = Some(proj))
  }

}