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

import controllers.MangoApplication
import net.liftweb.json._
import org.bdgenomics.adam.models.SequenceDictionary
import org.bdgenomics.mango.layout.{ BedRowJson, GenotypeJson, PositionCount }
import org.bdgenomics.mango.models.LazyMaterialization
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{ GlobalSettings, Play }
import play.mvc.Http
import play.twirl.api.Html
import utils.JsonImplicits._
import utils.{ MangoServletWrapper, ServerArgs }

class ApplicationSuite extends MangoFunSuite {

  def testApp = new GuiceApplicationBuilder()
    .global(new GlobalSettings() {})
    .build

  val withCache = play.api.Application.instanceCache[MangoApplication]

  // used for parsing json
  implicit val formats = DefaultFormats

  // resource files for all datasets
  val bamFile = resourcePath("mouse_chrM.bam")
  val referenceFile = resourcePath("mm10_chrM.fa")
  val vcfFile = resourcePath("truetest.genotypes.vcf")
  val featureFile = resourcePath("smalltest.bed")
  val coverageFile = resourcePath("mouse_chrM.coverage.adam")

  // other controller parameters
  val genesPath = "http://www.biodalliance.org/datasets/ensGene.bb"
  val port = 8080

  // contig to fetch
  val chr = "chrM"

  // keys used to retrieve file specific data
  val bamKey = LazyMaterialization.filterKeyFromFile(bamFile)
  val featureKey = LazyMaterialization.filterKeyFromFile(featureFile)
  val vcfKey = LazyMaterialization.filterKeyFromFile(vcfFile)
  val coverageKey = LazyMaterialization.filterKeyFromFile(coverageFile)

  // empty args used for 'no content' tests
  val emptyArgs = new ServerArgs()
  emptyArgs.referencePath = referenceFile
  emptyArgs.cacheSize = 100

  // default args with all datatypes
  val args = new ServerArgs()
  args.readsPaths = bamFile
  args.referencePath = referenceFile
  args.variantsPaths = vcfFile
  args.coveragePaths = coverageFile
  args.featurePaths = featureFile
  args.showGenotypes = true
  args.cacheSize = 100

  // start play app
  sparkBefore("before all") {
    Play.start(testApp)
  }

  // stop play app
  sparkAfter("after all") {
    Play.stop(testApp)
  }

  sparkTest("Gets sequence dictionary") {
    new MangoServletWrapper(emptyArgs).run(sc)

    //    val controller = new Application()
    val controller = withCache(testApp)

    val result = controller.sequenceDictionary().apply(FakeRequest())
    assert(await(result).header.status == Http.Status.OK)

    val sdJson = play.api.test.Helpers.contentAsJson(result)
    val sd: SequenceDictionary = sdJson.as[SequenceDictionary]

    assert(sd.records.length == 1)
    assert(sd.records.head.name == chr)
    assert(sd.records.head.length == 16299)
  }

  sparkTest("Sets session contig") {
    new MangoServletWrapper(emptyArgs).run(sc)

    val controller = withCache(testApp)

    val result = controller.setContig(chr, 1, 100).apply(FakeRequest())
    val session = play.api.test.Helpers.session(result)
    assert(session.get("contig").get == chr)
    assert(session.get("start").get.toInt == 1)
    assert(session.get("end").get.toInt == 100)
  }

  sparkTest("basic html test for get index with discovery mode") {
    // empty args used for 'no content' tests
    val discoveryArgs = new ServerArgs()
    discoveryArgs.referencePath = referenceFile
    discoveryArgs.discoveryMode = true

    new MangoServletWrapper(discoveryArgs).run(sc)

    val controller = withCache(testApp)

    val result = controller.index().apply(FakeRequest())
    assert(await(result).header.status == Http.Status.OK)

    val indexHtml = Html.apply(play.api.test.Helpers.contentAsString(result))

    // verify pileup is minified
    assert(indexHtml.body.contains("pileup.min.js"))
  }

  sparkTest("basic html test for browser") {
    new MangoServletWrapper(emptyArgs).run(sc)

    val controller = withCache(testApp)

    val result = controller.browser().apply(FakeRequest())
    assert(await(result).header.status == Http.Status.OK)
    val browserHtml: Html = Html.apply(play.api.test.Helpers.contentAsString(result))

    // verify pileup is minified
    assert(browserHtml.body.contains("pileup.min.js"))

    // verify pileup.js window is present
    assert(browserHtml.body.contains("<div id=\"pileup\">"))
  }

