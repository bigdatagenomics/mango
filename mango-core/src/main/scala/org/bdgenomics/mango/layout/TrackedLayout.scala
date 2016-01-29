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

object TrackTimers extends Metrics {
  val FindAddTimer = timer("Find and Add to Track")
  val FindConflict = timer("Finds first nonconflicting track")
  val TrackAssignmentsTimer = timer("Generate track Assignments")
  val PlusRec = timer("Plus Rec")
  val MinusTrack = timer("Minus Track")
  val PlusTrack = timer("Plus Track")
  val LoopTimer = timer("Loop Timer")
  val GetIndexTimer = timer("Get Index Timer")
  val ConflictTimer = timer("Conflict Timer")
}
/**
 * A TrackedLayout is an assignment of values of some type T (which presumably are mappable to
 * a reference genome or other linear coordinate space) to 'tracks' -- that is, to integers,
 * with the guarantee that no two values assigned to the same track will overlap.
 *
 * This is the kind of data structure which is required for non-overlapping genome visualization.
 *
 * @tparam T the type of value which is to be tracked.
 */

object TrackedLayout {

  def apply[T: ClassTag](values: Iterator[(ReferenceRegion, T)]): Iterator[Track[T]] = {
    new TrackedLayout[T](values).trackAssignments
  }

  def overlaps[T](rec1: (ReferenceRegion, T), rec2: (ReferenceRegion, T)): Boolean = {
    val ref1 = rec1._1
    val ref2 = rec2._1
    ref1.overlaps(ref2)
  }

  def overlaps[T](rec1: (ReferenceRegion, T), recs2: Seq[(ReferenceRegion, T)]): Boolean = {
    val ref1 = rec1._1
    if (recs2.size == 1)
      ref1.overlaps(recs2.head._1)
    else {
      val hull: ReferenceRegion = recs2.map(_._1).reduce((v1, v2) => v1.hull(v2))
      ref1.overlaps(hull)
    }
  }
}

/**
 * An implementation of TrackedLayout which takes a sequence of tuples of Reference Region and an input type,
 * and lays them out <i>in order</i> (i.e. from first-to-last).
 * Tracks are laid out by finding the first track in a buffer that does not conflict with
 * an input ReferenceRegion. After a ReferenceRegion is added to a track, the track is
 * put at the end of the buffer in an effort to make subsequent searches faster.
 *
 * @param values The set of values (i.e. reads, variants) to lay out in tracks
 * @tparam T the type of value which is to be tracked.
 */
class TrackedLayout[T: ClassTag](values: Iterator[(ReferenceRegion, T)]) extends Logging {
  val sequence = values.toList
  var trackBuilder = new mutable.ListBuffer[TrackBuffer[T]]()

  TrackTimers.FindAddTimer.time {
    sequence match {
      case a: List[(ReferenceRegion, AlignmentRecord)] if classTag[T] == classTag[AlignmentRecord] => {
        val readPairs: Map[String, List[(ReferenceRegion, T)]] = a.groupBy(_._2.readName)
        findPairsAndAddToTrack(readPairs)
      }
      case f: List[(ReferenceRegion, Feature)] if classTag[T] == classTag[Feature] => {
        f.foreach(findAndAddToTrack)
      }
      case g: List[(ReferenceRegion, Genotype)] if classTag[T] == classTag[Genotype] => {
        val data = g.groupBy(_._2.sampleId)
        addVariantsToTrack(data)
      }
      case _ => {
        log.info("No matched types")
      }
    }
  }

  trackBuilder = trackBuilder.filter(_.records.nonEmpty)
  val trackAssignments: Iterator[Track[T]] =
    trackBuilder.map(t => Track(t)).toIterator

  private def addVariantsToTrack(recs: Map[String, List[(ReferenceRegion, T)]]) {
    for (rec <- recs) {
      trackBuilder += TrackBuffer[T](rec._2)
    }
  }

  private def findPairsAndAddToTrack(readPairs: Map[String, List[(ReferenceRegion, T)]]) {
    readPairs.foreach {
      p =>
        {
          val recs: List[(ReferenceRegion, T)] = p._2
          val trackOption: Option[TrackBuffer[T]] = TrackTimers.FindConflict.time {
            trackBuilder.find(track => !track.conflictsPair(recs))
          }
          val track: TrackBuffer[T] = trackOption.getOrElse(addTrack(new TrackBuffer[T](recs)))
        }
    }
  }

  private def findAndAddToTrack(rec: (ReferenceRegion, T)) {
    val reg = rec._1
    if (reg != null) {
      val track: Option[TrackBuffer[T]] = TrackTimers.FindConflict.time {
        trackBuilder.find(track => !track.conflicts(rec))
      }
      track.map(trackval => {
        trackval += rec
        trackBuilder -= trackval
        trackBuilder += trackval
      }).getOrElse(addTrack(new TrackBuffer[T](rec)))
    }
  }

  private def addTrack(t: TrackBuffer[T]): TrackBuffer[T] = {
    trackBuilder += t
    t
  }
}

object Track {
  def apply[T: ClassTag](trackBuffer: TrackBuffer[T]): Track[T] = {
    new Track[T](trackBuffer.records.toList)
  }
}

case class Track[T](records: List[(ReferenceRegion, T)])

case class TrackBuffer[T: ClassTag](val recs: List[(ReferenceRegion, T)]) extends Logging {

  val records = new mutable.ListBuffer[(ReferenceRegion, T)]()
  records ++= recs

  def this(rec: (ReferenceRegion, T)) {
    this(List(rec))
  }

  def +=(rec: (ReferenceRegion, T)): TrackBuffer[T] = {
    records += rec
    this
  }

  def conflicts(rec: (ReferenceRegion, T)): Boolean =
    records.exists(r => TrackedLayout.overlaps(r, rec))

  // check if there are conflicting records in all tracks which would conflict
  // with both records and their mate pairs. this is used for AlignmentRecord data
  def conflictsPair(recs: Seq[(ReferenceRegion, T)]): Boolean = {
    val start = recs.map(rec => rec._1.start).min
    val end = recs.map(rec => rec._1.end).max
    val tempRegion = new ReferenceRegion(recs.head._1.referenceName, start, end)
    records.exists(r => r._1.overlaps(tempRegion))
  }

}
