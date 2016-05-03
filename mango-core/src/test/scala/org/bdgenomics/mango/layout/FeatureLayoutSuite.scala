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

import org.apache.spark.rdd.RDD
import org.bdgenomics.adam.util.ADAMFunSuite
import org.bdgenomics.formats.avro.{ Contig, Feature }
import org.bdgenomics.mango.util.MangoFunSuite

class FeatureLayoutSuite extends MangoFunSuite {

  sparkTest("test correct json format of 2 overlapping features") {
    val feature1 = Feature.newBuilder()
      .setContig(Contig.newBuilder().setContigName("contigName").build())
      .setStart(1)
      .setEnd(10)
      .build()

    val feature2 = Feature.newBuilder()
      .setContig(Contig.newBuilder().setContigName("contigName").build())
      .setStart(5)
      .setEnd(15)
      .build()

    val features: List[Feature] = List(feature1, feature2)

    val rdd: RDD[Feature] = sc.parallelize(features)
    val json: List[FeatureJson] = FeatureLayout(rdd)

    assert(json.size == 2)
    assert(json.map(r => r.track).distinct.size == 2)

  }

  sparkTest("test correct json format of 2 nonoverlapping features") {
    val feature1 = Feature.newBuilder()
      .setContig(Contig.newBuilder().setContigName("contigName").build())
      .setStart(1)
      .setEnd(10)
      .build()

    val feature2 = Feature.newBuilder()
      .setContig(Contig.newBuilder().setContigName("contigName").build())
      .setStart(15)
      .setEnd(21)
      .build()

    val features: List[Feature] = List(feature1, feature2)

    val rdd: RDD[Feature] = sc.parallelize(features, 1)
    val json: List[FeatureJson] = FeatureLayout(rdd)

    assert(json.size == 2)
    assert(json.map(r => r.track).distinct.size == 1)
  }

}
