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
import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.formats.avro.{ AlignmentRecord, Feature, Genotype, GenotypeAllele, NucleotideContigFragment }
import scala.collection.mutable
import scala.util.control.Breaks._
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
trait TrackedLayout[T] {
  def trackAssignments: List[((ReferenceRegion, T), Int)]
}

class GroupPair(val start: Long, val end: Long) {
}

object TrackedLayout {

  def overlaps[T](rec1: (ReferenceRegion, T), rec2: (ReferenceRegion, T)): Boolean = {
    val ref1 = rec1._1
    val ref2 = rec2._1
    ref1.overlaps(ref2)
  }

  def overlaps[T](rec1: (ReferenceRegion, T), recs2: Seq[(ReferenceRegion, T)]): Boolean = {
    val ref1 = rec1._1
    val recs2List = recs2.toList
    val start: Long = recs2List.min(Ordering.by((r: (ReferenceRegion, T)) => r._1.start))._1.start
    val end: Long = recs2List.max(Ordering.by((r: (ReferenceRegion, T)) => r._1.end))._1.end
    ref1.overlaps(new ReferenceRegion(recs2.last._1.referenceName, start, end))
  }

  def overlapsPair[T](rec1: GroupPair, recs2: Seq[(ReferenceRegion, T)]): Boolean = {
    val ref1 = new ReferenceRegion(recs2.last._1.referenceName, rec1.start, rec1.end)
    recs2.foreach {
      rec =>
        {
          val ref2 = rec._1
          if (ref1.overlaps(ref2))
            return true
        }
    }
    return false
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
class OrderedTrackedLayout[T: ClassTag](values: Traversable[(ReferenceRegion, T)]) extends TrackedLayout[T] with Logging {
  var trackBuilder = new mutable.ListBuffer[Track]()
  val sequence = values.toSeq
  var numTracks: Int = 0
  log.info("Number of values: " + values.size)

  TrackTimers.FindAddTimer.time {
    sequence match {
      case a: Seq[(ReferenceRegion, AlignmentRecord)] if classTag[T] == classTag[AlignmentRecord] => {
        val readPairs: Map[String, Seq[(ReferenceRegion, T)]] = a.groupBy(_._2.readName)
        findPairsAndAddToTrack(readPairs)
      }
      case f: Seq[(ReferenceRegion, Feature)] if classTag[T] == classTag[Feature] => {
        f.foreach(findAndAddToTrack)
      }
      case g: Seq[(ReferenceRegion, Genotype)] if classTag[T] == classTag[Genotype] => {
        addVariantsToTrack(g.groupBy(_._1))
      }
      case _ => {
        log.info("No matched types")
      }
    }
  }

  trackBuilder = trackBuilder.filter(_.records.nonEmpty)

  log.info("Number of tracks: " + numTracks)
  val trackAssignments: List[((ReferenceRegion, T), Int)] = TrackTimers.TrackAssignmentsTimer.time {
    val zippedWithIndex = trackBuilder.toList
    val flatMapped: List[((ReferenceRegion, T), Int)] = zippedWithIndex.flatMap {
      case (track: Track) => track.records.map(_ -> track.idx)
    }
    flatMapped
  }

  private def addVariantsToTrack(sites: Map[ReferenceRegion, Seq[(ReferenceRegion, T)]]) {
    for (site <- sites) {
      val trackBuilderIterator = trackBuilder.toIterator
      for (rec <- site._2) {
        if (!trackBuilderIterator.hasNext) {
          addTrack(new Track(rec))
        } else {
          val track = trackBuilderIterator.next
          track += rec
        }
      }
    }
  }

  private def findPairsAndAddToTrack(readPairs: Map[String, Seq[(ReferenceRegion, T)]]) {
    readPairs.foreach {
      p =>
        {
          val recs: Seq[(ReferenceRegion, T)] = p._2
          val trackOption: Option[Track] = TrackTimers.FindConflict.time {
            trackBuilder.find(track => !track.conflicts(recs))
          }
          val track: Track = trackOption.getOrElse(addTrack(new Track()))

          recs.foreach {
            rec: (ReferenceRegion, T) =>
              {
                if (!track.conflicts(rec)) {
                  track += rec
                  trackBuilder -= track
                  trackBuilder += track
                } else {
                  addTrack(new Track(rec))
                }
              }
          }
          track.addGroupPair(recs)
        }
    }
  }

  private def findAndAddToTrack(rec: (ReferenceRegion, T)) {
    val reg = rec._1
    if (reg != null) {
      val track: Option[Track] = TrackTimers.FindConflict.time {
        trackBuilder.find(track => !track.conflicts(rec))
      }
      track.map(trackval => {
        trackval += rec
        trackBuilder -= trackval
        trackBuilder += trackval
      }).getOrElse(addTrack(new Track(rec)))
    }
  }

  private def addTrack(t: Track): Track = {
    numTracks += 1
    trackBuilder += t
    t
  }

  class Track(val initial: (ReferenceRegion, T)) {

    val records = new mutable.ListBuffer[(ReferenceRegion, T)]()
    val groupPairs = new mutable.ListBuffer[GroupPair]()
    val idx: Int = numTracks

    if (initial != null)
      records += initial

    def this() {
      this(null)
    }

    def +=(rec: (ReferenceRegion, T)): Track = {
      records += rec
      this
    }

    def addGroupPair(recs: Seq[(ReferenceRegion, T)]) {
      if (recs.size < 2)
        return
      else if (recs.size == 2)
        createGroupPair(recs(0), recs(1))
      else
        log.info("Warning: Group Pairs do not support > 2 pairs per read")
    }

    def createGroupPair(rec1: (ReferenceRegion, T), rec2: (ReferenceRegion, T)) {
      val start: Long = math.min(rec1._1.end, rec2._1.end)
      val end: Long = math.max(rec1._1.start, rec2._1.start)

      if (start < end) {
        groupPairs += new GroupPair(start, end)
      }
    }

    def conflicts(rec: (ReferenceRegion, T)): Boolean =
      records.exists(r => TrackedLayout.overlaps(r, rec))

    def conflicts(recs: Seq[(ReferenceRegion, T)]): Boolean = {
      val rConflict: Boolean = records.exists(r => TrackedLayout.overlaps(r, recs))
      val gConflict: Boolean = groupPairs.exists(g => TrackedLayout.overlapsPair(g, recs))
      return (rConflict || gConflict)
    }
  }

}
