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

import java.io.{ File, FileNotFoundException }

import com.github.erictu.intervaltree._
import edu.berkeley.cs.amplab.spark.intervalrdd._
import org.apache.hadoop.fs.{ FileSystem, Path }
import org.apache.parquet.filter2.dsl.Dsl._
import org.apache.parquet.filter2.predicate.FilterPredicate
import org.apache.spark.{ Logging, _ }
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel
import org.bdgenomics.adam.models.{ RecordGroupDictionary, ReferencePosition, ReferenceRegion, SequenceDictionary }
import org.bdgenomics.adam.projections.{ AlignmentRecordField, FeatureField, GenotypeField, Projection }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.adam.rdd.GenomicRegionPartitioner
import org.bdgenomics.adam.rdd.read.AlignmentRecordRDD
import org.bdgenomics.formats.avro.{ AlignmentRecord, Feature, Genotype, NucleotideContigFragment }

import scala.collection.mutable
import scala.collection.mutable.{ HashMap, ListBuffer }
import scala.reflect.{ ClassTag, classTag }

class LazyMaterialization[T: ClassTag](sc: SparkContext, dict: SequenceDictionary, partitions: Int, chunkSize: Long) extends Serializable with Logging {

  var partitioner: Partitioner = null
  setPartitioner()

  def this(sc: SparkContext, dict: SequenceDictionary, partitions: Int) = {
    this(sc, dict, partitions, 1000)
  }

  // TODO: once tracking is pushed to front end, add sc.local partitioning
  def setPartitioner(): Unit = {
    partitioner = GenomicRegionPartitioner(partitions, dict)
  }

  def getDictionary: SequenceDictionary = {
    dict
  }

  // Stores location of sample at a given filepath
  def loadSample(sampleId: String, filePath: String) {
    fileMap += ((sampleId, filePath))
  }

  def loadADAMSample(filePath: String): String = {
    val region = ReferenceRegion("new", 0, chunkSize - 1)
    val rd: RecordGroupDictionary = loadAdam(region, filePath)._3
    val sample = rd.recordGroups.head.sample
    fileMap += ((sample, filePath))
    sample
  }

  // Keeps track of sample ids and corresponding files
  private var fileMap: mutable.HashMap[String, String] = new mutable.HashMap()

  def getFileMap: mutable.HashMap[String, String] = fileMap

  private var bookkeep: mutable.HashMap[String, IntervalTree[ReferenceRegion, String]] = new mutable.HashMap()

  var intRDD: IntervalRDD[ReferenceRegion, T] = null

  /*
  * Logs key and region values in a bookkeeping structure per chromosome
  */
  private def rememberValues(region: ReferenceRegion, ks: List[String]) = {
    if (bookkeep.contains(region.referenceName)) {
      bookkeep(region.referenceName).insert(region, ks.toIterator)
    } else {
      val newTree = new IntervalTree[ReferenceRegion, String]()
      newTree.insert(region, ks.toIterator)
      bookkeep += ((region.referenceName, newTree))
    }
  }

  def loadAdam(region: ReferenceRegion, fp: String): (RDD[(ReferenceRegion, T)], SequenceDictionary, RecordGroupDictionary) = {
    val isAlignmentRecord = classOf[AlignmentRecord].isAssignableFrom(classTag[T].runtimeClass)
    val isVariant = classOf[Genotype].isAssignableFrom(classTag[T].runtimeClass)
    val isFeature = classOf[Feature].isAssignableFrom(classTag[T].runtimeClass)
    val isNucleotideFrag = classOf[NucleotideContigFragment].isAssignableFrom(classTag[T].runtimeClass)
    val (rdd, sd, rg) =
      if (isAlignmentRecord) {
        val pred: FilterPredicate = ((LongColumn("end") >= region.start) && (LongColumn("start") <= region.end))
        val proj = Projection(AlignmentRecordField.contig, AlignmentRecordField.mapq, AlignmentRecordField.readName, AlignmentRecordField.start, AlignmentRecordField.end, AlignmentRecordField.sequence, AlignmentRecordField.cigar, AlignmentRecordField.readNegativeStrand, AlignmentRecordField.readPaired, AlignmentRecordField.recordGroupSample)
        val alignedReadRDD: AlignmentRecordRDD = sc.loadParquetAlignments(fp, predicate = Some(pred), projection = Some(proj))
        (alignedReadRDD.rdd.filter(_.getContig.getContigName == region.referenceName)
          .map(r => (ReferenceRegion(r), r)),
          alignedReadRDD.sequences, alignedReadRDD.recordGroups)
      } else if (isVariant) {
        val pred: FilterPredicate = ((LongColumn("variant.end") >= region.start) && (LongColumn("variant.start") <= region.end))
        val proj = Projection(GenotypeField.variant, GenotypeField.alleles)
        val d = sc.loadParquetGenotypes(fp, predicate = Some(pred), projection = Some(proj))
          .filter(_.getVariant.getContig.getContigName == region.referenceName)
          .map(r => (ReferenceRegion(ReferencePosition(r)), r))
        (d, null, null)
      } else if (isFeature) {
        val pred: FilterPredicate = ((LongColumn("end") >= region.start) && (LongColumn("start") <= region.end))
        val proj = Projection(FeatureField.contig, FeatureField.featureId, FeatureField.featureType, FeatureField.start, FeatureField.end)
        val d = sc.loadParquetAlignments(fp, predicate = Some(pred), projection = Some(proj))
          .filter(_.getContig.getContigName == region.referenceName)
          .map(r => (ReferenceRegion(r), r))
        (d, null, null)
      } else {
        log.warn("Generic type not supported")
        (null, null, null)
      }
    (rdd.asInstanceOf[RDD[(ReferenceRegion, T)]], sd, rg)
  }

