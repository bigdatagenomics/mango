#
# Licensed to Big Data Genomics (BDG) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The BDG licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

from bdgenomics.mango.test import SparkTestCase
from bdgenomics.mango.rdd import GenomicVizRDD

from bdgenomics.adam.adamContext import ADAMContext


class GenomicVizRDDTest(SparkTestCase):

    def test_visualize_alignments(self):
        genomicRDD = GenomicVizRDD(self.ss)

        # load file
        ac = ADAMContext(self.ss)
        testFile = self.resourceFile("small.sam")

        # read alignments
        reads = ac.loadAlignments(testFile)

        contig = "16"
        start = 26472780
        end = 26482780

        genomicRDD.ViewAlignments(reads, contig, start, end)
        assert(True)


    def test_visualize_variants(self):
        genomicRDD = GenomicVizRDD(self.ss)

        # load file
        ac = ADAMContext(self.ss)
        testFile = self.resourceFile("bqsr1.vcf")

        # read alignments
        variants = ac.loadVariants(testFile)

        contig = "chrM"
        start = 1
        end = 2000

        genomicRDD.ViewVariants(variants, contig, start, end)
        assert(True)


    def test_visualize_features(self):
        genomicRDD = GenomicVizRDD(self.ss)

        # load file
        ac = ADAMContext(self.ss)
        testFile = self.resourceFile("smalltest.bed")

        # read alignments
        features = ac.loadFeatures(testFile)

        contig = "chrM"
        start = 1
        end = 2000

        x = genomicRDD.ViewFeatures(features, contig, start, end)
        assert(True)



    def test_example(self):
        # these variables are read into mango-python.py
        spark = self.ss
        testMode = True
        alignmentFile = self.exampleFile("chr17.7500000-7515000.sam.adam")
        variantFile = self.exampleFile("snv.chr17.7502100-7502500.vcf")
        featureFile = self.exampleFile("chr17.582500-594500.bed")

        # this file is converted from ipynb in make test
        testFile = self.exampleFile("notebooks/mango-viz.py")
        execfile(testFile)
