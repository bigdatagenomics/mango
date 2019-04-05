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

import net.liftweb.json._
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.adam.models.{ VariantContext, ReferenceRegion, SequenceDictionary, SequenceRecord }
import org.bdgenomics.formats.avro.Variant
import org.bdgenomics.mango.converters.GA4GHutil
import org.bdgenomics.mango.util.MangoFunSuite

class VariantContextMaterializationSuite extends MangoFunSuite {
  implicit val formats = DefaultFormats

  val sd = new SequenceDictionary(Vector(SequenceRecord("chr1", 2000L),
    SequenceRecord("chrM", 20000L),
    SequenceRecord("20", 90000L)))

  // test vcf data'
  val vcfFile1 = resourcePath("truetest.genotypes.vcf")
  val vcfFile2 = resourcePath("bqsr1.vcf")

  val vcfFiles = List(vcfFile1, vcfFile2)
  val key = LazyMaterialization.filterKeyFromFile("truetest.genotypes.vcf")
  val key2 = LazyMaterialization.filterKeyFromFile("bqsr1.vcf")

  // test reference data
  var referencePath = resourcePath("mm10_chrM.fa")

  sparkTest("Fetch from 1 vcf file with genotypes") {
    val region = new ReferenceRegion("chrM", 0, 999)
    val data = new VariantContextMaterialization(sc, List(vcfFile1), sd)
    val json = data.getJson(region).get(key).get

    val vAndg = json.sortBy(_.getStart)

    assert(vAndg.length == 7)
    assert(vAndg.head.getCallsCount == 3)

  }

  sparkTest("gets sample information") {
    val data = new VariantContextMaterialization(sc, List(vcfFile1), sd)

    val samples = data.samples

    assert(samples.size == 1)
  }

  sparkTest("Can extract json") {
    val region = new ReferenceRegion("chrM", 0, 999)
    val data = new VariantContextMaterialization(sc, List(vcfFile1), sd)
    val buf = data.stringify(data.getJson(region).map(_._2).flatten.toArray)

    val vAndg = GA4GHutil.stringToVariantServiceResponse(buf).getVariantsList

    assert(vAndg.size() == 7)
    assert(vAndg.get(0).getCallsCount == 3)
  }

  sparkTest("Can read Partitioned Parquet Genotypes") {
    val inputPath = testFile("small.vcf")
    val outputPath = tmpLocation()
    val grdd = sc.loadGenotypes(inputPath)
    grdd.saveAsPartitionedParquet(outputPath)
    val grdd2 = sc.loadPartitionedParquetGenotypes(outputPath)
    val data: VariantContextMaterialization = new VariantContextMaterialization(sc, List(outputPath), grdd2.sequences)
    val mykey = LazyMaterialization.filterKeyFromFile(outputPath)
    val region = new ReferenceRegion("1", 0L, 2000000L)
    val results = data.getJson(region).get(mykey).get
    assert(results.length === 6)
  }

  sparkTest("correctly reassigns referenceName") {
    val region = new ReferenceRegion("chrM", 0, 999)
    val data = new VariantContextMaterialization(sc, List(vcfFile1), sd)

    val variant = Variant.newBuilder()
      .setReferenceName("20")
      .setStart(1L)
      .setEnd(2L)
      .setAlternateAllele("A")
      .setReferenceAllele("G")
      .build()

    val vc = VariantContext(variant)
    val newVc = data.setReferenceName(vc, "chr20")
    assert(newVc.variant.variant.getReferenceName == "chr20")
    assert(newVc.position.referenceName == "chr20")
  }

  sparkTest("returns from file without genotypes") {
    val region = new ReferenceRegion("chrM", 0, 999)
    val data = new VariantContextMaterialization(sc, List(vcfFile2), sd)
    val json = data.getJson(region).get(key2).get

    val vAndg = json.sortBy(_.getStart)

    assert(vAndg.length == 7)
    assert(vAndg.head.getCallsCount == 0)
    assert(vAndg.head.getReferenceBases == "C")
  }

  sparkTest("more than 1 vcf file") {
    val region = new ReferenceRegion("chrM", 0, 999)
    val data = new VariantContextMaterialization(sc, vcfFiles, sd)
    val json = data.getJson(region)
    var vAndg = json.get(key).get

    assert(vAndg.length == 7)

    vAndg = json.get(key2).get

    assert(vAndg.length == 7)
  }

  sparkTest("Should handle chromosomes with different prefixes") {

    val sd = new SequenceDictionary(Vector(SequenceRecord("1", 2000L),
      SequenceRecord("M", 20000L)))

    val region = new ReferenceRegion("M", 0, 999)
    val data = new VariantContextMaterialization(sc, vcfFiles, sd)
    val json = data.getJson(region)
    var vAndg = json.get(key).get

    assert(vAndg.length == 7)

    vAndg = json.get(key2).get

    assert(vAndg.length == 7)
  }

  sparkTest("fetches multiple regions from load") {
    val region1 = ReferenceRegion("chrM", 10L, 30L)
    val region2 = ReferenceRegion("chrM", 50L, 60L)
    val regions = Some(Iterable(region1, region2))
    val data1 = VariantContextMaterialization.load(sc, vcfFile1, Some(Iterable(region1)))
    val data2 = VariantContextMaterialization.load(sc, vcfFile1, Some(Iterable(region2)))
    val data = VariantContextMaterialization.load(sc, vcfFile1, regions)

    assert(data.rdd.count == data1.rdd.count + data2.rdd.count)
  }

  sparkTest("Fails on invalid http endpoint") {
    val endpoint = "http:fake.vcf"
    val thrown = intercept[Exception] {
      VariantContextMaterialization.createHttpEndpoint(endpoint)
    }
    assert(thrown.getMessage.contains("http host"))
  }

}
