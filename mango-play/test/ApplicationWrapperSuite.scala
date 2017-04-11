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

import org.bdgenomics.adam.models.ReferenceRegion
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.mango.models.LazyMaterialization
import utils.MangoServletWrapper

class ApplicationWrapperSuite extends MangoFunSuite {

  val region = ReferenceRegion("chrM", 1, 100)

  // resource files for all datasets
  val bamFile = resourcePath("mouse_chrM.bam")
  val referenceFile = resourcePath("mm10_chrM.fa")
  val vcfFile = resourcePath("truetest.genotypes.vcf")
  val featureFile = resourcePath("smalltest.bed")
  val coverageFile = resourcePath("mouse_chrM.coverage.adam")
  val genesPath = "http://www.biodalliance.org/datasets/ensGene.bb"
  val port = 8080

  // keys used to retrieve file specific data
  val bamKey = LazyMaterialization.filterKeyFromFile(bamFile)
  val featureKey = LazyMaterialization.filterKeyFromFile(featureFile)
  val vcfKey = LazyMaterialization.filterKeyFromFile(vcfFile)
  val coverageKey = LazyMaterialization.filterKeyFromFile(coverageFile)

  test("formats clickable regions for home page") {
    val region1 = (ReferenceRegion("chr1", 1, 10), 1.0)
    val region2 = (ReferenceRegion("chr2", 10, 12), 1.0)
    val regions = List(region1, region2)

    val formatted = MangoServletWrapper.formatClickableRegions(regions)
    val ans = s"${region1._1.referenceName}:${region1._1.start}-${region1._1.end}-1.0,${region2._1.referenceName}:${region2._1.start}-${region2._1.end}-1.0"
    assert(formatted == ans)
  }

  test("expands region") {
    val region = ReferenceRegion("chr1", 15, 32)
    var expanded = MangoServletWrapper.expand(region, minLength = 1000)
    assert(expanded.length == 2)
    assert(expanded.head.referenceName == region.referenceName)
    assert(expanded.head.start == 0)
    assert(expanded.head.end == 1000)
  }

  test("expands region to multiple regions") {
    val region = ReferenceRegion("chr1", 15, 132)
    val expanded = MangoServletWrapper.expand(region, minLength = 100)
    assert(expanded.length == 2)
    assert(expanded.head.referenceName == region.referenceName)
    assert(expanded.head.start == 0)
    assert(expanded.head.end == 100)
    assert(expanded.last.start == 100)
    assert(expanded.last.end == 200)
  }

  test("Wrapper can parse arguments") {
    val strArgs = s"${referenceFile} -show_genotypes -print_metrics -genes ${genesPath} -port ${port}"
    val wrapper = MangoServletWrapper(strArgs)

    assert(wrapper.args.referencePath == referenceFile)
    assert(wrapper.args.printMetrics == true)
    assert(wrapper.args.genePath == genesPath)
    assert(wrapper.args.port == port)
  }

  sparkTest("Can fetch reference") {
    val strArgs = s"${referenceFile}"
    MangoServletWrapper(strArgs).run(sc)

    assert(MangoServletWrapper.annotationRDD.getReferenceString(region).length == 99)
  }

  sparkTest("Can fetch variants") {

    val strArgs = s"${referenceFile} -variants ${vcfFile} -show_genotypes"
    MangoServletWrapper(strArgs).run(sc)

    // we are using the count here because this number does not match what is expected from the test file.
    // perhaps there is some issue in upstream?
    val correctCount = sc.loadVariants(vcfFile).rdd.collect.filter(r => {
      r.getContigName == region.referenceName && r.getStart < region.end && r.getEnd > region.start
    }).size

    assert(MangoServletWrapper.materializer.variantContextExist)
    val genotypes = MangoServletWrapper.materializer.getVariantContext().get.getGenotypeSamples().toMap
    assert(genotypes(vcfFile).length == 2)
    assert(MangoServletWrapper.materializer.getVariantContext().get.getJson(region)(vcfKey).length == correctCount)
  }

  sparkTest("Can fetch reads") {
    val path = resourcePath("mm10_chrM.fa")
    val port = 8080

    val strArgs = s"${path} -reads ${bamFile}"
    MangoServletWrapper(strArgs).run(sc)

    assert(MangoServletWrapper.materializer.readsExist)
    assert(!MangoServletWrapper.materializer.getReads().get.getJson(region)(bamKey).isEmpty)
  }

  sparkTest("Can fetch features") {

    val strArgs = s"${referenceFile} -features ${featureFile}"
    MangoServletWrapper(strArgs).run(sc)

    assert(MangoServletWrapper.materializer.featuresExist)
    assert(MangoServletWrapper.materializer.getFeatures().get.getJson(ReferenceRegion("chrM", 1000, 1500))(featureKey).length == 2)
  }

  sparkTest("Can fetch coverage") {

    val strArgs = s"${referenceFile} -coverage ${coverageFile}"
    MangoServletWrapper(strArgs).run(sc)

    assert(MangoServletWrapper.materializer.coveragesExist)
    assert(!MangoServletWrapper.materializer.getCoverage().get.getJson(region)(coverageKey).isEmpty)
  }

  sparkTest("Discover mode") {
    val strArgs = s"${referenceFile} -features ${featureFile} -discover"
    MangoServletWrapper(strArgs).run(sc)

    assert(MangoServletWrapper.prefetchedRegions.length == 31)
  }

}
