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
package org.bdgenomics.mango.core.util

import java.io._

import org.apache.hadoop.fs.{ FileSystem, Path }
import org.apache.parquet.filter2.dsl.Dsl._
import org.apache.parquet.filter2.predicate.FilterPredicate
import org.apache.parquet.io.api.Binary
import org.apache.spark.SparkContext
import org.bdgenomics.adam.models.ReferenceRegion

object ResourceUtils {

  // TODO: move
  def formReferenceRegionPredicate(regions: Iterable[ReferenceRegion]): FilterPredicate = {
    regions.map(r => {
      ((LongColumn("end") >= r.start) && (LongColumn("start") <= r.end) &&
        (BinaryColumn("contigName") === Binary.fromString(r.referenceName)))
    }).reduce(_ || _)
  }

  /**
   * Prints java heap map availability and usage
   */
  def printSysUsage() = {
    val mb: Int = 1024 * 1024;
    //Getting the runtime reference from system
    var runtime: Runtime = Runtime.getRuntime();

    println("##### Heap utilization statistics [MB] #####");

    //Print used memory
    println("Used Memory:"
      + (runtime.totalMemory() - runtime.freeMemory()) / mb);

    //Print free memory
    println("Free Memory:"
      + runtime.freeMemory() / mb);

    //Print total available memory
    println("Total Memory:" + runtime.totalMemory() / mb);

    //Print Maximum available memory
    println("Max Memory:" + runtime.maxMemory() / mb);
  }

  /**
   * Returns whether a file is local or remote, and throws an exception if it can't find the file
   */
  def isLocal(filePath: String, sc: SparkContext): Boolean = {
    val path: Path = new Path(filePath)
    val fs: FileSystem = path.getFileSystem(sc.hadoopConfiguration)
    val homedir = fs.getHomeDirectory.toString
    if (homedir.startsWith("file:") && fs.exists(path)) {
      true
    } else if ((homedir.startsWith("hdfs:") || homedir.startsWith("s3:")) && fs.exists(path)) {
      false
    } else {
      throw new FileNotFoundException("Couldn't find the file ${path.toUri}")
    }
  }
}
