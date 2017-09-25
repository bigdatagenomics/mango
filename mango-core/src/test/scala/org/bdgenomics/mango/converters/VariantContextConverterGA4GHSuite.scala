package org.bdgenomics.mango.converters

import com.google.common.collect.ImmutableList
import ga4gh.Variants
import org.bdgenomics.adam.models.{ SequenceDictionary, VariantContext }
import org.bdgenomics.formats.avro._

import scala.collection.JavaConversions._
import org.scalatest.FunSuite

/**
 * Created by jpaschall on 7/14/17.
 */
class VariantContextConverterGA4GHSuite extends FunSuite {

  test("converting a properly formatted VariantContext succeeds") {

    val contig = Contig.newBuilder.setContigName("chr11")
      .setContigLength(249250621L)
      .build

    val v0 = Variant.newBuilder
      .setContigName("chr11")
      .setStart(17409572L)
      .setEnd(17409573L)
      .setReferenceAllele("T")
      .setAlternateAllele("C")
      .setNames(ImmutableList.of("rs3131972"))
      .setFiltersApplied(true)
      .setFiltersPassed(true)
      .build

    val g0 = Genotype.newBuilder().setVariant(v0)
      .setSampleId("NA12878")
      .setAlleles(List(GenotypeAllele.REF, GenotypeAllele.ALT))
      .build

    val vctx0 = VariantContext(v0, Seq(g0))

    val x: Variants.Variant = VariantContextCoverterGA4GH.toGA4GHVariant(vctx0)

    assert(x.getReferenceName === "chr11")
    assert(x.getStart === 17409572L)
    assert(x.getEnd === 17409573L)
    assert(x.getReferenceBases === "T")
    assert(x.getAlternateBases(0) === "C")

  }

  test("convert list of VaraintContext to JSON") {

    val contig = Contig.newBuilder.setContigName("chr11")
      .setContigLength(249250621L)
      .build

    val v0 = Variant.newBuilder
      .setContigName("chr11")
      .setStart(17409572L)
      .setEnd(17409573L)
      .setReferenceAllele("T")
      .setAlternateAllele("C")
      .setNames(ImmutableList.of("rs3131972"))
      .setFiltersApplied(true)
      .setFiltersPassed(true)
      .build

    val g0 = Genotype.newBuilder().setVariant(v0)
      .setSampleId("NA12878")
      .setAlleles(List(GenotypeAllele.REF, GenotypeAllele.ALT))
      .build

    val vctx0 = VariantContext(v0, Seq(g0))

    val v1 = Variant.newBuilder
      .setContigName("chr11")
      .setStart(17409572L)
      .setEnd(17409573L)
      .setReferenceAllele("T")
      .setAlternateAllele("C")
      .setNames(ImmutableList.of("rs3131972"))
      .setFiltersApplied(true)
      .setFiltersPassed(true)
      .build

    val g1 = Genotype.newBuilder().setVariant(v0)
      .setSampleId("NA12878")
      .setAlleles(List(GenotypeAllele.REF, GenotypeAllele.ALT))
      .build

    val vctx1 = VariantContext(v1, Seq(g1))

    val myData: Seq[Variants.Variant] = List(VariantContextCoverterGA4GH.toGA4GHVariant(vctx0),
      VariantContextCoverterGA4GH.toGA4GHVariant(vctx1))

    // need to add test assertion that accounts for white space
    val resultJSON = VariantContextCoverterGA4GH.listGApbToJson(myData)
    println("GA4GH variant JSON: " + resultJSON)

    /*
    JSON output:
    {
  "variants": [{
    "names": ["rs3131972"],
    "referenceName": "chr11",
    "start": "17409572",
    "end": "17409573",
    "referenceBases": "T",
    "alternateBases": ["C"],
    "calls": [{
      "callSetName": "NA12878",
      "callSetId": "NA",
      "genotype": ["0", "1"]
    }],
    "filtersApplied": true,
    "filtersPassed": true
  }, {
    "names": ["rs3131972"],
    "referenceName": "chr11",
    "start": "17409572",
    "end": "17409573",
    "referenceBases": "T",
    "alternateBases": ["C"],
    "calls": [{
      "callSetName": "NA12878",
      "callSetId": "NA",
      "genotype": ["0", "1"]
    }],
    "filtersApplied": true,
    "filtersPassed": true
  }]
}

     */

  }

}
