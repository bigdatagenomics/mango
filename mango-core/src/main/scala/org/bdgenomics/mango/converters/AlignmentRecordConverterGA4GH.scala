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
package org.bdgenomics.mango.converters

import java.lang.Boolean
import java.util

import com.google.protobuf.ListValue

import htsjdk.samtools.{ ValidationStringency, CigarOperator, TextCigarCodec }
import org.bdgenomics.adam.models.VariantContext
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.formats.avro.{ AlignmentRecord, Genotype, GenotypeAllele, Feature }
import org.bdgenomics.utils.misc.Logging

import org.ga4gh._
import ga4gh.Variants.Call
import ga4gh.{ Common, Variants }
import scala.collection.JavaConverters._
import scala.collection.JavaConversions._

/**
 * Created by paschalj on 6/20/17.
 */
object AlignmentRecordConverterGA4GH extends Serializable with Logging {

  private[converters] val placeholder = "N/A"

  private[converters] def convertCigar(cigarString: java.lang.String): List[ga4gh.Reads.CigarUnit] = {
    if (cigarString == null) {
      List.empty[ga4gh.Reads.CigarUnit]
    } else {
      // convert to a samtools cigar
      val cigar = TextCigarCodec.decode(cigarString)

      // loop and build operators
      cigar.getCigarElements.asScala.map(element => {

        // can has cigar unit builder, plz?
        val cuBuilder = ga4gh.Reads.CigarUnit.newBuilder()

        // add length
        cuBuilder.setOperationLength(element.getLength)

        // set the operation
        cuBuilder.setOperation(element.getOperator match {
          case CigarOperator.M  => ga4gh.Reads.CigarUnit.Operation.ALIGNMENT_MATCH
          case CigarOperator.I  => ga4gh.Reads.CigarUnit.Operation.INSERT
          case CigarOperator.D  => ga4gh.Reads.CigarUnit.Operation.DELETE
          case CigarOperator.N  => ga4gh.Reads.CigarUnit.Operation.SKIP
          case CigarOperator.S  => ga4gh.Reads.CigarUnit.Operation.CLIP_SOFT
          case CigarOperator.H  => ga4gh.Reads.CigarUnit.Operation.CLIP_HARD
          case CigarOperator.P  => ga4gh.Reads.CigarUnit.Operation.PAD
          case CigarOperator.EQ => ga4gh.Reads.CigarUnit.Operation.SEQUENCE_MATCH
          case CigarOperator.X  => ga4gh.Reads.CigarUnit.Operation.SEQUENCE_MISMATCH
        })

        // build and return
        cuBuilder.build()
      }).toList
    }
  }

  def toGAReadAlignmentPB(record: AlignmentRecord): ga4gh.Reads.ReadAlignment = {

    val builder = ga4gh.Reads.ReadAlignment.newBuilder()

    // id needs to be nulled out
    builder.setId(null)

    // read must have a read group
    val rgName = Option(record.getRecordGroupName)
    require(rgName.isDefined,
      "Read %s does not have a read group attached.".format(record))
    rgName.foreach(builder.setReadGroupId)

    // read must have a name
    val readName = Option(record.getReadName)
    require(readName.isDefined,
      "Read %s does not have a read name attached.".format(record))
    readName.foreach(builder.setFragmentName)

    // set alignment flags
    builder.setImproperPlacement(!record.getProperPair)
    builder.setDuplicateFragment(record.getDuplicateRead)
    builder.setFailedVendorQualityChecks(record.getFailedVendorQualityChecks)
    builder.setSecondaryAlignment(record.getSecondaryAlignment)
    builder.setSupplementaryAlignment(record.getSupplementaryAlignment)
    if (record.getMateContigName != null)
      builder.setNextMatePosition(ga4gh.Common.Position.newBuilder.setReferenceName(record.getMateContigName)
        .setPosition(record.getMateAlignmentStart)
        .setStrand(if (record.getMateNegativeStrand) Common.Strand.NEG_STRAND else Common.Strand.POS_STRAND)
        .build())
    // we don't store the number of reads in a fragment; assume 2 if paired, 1 if not
    val paired: Boolean = Option(record.getReadPaired)
      .map(b => b: Boolean)
      .getOrElse(false)
    val numReads = if (paired) 2 else 1
    builder.setNumberReads(numReads)

    // however, we do store the read number
    builder.setReadNumber(record.getReadInFragment)

    // set fragment length
    Option(record.getInferredInsertSize)
      .map(l => l.intValue: java.lang.Integer)
      .foreach((x) => builder.setFragmentLength(x.toInt))

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
    builder.addAllAlignedQuality(qualArray)

    // if the read is aligned, we must build a linear alignment
    Option(record.getReadMapped)
      .filter(mapped => mapped)
      .foreach(isMapped => {

        // get us a builder
        val laBuilder = ga4gh.Reads.LinearAlignment.newBuilder()

        // get values from the ADAM record
        val start = record.getStart
        val contig = record.getContigName
        val reverse: Boolean = record.getReadNegativeStrand

        // check that they are not null
        require(start != null && contig != null && reverse != null,
          "Alignment start/contig/strand bad in %s.".format(record))

        // set position
        laBuilder.setPosition(ga4gh.Common.Position.newBuilder()
          .setReferenceName(contig)
          .setPosition(start.intValue)
          .setStrand(if (reverse) Common.Strand.NEG_STRAND else Common.Strand.POS_STRAND)
          .build())

        // set mapq
        laBuilder.setMappingQuality(record.getMapq)

        // convert cigar
        laBuilder.addAllCigar(convertCigar(record.getCigar))

        // build and attach
        builder.setAlignment(laBuilder.build)
      })

    // fin! we skip the info tags for now.
    builder.build()
  }

}