  sparkTest("/reference/:ref") {
    new MangoServletWrapper(emptyArgs).run(sc)
    val controller = withCache(testApp)

    val result = controller.reference(chr, 1, 100).apply(FakeRequest())

    assert(await(result).header.status == Http.Status.OK)

    val body = play.api.test.Helpers.contentAsString(result)
    assert(body.length == 99)
    assert(body.startsWith("TTAA"))
  }

  sparkTest("Should pass for discovery mode") {
    val args = new ServerArgs()
    args.discoveryMode = true
    args.referencePath = referenceFile
    args.featurePaths = featureFile

    new MangoServletWrapper(args).run(sc)
    val controller = withCache(testApp)

    val result = controller.features(featureKey, chr, 0, 2000, 1).apply(FakeRequest())
    assert(await(result).header.status == Http.Status.OK)

    val features = parse(play.api.test.Helpers.contentAsString(result)).extract[Array[BedRowJson]]
    assert(features.length == 2)

  }

  sparkTest("Returns no content error when no data exists") {
    new MangoServletWrapper(emptyArgs).run(sc)
    val controller = withCache(testApp)

    var result = await[Result](controller.reads(bamKey, chr, 0, 100).apply(FakeRequest()))
    assert(result.header.status == Http.Status.NOT_FOUND)

    result = await[Result](controller.variants(vcfKey, chr, 0, 100, 1).apply(FakeRequest()))
    assert(result.header.status == Http.Status.NOT_FOUND)

    result = await[Result](controller.features(featureKey, chr, 0, 100, 1).apply(FakeRequest()))
    assert(result.header.status == Http.Status.NOT_FOUND)

    result = await[Result](controller.coverage(coverageKey, chr, 0, 100, 1).apply(FakeRequest()))
    assert(result.header.status == Http.Status.NOT_FOUND)

    result = await[Result](controller.readCoverage(featureKey, chr, 0, 100, 1).apply(FakeRequest()))
    assert(result.header.status == Http.Status.NOT_FOUND)

  }

  sparkTest("Key does not exist") {
    new MangoServletWrapper(args).run(sc)
    val controller = withCache(testApp)

    // fake key that does not exist for any materializers
    val fakeKey = "fakeKey"

    var result = await[Result](controller.reads(fakeKey, chr, 0, 100).apply(FakeRequest()))
    assert(result.header.status == Http.Status.NO_CONTENT)

    result = await[Result](controller.variants(fakeKey, chr, 0, 100, 1).apply(FakeRequest()))
    assert(result.header.status == Http.Status.NO_CONTENT)

    result = await[Result](controller.features(fakeKey, chr, 0, 100, 1).apply(FakeRequest()))
    assert(result.header.status == Http.Status.NO_CONTENT)

    result = await[Result](controller.coverage(fakeKey, chr, 0, 100, 1).apply(FakeRequest()))
    assert(result.header.status == Http.Status.NO_CONTENT)

    result = await[Result](controller.readCoverage(fakeKey, chr, 0, 100, 1).apply(FakeRequest()))
    assert(result.header.status == Http.Status.NO_CONTENT)
  }

  /****************** Reads Tests *******************/
  sparkTest("/reads/:key/:ref") {
    new MangoServletWrapper(args).run(sc)
    val controller = withCache(testApp)

    var result = await[Result](controller.reads(bamKey, chr, 0, 100).apply(FakeRequest()))
    assert(result.header.status == Http.Status.OK)

    // out of bounds error
    result = await[Result](controller.reads(bamKey, "chr", 0, 100).apply(FakeRequest()))
    assert(result.header.status == Http.Status.NOT_FOUND)
  }

  sparkTest("/reads/coverage/:key/:ref") {
    new MangoServletWrapper(args).run(sc)
    val controller = withCache(testApp)

    val result = controller.readCoverage(bamKey, chr, 1, 100, 1).apply(FakeRequest())
    assert(await(result).header.status == Http.Status.OK)
    val json = parse(play.api.test.Helpers.contentAsString(result)).extract[Array[PositionCount]]
    assert(json.length == 99)

  }

  /************** Variants Tests ***************************/
  sparkTest("/variants/:key/:ref") {
    val args = new ServerArgs()
    args.referencePath = referenceFile
    args.variantsPaths = vcfFile
    args.showGenotypes = true

    new MangoServletWrapper(args).run(sc)
    val controller = withCache(testApp)

    var result = controller.variants(vcfKey, chr, 0, 100, 1).apply(FakeRequest())
    assert(await(result).header.status == Http.Status.OK)
    val json = parse(play.api.test.Helpers.contentAsString(result)).extract[Array[String]].map(r => GenotypeJson(r))
      .sortBy(_.variant.getStart)
    assert(json.length == 3)
    assert(json.head.variant.getStart == 19)
    assert(json.head.sampleIds.length == 2)

    result = controller.variants(vcfKey, "chr1", 0, 100, 1).apply(FakeRequest())
    assert(await(result).header.status == Http.Status.NOT_FOUND)

  }

