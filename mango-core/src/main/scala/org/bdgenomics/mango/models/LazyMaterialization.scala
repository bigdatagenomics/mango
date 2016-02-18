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

import collection.mutable.HashMap
import com.github.erictu.intervaltree._
import edu.berkeley.cs.amplab.spark.intervalrdd._
import java.io.File
import org.apache.parquet.filter2.predicate.FilterPredicate
import org.apache.parquet.filter2.dsl.Dsl._
import org.apache.spark.Dependency
import org.apache.spark.Partition
import org.apache.spark._
import org.apache.spark.SparkContext._
import org.apache.spark.TaskContext
import org.apache.spark.rdd.RDD
import org.apache.spark.Logging
import org.apache.spark.storage.StorageLevel
import org.apache.avro.specific.SpecificRecord
import org.bdgenomics.adam.rdd.GenomicRegionPartitioner
import org.bdgenomics.adam.rdd.read.{ AlignedReadRDD, AlignmentRecordRDD, AlignmentRecordRDDFunctions }
import org.bdgenomics.adam.models.{ ReferenceRegion, ReferencePosition, RecordGroupDictionary, SequenceDictionary, SequenceRecord }
import org.bdgenomics.adam.projections.{ Projection, VariantField, AlignmentRecordField, GenotypeField, NucleotideContigFragmentField, FeatureField }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.formats.avro.{ AlignmentRecord, Feature, Genotype, GenotypeAllele, NucleotideContigFragment, Contig }
import scala.collection.mutable.ListBuffer
import scala.reflect.{ classTag, ClassTag }

class LazyMaterialization[T: ClassTag](sc: SparkContext, partitions: Int, chunkSize: Long) extends Serializable with Logging {

  var dict: SequenceDictionary = null

  def this(sc: SparkContext, partitions: Int) = {
    this(sc, partitions, 1000)
  }

  def setDictionary(filePath: String) {
    val isAlignmentRecord = classOf[AlignmentRecord].isAssignableFrom(classTag[T].runtimeClass)
    val isGenotype = classOf[Genotype].isAssignableFrom(classTag[T].runtimeClass)

    if (isAlignmentRecord) {
      dict = sc.adamDictionaryLoad[AlignmentRecord](filePath)
    } else if (isGenotype) {
      // TODO: this takes forever
      // val projection = Projection(GenotypeField.variant)
      // val projected: RDD[Genotype] = sc.loadParquet[Genotype](filePath, None, projection = Some(projection))
      // val recs: RDD[SequenceRecord] = projected.map(rec => SequenceRecord(rec.getVariant.getContig.getContigName, (rec.getVariant.end - rec.getVariant.start)))
      // dict = recs.aggregate(SequenceDictionary())(
      //   (dict: SequenceDictionary, rec: SequenceRecord) => dict + rec,
      //   (dict1: SequenceDictionary, dict2: SequenceDictionary) => dict1 ++ dict2)
      dict = new SequenceDictionary(Vector(SequenceRecord("20", 25000000L),
        SequenceRecord("chrM", 2000L),
        SequenceRecord("chr3", 2000L)))
    }
  }

  // Stores location of sample at a given filepath
  def loadSample(sampleId: String, filePath: String) {
    if (dict == null) {
      setDictionary(filePath)
    }
    fileMap += ((sampleId, filePath))
  }

  def loadADAMSample(filePath: String, refName: String): String = {
    val region = ReferenceRegion(refName, 0, chunkSize - 1)
    val items: (RDD[(ReferenceRegion, T)], SequenceDictionary, RecordGroupDictionary) = loadadam(region, filePath)
    dict = items._2
    val sample = items._3.recordGroups.head.sample
    rememberValues(region, List(sample))
    fileMap += ((sample, filePath))
    intRDD = IntervalRDD(items._1.partitionBy(GenomicRegionPartitioner(partitions, dict)))
    return sample
  }

  // Keeps track of sample ids and corresponding files
  private var fileMap: HashMap[String, String] = new HashMap()

