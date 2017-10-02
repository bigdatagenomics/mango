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
package org.bdgenomics.mango.converters

import org.bdgenomics.adam.rdd.read.AlignmentRecordRDD
import org.bdgenomics.mango.util.MangoFunSuite
import org.scalatest.FunSuite
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.adam.rdd.feature.FeatureRDD
import org.bdgenomics.adam.rdd.variant.VariantRDD

class GA4GHutilSuite extends MangoFunSuite {
  sparkTest("converting an empty cigar should yield an empty cigar") {
    assert(1 === 1)
  }

  sparkTest("create JSON from AlignmentRecordRDD") {
    val inputPath = resourcePath("small.1.sam")
    val rrdd: AlignmentRecordRDD = sc.loadAlignments(inputPath)
    val json = GA4GHutil.alignmentRecordRDDtoJSON(rrdd).replaceAll("\\s", "")
    val correctJson = "{\"alignments\":[{\"id\":\"\",\"readGroupId\":\"1\",\"fragmentName\":\"simread:1:26472783:false\",\"improperPlacement\":true,\"duplicateFragment\":false,\"numberReads\":1,\"fragmentLength\":0,\"readNumber\":0,\"failedVendorQualityChecks\":false,\"alignment\":{\"position\":{\"referenceName\":\"1\",\"position\":\"26472783\",\"strand\":\"NEG_STRAND\"},\"mappingQuality\":60,\"cigar\":[{\"operation\":\"ALIGNMENT_MATCH\",\"operationLength\":\"75\",\"referenceSequence\":\"\"}]},\"secondaryAlignment\":false,\"supplementaryAlignment\":false,\"alignedSequence\":\"GTATAAGAGCAGCCTTATTCCTATTTATAATCAGGGTGAAACACCTGTGCCAATGCCAAGACAGGGGTGCCAAGA\",\"alignedQuality\":[]}],\"nextPageToken\":\"\"}"
    assert(json === correctJson)
  }

