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
      .setNames(ImmutableList.of("rs3131972", "rs201888535"))
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

    }

}
