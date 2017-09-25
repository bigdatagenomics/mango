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
package org.bdgenomics.mango.converters.ga4gh

import htsjdk.samtools.{ ValidationStringency, CigarOperator, TextCigarCodec }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.formats.avro.AlignmentRecord
import org.bdgenomics.utils.misc.Logging
import org.ga4gh._
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

object GA4GHConverter extends Serializable with Logging {

  private[converters] val placeholder = "N/A"

  private[converters] def convertCigar(cigarString: java.lang.String): List[GACigarUnit] = {
    if (cigarString == null) {
      List.empty[GACigarUnit]
    } else {
      // convert to a samtools cigar
      val cigar = TextCigarCodec.decode(cigarString)

      // loop and build operators
      cigar.getCigarElements.asScala.map(element => {

        // can has cigar unit builder, plz?
        val cuBuilder = GACigarUnit.newBuilder()

        // add length
        cuBuilder.setOperationLength(element.getLength)

        // set the operation
        cuBuilder.setOperation(element.getOperator match {
          case CigarOperator.M  => GACigarOperation.ALIGNMENT_MATCH
          case CigarOperator.I  => GACigarOperation.INSERT
          case CigarOperator.D  => GACigarOperation.DELETE
          case CigarOperator.N  => GACigarOperation.SKIP
          case CigarOperator.S  => GACigarOperation.CLIP_SOFT
          case CigarOperator.H  => GACigarOperation.CLIP_HARD
          case CigarOperator.P  => GACigarOperation.PAD
          case CigarOperator.EQ => GACigarOperation.SEQUENCE_MATCH
          case CigarOperator.X  => GACigarOperation.SEQUENCE_MISMATCH
        })

        // build and return
        cuBuilder.build()
      }).toList
    }
  }

  def toGAReadAlignment(record: AlignmentRecord, stringency: ValidationStringency = ValidationStringency.LENIENT): GAReadAlignment = {

    val builder = GAReadAlignment.newBuilder()

    // id needs to be nulled out
    builder.setId(null)

    // read group
    val rgName = Option(record.getRecordGroupName)
    stringency match {
      case ValidationStringency.STRICT =>
        require(rgName.isDefined,
          "Read %s does not have a read group attached.".format(record))
      case ValidationStringency.LENIENT =>
        log.warn("Read %s does not have a read group attached.".format(record))
      case _ => // no op
    }
    builder.setReadGroupId(rgName.getOrElse(placeholder))

    // read must have a name
    val readName = Option(record.getReadName)
    stringency match {
      case ValidationStringency.STRICT =>
        require(readName.isDefined,
          "Read %s does not have a read name attached.".format(record))
      case ValidationStringency.LENIENT =>
        log.warn("Read %s does not have a read name attached.".format(record))
      case _ => // no op
    }
    builder.setFragmentName(readName.getOrElse(placeholder))

    // set alignment flags
    builder.setProperPlacement(record.getProperPair)
    builder.setDuplicateFragment(record.getDuplicateRead)
    builder.setFailedVendorQualityChecks(record.getFailedVendorQualityChecks)
    builder.setSecondaryAlignment(record.getSecondaryAlignment)
    builder.setSupplementaryAlignment(record.getSupplementaryAlignment)
    if (record.getMateContigName != null)
      builder.setNextMatePosition(new GAPosition(record.getMateContigName, record.getMateAlignmentStart, record.getMateNegativeStrand))
    // we don't store the number of reads in a fragment; assume 2 if paired, 1 if not
    val paired = Option(record.getReadPaired)
      .map(b => b: Boolean)
      .getOrElse(false)
    val numReads = if (paired) 2 else 1
    builder.setNumberReads(numReads)

    // however, we do store the read number
    builder.setReadNumber(record.getReadInFragment)

    // set fragment length
    Option(record.getInferredInsertSize)
      .map(l => l.intValue: java.lang.Integer)
      .foreach(builder.setFragmentLength)

    // set sequence
    builder.setAlignedSequence(record.getSequence)

    // qual is an array<int> in ga4ghland, so convert
    // note: avro array<int> --> java.util.List[java.lang.Integer]
    val qualArray = Option(record.getQual)
      .map(qual => {
        qual.toList
          .map(c => c.toInt - 33)
      }).getOrElse(List.empty[Int])
      .map(i => i: java.lang.Integer)
    builder.setAlignedQuality(qualArray)

    // if the read is aligned, we must build a linear alignment
    Option(record.getReadMapped)
      .filter(mapped => mapped)
      .foreach(isMapped => {

        // get us a builder
        val laBuilder = GALinearAlignment.newBuilder()

        // get values from the ADAM record
        val start = record.getStart
        val contig = record.getContigName
        val reverse = record.getReadNegativeStrand

        // check that they are not null
        require(start != null && contig != null && reverse != null,
          "Alignment start/contig/strand bad in %s.".format(record))

        // set position
        laBuilder.setPosition(GAPosition.newBuilder()
          .setReferenceName(contig)
          .setPosition(start.intValue)
          .setReverseStrand(reverse)
          .build())

        // set mapq
        laBuilder.setMappingQuality(record.getMapq)

        // convert cigar
        laBuilder.setCigar(convertCigar(record.getCigar))

        // build and attach
        builder.setAlignment(laBuilder.build)
      })

    // fin! we skip the info tags for now.
    builder.build()
  }
}

