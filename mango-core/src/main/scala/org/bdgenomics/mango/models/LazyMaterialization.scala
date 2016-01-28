/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.bdgenomics.mango.models

import java.io.File

import com.github.akmorrow13.intervaltree._
import edu.berkeley.cs.amplab.spark.intervalrdd._
import scala.reflect.{ classTag, ClassTag }
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
import org.bdgenomics.adam.rdd.read.AlignmentRecordRDDFunctions
import org.bdgenomics.adam.models.{ ReferenceRegion, SequenceDictionary, SequenceRecord }
import org.bdgenomics.adam.projections.{ Projection, VariantField, AlignmentRecordField, GenotypeField, NucleotideContigFragmentField, FeatureField }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.formats.avro.{ AlignmentRecord, Feature, Genotype, GenotypeAllele, NucleotideContigFragment, Contig }

import scala.collection.mutable.ListBuffer
import collection.mutable.HashMap

class LazyMaterialization[T: ClassTag](sc: SparkContext, chunkSize: Long) extends Serializable with Logging {

  var dict: SequenceDictionary = null

  def this(sc: SparkContext) = {
    this(sc, 1000)
  }

  def setDictionary(filePath: String) {

    val isAlignmentRecord = classOf[AlignmentRecord].isAssignableFrom(classTag[T].runtimeClass)
    val isGenotype = classOf[Genotype].isAssignableFrom(classTag[T].runtimeClass)

    if (isAlignmentRecord) {
      dict = sc.adamDictionaryLoad[AlignmentRecord](filePath)
    } else if (isGenotype) {
      val projection = Projection(GenotypeField.variant)
      val projected: RDD[Genotype] = sc.loadParquet[Genotype](filePath, None, projection = Some(projection))
      val recs: RDD[SequenceRecord] = projected.map(rec => SequenceRecord(rec.getVariant.getContig.getContigName, (rec.getVariant.end - rec.getVariant.start)))
      dict = recs.aggregate(SequenceDictionary())(
        (dict: SequenceDictionary, rec: SequenceRecord) => dict + rec,
        (dict1: SequenceDictionary, dict2: SequenceDictionary) => dict1 ++ dict2)
    }
  }

  // Stores location of sample at a given filepath
  def loadSample(k: String, filePath: String) {
    if (dict == null) {
      setDictionary(filePath)
    }
    fileMap += ((k, filePath))
  }

  // Keeps track of sample ids and corresponding files
  private var fileMap: HashMap[String, String] = new HashMap()

  def getFileMap(): HashMap[String, String] = fileMap

  // TODO: could merge into 1 interval tree with nodes storing (chr, list(keys)). Storing the booleans is a waste of space
  private var bookkeep: HashMap[String, IntervalTree[ReferenceRegion, String]] = new HashMap()

  var intRDD: IntervalRDD[ReferenceRegion, T] = null