  def getFileMap(): HashMap[String, String] = fileMap

  private var bookkeep: HashMap[String, IntervalTree[ReferenceRegion, String]] = new HashMap()

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

  def loadadam(region: ReferenceRegion, fp: String): (RDD[(ReferenceRegion, T)], SequenceDictionary, RecordGroupDictionary) = {
    val isAlignmentRecord = classOf[AlignmentRecord].isAssignableFrom(classTag[T].runtimeClass)
    val isVariant = classOf[Genotype].isAssignableFrom(classTag[T].runtimeClass)
    val isFeature = classOf[Feature].isAssignableFrom(classTag[T].runtimeClass)
    val isNucleotideFrag = classOf[NucleotideContigFragment].isAssignableFrom(classTag[T].runtimeClass)

    if (isAlignmentRecord) {
      val pred: FilterPredicate = ((LongColumn("end") >= region.start) && (LongColumn("start") <= region.end))
      val proj = Projection(AlignmentRecordField.contig, AlignmentRecordField.readName, AlignmentRecordField.start, AlignmentRecordField.end, AlignmentRecordField.sequence, AlignmentRecordField.cigar, AlignmentRecordField.readNegativeStrand, AlignmentRecordField.readPaired, AlignmentRecordField.recordGroupSample)
      val alignedReadRDD: AlignmentRecordRDD = sc.loadParquetAlignments(fp, predicate = Some(pred), projection = Some(proj))
      (alignedReadRDD.rdd.map(r => (ReferenceRegion(r), r)).asInstanceOf[RDD[(ReferenceRegion, T)]], alignedReadRDD.sequences, alignedReadRDD.recordGroups)
    } else if (isVariant) {
      val pred: FilterPredicate = ((LongColumn("variant.end") >= region.start) && (LongColumn("variant.start") <= region.end))
      val proj = Projection(GenotypeField.variant, GenotypeField.alleles)
      val d = sc.loadParquetGenotypes(fp, predicate = Some(pred), projection = Some(proj)).map(r => (ReferenceRegion(ReferencePosition(r)), r)).asInstanceOf[RDD[(ReferenceRegion, T)]]
      (d, null, null)
    } else if (isFeature) {
      val pred: FilterPredicate = ((LongColumn("end") >= region.start) && (LongColumn("start") <= region.end))
      val proj = Projection(FeatureField.contig, FeatureField.featureId, FeatureField.featureType, FeatureField.start, FeatureField.end)
      val d = sc.loadParquetAlignments(fp, predicate = Some(pred), projection = Some(proj)).map(r => (ReferenceRegion(r), r)).asInstanceOf[RDD[(ReferenceRegion, T)]]
      (d, null, null)
    } else if (isNucleotideFrag) {
      // val pred: FilterPredicate = ((LongColumn("fragmentStartPosition") >= region.start) && (LongColumn("fragmentStartPosition") <= region.end))
      // sc.loadParquetFragments(fp, predicate = Some(pred)).map(r => (ReferenceRegion(r).get, r)).asInstanceOf[RDD[(ReferenceRegion, T)]]
      (null, null, null) // TODO
    } else {
      log.warn("Generic type not supported")
      (null, null, null)
    }
  }

  def loadFromBam(region: ReferenceRegion, fp: String): RDD[(ReferenceRegion, T)] = {
    val idxFile: File = new File(fp + ".bai")
    if (!idxFile.exists()) {
      sc.loadBam(fp).rdd.filterByOverlappingRegion(region).map(r => (ReferenceRegion(r), r)).asInstanceOf[RDD[(ReferenceRegion, T)]]
    } else {
      sc.loadIndexedBam(fp, region).map(r => (ReferenceRegion(r), r)).asInstanceOf[RDD[(ReferenceRegion, T)]]
    }
  }

  private def loadReference(region: ReferenceRegion, fp: String): RDD[(ReferenceRegion, T)] = {
    // TODO
    null
  }

