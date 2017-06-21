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
package org.bdgenomics.mango.converters

import java.lang.Boolean
import java.util

import com.google.protobuf.ListValue
import htsjdk.samtools.{ CigarOperator, TextCigarCodec, ValidationStringency }
import org.bdgenomics.adam.models.VariantContext
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.formats.avro.{ AlignmentRecord, Feature, Genotype, GenotypeAllele }
import org.bdgenomics.utils.misc.Logging
import org.ga4gh._
import ga4gh.Variants.Call
import ga4gh.{ Common, Variants }
import org.bdgenomics.mango.converters.GA4GHConverter.toGA4GHCall
import java.lang.Double

import scala.collection.JavaConverters._
import scala.collection.JavaConversions._

/**
 * Created by paschalj on 6/20/17.
 */
object VariantContextCoverterGA4GH extends Serializable with Logging {

  def toGA4GHVariant(record: VariantContext, variantID: String = "", variantSetID: String = ""): ga4gh.Variants.Variant = {

    val ga4ghCalls: Iterable[Call] = record.genotypes.map(g => toGA4GHCall(g))

    ga4gh.Variants.Variant.newBuilder()
      .setId(variantID)
      .setVariantSetId(variantSetID)
      .addAllNames(record.variant.variant.getNames())
      .setReferenceName(record.variant.variant.getContigName)
      .setStart(record.variant.variant.getStart)
      .setEnd(record.variant.variant.getEnd)
      .setReferenceBases(record.variant.variant.getReferenceAllele)
      .addAllAlternateBases(List(record.variant.variant.getAlternateAllele)) // note: VariantContext is defined only to have one altallele
      .addAllCalls(ga4ghCalls.asJava)
      .addAllFiltersFailed(record.variant.variant.getFiltersFailed)
      .setFiltersPassed(record.variant.variant.getFiltersPassed)
      .setFiltersApplied(record.variant.variant.getFiltersApplied)
      .build()
  }

  def toGA4GHCall(record: Genotype, callsetID: String = "NA"): ga4gh.Variants.Call = {

    def toGA4GHAllele(input: GenotypeAllele): String = {
      if (input == GenotypeAllele.REF) "0"
      else if (input == GenotypeAllele.ALT) "1"
      else "."
    }

    val inputAlleles: util.List[GenotypeAllele] = record.getAlleles

    val alleleFirst = toGA4GHAllele(inputAlleles(0))
    val alleleSecond = toGA4GHAllele(inputAlleles(1))

    val genotypeAlleles = List(alleleFirst, alleleSecond)

    val alleleFirstValue = com.google.protobuf.Value.newBuilder().setStringValue(alleleFirst)
    val alleleSecondValue = com.google.protobuf.Value.newBuilder().setStringValue(alleleSecond)

    val calls = ListValue.newBuilder().addValues(alleleFirstValue)
      .addValues(alleleSecondValue)

    // There has gotta be a better way, but compiler was not happy
    val gl: List[java.lang.Double] = record.getGenotypeLikelihoods.map((x) => { val y: java.lang.Double = x.toDouble; y }).toList

    ga4gh.Variants.Call.newBuilder()
      .setCallSetName(record.getSampleId)
      .setCallSetId(callsetID)
      .setPhaseset(record.getPhaseSetId.toString)
      .addAllGenotypeLikelihood(gl)
      .setGenotype(calls)
      .build()

  }

}
