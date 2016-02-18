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
package org.bdgenomics.mango.layout

import org.bdgenomics.adam.models.{ ReferenceRegion, ReferencePosition }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.formats.avro.{ AlignmentRecord, Contig, Feature, Genotype, Variant }
import org.scalatest.FunSuite
import org.bdgenomics.mango.layout._
import org.apache.spark.TaskContext
import org.bdgenomics.adam.util.ADAMFunSuite
import org.scalatest._
import org.apache.spark.{ SparkConf, Logging, SparkContext }
import org.apache.spark.TaskContext
import org.apache.spark.rdd.RDD
import org.scalatest.FunSuite
import org.bdgenomics.adam.util.ADAMFunSuite
import org.bdgenomics.adam.rdd.ADAMContext._
import scala.collection.mutable.ListBuffer

class VariantLayoutSuite extends ADAMFunSuite {

  sparkTest("test correct json format of 2 non overlapping variants") {
    val variant1 = Genotype.newBuilder
      .setVariant(Variant.newBuilder.setStart(5).setEnd(6).setContig(Contig.newBuilder().setContigName("contigName").build()).build)
      .setSampleId("SAMPLE")
      .build

    val variant2 = Genotype.newBuilder
      .setVariant(Variant.newBuilder.setStart(9).setEnd(10).setContig(Contig.newBuilder().setContigName("contigName").build()).build)
      .setSampleId("SAMPLE")
      .build

    val variants: List[Genotype] = List(variant1, variant2)

    val rdd: RDD[(ReferenceRegion, Genotype)] = sc.parallelize(variants, 1).keyBy(v => ReferenceRegion(ReferencePosition(v)))
    val json: List[VariantJson] = VariantLayout(rdd)

    assert(json.size == 2)
    assert(json.map(r => r.track).distinct.size == 1)

  }

  sparkTest("test correct json format of 2 overlapping variants") {
    val variant1 = Genotype.newBuilder
      .setVariant(Variant.newBuilder.setStart(5).setEnd(6).setContig(Contig.newBuilder().setContigName("contigName").build()).build)
      .setSampleId("NA12878")
      .build

    val variant2 = Genotype.newBuilder
      .setVariant(Variant.newBuilder.setStart(5).setEnd(6).setContig(Contig.newBuilder().setContigName("contigName2").build()).build)
      .setSampleId("NA12877")
      .build

    val variants: List[Genotype] = List(variant1, variant2)

    val rdd: RDD[(ReferenceRegion, Genotype)] = sc.parallelize(variants).keyBy(v => ReferenceRegion(ReferencePosition(v)))
    val json: List[VariantJson] = VariantLayout(rdd)

    assert(json.size == 2)
    assert(json.map(r => r.track).distinct.size == 2)

  }

  sparkTest("test correct json format for variant frequency") {
    val variant1 = Genotype.newBuilder
      .setVariant(Variant.newBuilder.setStart(5).setEnd(6).setContig(Contig.newBuilder().setContigName("contigName").build()).build)
      .setSampleId("NA12878")
      .build

    val variant2 = Genotype.newBuilder
      .setVariant(Variant.newBuilder.setStart(5).setEnd(6).setContig(Contig.newBuilder().setContigName("contigName").build()).build)
      .setSampleId("NA12877")
      .build

    val variants: List[Genotype] = List(variant1, variant2)

    val rdd: RDD[(ReferenceRegion, Genotype)] = sc.parallelize(variants).keyBy(v => ReferenceRegion(ReferencePosition(v)))
    val json: List[VariantFreqJson] = VariantFreqLayout(rdd)
    assert(json.size == 1)

  }

}
