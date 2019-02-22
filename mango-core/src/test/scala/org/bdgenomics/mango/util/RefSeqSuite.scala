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

import org.bdgenomics.mango.util.MangoFunSuite

class RefSeqSuite extends MangoFunSuite {

  test("Should convert refseq file to GA4GH genes") {

    // downloaded and subsetted from http://hgdownload.soe.ucsc.edu/goldenPath/hg19/database/refGene.txt.gz
    val path = resourcePath("refSeq_subset.txt")

    val genes = RefSeqFile.refSeqFileToGenes(path)

    assert(genes(0).getStart == 75084724)
    assert(genes(0).getEnd == 75091068)
    assert(genes(0).getReferenceName == "chr17")
    assert(genes(0).getName == "SNHG20")
    assert(genes(0).getId == "NR_027058")
    assert(genes(0).getStrand == ga4gh.Common.Strand.STRAND_UNSPECIFIED)
    assert(genes(0).getGeneSymbol == "NR_027058")
    assert(genes(0).getAttributes.getAttr.get("thickStart").getValues(0).getStringValue == "75091068")
    assert(genes(0).getAttributes.getAttr.get("blockSizes").getValues(0).getStringValue == "307,119,1743")

  }

}
