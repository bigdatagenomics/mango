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
package org.bdgenomics.mango.tiling

import java.io.File

import edu.berkeley.cs.amplab.spark.intervalrdd.IntervalRDD
import htsjdk.samtools.{ SAMRecord, SamReader, SamReaderFactory }
import net.liftweb.json.Serialization.write
import org.apache.spark.{ Logging, SparkContext }
import org.bdgenomics.adam.models.{ ReferenceRegion, SequenceDictionary }
import org.bdgenomics.mango.RDD.ReferenceRDD
import org.bdgenomics.mango.core.util.{ ResourceUtils, VizUtils }
import org.bdgenomics.mango.layout.{ CalculatedAlignmentRecord, ConvolutionalSequence, MergedAlignmentRecordLayout, MutationCount }
import org.bdgenomics.mango.models.AlignmentRecordMaterialization

class AlignmentRecordTile(sc: SparkContext,
                          dict: SequenceDictionary,
                          partitions: Int,
                          refRDD: ReferenceRDD) extends LayeredTile with Logging {

  val layer0 = AlignmentRecordMaterialization(sc, dict, partitions, refRDD)

  def init(readsPaths: List[String]): List[String] = {
    var sampNamesBuffer = new scala.collection.mutable.ListBuffer[String]
    for (readsPath <- readsPaths) {
      if (ResourceUtils.isLocal(readsPath, sc)) {
        if (readsPath.endsWith(".bam") || readsPath.endsWith(".sam")) {
          val srf: SamReaderFactory = SamReaderFactory.make()
          val samReader: SamReader = srf.open(new File(readsPath))
          val rec: SAMRecord = samReader.iterator().next()
          val sample = rec.getReadGroup.getSample
          sampNamesBuffer += sample
          layer0.loadSample(readsPath, Option(sample))
        } else if (readsPath.endsWith(".adam")) {
          sampNamesBuffer += layer0.loadADAMSample(readsPath)
        } else {
          log.info("WARNING: Invalid input for reads file on local fs")
          println("WARNING: Invalid input for reads file on local fs")
        }
      } else {
        if (readsPath.endsWith(".adam")) {
          sampNamesBuffer += layer0.loadADAMSample(readsPath)
        } else {
          log.info("WARNING: Invalid input for reads file on remote fs")
          println("WARNING: Invalid input for reads file on remote fs")
        }
      }
    }
    sampNamesBuffer.toList
  }

  def getL0(region: ReferenceRegion, ks: Option[List[String]]): String = {
    val dataOption: Option[IntervalRDD[ReferenceRegion, CalculatedAlignmentRecord]] = layer0.multiget(region, ks.get)
    dataOption match {
      case Some(_) => {
        //        val filteredData: RDD[(ReferenceRegion, CalculatedAlignmentRecord)] =
        //          AlignmentRecordFilter.filterByRecordQuality(dataOption.get.toRDD(), readQuality)
        val binSize = VizUtils.getBinSize(region, 1000)
        val alignmentData: Map[String, List[MutationCount]] = MergedAlignmentRecordLayout(dataOption.get.toRDD(), binSize)

        val fileMap = layer0.getFileMap
        var readRetJson: String = ""
        for (k <- ks.get) {
          val sampleData = alignmentData.get(k)
          sampleData match {
            case Some(_) =>
              readRetJson += "\"" + k + "\":" +
                "{ \"filename\": " + write(fileMap(k)) +
                ", \"indels\": " + write(sampleData.get.filter(_.op != "M")) +
                ", \"mismatches\": " + write(sampleData.get.filter(_.op == "M")) +
                ", \"layers\": " + 0 + "},"
            case None =>
              readRetJson += "\"" + k + "\":" +
                "{ \"filename\": " + write(fileMap(k)) + "},"
          }

        }
        readRetJson = readRetJson.dropRight(1)
        readRetJson = "{" + readRetJson + "}"
        readRetJson
      }
      case None => ""
    }
  }

  def getConvolved(region: ReferenceRegion, layer: Int, ks: Option[List[String]]): String = {
    var sampleMap: Map[String, Array[Double]] = Map()
    val layerType = LayeredTile.layers(layer)

    ks match {
      case Some(_) => {
        // get alignment records in RDD form
        val alignments = layer0.getRaw(region, ks.get)
        alignments match {
          case Some(_) => {

            val ref = refRDD.getConvolvedArray(region, layer)

            ks.get.map(k => {
              val c = ConvolutionalSequence.convolveRDD(region, ref, alignments.get.toRDD.map(_._2).filter(r => r.getRecordGroupSample == k), layerType.patchSize, layerType.stride)
              sampleMap = sampleMap + (k -> c)
            })
          } case None => {
            log.warn(s"No data found for alignments for samples ${ks.get}")
            ""
          }
        }
        // map to layer number

        write(sampleMap.mapValues(r => LayerData(1, r)))
      }
      case None => {
        log.warn("No keys specified for alignment data")
        ""
      }
    }

  }

}

case class LayerData(layers: Int, data: Any)