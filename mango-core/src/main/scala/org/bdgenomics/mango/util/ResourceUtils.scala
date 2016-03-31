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

object ResourceUtils {

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
}
