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
import org.bdgenomics.adam.rdd.read.AlignmentRecordRDDFunctions
import org.bdgenomics.adam.models.{ ReferenceRegion, ReferencePosition, SequenceDictionary, SequenceRecord }
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
  def loadSample(k: String, filePath: String) {
    if (dict == null) {
      setDictionary(filePath)
    }
    fileMap += ((k, filePath))
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

  def loadadam(region: ReferenceRegion, fp: String): RDD[(ReferenceRegion, T)] = {
    val isAlignmentRecord = classOf[AlignmentRecord].isAssignableFrom(classTag[T].runtimeClass)
    val isVariant = classOf[Genotype].isAssignableFrom(classTag[T].runtimeClass)
    val isFeature = classOf[Feature].isAssignableFrom(classTag[T].runtimeClass)
    val isNucleotideFrag = classOf[NucleotideContigFragment].isAssignableFrom(classTag[T].runtimeClass)

    if (isAlignmentRecord) {
      val pred: FilterPredicate = ((LongColumn("end") >= region.start) && (LongColumn("start") <= region.end))
      val proj = Projection(AlignmentRecordField.contig, AlignmentRecordField.readName, AlignmentRecordField.start, AlignmentRecordField.end, AlignmentRecordField.sequence, AlignmentRecordField.cigar, AlignmentRecordField.readNegativeStrand, AlignmentRecordField.readPaired)
      sc.loadParquetAlignments(fp, predicate = Some(pred), projection = Some(proj)).map(r => (ReferenceRegion(r), r)).asInstanceOf[RDD[(ReferenceRegion, T)]]
    } else if (isVariant) {
      val pred: FilterPredicate = ((LongColumn("variant.end") >= region.start) && (LongColumn("variant.start") <= region.end))
      val proj = Projection(GenotypeField.variant, GenotypeField.alleles)
      sc.loadParquetGenotypes(fp, predicate = Some(pred), projection = Some(proj)).map(r => (ReferenceRegion(ReferencePosition(r)), r)).asInstanceOf[RDD[(ReferenceRegion, T)]]
    } else if (isFeature) {
      val pred: FilterPredicate = ((LongColumn("end") >= region.start) && (LongColumn("start") <= region.end))
      val proj = Projection(FeatureField.contig, FeatureField.featureId, FeatureField.featureType, FeatureField.start, FeatureField.end)
      sc.loadParquetAlignments(fp, predicate = Some(pred), projection = Some(proj)).map(r => (ReferenceRegion(r), r)).asInstanceOf[RDD[(ReferenceRegion, T)]]
    } else if (isNucleotideFrag) {
      // val pred: FilterPredicate = ((LongColumn("fragmentStartPosition") >= region.start) && (LongColumn("fragmentStartPosition") <= region.end))
      // sc.loadParquetFragments(fp, predicate = Some(pred)).map(r => (ReferenceRegion(r).get, r)).asInstanceOf[RDD[(ReferenceRegion, T)]]
      null // TODO
    } else {
      log.warn("Generic type not supported")
      null
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
      data = loadadam(region, fp)
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

    // TODO: combine the 2 functions below (getChunk and partitionChunk)
    val matRegion: ReferenceRegion = getChunk(region)
    val regions = partitionChunk(matRegion)

    // TODO: you should use a diff here instead of calling put multiple times for each key
    for (r <- regions) {
      try {
        val found = bookkeep(r.referenceName).search(r).toList
        val notFound: List[String] = found.filterNot(found.contains(_))
        if (notFound.length > 0) {
          put(r, notFound)
        }
      } catch {
        case ex: NoSuchElementException => {
          put(region, ks)
        }
      }
    }
    //  }
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
        intRDD.multiput(loadFromFile(region, k))
      }
    })
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

}

case class UnsupportedFileException(message: String) extends Exception(message)

object LazyMaterialization {

  def apply[T: ClassTag](sc: SparkContext, partitions: Int): LazyMaterialization[T] = {
    new LazyMaterialization[T](sc, partitions)
  }

  def apply[T: ClassTag](sc: SparkContext, partitions: Int, chunkSize: Long): LazyMaterialization[T] = {
    new LazyMaterialization[T](sc, partitions, chunkSize)
  }
}
