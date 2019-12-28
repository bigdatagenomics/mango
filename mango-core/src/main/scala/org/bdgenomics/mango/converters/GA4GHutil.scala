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
import ga4gh.ReadServiceOuterClass.SearchReadsResponse
import ga4gh.Reads.ReadAlignment
import ga4gh.SequenceAnnotationServiceOuterClass.SearchFeaturesResponse
import ga4gh.VariantServiceOuterClass.SearchVariantsResponse
import net.liftweb.json.Extraction._
import net.liftweb.json._
import org.bdgenomics.adam.models.VariantContext
import org.bdgenomics.formats.avro.Feature
import org.bdgenomics.adam.rdd.read.AlignmentDataset
import org.bdgenomics.adam.rdd.variant.{ GenotypeDataset, VariantContextDataset, VariantDataset }
import org.bdgenomics.adam.rdd.feature.FeatureDataset
import org.bdgenomics.convert.ga4gh.Ga4ghModule
import org.bdgenomics.convert.{ ConversionStringency, Converter }
import org.bdgenomics.formats.avro.Alignment

import scala.collection.JavaConverters._
import scala.collection.JavaConversions._
import org.slf4j.LoggerFactory

/*
 * Converts ADAM datasets to json strings in GA4GH format.
 * See https://github.com/ga4gh/ga4gh-schemas.
 */
object GA4GHutil {
  val injector: Injector = Guice.createInjector(new Ga4ghModule())

  val alignmentConverter: Converter[Alignment, ReadAlignment] = injector
    .getInstance(Key.get(new TypeLiteral[Converter[Alignment, ReadAlignment]]() {}))

  val variantConverter: Converter[org.bdgenomics.formats.avro.Variant, ga4gh.Variants.Variant] = injector
    .getInstance(Key.get(new TypeLiteral[Converter[org.bdgenomics.formats.avro.Variant, ga4gh.Variants.Variant]]() {}))

  val genotypeConverter: Converter[org.bdgenomics.formats.avro.Genotype, ga4gh.Variants.Call] = injector
    .getInstance(Key.get(new TypeLiteral[Converter[org.bdgenomics.formats.avro.Genotype, ga4gh.Variants.Call]]() {}))

  val featureConverter: Converter[org.bdgenomics.formats.avro.Feature, ga4gh.SequenceAnnotations.Feature] = injector
    .getInstance(Key.get(new TypeLiteral[Converter[org.bdgenomics.formats.avro.Feature, ga4gh.SequenceAnnotations.Feature]]() {}))

  val logger = LoggerFactory.getLogger("GA4GHutil")

  /**
   * Converts avro Alignment Record to GA4GH Read Alignment
   *
   * @param alignment Alignment Record
   * @return GA4GH Read Alignment
   */
  def alignmentToGAReadAlignment(alignment: Alignment): ReadAlignment = {
    alignmentConverter.convert(alignment, ConversionStringency.LENIENT, logger)
  }

  /**
   * Converts VariantContext to GA4GH Variant
   *
   * @param variantContext variant context
   * @return GA4GH Variant
   */
  def variantContextToGAVariant(variantContext: VariantContext) = {
    ga4gh.Variants.Variant.newBuilder(variantConverter.convert(variantContext.variant.variant, ConversionStringency.LENIENT, logger))
      .addAllCalls(variantContext.genotypes.map((g) => genotypeConverter.convert(g, ConversionStringency.LENIENT, logger)).asJava)
      .build()
  }

  /**
   * Converts avro features to GA4GH features
   *
   * @param feature avro Features
   * @return GA4GH Feature
   */
  def featureToGAFeature(feature: Feature): ga4gh.SequenceAnnotations.Feature = {
    ga4gh.SequenceAnnotations.Feature
      .newBuilder(featureConverter.convert(feature, ConversionStringency.LENIENT, logger)).build()
  }

  /**
   * Converts a JSON formatted ga4gh.VariantServiceOuterClass.  in string form
   * back into a SearchVariantsResponse.
   *
   * @param variantServiceString string of JSONified
   * @return converted SearchVariantsResponse
   */
  def stringToVariantServiceResponse(variantServiceString: String): SearchVariantsResponse = {

    val builder = ga4gh.VariantServiceOuterClass.SearchVariantsResponse.newBuilder()

    com.google.protobuf.util.JsonFormat.parser().merge(variantServiceString,
      builder)

    builder.build()
  }

  /**
   * Converts JSON formatted ga4gh.ReadServiceOuterClass.SearchReadsResponse in string form
   * back into a SearchReadsResponse.
   *
   * @param readsServiceString string of JSONified ga4gh.ReadServiceOuterClass.SearchReadsResponse
   * @return converted SearchReadsResponse
   */
  def stringToSearchReadsResponse(readsServiceString: String): SearchReadsResponse = {

    val builder = SearchReadsResponse.newBuilder()

    com.google.protobuf.util.JsonFormat.parser().merge(readsServiceString,
      builder)

    builder.build()
  }

  /**
   * Converts a JSON formatted ga4gh.SequenceAnnotationServiceOuterClass.SearchFeaturesResponse
   * in string form back into a SearchFeaturesResponse.
   *
   * @param featureServiceString string of JSONified ga4gh.SequenceAnnotationServiceOuterClass.SearchFeaturesResponse
   * @return converted SearchFeaturesResponse
   */
  def stringToSearchFeaturesResponse(featureServiceString: String): SearchFeaturesResponse = {

    val builder = SearchFeaturesResponse.newBuilder()

    com.google.protobuf.util.JsonFormat.parser().merge(featureServiceString,
      builder)

    builder.build()
  }

