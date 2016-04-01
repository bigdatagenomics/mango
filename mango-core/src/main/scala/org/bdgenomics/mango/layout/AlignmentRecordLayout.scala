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
import org.apache.spark.{ Logging, SparkContext }
import org.apache.spark.SparkContext._
import org.bdgenomics.formats.avro.{ AlignmentRecord, Contig }
import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.utils.instrumentation.Metrics
import scala.collection.JavaConversions._
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object AlignmentLayoutTimers extends Metrics {
  val AlignmentLayout = timer("collect and filter alignment records")
}

object AlignmentRecordLayout extends Logging {

  /**
   * An implementation of AlignmentRecordLayout which takes in an RDD of (ReferenceRegion, AlignmentRecord) tuples, the reference String
   * over the region, and the region viewed.
   *
   * @param rdd: RDD of (ReferenceRegion, AlignmentRecord) tuples
   * @param reference: reference string used to calculate mismatches
   * @param region: ReferenceRegion to be viewed
   * @return List of ReadJsons, which takes (AlignmentRecord, List[MisMatchJson]) tuples and picks the required information and mismatches
   */
  def apply(rdd: RDD[(ReferenceRegion, AlignmentRecord)], reference: Option[String], region: ReferenceRegion): Map[AlignmentRecord, List[MisMatchJson]] = {
    val mappingTuples: List[(AlignmentRecord, List[MisMatchJson])] = {
        rdd.mapPartitions(AlignmentRecordLayout(_, reference, region)).collect
    }
    ReadJson(mappingTuples)
  }

  /**
   * An implementation of AlignmentRecordLayout which takes in an Iterator of (ReferenceRegion, AlignmentRecord) tuples, the reference String
   * over the region, and the region viewed.
   *
   * @param iter: Iterator of (ReferenceRegion, AlignmentRecord) tuples
   * @param reference: reference string used to calculate mismatches
   * @param region: ReferenceRegion to be viewed
   * @return Iterator of tuples of alignment records to list of mismatches 
   */
  def apply(iter: Iterator[(ReferenceRegion, AlignmentRecord)], reference: Option[String], region: ReferenceRegion): Iterator[(AlignmentRecord, List[MisMatchJson])] = {
    new AlignmentRecordLayout(iter).getMisMatches(reference, region)
  }
}

/**
 * AlignmentRecord data
 *
 * @param values The set of (Reference, AlignmentRecord) tuples
 */
class AlignmentRecordLayout(values: Iterator[(ReferenceRegion, AlignmentRecord)]) with Logging {
  val sequence = values.toList

  def getMisMatches(reference: Option[String], region: ReferenceRegion): Iterator[(AlignmentRecord, List[MisMatchJson])] = {
    reference match {
      case Some(_) => {
        val readToMismatch = new ListBuffer[(AlignmentRecord, List[MisMatchJson])]()
        sequence.foreach {
          record =>
            {
              val mismatches: List[MisMatchJson] = MismatchLayout(record._2, reference.get, region))
              readToMismatch += (record._2, mismatches)
            }
        }
        readToMismatch.toIterator
      } case None => {
        List[AlignmentRecord, List[MisMatchJson]]().toIterator
      }
    }
  }
}

/**
 * An implementation of ReadJson which converts AlignmentRecord data to ReadJson
 *
 * @param recs The list of (Reference, AlignmentRecord) tuples to lay out in json
 * @return List of Read Json objects
 */
object ReadJson {
  //track has been removed
  def apply(recs: List[(AlignmentRecord, List[MisMatchJson])]): List[ReadJson] = {
    recs.map(rec => new ReadJson(rec._1.readName, rec._1.start, rec._1.end, rec._1.readNegativeStrand, rec._1.sequence, rec._1.cigar, rec._2)) //removed track
  }
}

object MatePairJson {

  /**
   * An implementation of MatePairJson which converts a list of MatePairs into MatePair Json
   *
   * @param recs The list of MatePairs to be layed out in json
   * @return List of MatePair Json objects
   */
  def apply(recs: List[MatePair]): List[MatePairJson] = {
    recs.map(r => MatePairJson(r.start, r.end))
  }
}

// json classes for alignmentrecord visual data
case class ReadJson(readName: String, start: Long, end: Long, readNegativeStrand: Boolean, sequence: String, cigar: String, mismatches: List[MisMatchJson]) //removed track
case class MatePairJson(val start: Long, val end: Long)
