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
import org.apache.spark._
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel
import org.bdgenomics.adam.models.{ ReferencePosition, ReferenceRegion, SequenceDictionary }
import org.bdgenomics.adam.projections.{ GenotypeField, Projection }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.formats.avro.Genotype
import org.bdgenomics.mango.layout.{ VariantFreqLayout, VariantFreq, VariantLayout }
import org.bdgenomics.mango.tiling._
import org.bdgenomics.mango.util.Bookkeep
import org.bdgenomics.utils.intervalrdd.IntervalRDD

import scala.reflect.ClassTag

/*
 * Handles loading and tracking of data from persistent storage into memory for Genotype data.
 * @see LazyMaterialization.scala
 */
class GenotypeMaterialization(s: SparkContext, d: SequenceDictionary, parts: Int, chunkS: Int) extends LazyMaterialization[Genotype, VariantTile]
    with KTiles[VariantTile] with Serializable {

  val sc = s
  val dict = d
  val partitions = parts
  val chunkSize = chunkS
  val partitioner = setPartitioner
  val bookkeep = new Bookkeep(chunkSize)

  /**
   * Define layers and underlying data types
   */
  val rawLayer: Layer = L0
  val freqLayer: Layer = L1

  /*
   * Filter filepaths to just the name of the file, without an extension
   */
  def filterVariantName(name: String): String = {
    val slash = name.split("/")
    val fileName = slash.last
    fileName.replace(".", "_")
  }

  /*
   * Initialize materialization structure with filepaths
   */
  def init(filePaths: List[String]): Option[List[String]] = {
    val namedPaths = Option(filePaths.map(p => filterVariantName(p)))
    for (i <- filePaths.indices) {
      val varPath = filePaths(i)
      val varName = namedPaths.get(i)
      if (varPath.endsWith(".vcf")) {
        loadSample(varPath, Option(varName))
      } else if (varPath.endsWith(".adam")) {
        loadSample(varPath, Option(varName))
      } else {
        log.info("WARNING: Invalid input for variants file")
      }
    }
    namedPaths
  }

  def loadAdam(region: ReferenceRegion, fp: String): RDD[Genotype] = {
    val pred: FilterPredicate = ((LongColumn("end") >= region.start) && (LongColumn("start") <= region.end) && (BinaryColumn("contigName") === (region.referenceName)))
    val proj = Projection(GenotypeField.start, GenotypeField.end, GenotypeField.alleles, GenotypeField.sampleId)
    sc.loadParquetGenotypes(fp, predicate = Some(pred), projection = Some(proj))
    val genes = sc.loadParquetGenotypes(fp, predicate = Some(pred))
    genes
  }

  def get(region: ReferenceRegion, k: String, layerOpt: Option[Layer] = None): String = {
    multiget(region, List(k), layerOpt)
  }

  /* If the RDD has not been initialized, initialize it to the first get request
    * Gets the data for an interval for the file loaded by checking in the bookkeeping tree.
    * If it exists, call get on the IntervalRDD
    * Otherwise call put on the sections of data that don't exist
    * Here, ks, is an option of list of personids (String)
    */
  def multiget(region: ReferenceRegion, ks: List[String], layerOpt: Option[Layer] = None): String = {
    implicit val formats = net.liftweb.json.DefaultFormats
    val seqRecord = dict(region.referenceName)
    seqRecord match {
      case Some(_) => {
        val regionsOpt = bookkeep.getMaterializedRegions(region, ks)
        if (regionsOpt.isDefined) {
          for (r <- regionsOpt.get) {
            put(r, ks)
          }
        }

        layerOpt match {
          case Some(_) => {
            val dataLayer: Layer = layerOpt.get
            val layers = getTiles(region, ks, List(freqLayer, dataLayer))
            val freq = layers.get(freqLayer).get
            val variants = layers.get(dataLayer).get
            write(Map("freq" -> freq, "variants" -> variants))
          }
          case None => {
            val layers = getTiles(region, ks, List(freqLayer))
            val freq = layers.get(freqLayer).get
            write(Map("freq" -> freq))
          }
        }

      }
      case None => {
        throw new Exception("Not found in dictionary")
      }
    }
  }

  def stringify(data: RDD[(String, Iterable[Any])], region: ReferenceRegion, layer: Layer): String = {
    layer match {
      case `rawLayer`  => stringifyRawGenotypes(data, region)
      case `freqLayer` => stringifyFreq(data, region)
      case _           => ""
    }
  }

  /**
   * Stringifies raw genotypes to a string
   *
   * @param rdd
   * @param region
   * @return
   */
  def stringifyRawGenotypes(rdd: RDD[(String, Iterable[Any])], region: ReferenceRegion): String = {
    implicit val formats = net.liftweb.json.DefaultFormats

    val data: Array[(String, Iterable[Genotype])] = rdd
      .mapValues(_.asInstanceOf[Iterable[Genotype]])
      .mapValues(r => r.filter(r => r.getStart <= region.end && r.getEnd >= region.start)).collect

    val flattened: Map[String, Array[Genotype]] = data.groupBy(_._1)
      .map(r => (r._1, r._2.flatMap(_._2)))
    write(flattened.mapValues(r => VariantLayout(r)))
  }

  /**
   * Stringifies raw genotypes to a string
   *
   * @param rdd
   * @param region
   * @return
   */
  def stringifyFreq(rdd: RDD[(String, Iterable[Any])], region: ReferenceRegion): String = {
    implicit val formats = net.liftweb.json.DefaultFormats
    val data: Array[(String, Iterable[VariantFreq])] = rdd
      .mapValues(_.asInstanceOf[Iterable[VariantFreq]])
      .mapValues(r => r.filter(r => r.start <= region.end && r.start >= region.start)).collect
    write(data.toMap)
  }

  override def getFileReference(fp: String): String = {
    fp
  }

  def loadFromFile(region: ReferenceRegion, k: String): RDD[Genotype] = {
    if (!fileMap.containsKey(k)) {
      log.error("Key not in FileMap")
      null
    }
    val fp = fileMap(k)
    if (fp.endsWith(".adam")) {
      loadAdam(region, fp)
    } else if (fp.endsWith(".vcf")) {
      sc.loadGenotypes(fp).filterByOverlappingRegion(region)
    } else {
      throw UnsupportedFileException("File type not supported")
      null
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
  override def put(region: ReferenceRegion, ks: List[String]) = {
    val seqRecord = dict(region.referenceName)
    seqRecord match {
      case Some(_) =>
        val end =
          Math.min(region.end, seqRecord.get.length)
        val start = Math.min(region.start, end)
        val trimmedRegion = new ReferenceRegion(region.referenceName, start, end)

        //divide regions by chunksize
        val c = chunkSize
        val regions: List[ReferenceRegion] = Bookkeep.unmergeRegions(region, chunkSize)
        ks.map(k => {
          val data = loadFromFile(trimmedRegion, k)
          //where k is the filename itself
          putPerSample(data, k, regions)
        })
        bookkeep.rememberValues(region, ks)
      case None =>
    }
  }

  /**
   * Puts in the data from a file/sample into the IntervalRDD
   */
  def putPerSample(data: RDD[Genotype], fileName: String, regions: List[ReferenceRegion]) = {
    var mappedRecords: RDD[(ReferenceRegion, Genotype)] = sc.emptyRDD[(ReferenceRegion, Genotype)]

    regions.foreach(r => {
      val grouped = data.filter(vr => r.overlaps(ReferenceRegion(vr.getContigName, vr.getStart, vr.getEnd)))
        .map(vr => (r, vr))
      mappedRecords = mappedRecords.union(grouped)
    })

    val groupedRecords: RDD[(ReferenceRegion, Iterable[Genotype])] =
      mappedRecords
        .groupBy(_._1)
        .map(r => (r._1, r._2.map(_._2)))

    val tiles: RDD[(ReferenceRegion, VariantTile)] = groupedRecords.map(r => (r._1, VariantTile(r._2, fileName)))
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
  }
}

object GenotypeMaterialization {

  def apply(sc: SparkContext, dict: SequenceDictionary, partitions: Int): GenotypeMaterialization = {
    new GenotypeMaterialization(sc, dict, partitions, 100)
  }

  def apply[T: ClassTag, C: ClassTag](sc: SparkContext, dict: SequenceDictionary, partitions: Int, chunkSize: Int): GenotypeMaterialization = {
    new GenotypeMaterialization(sc, dict, partitions, chunkSize)
  }
}