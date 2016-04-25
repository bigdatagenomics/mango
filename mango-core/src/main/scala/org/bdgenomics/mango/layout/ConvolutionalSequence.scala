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

import org.apache.spark.Logging
import org.apache.spark.rdd.RDD
import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.formats.avro.AlignmentRecord

object ConvolutionalSequence extends Serializable with Logging {

  /*
   * Convolves string of text into an array of patch summations
   *
   * @param sequence: String to convolve over
   * @param patchSize: Int
   * @stride: Int
   *
   * @return Array[Double] Convolutional results
   */
  def convolveSequence(sequence: String, patchSize: Int, stride: Int): Array[Double] = {
    val seqArray: Array[Char] = sequence.toCharArray
    getConvolutionIndices(sequence.length, patchSize, stride).map(
      i => averageChars(seqArray.slice(i, i + patchSize)))
  }

  /*
   * Convolves an RDD of alignment records and calculates the diff compared to a reference
   *
   * @param region: ReferenceRegion over original reference string
   * @param reference: String to convolved and compared to
   * @param alignments: RDD[AlignmentRecord] to convolve
   *
   * @return: Array[Double] total diff of AlignmentRecords compared to region
   */
  def convolveRDD(region: ReferenceRegion, reference: String, alignments: RDD[AlignmentRecord], patchSize: Int, stride: Int): Array[Double] = {
    val convolvedReference = convolveSequence(reference.toUpperCase(), patchSize, stride)
    val x: RDD[Array[Double]] = alignments.map(r => convolveAlignmentRecord(region,
      convolvedReference,
      r,
      patchSize,
      stride))

    val k = x.reduce((x, y) => (x, y).zipped.map(_ + _))
    k
  }

  /*
   * Convolves an single alignment record to a convolved reference
   *
   * @param region: ReferenceRegion over original reference string
   * @param convolvedReference: Array[Double] Convolved reference to compare to
   * @param alignment: AlignmentRecord
   * @param patchSize: Size that convolvedReference was convolved over
   * @param stride: stride taht convolvedReference was convolved over
   *
   * @return: Array[Double] total diff of AlignmentRecords compared to region
   */
  def convolveAlignmentRecord(region: ReferenceRegion,
                              convolvedReference: Array[Double],
                              alignment: AlignmentRecord,
                              patchSize: Int,
                              stride: Int): Array[Double] = {
    val startSize = region.end - region.start

    // TODO: insert/delete based on contig
    val sequence =
      if (alignment.getCigar.contains("I")) {
        alignment.getSequence
      } else if (alignment.getCigar.contains("D")) {
        alignment.getSequence
      } else alignment.getSequence

    val convolvedAR = convolveSequence(sequence.toUpperCase, patchSize, stride)
    val temp: Array[Double] = convolvedReference.clone()

    val start: Int = Math.max(0, Math.floor((alignment.getStart - region.start) / stride).toInt)
    val end: Int = Math.min(start + convolvedAR.length, temp.length - 1)

    Array.range(start, end).map(i => { temp(i) = convolvedAR(i - start) })
    compareConvolutions(convolvedReference, temp)
  }

  private def compareConvolutions(sequence1: Array[Double],
                                  sequence2: Array[Double]): Array[Double] = {
    assert(sequence1.length == sequence2.length)
    sequence1.zip(sequence2).map { case (a, b) => Math.abs(a - b) }
  }

  def getPatchSize(startSize: Int, finalSize: Int, strideOpt: Option[Int] = None): (Int, Int) = {
    val stride =
      strideOpt match {
        case Some(_) => strideOpt.get
        case None =>
          if (startSize < 100000) 3
          else if (startSize < 1000000) 5
          else 8
      }
    val ps = startSize - (finalSize - 1) * stride
    (ps, stride)
  }

  /*
   * Given a length of a sequence and convolution parameters, calculates an array
   * of indices to be mapped over during convolutiohn
   *
   * @param length: length of sequence
   * @param pathSize
   * @param stride
   *
   * @return array of indices to map
   */
  private def getConvolutionIndices(length: Int, patchSize: Int, stride: Int): Array[Int] = {
    Array.range(0, Math.max(1, length - patchSize - 1), stride)
  }

  /*
   * Aggregate functions for text (sum and average)
   */
  private def sumChars(ch: Array[Char]): Double = {
    ch.sum
  }

  private def averageChars(ch: Array[Char]): Double = {
    sumChars(ch) / ch.size
  }

}
