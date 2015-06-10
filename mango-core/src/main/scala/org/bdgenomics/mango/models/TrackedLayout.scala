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
import scala.collection.mutable

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
  def numTracks: Int
  def trackAssignments: Map[(ReferenceRegion, T), Int]
}

object TrackedLayout {

  def overlaps[T](rec1: (ReferenceRegion, T), rec2: (ReferenceRegion, T)): Boolean = {
    val ref1 = rec1._1
    val ref2 = rec2._1
    ref1.overlaps(ref2)
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
class OrderedTrackedLayout[T](values: Traversable[(ReferenceRegion, T)]) extends TrackedLayout[T] {
  private var trackBuilder = new mutable.ListBuffer[Track]()
  values.toSeq.foreach(findAndAddToTrack)
  val endfaatt = System.currentTimeMillis
  trackBuilder = trackBuilder.filter(_.records.nonEmpty)

  val numTracks = trackBuilder.size
  val trackAssignments: Map[(ReferenceRegion, T), Int] =
    Map(trackBuilder.toList.zip(0 to numTracks).flatMap {
      case (track: Track, idx: Int) => track.records.map(_ -> idx)
    }: _*)

  private def findAndAddToTrack(rec: (ReferenceRegion, T)) {
    val reg = rec._1
    if (reg != null) {
      val track: Option[Track] = trackBuilder.find(track => !track.conflicts(rec))
      track.map(trackval => {
        trackval += rec
        trackBuilder -= trackval
        trackBuilder += trackval
      }).getOrElse(addTrack(new Track(rec)))
    }

  }

  private def addTrack(t: Track): Track = {
    trackBuilder += t
    t
  }

  private class Track(val initial: (ReferenceRegion, T)) {

    val records = new mutable.ListBuffer[(ReferenceRegion, T)]()
    records += initial

    def +=(rec: (ReferenceRegion, T)): Track = {
      records += rec
      this
    }

    def conflicts(rec: (ReferenceRegion, T)): Boolean =
      records.exists(r => TrackedLayout.overlaps(r, rec))
  }

}
