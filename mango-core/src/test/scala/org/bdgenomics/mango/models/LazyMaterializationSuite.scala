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

package org.bdgenomics.mango.models

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD
import org.bdgenomics.adam.models.{ ReferenceRegion, SequenceDictionary, SequenceRecord }
import org.bdgenomics.mango.util.{ Bookkeep, MangoFunSuite }

class LazyMaterializationSuite extends MangoFunSuite {

  val sd = new SequenceDictionary(Vector(SequenceRecord("chr1", 2000L),
    SequenceRecord("chrM", 20000L),
    SequenceRecord("20", 90000L)))

  test("Should correctly reassigns chr prefix") {
    var region = ReferenceRegion("chr20", 1, 100)
    var hasChrPrefix = true

    // case 1: should keep chr prefix
    var modified = LazyMaterialization.modifyChrPrefix(region, hasChrPrefix)
    assert(modified.referenceName == region.referenceName)

    // case 2: should remove chr prefix
    hasChrPrefix = false
    modified = LazyMaterialization.modifyChrPrefix(region, hasChrPrefix)
    assert(modified.referenceName == "20")

    // case 3: should add chr prefix
    hasChrPrefix = true
    region = ReferenceRegion("20", 1, 100)
    modified = LazyMaterialization.modifyChrPrefix(region, hasChrPrefix)
    assert(modified.referenceName == "chr20")

    // case 4: should leave prefix off
    hasChrPrefix = false
    modified = LazyMaterialization.modifyChrPrefix(region, hasChrPrefix)
    assert(modified.referenceName == region.referenceName)
  }

  sparkTest("Should check and clear memory") {
    val lazyDummy = new LazyDummy(sc, List("FakeFile"), sd)
    lazyDummy.setMemoryFraction(0.0000001) // this is a very low test value
    lazyDummy.get(Some(ReferenceRegion("chrM", 0, 10L))).count
    assert(lazyDummy.bookkeep.queue.contains("chrM"))

    lazyDummy.get(Some(ReferenceRegion("20", 0, 10L))).count

    // these calls should have removed chrM from cache
    assert(!lazyDummy.bookkeep.queue.contains("chrM"))
  }

  sparkTest("Should not add http files to spark") {
    val lazyDummy = new LazyDummy(sc, List("http://httpfile.bam"), sd)
    assert(lazyDummy.getFiles(true).size == 0)
  }

}

/**
 * Dummy class that extends LazyMaterialization. Used for unit testing LazyMaterialization
 * @param sc SparkContext
 * @param files Dummy filepaths that are not used
 * @param sd SequenceDictionary
 */
class LazyDummy(@transient sc: SparkContext,
                files: List[String],
                sd: SequenceDictionary) extends LazyMaterialization[ReferenceRegion, ReferenceRegion]("TestRDD", sc, files, sd, false, Some(100L)) with Serializable {

  def getReferenceRegion = (r: ReferenceRegion) => r

  def load = (file: String, regions: Option[Iterable[ReferenceRegion]]) => {
    val region = regions.get.head
    sc.parallelize(Array.range(region.start.toInt, region.end.toInt)
      .map(r => ReferenceRegion(region.referenceName, r, r + 1)))
  }

  def stringify = (data: Array[ReferenceRegion]) => {
    // empty
    ""
  }

  def createHttpEndpoint = (endpoint: String) => None

  def setReferenceName = (r: ReferenceRegion, referenceName: String) => {
    ReferenceRegion(referenceName, r.start, r.end)
    r
  }

  def toJson(data: RDD[(String, ReferenceRegion)]): Map[String, Array[ReferenceRegion]] = {
    data.collect.groupBy(_._1).map(r => (r._1, r._2.map(_._2)))
  }

}

