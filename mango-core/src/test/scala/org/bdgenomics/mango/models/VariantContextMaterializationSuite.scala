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
import org.bdgenomics.adam.models.{ ReferenceRegion, SequenceDictionary, SequenceRecord }
import org.bdgenomics.formats.avro.Variant
import org.bdgenomics.mango.layout.GenotypeJson
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

    val vAndg = parse(json).extract[Array[String]].map(r => GenotypeJson(r))
      .sortBy(_.variant.getStart)

    assert(vAndg.length == 3)
    assert(vAndg.head.sampleIds.length == 2)

  }

  sparkTest("correctly reassigns contigName") {
    val region = new ReferenceRegion("chrM", 0, 999)
    val data = new VariantContextMaterialization(sc, List(vcfFile1), sd)

    val variant = Variant.newBuilder()
      .setContigName("20")
      .setStart(1L)
      .setEnd(2L)
      .setAlternateAllele("A")
      .setReferenceAllele("G")
      .build()
    val json = GenotypeJson(variant, Array("NA12878"))
    val newJson = data.setContigName(json, "chr20")
    assert(newJson.variant.getContigName == "chr20")
  }

  sparkTest("returns from file without genotypes") {
    val region = new ReferenceRegion("chrM", 0, 999)
    val data = new VariantContextMaterialization(sc, List(vcfFile2), sd)
    val json = data.getJson(region).get(key2).get

    val vAndg = parse(json).extract[Array[String]].map(r => GenotypeJson(r))
      .sortBy(_.variant.getStart)

    assert(vAndg.length == 7)
    assert(vAndg.head.sampleIds.length == 0)
    assert(vAndg.head.variant.getReferenceAllele == "C")
  }

  sparkTest("more than 1 vcf file") {
    val region = new ReferenceRegion("chrM", 0, 999)
    val data = new VariantContextMaterialization(sc, vcfFiles, sd)
    val json = data.getJson(region)
    var vAndg = parse(json.get(key).get).extract[Array[String]].map(r => GenotypeJson(r))

    assert(vAndg.length == 3)

    vAndg = parse(json.get(key2).get).extract[Array[String]].map(r => GenotypeJson(r))

    assert(vAndg.length == 7)
  }

  sparkTest("does not return genotypes at zoomed out regions") {
    val region = new ReferenceRegion("chrM", 0, 999)
    val data = new VariantContextMaterialization(sc, List(vcfFile1), sd)
    val json = data.getJson(region, true, binning = 100).get(key).get

    val vAndg = parse(json).extract[Array[String]].map(r => GenotypeJson(r))
      .sortBy(_.variant.getStart)

    // should bin all variants
    assert(vAndg.length == 1)
    assert(vAndg.head.sampleIds.length == 0)
    assert(vAndg.head.variant.getStart == 19)
    assert(vAndg.head.variant.getEnd == 50)
  }

  sparkTest("Should handle chromosomes with different prefixes") {

    val sd = new SequenceDictionary(Vector(SequenceRecord("1", 2000L),
      SequenceRecord("M", 20000L)))

    val region = new ReferenceRegion("M", 0, 999)
    val data = new VariantContextMaterialization(sc, vcfFiles, sd)
    val json = data.getJson(region)
    var vAndg = parse(json.get(key).get).extract[Array[String]].map(r => GenotypeJson(r))

    assert(vAndg.length == 3)

    vAndg = parse(json.get(key2).get).extract[Array[String]].map(r => GenotypeJson(r))

    assert(vAndg.length == 7)
  }

}
