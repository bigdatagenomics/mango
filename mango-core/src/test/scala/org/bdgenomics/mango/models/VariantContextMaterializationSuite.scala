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
import org.bdgenomics.adam.models.{ ReferenceRegion, SequenceDictionary, SequenceRecord, VariantContext }
import org.bdgenomics.formats.avro.Variant
//import org.bdgenomics.mango.cli.VizReads
//import org.bdgenomics.mango.cli.VizReads
import org.bdgenomics.mango.layout.GenotypeJson
import org.bdgenomics.mango.util.MangoFunSuite

// For test purposes if we want to print to console from inside test
object PrintUtiltity {
  def print(data: String) = {
    println(data)
  }
}

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

    val vAndg = json.sortBy(_.variant.variant.getStart)

    assert(vAndg.length == 3)
    assert(vAndg.head.genotypes.size == 2)

  }

  sparkTest("Can extract json") {
    val region = new ReferenceRegion("chrM", 0, 999)
    val data: VariantContextMaterialization = new VariantContextMaterialization(sc, List(vcfFile1), sd)
    val x: Map[String, Array[VariantContext]] = data.getJson(region, true, 1)

    //data.getJson(region)
    val x2: Array[VariantContext] = x.values.head
    val results: Option[String] = Some(data.stringifyLegacy(x2))
    org.bdgenomics.mango.models.PrintUtiltity.print("results: " + results.get)

    val x3: Array[GenotypeJson] = parse(results.get).extract[Array[String]].map(r => GenotypeJson(r))

    //x3.
    //val json: Array[VariantContext] = data.getJson(region).get(key).get
    /*
    val result: Array[VariantContext] = variantsCache.get(key).getOrElse(Array.empty)
      .filter(z => { ReferenceRegion(z.variant.variant).overlaps(viewRegion) })
*/

    //val vAndg = parse(data.stringify(json)).extract[Array[String]].map(r => GenotypeJson(r))
    //   .sortBy(_.variant.getStart)

    assert(x3.length == 3)
    //    assert(vAndg.length == 3)
    //  assert(vAndg.head.sampleIds.length == 2)

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
    val json = VariantContext(variant)
    val newJson = data.setContigName(json, "chr20")
    assert(newJson.variant.variant.getContigName == "chr20")
  }

  sparkTest("returns from file without genotypes") {
    val region = new ReferenceRegion("chrM", 0, 999)
    val data = new VariantContextMaterialization(sc, List(vcfFile2), sd)
    val json = data.getJson(region).get(key2).get

    val vAndg = json.sortBy(_.variant.variant.getStart)

    assert(vAndg.length == 7)
    assert(vAndg.head.genotypes.size == 0)
    assert(vAndg.head.variant.variant.getReferenceAllele == "C")
  }

  sparkTest("more than 1 vcf file") {
    val region = new ReferenceRegion("chrM", 0, 999)
    val data = new VariantContextMaterialization(sc, vcfFiles, sd)
    val json = data.getJson(region)
    var vAndg = json.get(key).get

    assert(vAndg.length == 3)

    vAndg = json.get(key2).get

    assert(vAndg.length == 7)
  }

  //todo: need to fix and update this test
  /*
  sparkTest("Should bin and not return genotypes at zoomed out regions") {
    val region = new ReferenceRegion("chrM", 0, 999)
    val data = new VariantContextMaterialization(sc, List(vcfFile1), sd)
    val json = data.stringify(data.getJson(region, true, binning = 20).get(key).get)

    val vAndg = parse(json).extract[Array[String]].map(r => GenotypeJson(r))
      .sortBy(_.variant.getStart)

    // should bin all variants
    assert(vAndg.length == 3)
    assert(vAndg.head.sampleIds.length == 0)
    assert(vAndg.head.variant.getStart == 0)
    assert(vAndg.head.variant.getEnd == 20)
  }
  */

  sparkTest("Should handle chromosomes with different prefixes") {

    val sd = new SequenceDictionary(Vector(SequenceRecord("1", 2000L),
      SequenceRecord("M", 20000L)))

    val region = new ReferenceRegion("M", 0, 999)
    val data = new VariantContextMaterialization(sc, vcfFiles, sd)
    val json = data.getJson(region)
    var vAndg = json.get(key).get

    assert(vAndg.length == 3)

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

}