  /*
  * Logs key and region values in a bookkeeping structure
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

  def loadadam(region: ReferenceRegion, fp: String): RDD[T] = {
    val isAlignmentRecord = classOf[AlignmentRecord].isAssignableFrom(classTag[T].runtimeClass)
    val isVariant = classOf[Genotype].isAssignableFrom(classTag[T].runtimeClass)
    val isFeature = classOf[Feature].isAssignableFrom(classTag[T].runtimeClass)
    val isNucleotideFrag = classOf[NucleotideContigFragment].isAssignableFrom(classTag[T].runtimeClass)

    if (isAlignmentRecord) {
      val pred: FilterPredicate = ((LongColumn("end") >= region.start) && (LongColumn("start") <= region.end))
      val proj = Projection(AlignmentRecordField.contig, AlignmentRecordField.readName, AlignmentRecordField.start, AlignmentRecordField.end, AlignmentRecordField.sequence, AlignmentRecordField.cigar, AlignmentRecordField.readNegativeStrand, AlignmentRecordField.readPaired)
      sc.loadParquetAlignments(fp, predicate = Some(pred), projection = Some(proj)).asInstanceOf[RDD[T]]
    } else if (isVariant) {
      val pred: FilterPredicate = ((LongColumn("variant.end") >= region.start) && (LongColumn("variant.start") <= region.end))
      val proj = Projection(GenotypeField.variant, GenotypeField.alleles)
      sc.loadParquetGenotypes(fp, predicate = Some(pred), projection = Some(proj)).asInstanceOf[RDD[T]]
    } else if (isFeature) {
      val pred: FilterPredicate = ((LongColumn("end") >= region.start) && (LongColumn("start") <= region.end))
      val proj = Projection(FeatureField.contig, FeatureField.featureId, FeatureField.featureType, FeatureField.start, FeatureField.end)
      sc.loadParquetAlignments(fp, predicate = Some(pred), projection = Some(proj)).asInstanceOf[RDD[T]]
    } else if (isNucleotideFrag) {
      val pred: FilterPredicate = ((LongColumn("fragmentStartPosition") >= region.start) && (LongColumn("fragmentStartPosition") <= region.end))
      sc.loadParquetFragments(fp, predicate = Some(pred)).asInstanceOf[RDD[T]]
    } else {
      log.warn("Generic type not supported")
      null
    }
  }

  def loadFromBam(region: ReferenceRegion, fp: String): RDD[T] = {
    val idxFile: File = new File(fp + ".bai")
    if (!idxFile.exists()) {
      sc.loadBam(fp).rdd.filterByOverlappingRegion(region).asInstanceOf[RDD[T]]
    } else {
      sc.loadIndexedBam(fp, region).asInstanceOf[RDD[T]]
    }
  }

  private def loadReference(region: ReferenceRegion, fp: String): RDD[T] = {
    // TODO
    nullØ
  }

  def loadFromFile(region: ReferenceRegion, ks: List[String]): RDD[T] = {
    var ret: RDD[T] = null
    var load: RDD[T] = null

    for (k <- ks) {
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
        load = loadadam(region, fp)
      } else if (fp.endsWith(".sam") || fp.endsWith(".bam")) {
        load = loadFromBam(region, fp)
      } else if (fp.endsWith(".vcf")) {
        load = sc.loadGenotypes(fp).filterByOverlappingRegion(region).asInstanceOf[RDD[T]]
      } else if (fp.endsWith(".bed")) {
        load = sc.loadFeatures(fp).filterByOverlappingRegion(region).asInstanceOf[RDD[T]]
      } else if (fp.endsWith(".fa") || fp.endsWith(".fasta")) {
        load = loadReference(region, fp)
      } else {
        throw UnsupportedFileException("File type not supported")
      }
      if (ret == null) {
        ret = load
      } else {
        ret = ret.union(load)
      }
    }
    ret
  }

  def get(region: ReferenceRegion, k: String): List[T] = {
    multiget(region, List(k)).toList.map(elem => elem._2).flatten
  }

  /* If the RDD has not been initialized, initialize it to the first get request
	* Gets the data for an interval for the file loaded by checking in the bookkeeping tree.
	* If it exists, call get on the IntervalRDD
	* Otherwise call put on the sections of data that don't exist
	* Here, ks, is an option of list of personids (String)
	*/
  def multiget(region: ReferenceRegion, ks: List[String]): Map[String, Array[T]] = {

    val matRegion: ReferenceRegion = getChunk(region)
    if (intRDD == null) {
      //  load all data from keys
      val rdd: RDD[(ReferenceRegion, T)] = loadFromFile(matRegion, ks).map(r => (region, r)).partitionBy(GenomicRegionPartitioner(10, dict))
      intRDD = IntervalRDD(rdd)
      rememberValues(matRegion, ks)
    } else {
      val regions = partitionChunk(matRegion)
      for (r <- regions) {
        try {
          val found = bookkeep(r.referenceName).search(r).toList
          val notFound: List[String] = found.filterNot(found.contains(_))
          if (notFound.length > 0) {
            put(r, notFound)
          }
        } catch {
          case ex: NoSuchElementException => {
            log.warn("File not found in bookkeep")
            null
          }
        }
      }
    }
    filterByRegionAndKey(region, ks)
  }

  /* Transparent to the user, should only be called by get if IntervalRDD.get does not return data
	* Fetches the data from disk, using predicates and range filtering
	* Then puts fetched data in the IntervalRDD, and calls multiget again, now with the data existing
	*/
  private def put(region: ReferenceRegion, ks: List[String]) = {
    val rdd: Array[(ReferenceRegion, T)] = loadFromFile(region, ks).map(r => (region, r)).collect
    intRDD = intRDD.multiput(rdd)
    rememberValues(region, ks)
  }

  // get block chunk of request
  private def getChunk(region: ReferenceRegion): ReferenceRegion = {
    val start = region.start / chunkSize * chunkSize
    val end = region.end / chunkSize * chunkSize + (chunkSize - 1)
    new ReferenceRegion(region.referenceName, start, end)
  }

  // get block chunk of request
  private def partitionChunk(region: ReferenceRegion): List[ReferenceRegion] = {
    var regions: ListBuffer[ReferenceRegion] = new ListBuffer[ReferenceRegion]()
    var start = region.start / chunkSize * chunkSize
    var end = start + (chunkSize - 1)

    while (start <= region.end) {
      regions += new ReferenceRegion(region.referenceName, start, end)
      start += chunkSize
      end += chunkSize
    }
    regions.toList
  }

  private def filterByRegionAndKey(region: ReferenceRegion, ks: List[String]): Map[String, Array[T]] = {

    val isAlignmentRecord = classOf[AlignmentRecord].isAssignableFrom(classTag[T].runtimeClass)
    val isGenotype = classOf[Genotype].isAssignableFrom(classTag[T].runtimeClass)

    if (isAlignmentRecord) {
      intRDD.asInstanceOf[IntervalRDD[ReferenceRegion, AlignmentRecord]].filterByRegion(region).collect.filter(ReferenceRegion(_).overlaps(region)).groupBy(_.recordGroupSample).asInstanceOf[Map[String, Array[T]]]
    } else if (isGenotype)
      intRDD.asInstanceOf[IntervalRDD[ReferenceRegion, Genotype]].filterByRegion(region).collect.groupBy(_.sampleId).asInstanceOf[Map[String, Array[T]]]
    else {
      log.warn("type not supported")
      null
    }
  }

}

case class UnsupportedFileException(message: String) extends Exception(message)

object LazyMaterialization {

  def apply[T: ClassTag](sc: SparkContext): LazyMaterialization[T] = {
    new LazyMaterialization[T](sc)
  }

  def apply[T: ClassTag](sc: SparkContext, chunkSize: Long): LazyMaterialization[T] = {
    new LazyMaterialization[T](sc, chunkSize)
  }
}