  sparkTest("create JSON with genotypes from VCF using genotypeRDD") {
    val inputPath = resourcePath("truetest.genotypes.vcf")
    val grdd = sc.loadGenotypes(inputPath)
    val json = GA4GHutil.genotypeRDDtoJSON(grdd).replaceAll("\\s", "")
    val correctJson = "{\"variants\":[{\"id\":\"\",\"variantSetId\":\"\",\"names\":[],\"created\":\"0\",\"updated\":\"0\",\"referenceName\":\"chrM\",\"start\":\"19\",\"end\":\"20\",\"referenceBases\":\"T\",\"alternateBases\":[\"A\"],\"calls\":[{\"callSetName\":\"NA00001\",\"callSetId\":\"NA00001\",\"phaseset\":\"\",\"genotypeLikelihood\":[],\"genotype\":[\"0\",\"1\"]},{\"callSetName\":\"NA00002\",\"callSetId\":\"NA00002\",\"phaseset\":\"\",\"genotypeLikelihood\":[],\"genotype\":[\"0\",\"1\"]},{\"callSetName\":\"NA00003\",\"callSetId\":\"NA00003\",\"phaseset\":\"\",\"genotypeLikelihood\":[],\"genotype\":[\"0\",\"1\"]}],\"filtersApplied\":false,\"filtersPassed\":true,\"filtersFailed\":[\"q10\"],\"variantType\":\"\",\"svlen\":\"0\",\"cipos\":[],\"ciend\":[]},{\"id\":\"\",\"variantSetId\":\"\",\"names\":[\"microsat1\"],\"created\":\"0\",\"updated\":\"0\",\"referenceName\":\"chrM\",\"start\":\"49\",\"end\":\"52\",\"referenceBases\":\"GTC\",\"alternateBases\":[\"G\"],\"calls\":[{\"callSetName\":\"NA00001\",\"callSetId\":\"NA00001\",\"phaseset\":\"\",\"genotypeLikelihood\":[],\"genotype\":[\"0\",\"1\"]},{\"callSetName\":\"NA00002\",\"callSetId\":\"NA00002\",\"phaseset\":\"\",\"genotypeLikelihood\":[],\"genotype\":[\"0\",\"0\"]},{\"callSetName\":\"NA00003\",\"callSetId\":\"NA00003\",\"phaseset\":\"\",\"genotypeLikelihood\":[],\"genotype\":[\"1\",\"1\"]}],\"filtersApplied\":false,\"filtersPassed\":true,\"filtersFailed\":[],\"variantType\":\"\",\"svlen\":\"0\",\"cipos\":[],\"ciend\":[]},{\"id\":\"\",\"variantSetId\":\"\",\"names\":[\"rs6040355\"],\"created\":\"0\",\"updated\":\"0\",\"referenceName\":\"chrM\",\"start\":\"29\",\"end\":\"30\",\"referenceBases\":\"A\",\"alternateBases\":[\"G\"],\"calls\":[{\"callSetName\":\"NA00001\",\"callSetId\":\"NA00001\",\"phaseset\":\"\",\"genotypeLikelihood\":[],\"genotype\":[\"1\",\"0\"]},{\"callSetName\":\"NA00002\",\"callSetId\":\"NA00002\",\"phaseset\":\"\",\"genotypeLikelihood\":[],\"genotype\":[\"0\",\"1\"]},{\"callSetName\":\"NA00003\",\"callSetId\":\"NA00003\",\"phaseset\":\"\",\"genotypeLikelihood\":[],\"genotype\":[\"0\",\"0\"]}],\"filtersApplied\":false,\"filtersPassed\":true,\"filtersFailed\":[],\"variantType\":\"\",\"svlen\":\"0\",\"cipos\":[],\"ciend\":[]},{\"id\":\"\",\"variantSetId\":\"\",\"names\":[\"rs6040355\"],\"created\":\"0\",\"updated\":\"0\",\"referenceName\":\"chrM\",\"start\":\"29\",\"end\":\"30\",\"referenceBases\":\"A\",\"alternateBases\":[\"T\"],\"calls\":[{\"callSetName\":\"NA00001\",\"callSetId\":\"NA00001\",\"phaseset\":\"\",\"genotypeLikelihood\":[],\"genotype\":[\"0\",\"1\"]},{\"callSetName\":\"NA00002\",\"callSetId\":\"NA00002\",\"phaseset\":\"\",\"genotypeLikelihood\":[],\"genotype\":[\"1\",\"0\"]},{\"callSetName\":\"NA00003\",\"callSetId\":\"NA00003\",\"phaseset\":\"\",\"genotypeLikelihood\":[],\"genotype\":[\"1\",\"1\"]}],\"filtersApplied\":false,\"filtersPassed\":true,\"filtersFailed\":[],\"variantType\":\"\",\"svlen\":\"0\",\"cipos\":[],\"ciend\":[]},{\"id\":\"\",\"variantSetId\":\"\",\"names\":[],\"created\":\"0\",\"updated\":\"0\",\"referenceName\":\"chrM\",\"start\":\"39\",\"end\":\"40\",\"referenceBases\":\"T\",\"alternateBases\":[],\"calls\":[{\"callSetName\":\"NA00001\",\"callSetId\":\"NA00001\",\"phaseset\":\"\",\"genotypeLikelihood\":[],\"genotype\":[\"0\",\"0\"]},{\"callSetName\":\"NA00002\",\"callSetId\":\"NA00002\",\"phaseset\":\"\",\"genotypeLikelihood\":[],\"genotype\":[\"0\",\"0\"]},{\"callSetName\":\"NA00003\",\"callSetId\":\"NA00003\",\"phaseset\":\"\",\"genotypeLikelihood\":[],\"genotype\":[\"0\",\"0\"]}],\"filtersApplied\":false,\"filtersPassed\":true,\"filtersFailed\":[],\"variantType\":\"\",\"svlen\":\"0\",\"cipos\":[],\"ciend\":[]},{\"id\":\"\",\"variantSetId\":\"\",\"names\":[\"rs6054257\"],\"created\":\"0\",\"updated\":\"0\",\"referenceName\":\"chrM\",\"start\":\"9\",\"end\":\"10\",\"referenceBases\":\"G\",\"alternateBases\":[\"A\"],\"calls\":[{\"callSetName\":\"NA00001\",\"callSetId\":\"NA00001\",\"phaseset\":\"\",\"genotypeLikelihood\":[],\"genotype\":[\"1\",\"1\"]},{\"callSetName\":\"NA00002\",\"callSetId\":\"NA00002\",\"phaseset\":\"\",\"genotypeLikelihood\":[],\"genotype\":[\"1\",\"1\"]},{\"callSetName\":\"NA00003\",\"callSetId\":\"NA00003\",\"phaseset\":\"\",\"genotypeLikelihood\":[],\"genotype\":[\"1\",\"1\"]}],\"filtersApplied\":false,\"filtersPassed\":true,\"filtersFailed\":[],\"variantType\":\"\",\"svlen\":\"0\",\"cipos\":[],\"ciend\":[]},{\"id\":\"\",\"variantSetId\":\"\",\"names\":[\"microsat1\"],\"created\":\"0\",\"updated\":\"0\",\"referenceName\":\"chrM\",\"start\":\"49\",\"end\":\"52\",\"referenceBases\":\"GTC\",\"alternateBases\":[\"GTCT\"],\"calls\":[{\"callSetName\":\"NA00001\",\"callSetId\":\"NA00001\",\"phaseset\":\"\",\"genotypeLikelihood\":[],\"genotype\":[\"0\",\"0\"]},{\"callSetName\":\"NA00002\",\"callSetId\":\"NA00002\",\"phaseset\":\"\",\"genotypeLikelihood\":[],\"genotype\":[\"0\",\"1\"]},{\"callSetName\":\"NA00003\",\"callSetId\":\"NA00003\",\"phaseset\":\"\",\"genotypeLikelihood\":[],\"genotype\":[\"0\",\"0\"]}],\"filtersApplied\":false,\"filtersPassed\":true,\"filtersFailed\":[],\"variantType\":\"\",\"svlen\":\"0\",\"cipos\":[],\"ciend\":[]}],\"nextPageToken\":\"\"}"

    assert(json === correctJson)
  }

