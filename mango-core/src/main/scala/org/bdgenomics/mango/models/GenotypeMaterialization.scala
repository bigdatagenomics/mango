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

import org.apache.parquet.filter2.dsl.Dsl._
import org.apache.parquet.filter2.predicate.FilterPredicate
import org.apache.spark._
import org.apache.spark.rdd.RDD
import org.bdgenomics.adam.models.{ RecordGroupDictionary, ReferencePosition, ReferenceRegion, SequenceDictionary }
import org.bdgenomics.adam.projections.{ GenotypeField, Projection }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.formats.avro.Genotype

import scala.reflect.ClassTag

class GenotypeMaterialization(s: SparkContext, d: SequenceDictionary, parts: Int, chunkS: Long) extends LazyMaterialization[Genotype, Genotype] {

  val sc = s
  val dict = d
  val partitions = parts
  val chunkSize = chunkS
  val partitioner = setPartitioner

  override def loadAdam(region: ReferenceRegion, fp: String): (RDD[(ReferenceRegion, Genotype)], SequenceDictionary, RecordGroupDictionary) = {
    val pred: FilterPredicate = ((LongColumn("variant.end") >= region.start) && (LongColumn("variant.start") <= region.end) && (BinaryColumn("variant.contig.contigName") === (region.referenceName)))
    val proj = Projection(GenotypeField.variant, GenotypeField.alleles, GenotypeField.sampleId)
    val d = sc.loadParquetGenotypes(fp, predicate = Some(pred), projection = Some(proj))
      .map(r => (ReferenceRegion(ReferencePosition(r)), r))
    (d, null, null)
  }

  override def loadFromFile(region: ReferenceRegion, k: String): RDD[(ReferenceRegion, Genotype)] = {
    if (!fileMap.containsKey(k)) {
      log.error("Key not in FileMap")
      null
    }
    val fp = fileMap(k)
    val data: RDD[(ReferenceRegion, Genotype)] =
      if (fp.endsWith(".adam")) {
        loadAdam(region, fp)._1
      } else if (fp.endsWith(".vcf")) {
        sc.loadGenotypes(fp).filterByOverlappingRegion(region).map(r => (ReferenceRegion(ReferencePosition(r)), r))
      } else {
        throw UnsupportedFileException("File type not supported")
        null
      }
    data.partitionBy(partitioner)
  }

}

object GenotypeMaterialization {

  def apply(sc: SparkContext, dict: SequenceDictionary, partitions: Int): GenotypeMaterialization = {
    new GenotypeMaterialization(sc, dict, partitions, 1000)
  }

  def apply[T: ClassTag, C: ClassTag](sc: SparkContext, dict: SequenceDictionary, partitions: Int, chunkSize: Long): GenotypeMaterialization = {
    new GenotypeMaterialization(sc, dict, partitions, chunkSize)
  }
}