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
package org.bdgenomics.mango.serialization

import com.esotericsoftware.kryo.Kryo
import org.apache.spark.serializer.KryoRegistrator
import org.bdgenomics.adam.models._
import org.bdgenomics.adam.rdd.read.realignment._
import org.bdgenomics.adam.serialization._
import org.bdgenomics.adam.util.{ TwoBitFile, TwoBitFileSerializer }
import org.bdgenomics.formats.avro._

import scala.collection.mutable.{ ArrayBuffer, ListBuffer }

class MangoKryoRegistrator extends ADAMKryoRegistrator {
  override def registerClasses(kryo: Kryo): Unit = {
    kryo.register(classOf[ListBuffer[AlignmentRecord]])
    kryo.register(classOf[ArrayBuffer[AlignmentRecord]])
    kryo.register(classOf[ListBuffer[Genotype]])
    kryo.register(classOf[ArrayBuffer[Genotype]])
    kryo.register(classOf[List[Sample]])
    kryo.register(classOf[AlignmentRecord], new AvroSerializer[AlignmentRecord]())
    kryo.register(classOf[org.bdgenomics.adam.models.VariantContext],
      new org.bdgenomics.adam.models.VariantContextSerializer)
    kryo.register(classOf[org.bdgenomics.formats.avro.Sample], new AvroSerializer[org.bdgenomics.formats.avro.Sample]())
    kryo.register(classOf[Genotype], new AvroSerializer[Genotype]())
    kryo.register(classOf[Variant], new AvroSerializer[Variant]())
    kryo.register(classOf[Reference], new AvroSerializer[Reference]())
    kryo.register(classOf[Dbxref], new AvroSerializer[Dbxref]())
    kryo.register(classOf[Feature], new AvroSerializer[Feature]())
    kryo.register(classOf[ReferencePosition], new ReferencePositionSerializer)
    kryo.register(classOf[TwoBitFile], new TwoBitFileSerializer)

    // scala
    kryo.register(classOf[scala.Array[org.bdgenomics.formats.avro.Sample]])
    kryo.register(classOf[scala.List[org.bdgenomics.formats.avro.Sample]])
  }
}