  sparkTest("create JSON without genotypes from VCF using variantRDD") {
    val inputPath = resourcePath("truetest.genotypes.vcf")
    val vrdd: VariantRDD = sc.loadVariants(inputPath)
    val json = GA4GHutil.variantRDDtoJSON(vrdd).replaceAll("\\s", "")
    val correctJson = "{\"variants\":[{\"id\":\"\",\"variantSetId\":\"\",\"names\":[\"rs6054257\"],\"created\":\"0\",\"updated\":\"0\",\"referenceName\":\"chrM\",\"start\":\"9\",\"end\":\"10\",\"referenceBases\":\"G\",\"alternateBases\":[\"A\"],\"calls\":[],\"filtersApplied\":false,\"filtersPassed\":true,\"filtersFailed\":[],\"variantType\":\"\",\"svlen\":\"0\",\"cipos\":[],\"ciend\":[]},{\"id\":\"\",\"variantSetId\":\"\",\"names\":[],\"created\":\"0\",\"updated\":\"0\",\"referenceName\":\"chrM\",\"start\":\"19\",\"end\":\"20\",\"referenceBases\":\"T\",\"alternateBases\":[\"A\"],\"calls\":[],\"filtersApplied\":false,\"filtersPassed\":true,\"filtersFailed\":[\"q10\"],\"variantType\":\"\",\"svlen\":\"0\",\"cipos\":[],\"ciend\":[]},{\"id\":\"\",\"variantSetId\":\"\",\"names\":[\"rs6040355\"],\"created\":\"0\",\"updated\":\"0\",\"referenceName\":\"chrM\",\"start\":\"29\",\"end\":\"30\",\"referenceBases\":\"A\",\"alternateBases\":[\"G\"],\"calls\":[],\"filtersApplied\":false,\"filtersPassed\":true,\"filtersFailed\":[],\"variantType\":\"\",\"svlen\":\"0\",\"cipos\":[],\"ciend\":[]},{\"id\":\"\",\"variantSetId\":\"\",\"names\":[\"rs6040355\"],\"created\":\"0\",\"updated\":\"0\",\"referenceName\":\"chrM\",\"start\":\"29\",\"end\":\"30\",\"referenceBases\":\"A\",\"alternateBases\":[\"T\"],\"calls\":[],\"filtersApplied\":false,\"filtersPassed\":true,\"filtersFailed\":[],\"variantType\":\"\",\"svlen\":\"0\",\"cipos\":[],\"ciend\":[]},{\"id\":\"\",\"variantSetId\":\"\",\"names\":[],\"created\":\"0\",\"updated\":\"0\",\"referenceName\":\"chrM\",\"start\":\"39\",\"end\":\"40\",\"referenceBases\":\"T\",\"alternateBases\":[],\"calls\":[],\"filtersApplied\":false,\"filtersPassed\":true,\"filtersFailed\":[],\"variantType\":\"\",\"svlen\":\"0\",\"cipos\":[],\"ciend\":[]},{\"id\":\"\",\"variantSetId\":\"\",\"names\":[\"microsat1\"],\"created\":\"0\",\"updated\":\"0\",\"referenceName\":\"chrM\",\"start\":\"49\",\"end\":\"52\",\"referenceBases\":\"GTC\",\"alternateBases\":[\"G\"],\"calls\":[],\"filtersApplied\":false,\"filtersPassed\":true,\"filtersFailed\":[],\"variantType\":\"\",\"svlen\":\"0\",\"cipos\":[],\"ciend\":[]},{\"id\":\"\",\"variantSetId\":\"\",\"names\":[\"microsat1\"],\"created\":\"0\",\"updated\":\"0\",\"referenceName\":\"chrM\",\"start\":\"49\",\"end\":\"52\",\"referenceBases\":\"GTC\",\"alternateBases\":[\"GTCT\"],\"calls\":[],\"filtersApplied\":false,\"filtersPassed\":true,\"filtersFailed\":[],\"variantType\":\"\",\"svlen\":\"0\",\"cipos\":[],\"ciend\":[]}],\"nextPageToken\":\"\"}";

    assert(json === correctJson)
  }

