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

package org.bdgenomics.mango.io

import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.mango.util.MangoFunSuite
import net.liftweb.json._

class BedReaderSuite extends MangoFunSuite {

  implicit val formats = DefaultFormats

  // test vcf data
  val bedFile = resourcePath("smalltest.bed")
  val bbFile = resourcePath("ensembl.chr17.bb")
  val narrowPeakFile = resourcePath("small.1.sorted.narrowPeak")

  test("Should load local bed file") {
    val region = new ReferenceRegion("chrM", 0, 3000)
    val data = BedReader.load(bedFile, Iterable(region)).toList
    assert(data.size == 3)
  }

  test("Fails on files that are not bed format") {
    val region = new ReferenceRegion("chrM", 90L, 110L)

    val thrown = intercept[Exception] {
      BedReader.load("myfile.badSuffix", Iterable(region))
    }
    assert(thrown.getMessage.contains("myfile.badSuffix"))
  }

  test("Should return empty on invalid chromosome") {
    val region = new ReferenceRegion("chrT", 90L, 110L)
    val data = BedReader.load(bedFile, Iterable(region))
    assert(data.size == 0)
  }

  test("Should load local narrowPeak file") {
    val region = new ReferenceRegion("chr1", 0, 26472859)
    val data = BedReader.load(narrowPeakFile, Iterable(region)).toList
    assert(data.size == 4)
  }

  test("Should load local bed files with incorrect prefixes file") {
    val region = new ReferenceRegion("M", 0, 2000)
    val data = BedReader.load(bedFile, Iterable(region)).toList
    assert(data.size == 2)
  }

  sparkTest("Should load data from HDFS using Spark") {
    val region = new ReferenceRegion("chrM", 90L, 91L)
    val data = BedReader.loadHDFS(sc, bedFile, Iterable(region))
    val data2 = BedReader.load(bedFile, Iterable(region))

    assert(data.rdd.count == data2.size)
  }

  test("Should load remote bed file") {
    val url = "https://www.encodeproject.org/files/ENCFF499IRL/@@download/ENCFF499IRL.bed.gz"

    val region = new ReferenceRegion("chr4", 86264, 86895)
    val data = BedReader.loadHttp(url, Iterable(region)).toList
    print(data.size)
    assert(data.size < 77)
  }

}
