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

import java.io._

import com.typesafe.config.ConfigException
import org.apache.spark.{ SparkContext, SparkConf }
import play.api._
import org.bdgenomics.utils.misc.Logging

object Global extends GlobalSettings with Logging {

  override def onStop(app: Application): Unit = {
    // print timing metrics
    MangoServletWrapper.printMetrics
  }

  override def beforeStart(app: Application) = {

    // Load in Spark Home
    val sparkHome =
      try {
        app.configuration.underlying.getString("spark.home")
      } catch {
        case e: ConfigException =>
          log.error("Spark Home not set")
          System.exit(-1)
          null
      }

    // Load in Mango Args
    val appArgs =
      try {
        app.configuration.underlying.getString("app.args")
      } catch {
        case e: ConfigException =>
          log.warn("No Mango Args provided")
          System.exit(-1)
          null
      }

    // TODO: set spark args
    val conf = new SparkConf(false) // skip loading external settings
      .setAppName(MangoServletWrapper.commandName)
      .setMaster("local[%d]".format(Runtime.getRuntime.availableProcessors()))
      .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      .set("spark.kryo.registrator", "org.bdgenomics.mango.serialization.MangoKryoRegistrator")
      .set("spark.dynamicAllocation.executorIdleTimeout", "10d")
      .setSparkHome(sparkHome)

    try {
      val sparkArgs = parse(app.configuration.underlying.getString("spark.args"))
      // set master
      val master = sparkArgs.find(_._1.contains("master"))
      if (master.isDefined) {
        conf.setMaster(master.get._2)
      }
      conf.setAll(sparkArgs.filter(!_._1.contains("master")).map(r => (r._1, r._2)).toTraversable)
    } catch {
      case e: ConfigException =>
        log.info("No Spark Args Provided.")
    }

    val sc = new SparkContext(conf)
    MangoServletWrapper(appArgs).run(sc)
  }

  /**
   * Parse a list of spark-submit command line options.
   *
   * See SparkSubmitArguments.scala for a more formal description of available options.
   *
   */
  def parse(argStr: String): Iterator[(String, String)] = {
    val args = argStr.split("\\s").filter(!_.isEmpty).sliding(2, 2).toArray
    // require that all spark args have value
    require(args.forall(_.length == 2), "Error, Invalid spark args.")
    args.map(r => (r(0), r(1))).toIterator
  }

}
