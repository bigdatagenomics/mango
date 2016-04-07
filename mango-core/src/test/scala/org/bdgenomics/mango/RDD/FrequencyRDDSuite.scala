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
package org.bdgenomics.mango.RDD

import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.adam.util.ADAMFunSuite
import org.bdgenomics.formats.avro.AlignmentRecord

import scala.collection.mutable.ListBuffer

class FrequencyRDDSuite extends ADAMFunSuite {
  val binSize = 2

  val chrM = resourcePath("mouse_chrM.bam")

  sparkTest("get frequency from reads of a 10 base pair long region") {
    val samples = List("sample1")

    val region = new ReferenceRegion("chr1", 0, 10)
    val records = new ListBuffer[AlignmentRecord]
    val sequence = "GATAAA"
    for (i <- 0L to 10L) {
      records += AlignmentRecord.newBuilder()
        .setStart(i)
        .setEnd(i + sequence.length)
        .setContigName("chr1")
        .setSequence(sequence)
        .setRecordGroupSample(samples(0))
        .build
    }

    val freq = new FrequencyRDD
    val rdd = sc.parallelize(records.toList, 1)

    freq.put(rdd, region, stride = 1)
    val results = freq.get(region, samples)
    assert(results(samples(0)).size == 10)

  }

  sparkTest("get frequency from medium amount of reads") {

    val region = new ReferenceRegion("chrM", 1, 5000)
    var rdd = sc.loadIndexedBam(chrM, region)
    val samples = List(rdd.first.getRecordGroupSample)

    val freq = new FrequencyRDD

    freq.put(rdd, region, stride = 100)
    val results = freq.get(region, samples)
    println(results(samples(0)).size)
    assert(results(samples(0)).size == 50)

  }
}
