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
package org.bdgenomics.mango.cli

import net.liftweb.json.Serialization._
import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.mango.cli.MangoServletWrapper._
import org.bdgenomics.mango.core.util.{ VizUtils }
import org.bdgenomics.mango.layout._
import org.bdgenomics.mango.models._
import org.bdgenomics.utils.misc.Logging
import org.eclipse.jetty.server.Server
import org.fusesource.scalate.TemplateEngine
import org.ga4gh.GAReadAlignment
import org.scalatra._

// HTTP ERROR RESPONSES
object HttpError {
  // Error message For out of bounds ReferenceRegion
  val outOfBounds = NotFound("Region not found in Reference Sequence Dictionary")

  // Error for oversized ReferenceRegion
  val largeRegion = RequestEntityTooLarge("Region too large")

  // Error for unsupported file
  val unprocessableFile = UnprocessableEntity("File type not supported")

  // Error for file not found
  val notFound = NotFound("File not found")

  def keyNotFound(key: String): ActionResult = {
    val msg = s"No content available for key ${key}"
    NoContent(Map.empty, key)
  }

  /**
   * Error message for no content for a given ReferenceRegion
   * @param region ReferenceRegion over which there is no content
   * @return ActionalResult
   */
  def noContent(region: ReferenceRegion): ActionResult = {
    val msg = s"No content available at ${region.toString}"
    NoContent(Map.empty, msg)
  }
}

/**
 * Servlet which receives and serves json requests from front end.
 *
 * Available APIs for server include:
 * - /quit: Correctly shuts down server
 * - /setContig: sets initial chromosome to view. used for server startup
 * - /browser: visualization settings for genome browser home page
 * - /reference: serves reference data
 * - /sequenceDictionary: returns sequence dictionary for species
 * - /reads: returns AlignmentRecords
 * - /coverage: returns Coverage
 * - /reads/coverage: Returns read specific coverage
 * - /variants: returns variants and genotypes
 * - /features: returns features
 */
class MangoServlet extends ScalatraServlet with Logging {
  implicit val formats = net.liftweb.json.DefaultFormats

  get("/?") {
    redirect("/overall")
  }

  get("/quit") {
    MangoServlet.quit(server)
  }

