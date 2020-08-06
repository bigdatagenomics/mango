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

import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * A utility that downloads a file from a URL.
 *
 * Modified from https://www.codejava.net/
 *
 */
object HttpDownloadUtility {
  private val BUFFER_SIZE = 4096

  /**
   * Downloads a file from a URL
   *
   * @param fileURL HTTP URL of the file to be downloaded
   * @param saveFilePath path of the directory to save the file
   * @throws IOException
   */
  @throws[IOException]
  def downloadFile(fileURL: String, saveFilePath: String): Int = {
    val url = new URL(fileURL)
    val httpConn = url.openConnection.asInstanceOf[HttpURLConnection]
    val responseCode = httpConn.getResponseCode
    // always check HTTP response code first
    if (responseCode == HttpURLConnection.HTTP_OK) {

      // opens input stream from the HTTP connection
      val inputStream = httpConn.getInputStream

      // opens an output stream to save into file
      val outputStream = new FileOutputStream(saveFilePath)
      val buffer = new Array[Byte](BUFFER_SIZE)

      var bytesRead: Int = inputStream.read(buffer)

      while (bytesRead != -1) {
        outputStream.write(buffer, 0, bytesRead)
        bytesRead = inputStream.read(buffer)
      }

      outputStream.close()
      inputStream.close()
    }
    httpConn.disconnect()
    return responseCode

  }

  def checkFile(fileUrl: String): Int = {
    val url = new URL(fileUrl)
    val httpConn = url.openConnection.asInstanceOf[HttpURLConnection]
    val responseCode = httpConn.getResponseCode
    httpConn.disconnect()
    return responseCode
  }
}
