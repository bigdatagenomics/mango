package org.bdgenomics.mango.converters

import org.bdgenomics.adam.rdd.read.AlignmentRecordRDD
import org.bdgenomics.mango.util.MangoFunSuite
import org.scalatest.FunSuite
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.adam.rdd.feature.FeatureRDD

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

  sparkTest("create JSON from Variant") {
    val inputPath = resourcePath("truetest.genotypes.vcf")
    val grdd = sc.loadGenotypes(inputPath)
    val json = GA4GHutil.genotypeRDDtoJSON(grdd).replaceAll("\\s", "")
    val correctJson = "{\"variants\":[{\"id\":\"\",\"variantSetId\":\"\",\"names\":[\"rs2905037\"],\"created\":\"0\",\"updated\":\"0\",\"referenceName\":\"chrM\",\"start\":\"19\",\"end\":\"20\",\"referenceBases\":\"G\",\"alternateBases\":[\"T\"],\"calls\":[{\"callSetName\":\"NA12878\",\"callSetId\":\"NA12878\",\"phaseset\":\"\",\"genotypeLikelihood\":[],\"genotype\":[\"1\",\"1\"]},{\"callSetName\":\"NA12879\",\"callSetId\":\"NA12879\",\"phaseset\":\"\",\"genotypeLikelihood\":[],\"genotype\":[\"1\",\"1\"]}],\"filtersApplied\":true,\"filtersPassed\":true,\"filtersFailed\":[],\"variantType\":\"\",\"svlen\":\"0\",\"cipos\":[],\"ciend\":[]},{\"id\":\"\",\"variantSetId\":\"\",\"names\":[\"rs2905037\"],\"created\":\"0\",\"updated\":\"0\",\"referenceName\":\"chrM\",\"start\":\"29\",\"end\":\"30\",\"referenceBases\":\"G\",\"alternateBases\":[\"A\"],\"calls\":[{\"callSetName\":\"NA12878\",\"callSetId\":\"NA12878\",\"phaseset\":\"\",\"genotypeLikelihood\":[],\"genotype\":[\"1\",\"1\"]},{\"callSetName\":\"NA12879\",\"callSetId\":\"NA12879\",\"phaseset\":\"\",\"genotypeLikelihood\":[],\"genotype\":[\"1\",\"1\"]}],\"filtersApplied\":true,\"filtersPassed\":true,\"filtersFailed\":[],\"variantType\":\"\",\"svlen\":\"0\",\"cipos\":[],\"ciend\":[]},{\"id\":\"\",\"variantSetId\":\"\",\"names\":[\"rs6701114\"],\"created\":\"0\",\"updated\":\"0\",\"referenceName\":\"chrM\",\"start\":\"49\",\"end\":\"50\",\"referenceBases\":\"C\",\"alternateBases\":[\"T\"],\"calls\":[{\"callSetName\":\"NA12878\",\"callSetId\":\"NA12878\",\"phaseset\":\"\",\"genotypeLikelihood\":[],\"genotype\":[\"0\",\"1\"]},{\"callSetName\":\"NA12879\",\"callSetId\":\"NA12879\",\"phaseset\":\"\",\"genotypeLikelihood\":[],\"genotype\":[\"1\",\"1\"]}],\"filtersApplied\":true,\"filtersPassed\":true,\"filtersFailed\":[],\"variantType\":\"\",\"svlen\":\"0\",\"cipos\":[],\"ciend\":[]}],\"nextPageToken\":\"\"}"
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