  def loadFromFile(region: ReferenceRegion, k: String): RDD[(ReferenceRegion, T)] = {
    var data: RDD[(ReferenceRegion, T)] = null
    if (!fileMap.containsKey(k)) {
      log.error("Key not in FileMap")
      null
    }
    val fp = fileMap(k)
    if (!(new File(fp)).exists()) {
      log.warn("File path for sample " + k + " not loaded")
      null
    }
    if (fp.endsWith(".adam")) {
      data = loadadam(region, fp)._1
    } else if (fp.endsWith(".sam") || fp.endsWith(".bam")) {
      data = loadFromBam(region, fp)
    } else if (fp.endsWith(".vcf")) {
      data = sc.loadGenotypes(fp).filterByOverlappingRegion(region).map(r => (ReferenceRegion(ReferencePosition(r)), r)).asInstanceOf[RDD[(ReferenceRegion, T)]]
    } else if (fp.endsWith(".bed")) {
      data = sc.loadFeatures(fp).filterByOverlappingRegion(region).map(r => (ReferenceRegion(r), r)).asInstanceOf[RDD[(ReferenceRegion, T)]]
    } else if (fp.endsWith(".fa") || fp.endsWith(".fasta")) {
      data = loadReference(region, fp)
    } else {
      throw UnsupportedFileException("File type not supported")
      data
    }
    data.partitionBy(GenomicRegionPartitioner(partitions, dict))
  }

  def get(region: ReferenceRegion, k: String): IntervalRDD[ReferenceRegion, T] = {
    multiget(region, List(k))
  }

  /* If the RDD has not been initialized, initialize it to the first get request
	* Gets the data for an interval for the file loaded by checking in the bookkeeping tree.
	* If it exists, call get on the IntervalRDD
	* Otherwise call put on the sections of data that don't exist
	* Here, ks, is an option of list of personids (String)
	*/
  def multiget(region: ReferenceRegion, ks: List[String]): IntervalRDD[ReferenceRegion, T] = {

    val regionsOpt = getMaterializedRegions(region, ks)

    regionsOpt match {
      case Some(_) => {
        for (r <- regionsOpt.get) {
          try {
            put(r, ks)
          } catch {
            case ex: NoSuchElementException => {
              put(r, ks)
            }
          }
        }
      } case None => {

      }
    }
    intRDD.filterByInterval(region)
  }

  /* Transparent to the user, should only be called by get if IntervalRDD.get does not return data
	* Fetches the data from disk, using predicates and range filtering
	* Then puts fetched data in the IntervalRDD, and calls multiget again, now with the data existing
	*/
  private def put(region: ReferenceRegion, ks: List[String]) = {
    ks.map(k => {
      if (intRDD == null) {
        intRDD = IntervalRDD(loadFromFile(region, k))
      } else {
        intRDD = intRDD.multiput(loadFromFile(region, k))
      }
    })
    rememberValues(region, ks)
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

  def apply[T: ClassTag](sc: SparkContext, partitions: Int): LazyMaterialization[T] = {
    new LazyMaterialization[T](sc, partitions)
  }

  def apply[T: ClassTag](sc: SparkContext, partitions: Int, chunkSize: Long): LazyMaterialization[T] = {
    new LazyMaterialization[T](sc, partitions, chunkSize)
  }

  /**
   * generates a list of closely overlapping regions, counting for gaps in the list
   *
   * @note For example, given a list of regions with ranges (0, 999), (1000, 1999) and (3000, 3999)
   * This function will consolidate adjacent regions and output (0, 1999), (3000, 3999)
   *
   * @note Requires that list region is ordered
   *
   * @param Option of list of regions to merge
   * @return Option of list of merged adjacent regions
   */
  def mergeRegions(regionsOpt: Option[List[ReferenceRegion]]): Option[List[ReferenceRegion]] = {
    regionsOpt match {
      case Some(_) => {
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
      }
      case None => None
    }
  }
}
