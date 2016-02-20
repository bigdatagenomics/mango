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
import org.bdgenomics.adam.models.ReferenceRegion
import edu.berkeley.cs.amplab.spark.intervalrdd._
import org.bdgenomics.formats.avro.{ AlignmentRecord, Feature, Genotype, GenotypeAllele, NucleotideContigFragment }
import scala.collection.mutable
import scala.util.control.Breaks._
import org.apache.spark.rdd.RDD
import org.apache.spark.rdd.MetricsContext._
import org.bdgenomics.utils.instrumentation.Metrics
import org.apache.spark.Logging
import scala.reflect.{ ClassTag, classTag }

/**
 * An abstract Track class to support Tracks for different Genomic Types
 *
 * @tparam T: Genomic type
 */
abstract class Track[T] {
  def records: List[(ReferenceRegion, T)]
}

/**
 * An abstract TrackBuffer class to support TrackBiufferss for different Genomic Types
 *
 * @tparam T: Genomic type
 */
abstract class TrackBuffer[T] {
  val records: mutable.ListBuffer[(ReferenceRegion, T)] = new mutable.ListBuffer[(ReferenceRegion, T)]()

  def +=(rec: (ReferenceRegion, T)): TrackBuffer[T] = {
    records += rec
    this
  }

  def conflicts(rec: (ReferenceRegion, T)): Boolean = conflicts(Seq(rec))
  def conflicts(recs: Seq[(ReferenceRegion, T)]): Boolean
}

object Track {

  /**
   * An implementation of Track which generates GenericTracks from a GenericTrackBuffer
   *
   * @param trackBuffer: Mutable listbuffer of GenericTrackBuffer
   * @tparam T: Genomic type
   * @return List of Generic Tracks
   */
  def apply[T: ClassTag](trackBuffer: GenericTrackBuffer[T]): GenericTrack[T] = {
    new GenericTrack[T](trackBuffer.records.toList)
  }

  /**
   * An implementation of Track which generates ReadsTracks. Meant for AlignmentRecord data
   *
   * @param trackBuffer: Mutable listbuffer of ReadsTrackBuffer
   * @return List of ReadsTracks
   */
  def apply(trackBuffer: ReadsTrackBuffer, reference: Option[String], region: ReferenceRegion): ReadsTrack = {
    new ReadsTrack(trackBuffer.records.toList, trackBuffer.sample, reference, region)
  }
}

/**
 * Extension of Track to support all but AlignmentRecord Data
 *
 * @param recs: List of (ReferenceRegion, T) tuples in a ReadsTrack
 * @tparam T: Genomic type
 */
case class GenericTrack[T: ClassTag](records: List[(ReferenceRegion, T)]) extends Track[T]

/**
 * Extension of Track to support AlignmentRecord Data
 *
 * @param recs: List of (ReferenceRegion, AlignmentRecord) tuples in a ReadsTrack
 * @param sampOpt: Option of sample id
 * @param reference: String of referenceregion to compare records to
 * @param region: ReferenceRegion tracks are viewed over
 */
class ReadsTrack(recs: List[(ReferenceRegion, AlignmentRecord)], sampOpt: Option[String], reference: Option[String], region: ReferenceRegion) extends Track[AlignmentRecord] {

  val sample = sampOpt.get
  val records = recs
  val matePairs: List[MatePair] = getMatePairs
  val misMatches: List[MisMatch] = getMisMatches

  getMisMatches

  def getMatePairs(): List[MatePair] = {
    val pairs = records.groupBy(_._2.readName).filter(_._2.size == 2).map(_._2)
    val nonOverlap = pairs.filter(r => !(r(0)._1.overlaps(r(1)._1)))
    nonOverlap.map(p => MatePair(p.map(_._1.end).min, p.map(_._1.start).max)).toList
  }

  def getMisMatches: List[MisMatch] = {
    reference match {
      case Some(_) => {
        records.flatMap(r => MismatchLayout(r._2, reference.get, region))
      } case None => {
        List[MisMatch]()
      }
    }
  }

}

/**
 * Extension of TrackBuffer to support all but AlignmentRecord Data
 *
 * @param recs: List of (ReferenceRegion, T) tuples in a ReadsTrack
 * @tparam T: Genomic type
 */
case class GenericTrackBuffer[T: ClassTag](recs: List[(ReferenceRegion, T)]) extends TrackBuffer[T] {
  records ++= recs
  def this(rec: (ReferenceRegion, T)) {
    this(List(rec))
  }

  def conflicts(recs: Seq[(ReferenceRegion, T)]): Boolean =
    records.exists(r => TrackedLayout.overlaps(r, recs))

}

/**
 * Extension of TrackBuffer to support AlignmentRecord Data
 *
 * @param recs: List of (ReferenceRegion, AlignmentRecord) tuples in a ReadsTrackBuffer
 */
case class ReadsTrackBuffer(recs: List[(ReferenceRegion, AlignmentRecord)]) extends TrackBuffer[AlignmentRecord] {
  records ++= recs
  val sample: Option[String] = Option(records.head._2.recordGroupSample)

  def conflicts(recs: Seq[(ReferenceRegion, AlignmentRecord)]): Boolean = {
    assert(sample != None)
    val start = recs.map(rec => rec._1.start).min
    val end = recs.map(rec => rec._1.end).max
    val groupedSample = recs.head._2.recordGroupSample
    val tempRegion = new ReferenceRegion(recs.head._1.referenceName, start, end)

    val pairs = records.toList.groupBy(_._2.readName).map(_._2)
    val aggregatedPairs = pairs.map(p => ReferenceRegion(p.head._1.referenceName, p.map(rec => rec._1.start).min, p.map(rec => rec._1.end).max))
    sample.get != groupedSample || aggregatedPairs.exists(r => r.overlaps(tempRegion))
  }
}
