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

import com.github.erictu.intervaltree._
import edu.berkeley.cs.amplab.spark.intervalrdd._
import java.io.File
import org.apache.parquet.filter2.predicate.FilterPredicate
import org.apache.parquet.filter2.dsl.Dsl._
import org.apache.spark.Dependency
import org.apache.spark.Partition
import org.apache.spark._
import org.apache.spark.SparkContext
import org.apache.spark.TaskContext
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel
import org.bdgenomics.adam.cli.DictionaryCommand
import org.bdgenomics.adam.rdd.read.AlignmentRecordRDDFunctions
import org.bdgenomics.adam.models.{ ReferenceRegion, SequenceRecord, SequenceDictionary }
import org.bdgenomics.adam.projections.{ Projection, VariantField, AlignmentRecordField, GenotypeField, NucleotideContigFragmentField, FeatureField }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.adam.util.ADAMFunSuite
import org.bdgenomics.formats.avro.{ AlignmentRecord, Feature, Genotype, GenotypeAllele, NucleotideContigFragment }
import org.scalatest.FunSuite
import scala.collection.mutable.ListBuffer
import scala.reflect.ClassTag

class LazyMaterializationSuite extends ADAMFunSuite {

  def getDataCountFromBamFile(file: String, viewRegion: ReferenceRegion): Long = {
    sc.loadIndexedBam(file, viewRegion).count
  }

  val sd = new SequenceDictionary(Vector(SequenceRecord("chr1", 2000L),
    SequenceRecord("chrM", 2000L),
    SequenceRecord("chr3", 2000L)))

  val bamFile = "./src/test/resources/mouse_chrM.bam"
  val vcfFile = "./src/test/resources/truetest.vcf"

  sparkTest("assert the data pulled from a file is the same") {

    val sample = "sample1"
    var lazyMat = LazyMaterialization[AlignmentRecord](sc, 10)
    lazyMat.loadSample(sample, bamFile)

    val region = new ReferenceRegion("chrM", 0L, 1000L)

    var results = lazyMat.get(region, sample).get
    var lazySize = results.count
    val dataSize = getDataCountFromBamFile(bamFile, region)
    assert(dataSize == lazySize)
  }

  sparkTest("Get data from different samples at the same region") {
    val sample1 = "person1"
    val sample2 = "person2"

    var lazyMat = LazyMaterialization[AlignmentRecord](sc, 10)
    val region = new ReferenceRegion("chrM", 0L, 100L)
    lazyMat.loadSample(sample1, bamFile)
    lazyMat.loadSample(sample2, bamFile)
    val results1 = lazyMat.get(region, sample1).get
    val lazySize1 = results1.count

    val results2 = lazyMat.get(region, sample2).get
    val lazySize2 = results2.count
    assert(lazySize1 == getDataCountFromBamFile(bamFile, region))
  }

  sparkTest("Fetch region out of bounds") {
    val sample1 = "person1"

    var lazyMat = LazyMaterialization[AlignmentRecord](sc, 10)
    val bigRegion = new ReferenceRegion("chrM", 0L, 20000L)
    lazyMat.loadSample(sample1, bamFile)
    val results = lazyMat.get(bigRegion, sample1).get
    val lazySize = results.count

    val smRegion = new ReferenceRegion("chrM", 0L, 19299L)
    assert(lazySize == getDataCountFromBamFile(bamFile, smRegion))
  }

  sparkTest("Fetch region whose name is not yet loaded") {
    val sample1 = "person1"

    var lazyMat = LazyMaterialization[AlignmentRecord](sc, 10)
    val bigRegion = new ReferenceRegion("M", 0L, 20000L)
    lazyMat.loadSample(sample1, bamFile)
    val results = lazyMat.get(bigRegion, sample1)

    assert(results == None)
  }

  sparkTest("Get data for variants") {
    val region = new ReferenceRegion("chrM", 0L, 100L)
    val callset = "callset1"
    var lazyMat = LazyMaterialization[Genotype](sc, 10)
    lazyMat.loadSample(callset, vcfFile)

    val results = lazyMat.get(region, callset).get
    assert(results.count == 3)

  }

  sparkTest("Merge Regions") {
    val r1 = new ReferenceRegion("chr1", 0, 999)
    val r2 = new ReferenceRegion("chr1", 1000, 1999)

    val merged = LazyMaterialization.mergeRegions(Option(List(r1, r2))).get
    assert(merged.size == 1)
    assert(merged.head.start == 0 && merged.head.end == 1999)
  }

  sparkTest("Merge Regions with gap") {
    val r1 = new ReferenceRegion("chr1", 0, 999)
    val r2 = new ReferenceRegion("chr1", 1000, 1999)
    val r3 = new ReferenceRegion("chr1", 3000, 3999)

    val merged = LazyMaterialization.mergeRegions(Option(List(r1, r2, r3))).get
    assert(merged.size == 2)
    assert(merged.head.end == 1999 && merged.last.end == 3999)
  }

}