  /**
   * Converts AlignmentDataset to GA4GHReadsResponse string
   *
   * @param alignmentDataset dataset to convert
   * @param multipleGroupNames Boolean determining whether to map group names separately
   * @return GA4GHReadsResponse json string
   */
  def alignmentDatasetToJSON(alignmentDataset: AlignmentDataset,
                             multipleGroupNames: Boolean = false): java.util.Map[String, String] = {

    val gaReads: Array[ReadAlignment] = alignmentDataset.rdd.collect.map(a => alignmentConverter.convert(a, ConversionStringency.LENIENT, logger))

    // Group by ReadGroupID, which is set in bdg convert to alignment's getRecordGroupName(), if it exists, or "1"
    val results: Map[String, ga4gh.ReadServiceOuterClass.SearchReadsResponse] =
      if (multipleGroupNames) {
        gaReads.groupBy(r => r.getReadGroupId).map(sampleReads =>
          (sampleReads._1,
            ga4gh.ReadServiceOuterClass.SearchReadsResponse.newBuilder()
            .addAllAlignments(sampleReads._2.toList.asJava).build()))
      } else {
        Map(("1",
          ga4gh.ReadServiceOuterClass.SearchReadsResponse.newBuilder()
          .addAllAlignments(gaReads.toList.asJava).build()))
      }

    // convert results to json strings for each readGroupName
    val jsonMap = results.map(r => (r._1, com.google.protobuf.util.JsonFormat.printer().includingDefaultValueFields().print(r._2)))
    return mapAsJavaMap(jsonMap)
  }

  /**
   * Converts VariantContextDataset to GA4GHVariantResponse
   *
   * @param variantContextDataset dataset to convert
   * @return GA4GHVariantResponse json string
   */
  def variantContextDatasetToJSON(variantContextDataset: VariantContextDataset): String = {

    val gaVariants: Array[ga4gh.Variants.Variant] = variantContextDataset.rdd.collect.map(a => {
      variantContextToGAVariant(a)
    })

    val result: ga4gh.VariantServiceOuterClass.SearchVariantsResponse = ga4gh.VariantServiceOuterClass
      .SearchVariantsResponse.newBuilder().addAllVariants(gaVariants.toList.asJava).build()

    com.google.protobuf.util.JsonFormat.printer().includingDefaultValueFields().print(result)
  }

  /**
   * Converts GenotypeDataset to GA4GHVariantResponse
   *
   * @param genotypeDataset dataset to convert
   * @return GA4GHVariantResponse json string
   */
  def genotypeDatasetToJSON(genotypeDataset: GenotypeDataset): String = {
    variantContextDatasetToJSON(genotypeDataset.toVariantContexts)
  }

  def featureDatasetToJSON(featureDataset: FeatureDataset): String = {

    val gaFeatures: Array[ga4gh.SequenceAnnotations.Feature] = featureDataset.rdd.collect.map(a => featureToGAFeature(a))

    val result: ga4gh.SequenceAnnotationServiceOuterClass.SearchFeaturesResponse = ga4gh.SequenceAnnotationServiceOuterClass.SearchFeaturesResponse
      .newBuilder().addAllFeatures(gaFeatures.toList.asJava).build()

    com.google.protobuf.util.JsonFormat.printer().includingDefaultValueFields().print(result)

  }

  /**
   * Converts VariantDataset to GA4GHVariantResponse
   *
   * @param variantDataset dataset to convert
   * @return GA4GHVariantResponse json string
   */
  def variantDatasetToJSON(variantDataset: VariantDataset): String = {
    val logger = LoggerFactory.getLogger("GA4GHutil")
    val gaVariants: Array[ga4gh.Variants.Variant] = variantDataset.rdd.collect.map(a => {
      ga4gh.Variants.Variant.newBuilder(variantConverter.convert(a, ConversionStringency.LENIENT, logger))
        .build()
    })
    val result: ga4gh.VariantServiceOuterClass.SearchVariantsResponse = ga4gh.VariantServiceOuterClass
      .SearchVariantsResponse.newBuilder().addAllVariants(gaVariants.toList.asJava).build()

    com.google.protobuf.util.JsonFormat.printer().includingDefaultValueFields().print(result)

  }
}

case class SearchVariantsRequestGA4GH(variantSetId: String,
                                      pageToken: String,
                                      pageSize: Int,
                                      referenceName: String,
                                      callSetIds: Array[String],
                                      start: Long,
                                      end: Long) {

  // converts object to JSON byte array. For testing POSTs.
  def toByteArray(): Array[Byte] = {
    implicit val formats = DefaultFormats
    compactRender(decompose(this)).toCharArray.map(_.toByte)
  }

}

case class SearchFeaturesRequestGA4GH(featureSetId: String,
                                      pageToken: String,
                                      pageSize: Int,
                                      referenceName: String,
                                      start: Long,
                                      end: Long) {

  // converts object to JSON byte array. For testing POSTs.
  def toByteArray(): Array[Byte] = {
    implicit val formats = DefaultFormats
    compactRender(decompose(this)).toCharArray.map(_.toByte)
  }

}

// see proto defintiion: https://github.com/ga4gh/ga4gh-schemas/blob/master/src/main/proto/ga4gh/read_service.proto#L117
case class SearchReadsRequestGA4GH(pageToken: String,
                                   pageSize: Int,
                                   readGroupIds: Array[String],
                                   referenceId: String,
                                   start: Long,
                                   end: Long) {

  // converts object to JSON byte array. For testing POSTs.
  def toByteArray(): Array[Byte] = {
    implicit val formats = DefaultFormats
    compactRender(decompose(this)).toCharArray.map(_.toByte)
  }

}
