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

  sparkTest("Should check and clear memory") {
    val lazyDummy = new LazyDummy(sc, List("FakeFile"), sd)
    lazyDummy.setMemoryFraction(0.0000001) // this is a very low test value
    lazyDummy.get(ReferenceRegion("chrM", 0, 10L)).count
    assert(lazyDummy.bookkeep.queue.contains("chrM"))

    lazyDummy.get(ReferenceRegion("20", 0, 10L)).count

    // these calls should have removed chrM from cache
    assert(!lazyDummy.bookkeep.queue.contains("chrM"))
  }

}

/**
 * Dummy class that extends LazyMaterialization. Used for unit testing LazyMaterialization
 * @param s SparkContext
 * @param filePaths Dummy filepaths that are not used
 * @param dict SequenceDictionary
 */
class LazyDummy(s: SparkContext,
                filePaths: List[String],
                dict: SequenceDictionary) extends LazyMaterialization[ReferenceRegion]("TestRDD", Some(100)) with Serializable {
  @transient implicit val formats = net.liftweb.json.DefaultFormats
  @transient val sc = s
  val sd = dict
  val files = filePaths

  def getReferenceRegion = (r: ReferenceRegion) => r

  def load = (file: String, region: Option[ReferenceRegion]) => {
    sc.parallelize(Array.range(region.get.start.toInt, region.get.end.toInt)
      .map(r => ReferenceRegion(region.get.referenceName, r, r + 1)))
  }

  def stringify(data: RDD[(String, ReferenceRegion)]): Map[String, String] = {
    data
      .collect
      .groupBy(_._1)
      .map(r => (r._1, r._2.map(_._2)))
      .mapValues(r => r.map(f => f.toString).mkString(","))
  }
}

