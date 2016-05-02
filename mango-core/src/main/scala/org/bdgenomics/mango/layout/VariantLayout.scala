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

import org.apache.spark.{ SparkContext, Logging }
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.{ SQLContext, Row, DataFrame }
import org.apache.spark.storage.StorageLevel
import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.mango.core.util.VizUtils
import org.bdgenomics.mango.util.Bookkeep

class VariantLayout(sc: SparkContext) extends Logging {

  val sqlContext = new SQLContext(sc)

  var fileMap: Map[String, DataFrame] = Map[String, DataFrame]()
  var sampMap: Option[Map[String, Int]] = None

  var freqBook: Bookkeep = new Bookkeep(1000)
  var varBook: Bookkeep = new Bookkeep(1000)

  var wsetFreq: DataFrame = sqlContext.createDataFrame(sc.emptyRDD[Counts])
  var wsetVar: DataFrame = sqlContext.createDataFrame(sc.emptyRDD[VariantSchema])

  /*
   * Stores the filemap of variant information for each chromosome. Each file is stored as a dataframe
   */
  def loadChr(filePath: String): Unit = {
    val df: DataFrame = sqlContext.read.load(filePath)
    val chr: String = df.first.get(1).asInstanceOf[String] //TODO: should be contigName, correct based on schema
    //TODO: perform schema projection here instead of in the get functions?
    fileMap += (chr -> df)
  }

  /*
   * Gets the dataframe associated with each file
   */
  def getDF(chr: String): Option[DataFrame] = {
    fileMap.get(chr)
  }

  /*
   * Gets the frequency information for a region, formatted as JSON
   */
  def getFreq(region: ReferenceRegion): Array[String] = {
    if (fetchVarFreqData(region)) { //file exists for what we're querying for
      val binSize = VizUtils.getBinSize(region, 1000)
      wsetFreq.filter(wsetFreq("variant__start") >= region.start && wsetFreq("variant__start") <= region.end
        && wsetFreq("variant__start") % binSize === 0).toJSON.collect
    } else {
      Array[String]() //return empty array
    }
  }

  /*
   * Materializes a region of frequency data. This is used for both regular fetching
   */
  def fetchVarFreqData(region: ReferenceRegion, prefetch: Boolean = false): Boolean = {
    val df = getDF(region.referenceName)
    df match {
      case Some(_) => {
        //TODO: contains all the samples right now
        val matRegions: Option[List[ReferenceRegion]] = freqBook.getMaterializedRegions(region, List("all"))
        if (matRegions.isDefined) {
          for (reg <- matRegions.get) {
            println(region)
            val filt = df.get.select("variant__start")
            val counts = filt.filter(filt("variant__start") >= reg.start && filt("variant__start") <= reg.end).groupBy("variant__start").count
            wsetFreq = wsetFreq.unionAll(counts)
            freqBook.rememberValues(reg, "all") //TODO: contains all the samples right now
            wsetFreq.cache
            if (prefetch) wsetFreq.count
          }
        } else {
          println("ALREADY FETCHED BEFORE")
        }
        true
      }
      case None => {
        println("FILE NOT PROVIDED FOR FREQ")
        false
      }
    }
  }

  /*
   * Gets the variant data for a region, formatted as JSON
   */
  def get(region: ReferenceRegion): Array[VariantJson] = {
    if (fetchVarData(region)) { //file exists for what we're querying for
      val total = wsetVar.filter(wsetVar("variant__start") >= region.start && wsetVar("variant__start") <= region.end)
      val data: Array[Row] = total.collect
      val grouped: Map[(String, Array[org.apache.spark.sql.Row]), Int] = data.groupBy(_.get(1).asInstanceOf[String]).zipWithIndex
      grouped.flatMap(recs => recs._1._2.map(r => new VariantJson(region.referenceName, recs._1._1, r.get(0).asInstanceOf[Long],
        r.get(0).asInstanceOf[Long] + 1, recs._2))).toArray
    } else {
      Array[VariantJson]() //return empty array
    }
  }

  /*
   * Materializes a region of variant data. This is used for both regular fetching
   */
  def fetchVarData(region: ReferenceRegion, prefetch: Boolean = false): Boolean = {
    val df = getDF(region.referenceName)
    df match {
      case Some(_) => {
        //TODO: contains all the samples right now
        val matRegions: Option[List[ReferenceRegion]] = varBook.getMaterializedRegions(region, List("all"))
        if (matRegions.isDefined) {
          for (reg <- matRegions.get) {
            val filt = df.get.select("variant__start", "sampleId")
            val data = filt.filter(filt("variant__start") >= reg.start && filt("variant__start") <= reg.end)
            wsetVar = wsetVar.unionAll(data)
            varBook.rememberValues(reg, "all") //TODO: contains all the samples right now
            wsetVar.cache
            if (prefetch) wsetVar.count
          }
        }
        true
      }
      case None => {
        println("FILE NOT PROVIDED FOR VAR")
        false
      }
    }
  }

}

// tracked json objects for genotype visual data
case class VariantJson(contigName: String, sampleId: String, start: Long, end: Long, track: Int)
case class VariantSchema(variant__start: Long, sampleId: String)
case class Counts(variant__start: Long, count: Int)