  get("/overall") {
    contentType = "text/html"
    val templateEngine = new TemplateEngine
    // set initial referenceRegion so it is defined. pick first chromosome to view
    val firstChr = globalDict.records.head.name
    session("referenceRegion") = ReferenceRegion(firstChr, 1, 100)
    templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/overall.ssp",
      Map("dictionary" -> formatDictionaryOpts(globalDict),
        "regions" -> formatClickableRegions(prefetchedRegions)))
  }

  get("/setContig/:ref") {
    val viewRegion = ReferenceRegion(params("ref"), params("start").toLong, params("end").toLong)
    session("referenceRegion") = viewRegion
  }

  get("/browser") {
    contentType = "text/html"
    // if session variable for reference region is not yet set, randomly set it
    try {
      session("referenceRegion")
    } catch {
      case e: Exception =>
        val firstChr = globalDict.records.head.name
        session("referenceRegion") = ReferenceRegion(firstChr, 1, 100)
    }

    val templateEngine = new TemplateEngine
    // set initial referenceRegion so it is defined
    val region = session("referenceRegion").asInstanceOf[ReferenceRegion]

    // generate file keys for front end
    val readsSamples: Option[List[(String, Option[String])]] = try {
      val reads = materializer.getReads().get.getFiles.map(r => LazyMaterialization.filterKeyFromFile(r))

      // check if there are precomputed coverage files for reads. If so, send this information to the frontend
      // to avoid extra coverage computation
      if (materializer.getCoverage().isDefined) {
        Some(reads.map(r => {
          val coverage = materializer.getCoverage().get.getFiles.map(c => LazyMaterialization.filterKeyFromFile(c))
            .find(c => {
              c.contains(r)
            })
          (r, coverage)
        }))
      } else Some(reads.map((_, None)))

    } catch {
      case e: Exception => None
    }

    val coverageSamples = try {
      val coverage = materializer.getCoverage().get.getFiles.map(r => LazyMaterialization.filterKeyFromFile(r))

      // filter out coverage samples that will be displayed with reads
      if (readsSamples.isDefined) {
        val readsCoverage = readsSamples.get.map(_._2).flatten
        Some(coverage.filter(c => !readsCoverage.contains(c)))
      } else Some(coverage)
    } catch {
      case e: Exception => None
    }

    val variantSamples = try {
      if (showGenotypes)
        Some(materializer.getVariantContext().get.getGenotypeSamples().map(r => (LazyMaterialization.filterKeyFromFile(r._1), r._2.mkString(","))))
      else Some(materializer.getVariantContext().get.getFiles.map(r => (LazyMaterialization.filterKeyFromFile(r), "")))
    } catch {
      case e: Exception => None
    }

    val featureSamples = try {
      Some(materializer.getFeatures().get.getFiles.map(r => LazyMaterialization.filterKeyFromFile(r)))
    } catch {
      case e: Exception => None
    }

    templateEngine.layout("mango-cli/src/main/webapp/WEB-INF/layouts/browser.ssp",
      Map("dictionary" -> formatDictionaryOpts(globalDict),
        "genes" -> genes,
        "reads" -> readsSamples,
        "coverage" -> coverageSamples,
        "variants" -> variantSamples,
        "features" -> featureSamples,
        "contig" -> session("referenceRegion").asInstanceOf[ReferenceRegion].referenceName,
        "start" -> session("referenceRegion").asInstanceOf[ReferenceRegion].start.toString,
        "end" -> session("referenceRegion").asInstanceOf[ReferenceRegion].end.toString))
  }

  get("/reference/:ref") {
    val viewRegion = ReferenceRegion(params("ref"), params("start").toLong, params("end").toLong)
    session("referenceRegion") = viewRegion
    val dictOpt = globalDict(viewRegion.referenceName)
    if (dictOpt.isDefined) {
      Ok(write(annotationRDD.getReferenceString(viewRegion)))
    } else HttpError.outOfBounds
  }

  get("/sequenceDictionary") {
    Ok(write(annotationRDD.getSequenceDictionary.records))
  }

  get("/reads/:key/:ref") {
    VizTimers.ReadsRequest.time {
      if (!materializer.readsExist) {
        HttpError.notFound
      } else {
        val dictOpt = globalDict(params("ref"))
        if (dictOpt.isDefined) {
          val viewRegion = ReferenceRegion(params("ref"), params("start").toLong,
            VizUtils.getEnd(params("end").toLong, globalDict(params("ref"))))

          val key: String = params("key")
          contentType = "json"
          var results: Option[Array[GAReadAlignment]] = None
          syncObject(AlignmentRecordMaterialization.name).synchronized {
            val readsCache = cache.getReads(viewRegion, key)
            // if there is no cache or the cache does not cover current region, get alignment data
            if (!readsCache.isDefined) {
              val expandedRegion = expand(viewRegion)

              // fetch region and store in cache
              val data = materializer.getReads.get.getJson(expandedRegion)

              val x = cache.setReads(data.toMap, expandedRegion)
            }
            // region was already collected, grab from cache
            val startTime = System.currentTimeMillis()
            results = cache.getReads(viewRegion, key)
            val endTime = System.currentTimeMillis()
            println("getting reads from cache took " + (endTime - startTime) + " milliseconds")
          }
          if (results.isDefined) {
            // filter elements by region
            Ok(materializer.getReads().get.stringify(results.get))
          } else HttpError.noContent(viewRegion)
        } else HttpError.outOfBounds
      }
    }
  }

  get("/coverage/:key/:ref") {
    val viewRegion = ReferenceRegion(params("ref"), params("start").toLong,
      VizUtils.getEnd(params("end").toLong, globalDict(params("ref"))))
    val key: String = params("key")
    val binning: Int =
      try
        params("binning").toInt
      catch {
        case e: Exception => 1
      }
    contentType = "json"
    MangoServlet.getCoverage(viewRegion, key, binning)
  }

  get("/reads/coverage/:key/:ref") {
    VizTimers.ReadsRequest.time {
      if (!materializer.readsExist) {
        HttpError.notFound
      } else {
        val viewRegion = ReferenceRegion(params("ref"), params("start").toLong,
          VizUtils.getEnd(params("end").toLong, globalDict(params("ref"))))
        val key: String = params("key")
        contentType = "json"

        // get all coverage files that have been loaded
        val coverageFiles =
          if (materializer.coveragesExist) {
            Some(materializer.getCoverage().get.getFiles.map(f => LazyMaterialization.filterKeyFromFile(f)))
          } else None

        // check if there is a precomputed coverage file for this reads file
        if (coverageFiles.isDefined && coverageFiles.get.contains(key)) {
          val binning: Int =
            try
              params("binning").toInt
            catch {
              case e: Exception => 1
            }

          MangoServlet.getCoverage(viewRegion, key, binning)
        } else {
          // no precomputed coverage. compute from reads
          val dictOpt = globalDict(viewRegion.referenceName)
          if (dictOpt.isDefined) {
            var results: Option[Array[GAReadAlignment]] = None
            syncObject(AlignmentRecordMaterialization.name).synchronized {

              val readsCache = cache.getReads(viewRegion, key)
              // if there is no cache or the cache does not cover current region, get alignment data
              if (!readsCache.isDefined) {
                val expandedRegion = expand(viewRegion)
                // fetch region and store in cache
                val data = materializer.getReads.get.getJson(expandedRegion)
                val x = cache.setReads(data.toMap, expandedRegion)
              }
              results = cache.getReads(viewRegion, key)
            }
            if (results.isDefined) {
              // compute coverage from collected reads on the fly
              Ok(write(materializer.getReads.get.toCoverage(results.get, viewRegion)))
            } else HttpError.noContent(viewRegion)
          } else HttpError.outOfBounds
        }
      }
    }
  }

  get("/variants/:key/:ref") {
    VizTimers.VarRequest.time {
      if (!materializer.variantContextExist)
        HttpError.notFound
      else {
        val viewRegion = ReferenceRegion(params("ref"), params("start").toLong,
          VizUtils.getEnd(params("end").toLong, globalDict(params("ref"))))
        val key: String = params("key")
        contentType = "json"

        // if region is in bounds of reference, return data
        val dictOpt = globalDict(viewRegion.referenceName)
        if (dictOpt.isDefined) {
          var results: Option[Array[GenotypeJson]] = None
          val binning: Int =
            try {
              params("binning").toInt
            } catch {
              case e: Exception => 1
            }
          syncObject(VariantContextMaterialization.name).synchronized {
            // region was already collected, grab from cache
            val variantCache = cache.getVariants(viewRegion, key, Some(binning))
            if (!variantCache.isDefined) {
              val expandedRegion = expand(viewRegion)
              val data = materializer.getVariantContext().get.getJson(expandedRegion,
                showGenotypes,
                binning)
              cache.setVariants(data, viewRegion, binning)
            }
            results = cache.getVariants(viewRegion, key, Some(binning))
          }
          if (results.isDefined) {
            // extract variants only and parse to stringified json
            Ok(materializer.getVariantContext().get.stringify(results.get))
          } else HttpError.noContent(viewRegion)
        } else HttpError.outOfBounds
      }
    }
  }

  get("/features/:key/:ref") {
    VizTimers.FeatRequest.time {
      if (!materializer.featuresExist)
        HttpError.notFound
      else {
        val viewRegion = ReferenceRegion(params("ref"), params("start").toLong,
          VizUtils.getEnd(params("end").toLong, globalDict(params("ref"))))
        val key: String = params("key")
        contentType = "json"

        // if region is in bounds of reference, return data
        val dictOpt = globalDict(viewRegion.referenceName)
        if (dictOpt.isDefined) {
          var results: Option[Array[BedRowJson]] = None
          val binning: Int =
            try {
              params("binning").toInt
            } catch {
              case e: Exception => 1
            }
          syncObject(FeatureMaterialization.name).synchronized {
            // region was already collected, grab from cache
            val featureCache = cache.getFeatures(viewRegion, key, Some(binning))
            if (!featureCache.isDefined) {
              val expandedRegion = expand(viewRegion)
              // fetch region and store in cache
              val data = materializer.getFeatures().get.getJson(expandedRegion, binning)
              cache.setFeatures(data.toMap, expandedRegion, binning)
            }
            results = cache.getFeatures(viewRegion, key, Some(binning))
          }
          if (results.isDefined) {
            Ok(materializer.getFeatures().get.stringify(results.get))
          } else HttpError.noContent(viewRegion)
        } else HttpError.outOfBounds
      }
    }
  }

}

