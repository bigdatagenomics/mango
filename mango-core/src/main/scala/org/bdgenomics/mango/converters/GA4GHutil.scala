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

import javax.inject.Inject

import com.google.inject._
import ga4gh.Reads.ReadAlignment
import net.codingwell.scalaguice.ScalaModule
import org.bdgenomics.adam.rdd.read.AlignmentRecordRDD
import org.bdgenomics.adam.rdd.variant.{ GenotypeRDD, VariantContextRDD, VariantRDD }
import org.bdgenomics.adam.rdd.feature.FeatureRDD
import org.bdgenomics.convert.ga4gh.Ga4ghModule
import org.bdgenomics.convert.{ ConversionStringency, Converter }
import org.bdgenomics.formats.avro.AlignmentRecord

import scala.collection.JavaConverters._
import org.slf4j.LoggerFactory

/*
 * Converts ADAM RDDs to json strings in GA4GH format.
 * See https://github.com/ga4gh/ga4gh-schemas.
 */
object GA4GHutil {
  val injector: Injector = Guice.createInjector(new Ga4ghModule())

  val alignmentConverter: Converter[AlignmentRecord, ReadAlignment] = injector
    .getInstance(Key.get(new TypeLiteral[Converter[AlignmentRecord, ReadAlignment]]() {}))

  val variantConverter: Converter[org.bdgenomics.formats.avro.Variant, ga4gh.Variants.Variant] = injector
    .getInstance(Key.get(new TypeLiteral[Converter[org.bdgenomics.formats.avro.Variant, ga4gh.Variants.Variant]]() {}))

  val genotypeConverter: Converter[org.bdgenomics.formats.avro.Genotype, ga4gh.Variants.Call] = injector
    .getInstance(Key.get(new TypeLiteral[Converter[org.bdgenomics.formats.avro.Genotype, ga4gh.Variants.Call]]() {}))

  val featureConverter: Converter[org.bdgenomics.formats.avro.Feature, ga4gh.SequenceAnnotations.Feature] = injector
    .getInstance(Key.get(new TypeLiteral[Converter[org.bdgenomics.formats.avro.Feature, ga4gh.SequenceAnnotations.Feature]]() {}))

  /**
   * Converts alignmentRecordRDD to GA4GHReadsResponse string
   *
   * @param alignmentRecordRDD rdd to convert
   * @return GA4GHReadsResponse json string
   */
  def alignmentRecordRDDtoJSON(alignmentRecordRDD: AlignmentRecordRDD): String = {
    val logger = LoggerFactory.getLogger("GA4GHutil")

    val gaReads: Array[ReadAlignment] = alignmentRecordRDD.rdd.collect.map(a => alignmentConverter.convert(a, ConversionStringency.LENIENT, logger))

    val result: ga4gh.ReadServiceOuterClass.SearchReadsResponse = ga4gh.ReadServiceOuterClass.SearchReadsResponse.newBuilder()
      .addAllAlignments(gaReads.toList.asJava).build()

    com.google.protobuf.util.JsonFormat.printer().includingDefaultValueFields().print(result)
  }

  /**
   * Converts variantContextRDD to GA4GHVariantResponse
   *
   * @param variantContextRDD rdd to convert
   * @return GA4GHVariantResponse json string
   */
  def variantContextRDDtoJSON(variantContextRDD: VariantContextRDD): String = {
    val logger = LoggerFactory.getLogger("GA4GHutil")

    val gaVariants: Array[ga4gh.Variants.Variant] = variantContextRDD.rdd.collect.map(a => {
      ga4gh.Variants.Variant.newBuilder(variantConverter.convert(a.variant.variant, ConversionStringency.LENIENT, logger))
        .addAllCalls(a.genotypes.map((g) => genotypeConverter.convert(g, ConversionStringency.LENIENT, logger)).asJava)
        .build()
    })

    val result: ga4gh.VariantServiceOuterClass.SearchVariantsResponse = ga4gh.VariantServiceOuterClass
      .SearchVariantsResponse.newBuilder().addAllVariants(gaVariants.toList.asJava).build()

    com.google.protobuf.util.JsonFormat.printer().includingDefaultValueFields().print(result)
  }

  /**
   * Converts genotypeRDD to GA4GHVariantResponse
   *
   * @param genotypeRDD rdd to convert
   * @return GA4GHVariantResponse json string
   */
  def genotypeRDDtoJSON(genotypeRDD: GenotypeRDD): String = {
    variantContextRDDtoJSON(genotypeRDD.toVariantContexts)
  }

  def featureRDDtoJSON(featureRDD: FeatureRDD): String = {
    val logger = LoggerFactory.getLogger("GA4GHutil")
    val gaFeatures: Array[ga4gh.SequenceAnnotations.Feature] = featureRDD.rdd.collect.map(a =>
      {
        ga4gh.SequenceAnnotations.Feature
          .newBuilder(featureConverter.convert(a, ConversionStringency.LENIENT, logger)).build()
      })

    val result: ga4gh.SequenceAnnotationServiceOuterClass.SearchFeaturesResponse = ga4gh.SequenceAnnotationServiceOuterClass.SearchFeaturesResponse
      .newBuilder().addAllFeatures(gaFeatures.toList.asJava).build()

    com.google.protobuf.util.JsonFormat.printer().includingDefaultValueFields().print(result)

  }

  /**
   * Converts variantRDD to GA4GHVariantResponse
   *
   * @param variantRDD rdd to convert
   * @return GA4GHVariantResponse json string
   */
  def variantRDDtoJSON(variantRDD: VariantRDD): String = {
    val logger = LoggerFactory.getLogger("GA4GHutil")
    val gaVariants: Array[ga4gh.Variants.Variant] = variantRDD.rdd.collect.map(a => {
      ga4gh.Variants.Variant.newBuilder(variantConverter.convert(a, ConversionStringency.LENIENT, logger))
        .build()
    })
    val result: ga4gh.VariantServiceOuterClass.SearchVariantsResponse = ga4gh.VariantServiceOuterClass
      .SearchVariantsResponse.newBuilder().addAllVariants(gaVariants.toList.asJava).build()

    com.google.protobuf.util.JsonFormat.printer().includingDefaultValueFields().print(result)

  }
}
