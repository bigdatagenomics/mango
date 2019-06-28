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

import ga4gh.Reads
import org.bdgenomics.adam.models.{ ReferenceRegion, SequenceDictionary, SequenceRecord }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.mango.converters.GA4GHutil
import org.bdgenomics.mango.util.MangoFunSuite
import net.liftweb.json._
import net.liftweb.json.Serialization.write
import ga4gh.Reads.ReadAlignment

class AlignmentRecordMaterializationSuite extends MangoFunSuite {

  implicit val formats = DefaultFormats

  val dict = new SequenceDictionary(Vector(SequenceRecord("chrM", 16699L)))
  val chunkSize = 100

  def getDataCountFromBamFile(file: String, viewRegion: ReferenceRegion): Long = {
    sc.loadIndexedBam(file, viewRegion).rdd.count
  }

  // test alignment data
  val bamFile = resourcePath("mouse_chrM.bam")
  val samFile = resourcePath("multi_chr.sam")

  val key = LazyMaterialization.filterKeyFromFile(bamFile)
  val samKey = LazyMaterialization.filterKeyFromFile(samFile)

  // test reference data
  var referencePath = resourcePath("mm10_chrM.fa")
  val files = List(bamFile)
  val samFiles = List(samFile)

  sparkTest("create new AlignmentRecordMaterialization") {
    val lazyMat = new AlignmentRecordMaterialization(sc, files, dict)
  }

  sparkTest("return raw data from AlignmentRecordMaterialization") {
    val data = new AlignmentRecordMaterialization(sc, files, dict)
    val region = new ReferenceRegion("chrM", 0L, 900L)
    val results: Array[ReadAlignment] = data.getJson(region).get(key).get
    assert(results.length == 9387)
  }

  sparkTest("can process stringified data") {
    val data = new AlignmentRecordMaterialization(sc, files, dict)
    val region = new ReferenceRegion("chrM", 0L, 900L)
    val results: Array[ReadAlignment] = data.getJson(region).get(key).get

    val buf = data.stringify(results)
    val keyData = GA4GHutil.stringToSearchReadsResponse(buf).getAlignmentsList

    assert(keyData.size() == results.length)
  }

  sparkTest("Read Partitioned Data") {
    val inputPath = testFile("multi_chr.sam")
    val outputPath = tmpLocation()
    val rrdd = sc.loadAlignments(inputPath)
    rrdd.saveAsPartitionedParquet(outputPath, partitionSize = 1000000)
    val rdd2 = sc.loadPartitionedParquetAlignments(outputPath)
    val data: AlignmentRecordMaterialization = new AlignmentRecordMaterialization(sc, List(outputPath), rdd2.sequences)
    val region = new ReferenceRegion("2", 189000000L, 190000000L)
    val mykey = LazyMaterialization.filterKeyFromFile(outputPath)
    val results: Array[ReadAlignment] = data.getJson(region).get(mykey).get
    assert(results.length === 1)
  }

  sparkTest("fetches multiple regions from load") {
    val regions = Iterable(ReferenceRegion("chrM", 90L, 110L), ReferenceRegion("chrM", 10100L, 10300L))
    val data1 = AlignmentRecordMaterialization.load(sc, bamFile, Some(Iterable(ReferenceRegion("chrM", 90L, 110L))))
    val data2 = AlignmentRecordMaterialization.load(sc, bamFile, Some(Iterable(ReferenceRegion("chrM", 10100L, 10300L))))
    val data = AlignmentRecordMaterialization.load(sc, bamFile, Some(regions))

    assert(data.length == data1.length + data2.length)

  }

  sparkTest("Should handle chromosomes with different prefixes") {
    val dict = new SequenceDictionary(Vector(SequenceRecord("M", 16699L)))
    val data = new AlignmentRecordMaterialization(sc, files, dict)
    val region = new ReferenceRegion("M", 90L, 110L)
    val results: Array[ReadAlignment] = data.getJson(region).get(key).get

    val buf = data.stringify(results)
    val keyData = GA4GHutil.stringToSearchReadsResponse(buf).getAlignmentsList

    assert(keyData.size() == results.length)
  }

  sparkTest("should get data from unindexed file when referenceName predicates do not match") {
    val dict = new SequenceDictionary(Vector(SequenceRecord("chr1", 248956422L)))
    val data = new AlignmentRecordMaterialization(sc, samFiles, dict)
    val region = new ReferenceRegion("chr1", 26472784L, 26472884L)
    val results: Array[ReadAlignment] = data.getJson(region).get(samKey).get

    val buf = data.stringify(results)
    val keyData = GA4GHutil.stringToSearchReadsResponse(buf).getAlignmentsList

    assert(keyData.size() == results.length)
  }

}