/**
 * Helper functions Request Class
 */
object MangoServlet extends Logging {

  /**
   * Starts server once on startup
   */
  def startServer(port: Int): Server = {
    val server = new org.eclipse.jetty.server.Server(port)
    val handlers = new org.eclipse.jetty.server.handler.ContextHandlerCollection()
    server.setHandler(handlers)
    handlers.addHandler(new org.eclipse.jetty.webapp.WebAppContext("mango-cli/src/main/webapp", "/"))
    server.start()
    println("View the visualization at: " + port)
    println("Quit at: /quit")
    server
  }

  //Correctly shuts down the server
  def quit(server: Server) {
    val thread = new Thread {
      override def run() {
        try {
          log.info("Shutting down the server")
          server.stop()
          log.info("Server has stopped")
        } catch {
          case e: Exception => {
            log.info("Error when stopping Jetty server: " + e.getMessage, e)
          }
        }
      }
    }
    thread.start()
  }

  /**
   * Gets Coverage for a get Request. This is used to get both Reads based coverage and generic coverage.
   * @param viewRegion ReferenceRegion to view coverage over
   * @param key key for coverage file (see LazyMaterialization)
   * @return ActionResult of coverage json
   */
  def getCoverage(viewRegion: ReferenceRegion, key: String, binning: Int = 1): ActionResult = {
    VizTimers.CoverageRequest.time {
      if (!materializer.coveragesExist) {
        HttpError.notFound
      } else {
        val dictOpt = globalDict(viewRegion.referenceName)
        if (dictOpt.isDefined) {
          var results: Option[Array[PositionCount]] = None
          syncObject(CoverageMaterialization.name).synchronized {
            // region was already collected, grab from cache
            val coverageCache = cache.getCoverage(viewRegion, key)
            if (!coverageCache.isDefined) {
              val expandedRegion = expand(viewRegion)
              val data = materializer.getCoverage().get.getCoverage(expandedRegion, binning)
              cache.setCoverage(data.toMap, expandedRegion, binning)
            }
            results = cache.getCoverage(viewRegion, key)
          }
          if (results.isDefined) {
            Ok(materializer.getCoverage().get.stringify(results.get))
          } else HttpError.noContent(viewRegion)
        } else HttpError.outOfBounds
      }
    }
  }
}