  def loadFromBam(region: ReferenceRegion, fp: String): RDD[(ReferenceRegion, T)] = {
    if (LazyMaterialization.isLocal(fp + ".bai", sc)) {
      sc.loadIndexedBam(fp, region).map(r => (ReferenceRegion(r), r)).asInstanceOf[RDD[(ReferenceRegion, T)]]
    } else {
      sc.loadBam(fp).rdd.filterByOverlappingRegion(region).map(r => (ReferenceRegion(r), r)).asInstanceOf[RDD[(ReferenceRegion, T)]]
    }
  }

  def loadFromFile(region: ReferenceRegion, k: String): RDD[(ReferenceRegion, T)] = {
    var data: RDD[(ReferenceRegion, T)] = null
    if (!fileMap.containsKey(k)) {
      log.error("Key not in FileMap")
    }
    val fp = fileMap(k)
    if (fp.endsWith(".adam")) {
      data = loadAdam(region, fp)._1
    } else if (fp.endsWith(".sam") || fp.endsWith(".bam")) {
      data = loadFromBam(region, fp)
    } else if (fp.endsWith(".vcf")) {
      data = sc.loadGenotypes(fp).filterByOverlappingRegion(region).map(r => (ReferenceRegion(ReferencePosition(r)), r)).asInstanceOf[RDD[(ReferenceRegion, T)]]
    } else if (fp.endsWith(".bed")) {
      data = sc.loadFeatures(fp).filterByOverlappingRegion(region).map(r => (ReferenceRegion(r), r)).asInstanceOf[RDD[(ReferenceRegion, T)]]
    } else {
      throw UnsupportedFileException("File type not supported")
    }
    data.partitionBy(partitioner)
  }

  def get(region: ReferenceRegion, k: String): Option[IntervalRDD[ReferenceRegion, T]] = {
    multiget(region, List(k))
  }

  /* If the RDD has not been initialized, initialize it to the first get request
	* Gets the data for an interval for the file loaded by checking in the bookkeeping tree.
	* If it exists, call get on the IntervalRDD
	* Otherwise call put on the sections of data that don't exist
	* Here, ks, is an option of list of personids (String)
	*/
  def multiget(region: ReferenceRegion, ks: List[String]): Option[IntervalRDD[ReferenceRegion, T]] = {
    val seqRecord = dict(region.referenceName)
    val regionsOpt = getMaterializedRegions(region, ks)
    seqRecord match {
      case Some(_) =>
        regionsOpt match {
          case Some(_) =>
            for (r <- regionsOpt.get) {
              put(r, ks)
            }
          case None =>
          // DO NOTHING
        }
        // TODO: return data to multiget instead of making subsequent call to RDD
        Option(intRDD.filterByInterval(region))
      case None =>
        None
    }
  }

