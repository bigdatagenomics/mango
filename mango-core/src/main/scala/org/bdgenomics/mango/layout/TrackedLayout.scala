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

import edu.berkeley.cs.amplab.spark.intervalrdd._
import org.apache.spark.Logging
import org.apache.spark.rdd.RDD
import org.apache.spark.rdd.MetricsContext._
import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.formats.avro.{ AlignmentRecord, Feature, Genotype, GenotypeAllele, NucleotideContigFragment }
import org.bdgenomics.utils.instrumentation.Metrics
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.reflect.{ ClassTag, classTag }
import scala.util.control.Breaks._

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
 * Abstract TrackedLayout is an assignment of values of some type T to nonoverlapping tracks
 *
 *
 * @tparam T the type of value which is to be tracked.
 * @tparam U the TrackBuffer type to be created, dependent on T
 */
abstract class TrackedLayout[T, U <: TrackBuffer[T]] {

  def sequence: List[(ReferenceRegion, T)]
  def trackBuilder: ListBuffer[U]

  def addTracks

  def addTrack(t: U): U = {
    trackBuilder += t
    t
  }
}
