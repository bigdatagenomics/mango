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
import htsjdk.samtools.{ CigarOperator, TextCigarCodec, ValidationStringency }
import org.bdgenomics.adam.models.VariantContext
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.formats.avro._
import org.bdgenomics.utils.misc.Logging
import org.ga4gh._
import ga4gh.Variants.Call
import ga4gh.{ Common, Variants }

import scala.collection.JavaConverters._
import scala.collection.JavaConversions._

/**
 * Created by paschalj on 6/20/17.
 */
object FeatureConverterGA4GH extends Serializable with Logging {

  def toGA4GHFeature(record: Feature): ga4gh.SequenceAnnotations.Feature = {

    val x: Strand = record.getStrand

    def bdgStrandtoGA4GH(strand: Strand): ga4gh.Common.Strand = {
      if (strand == Strand.FORWARD) ga4gh.Common.Strand.POS_STRAND
      else if (strand == Strand.REVERSE) ga4gh.Common.Strand.NEG_STRAND
      else if (strand == Strand.UNKNOWN) ga4gh.Common.Strand.UNRECOGNIZED
      else ga4gh.Common.Strand.STRAND_UNSPECIFIED
    }

    def bdgFeatureTypeTermToGA4GH(featureType: String): ga4gh.Common.OntologyTerm = {
      ga4gh.Common.OntologyTerm.newBuilder().setTermId(featureType).build()
    }

    ga4gh.SequenceAnnotations.Feature.newBuilder()
      .setStart(record.getStart)
      .setEnd(record.getEnd)
      .setStrand(bdgStrandtoGA4GH(record.getStrand))
      .setReferenceName(record.getContigName)
      .setFeatureType(bdgFeatureTypeTermToGA4GH(record.getFeatureType))
      .setAttributes(Common.Attributes.newBuilder()
        .putAttr("Score", Common.AttributeValueList.newBuilder()
          .addValues(0, Common.AttributeValue.newBuilder().setInt64Value(record.getScore.toLong)).build()))
      .build()

  }

}