  sparkTest("variants: no content error") {
    new MangoServletWrapper(args).run(sc)
    val controller = withCache(testApp)

    val result = await[Result](controller.variants(vcfKey, chr, 10000, 12000, 1).apply(FakeRequest()))
    assert(result.header.status == Http.Status.NO_CONTENT)
  }

  sparkTest("does not return genotypes when binned") {

    new MangoServletWrapper(args).run(sc)
    val controller = withCache(testApp)

    val result = controller.variants(vcfKey, chr, 0, 100, 100).apply(FakeRequest())
    assert(await(result).header.status == Http.Status.OK)
    val json = parse(play.api.test.Helpers.contentAsString(result)).extract[Array[String]].map(r => GenotypeJson(r))
      .sortBy(_.variant.getStart)
    assert(json.length == 1)
    assert(json.head.sampleIds.length == 0)
  }

  /************** Feature Tests ***************************/
  sparkTest("/features/:key/:ref") {

    new MangoServletWrapper(args).run(sc)
    val controller = withCache(testApp)

    // no data
    var result = controller.features(featureKey, chr, 0, 100, 1).apply(FakeRequest())
    assert(await(result).header.status == Http.Status.NO_CONTENT)

    // within cache bin
    result = controller.features(featureKey, chr, 1000, 1100, 1).apply(FakeRequest())
    assert(await(result).header.status == Http.Status.OK)
    var json = parse(play.api.test.Helpers.contentAsString(result)).extract[Array[BedRowJson]]
    assert(json.length == 1)

    // across cache bins
    result = controller.features(featureKey, chr, 0, 1200, 1).apply(FakeRequest())
    assert(await(result).header.status == Http.Status.OK)
    json = parse(play.api.test.Helpers.contentAsString(result)).extract[Array[BedRowJson]]
    assert(json.length == 2)

    // out of bounds error
    result = controller.features(featureKey, "chr1", 0, 100, 1).apply(FakeRequest())
    assert(await(result).header.status == Http.Status.NOT_FOUND)

  }

  sparkTest("bins features") {
    new MangoServletWrapper(args).run(sc)
    val controller = withCache(testApp)

    var result = controller.features(featureKey, chr, 0, 1200, 10).apply(FakeRequest())
    assert(await(result).header.status == Http.Status.OK)
    var json = parse(play.api.test.Helpers.contentAsString(result)).extract[Array[BedRowJson]]
    assert(json.length == 2)

    result = controller.features(featureKey, chr, 0, 1200, 1000).apply(FakeRequest())
    assert(await(result).header.status == Http.Status.OK)
    json = parse(play.api.test.Helpers.contentAsString(result)).extract[Array[BedRowJson]]
    assert(json.length == 1)
  }

  /************** Coverage Tests ***************************/
  sparkTest("/coverage/:key/:ref") {
    val args = new ServerArgs()
    args.referencePath = referenceFile
    args.coveragePaths = coverageFile

    new MangoServletWrapper(args).run(sc)
    val controller = withCache(testApp)

    var result = controller.coverage(coverageKey, chr, 0, 1200, 1).apply(FakeRequest())
    assert(await(result).header.status == Http.Status.OK)
    val json = parse(play.api.test.Helpers.contentAsString(result)).extract[Array[PositionCount]]
    assert(json.map(_.start).distinct.length == 1200)

    // out of bounds error
    result = controller.coverage(coverageKey, "chr1", 0, 1200, 1).apply(FakeRequest())
    assert(await(result).header.status == Http.Status.NOT_FOUND)
  }

  sparkTest("bins coverage") {
    val args = new ServerArgs()
    args.referencePath = referenceFile
    args.coveragePaths = coverageFile

    new MangoServletWrapper(args).run(sc)
    val controller = withCache(testApp)

    val result = controller.coverage(coverageKey, chr, 0, 1200, 10).apply(FakeRequest())
    assert(await(result).header.status == Http.Status.OK)
    val json = parse(play.api.test.Helpers.contentAsString(result)).extract[Array[PositionCount]]
    assert(json.map(_.start).distinct.length == 120)
  }

}