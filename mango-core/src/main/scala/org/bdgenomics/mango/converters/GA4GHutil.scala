package org.bdgenomics.mango.converters

import javax.inject.Inject

import com.google.inject._
import ga4gh.Reads.ReadAlignment
import ga4gh.Variants.Variant
import net.codingwell.scalaguice.ScalaModule
import org.apache.spark.rdd.RDD
import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.adam.rdd.read.AlignmentRecordRDD
import org.bdgenomics.adam.rdd.variant.{ GenotypeRDD, VariantContextRDD }
import org.bdgenomics.adam.models.VariantContext
import org.bdgenomics.adam.rdd.feature.FeatureRDD
import org.bdgenomics.convert.ga4gh.Ga4ghModule
import org.bdgenomics.convert.{ ConversionStringency, Converter }
import org.bdgenomics.formats.avro.AlignmentRecord
import org.bdgenomics.formats.avro.Variant
import org.bdgenomics.mango.converters

import scala.collection.JavaConverters._
import scala.collection.JavaConversions._
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * Created by paschalj on 9/8/17.
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

  def alignmentRecordRDDtoJSON(alignmentRecordRDD: AlignmentRecordRDD): String = {
    val logger = LoggerFactory.getLogger("GA4GHutil")

    val gaReads: Array[ReadAlignment] = alignmentRecordRDD.rdd.collect.map(a => alignmentConverter.convert(a, ConversionStringency.LENIENT, logger))

    // We would prefer to run the convert as a map on the RDD, however fails currently because GA4GH type
    // is not registered in kryo
    /*
    val gaReads: Array[ReadAlignment] = alignmentRecordRDD.rdd.map(a => alignmentConverter
      .convert(a, ConversionStringency.LENIENT, logger))
      .collect()
    */

    val result: ga4gh.ReadServiceOuterClass.SearchReadsResponse = ga4gh.ReadServiceOuterClass.SearchReadsResponse.newBuilder()
      .addAllAlignments(gaReads.toList.asJava).build()

    com.google.protobuf.util.JsonFormat.printer().includingDefaultValueFields().print(result)
  }

  def variantContextRDDtoJSON(variantContextRDD: VariantContextRDD): String = {
    val logger = LoggerFactory.getLogger("GA4GHutil")

    val gaVariants: Array[ga4gh.Variants.Variant] = variantContextRDD.rdd.collect.map(a => {
      ga4gh.Variants.Variant.newBuilder(variantConverter.convert(a.variant.variant, ConversionStringency.LENIENT, logger))
        .addAllCalls(a.genotypes.map((g) => genotypeConverter.convert(g, ConversionStringency.LENIENT, logger)).asJava)
        .build()
    })

    // We would prefer to run the convert as a map on the RDD, however fails currently because GA4GH type
    // is not registered in kryo
    /*
    val gaVariants: Array[ga4gh.Variants.Variant] = variantContextRDD.rdd.map(a => {
       ga4gh.Variants.Variant.newBuilder(variantConverter.convert(a.variant.variant, ConversionStringency.LENIENT, logger))
       .addAllCalls(a.genotypes.map((g) => genotypeConverter.convert(g, ConversionStringency.LENIENT, logger)).asJava)
       .build()
       }).collect()
     */

    val result: ga4gh.VariantServiceOuterClass.SearchVariantsResponse = ga4gh.VariantServiceOuterClass
      .SearchVariantsResponse.newBuilder().addAllVariants(gaVariants.toList.asJava).build()

    com.google.protobuf.util.JsonFormat.printer().includingDefaultValueFields().print(result)
  }

  def genotypeRDDtoJSON(genotypeRDD: GenotypeRDD): String = {
    variantContextRDDtoJSON(genotypeRDD.toVariantContextRDD)
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

}

