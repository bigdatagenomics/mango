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

package org.bdgenomics.mango.io

import java.io.File
import java.net.HttpURLConnection
import org.bdgenomics.mango.io.HttpDownloadUtility
import org.bdgenomics.mango.util.MangoFunSuite

class HttpDownloadUtilitySuite extends MangoFunSuite {

  val sampleURL = "https://bdg-mango.s3-us-west-2.amazonaws.com/install-bdg-mango-dist-emr5.sh"
  val fakeURL = "https://bdg-mango.s3-us-west-2.amazonaws.com/fakeFile.txt"

  test("Returns valid response code for file check") {
    val responseCode = HttpDownloadUtility.checkFile(sampleURL)
    assert(responseCode == HttpURLConnection.HTTP_OK)
  }

  test("Returns valid response code for file download") {
    val out = File.createTempFile("fake-", ".sh").toPath.toString

    val responseCode = HttpDownloadUtility.downloadFile(sampleURL, out)
    assert(responseCode == HttpURLConnection.HTTP_OK)
    assert(new java.io.File(out).isFile)
  }

  test("Fails file check on invalid response code") {
    val responseCode = HttpDownloadUtility.checkFile(fakeURL)
    assert(responseCode == HttpURLConnection.HTTP_FORBIDDEN)
  }

  test("Fails file download on invalid response code") {
    val out = File.createTempFile("fake-", ".txt").toPath.toString

    val responseCode = HttpDownloadUtility.downloadFile(fakeURL, out)
    assert(responseCode == HttpURLConnection.HTTP_FORBIDDEN)
  }

}