  /**
   *  Transparent to the user, should only be called by get if IntervalRDD.get does not return data
   * Fetches the data from disk, using predicates and range filtering
   * Then puts fetched data in the IntervalRDD, and calls multiget again, now with the data existing
   *
   * @param region ReferenceRegion in which data is retreived
   * @param keys to be retreived
   */
  private def put(region: ReferenceRegion, ks: List[String]) = {
    val seqRecord = dict(region.referenceName)
    seqRecord match {
      case Some(_) =>
        val end =
          Math.min(region.end, seqRecord.get.length)
        val start = Math.min(region.start, end)
        val reg = new ReferenceRegion(region.referenceName, start, end)
        ks.map(k => {
          val data = loadFromFile(reg, k)
          if (intRDD == null) {
            intRDD = IntervalRDD(data)
            intRDD.persist(StorageLevel.MEMORY_AND_DISK)
          } else {
            intRDD = intRDD.multiput(data)
            intRDD.persist(StorageLevel.MEMORY_AND_DISK)
          }
        })
        rememberValues(region, ks)
      case None =>
    }
  }

  /**
   * gets materialized regions that are not yet loaded into the bookkeeping structure
   *
   * @param region that to be searched over
   * @param keys in which region is searched over. these are sample IDs
   * @return List of materialied and merged reference regions not found in bookkeeping structure
   */
  def getMaterializedRegions(region: ReferenceRegion, ks: List[String]): Option[List[ReferenceRegion]] = {
    val start = region.start / chunkSize * chunkSize
    val end = region.end / chunkSize * chunkSize + (chunkSize - 1)
    getMissingRegions(new ReferenceRegion(region.referenceName, start, end), ks)
  }

  /**
   * generates a list of reference regions that were not found in bookkeeping structure
   *
   * @param region that is divided into chunks and searched for in bookkeeping structure
   * @param keys in which region is searched over. these are sample IDs
   * @return List of reference regions not found in bookkeeping structure
   */
  def getMissingRegions(region: ReferenceRegion, ks: List[String]): Option[List[ReferenceRegion]] = {
    var regions: ListBuffer[ReferenceRegion] = new ListBuffer[ReferenceRegion]()
    var start = region.start / chunkSize * chunkSize
    var end = start + (chunkSize - 1)

    while (start <= region.end) {
      val r = new ReferenceRegion(region.referenceName, start, end)
      val size = {
        try {
          bookkeep(r.referenceName).search(r).length
        } catch {
          case ex: NoSuchElementException => 0
        }
      }
      if (size < ks.size) {
        regions += r
      }
      start += chunkSize
      end += chunkSize
    }

    if (regions.size < 1) {
      None
    } else {
      LazyMaterialization.mergeRegions(Option(regions.toList))
    }

  }
}

case class UnsupportedFileException(message: String) extends Exception(message)

object LazyMaterialization {

  def apply[T: ClassTag](sc: SparkContext, dict: SequenceDictionary, partitions: Int): LazyMaterialization[T] = {
    new LazyMaterialization[T](sc, dict, partitions)
  }

  def apply[T: ClassTag](sc: SparkContext, dict: SequenceDictionary, partitions: Int, chunkSize: Long): LazyMaterialization[T] = {
    new LazyMaterialization[T](sc, dict, partitions, chunkSize)
  }

  /**
   * generates a list of closely overlapping regions, counting for gaps in the list
   *
   * @note For example, given a list of regions with ranges (0, 999), (1000, 1999) and (3000, 3999)
   * This function will consolidate adjacent regions and output (0, 1999), (3000, 3999)
   * @note Requires that list region is ordered
   * @param Option of list of regions to merge
   * @return Option of list of merged adjacent regions
   */
  def mergeRegions(regionsOpt: Option[List[ReferenceRegion]]): Option[List[ReferenceRegion]] = {
    regionsOpt match {
      case Some(_) =>
        val regions = regionsOpt.get
        var rmerged: ListBuffer[ReferenceRegion] = new ListBuffer[ReferenceRegion]()
        rmerged += regions.head
        for (r2 <- regions) {
          if (r2 != regions.head) {
            val r1 = rmerged.last
            if (r1.end == r2.start - 1) {
              rmerged -= r1
              rmerged += r1.hull(r2)
            } else {
              rmerged += r2
            }
          }
        }
        Option(rmerged.toList)
      case None => None
    }
  }

  /**
   * Returns whether a file is local or remote, and throws an exception if it can't find the file
   */
  def isLocal(filePath: String, sc: SparkContext): Boolean = {
    val localFile: File = new File(filePath)
    val path: Path = new Path(filePath)
    val fs: FileSystem = path.getFileSystem(sc.hadoopConfiguration)
    if (localFile.exists) {
      true
    } else if (fs.exists(path)) {
      false
    } else {
      throw new FileNotFoundException("Couldn't find the file ${path.toUri}")
    }
  }

}