  sparkTest("create JSON from Feature") {
    val inputPath = resourcePath("smalltest.bed")
    val frdd: FeatureRDD = sc.loadBed(inputPath)
    val json = GA4GHutil.featureRDDtoJSON(frdd).replaceAll("\\s", "")

    val correctJson = "{\"features\":[{\"id\":\"\",\"name\":\"\",\"geneSymbol\":\"\",\"parentId\":\"\",\"childIds\":[],\"featureSetId\":\"\",\"referenceName\":\"chrM\",\"start\":\"1107\",\"end\":\"1200\",\"strand\":\"POS_STRAND\"},{\"id\":\"\",\"name\":\"\",\"geneSymbol\":\"\",\"parentId\":\"\",\"childIds\":[],\"featureSetId\":\"\",\"referenceName\":\"chrM\",\"start\":\"1180\",\"end\":\"1210\",\"strand\":\"POS_STRAND\"},{\"id\":\"\",\"name\":\"\",\"geneSymbol\":\"\",\"parentId\":\"\",\"childIds\":[],\"featureSetId\":\"\",\"referenceName\":\"chrM\",\"start\":\"2180\",\"end\":\"2210\",\"strand\":\"POS_STRAND\"},{\"id\":\"\",\"name\":\"\",\"geneSymbol\":\"\",\"parentId\":\"\",\"childIds\":[],\"featureSetId\":\"\",\"referenceName\":\"chrM\",\"start\":\"3109\",\"end\":\"3110\",\"strand\":\"POS_STRAND\"}],\"nextPageToken\":\"\"}"

    assert(json === correctJson)
  }

}
