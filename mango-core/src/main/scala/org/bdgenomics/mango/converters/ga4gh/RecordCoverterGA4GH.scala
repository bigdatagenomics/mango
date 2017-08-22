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

import java.lang.Boolean
import java.util

import com.google.protobuf.ListValue
import htsjdk.samtools.{ CigarOperator, TextCigarCodec, ValidationStringency }
import org.bdgenomics.adam.models.VariantContext
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.formats.avro.{ AlignmentRecord, Feature, Genotype, GenotypeAllele }
import org.bdgenomics.utils.misc.Logging
import org.ga4gh._
import ga4gh.Variants.Call
import ga4gh.{ Common, Variants }
import ga4gh.Reads

import scala.collection.JavaConverters._
import scala.collection.JavaConversions._
import scala.reflect.ClassTag

/**
 * Created by paschalj on 6/20/17.
 */
abstract class RecordConverterGA4GH[T: ClassTag, S: ClassTag] extends Serializable with Logging {

  def bdgToGA4GH(record: T): S

  def ga4ghSeqtoJSON(gaReads: Seq[S]): String

}
