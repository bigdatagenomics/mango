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
package utils

import org.bdgenomics.adam.models.{ SequenceRecord, SequenceDictionary }
import play.api.libs.json._

/**
 * Holds all implicit conversion that will be used by play.api.libs.json. These
 * are used for Read and Write to convert objects to and from play json
 */
object JsonImplicits {

  // implicit writer for SequenceDictionary
  implicit val sdWrites = new Writes[SequenceDictionary] {
    def writes(sd: SequenceDictionary) = {
      val records = sd.records.sortBy(_.length).reverse.map(r => Json.obj(
        "name" -> r.name,
        "length" -> r.length))
      records.foldLeft(JsArray())((acc, x) => acc ++ Json.arr(x))
    }
  }

  // implicit reader for SequenceDictionary
  implicit val sdReads = new Reads[SequenceDictionary] {
    def reads(json: JsValue): JsResult[SequenceDictionary] = {
      val jsArr: JsArray = json.as[JsArray]
      val records = jsArr.value.map(r => {
        val name = (r \ "name").as[String]
        val length = (r \ "length").as[Long]
        SequenceRecord(name, length)
      })
      JsSuccess(new SequenceDictionary(records.toVector))
    }
  }
}