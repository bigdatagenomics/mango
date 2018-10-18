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
from bdgenomics.mango.variants import *

from bdgenomics.adam.adamContext import ADAMContext
from collections import Counter


class VariantTest(SparkTestCase):

    def test_visualize_variants(self):
        # load file
        ac = ADAMContext(self.ss)
        testFile = self.resourceFile("bqsr1.vcf")

        # read alignments
        variants = ac.loadVariants(testFile)

        variantViz = VariantSummary(ac, variants)

        contig = "chrM"
        start = 1
        end = 2000

        x = variantViz.viewPileup(contig, start, end)
        assert(x != None)

    def test_QualitByDepthDistribution(self):
        #load file
        ac = ADAMContext(self.ss)
        testFile = self.resourceFile("genodata.v3.test.vcf")

        #read variants
        variants = ac.loadVariants(testFile)
        _, data = QualityByDepthDistribution(ac, variants).plot(testMode= True)

        expected = Counter({14.6: 10, 12.6: 8, 13.4: 8, 12.9: 7, 15.2: 7, 14.3: 7, 13.2: 7, 11.0: 5, 14.2: 5, \
                            0.6: 5, 12.1: 5, 13.3: 5, 14.4: 5, 16.5: 5, 11.6: 5, 13.7: 5, 13.1: 5, 11.8: 5, 15.4: 5,\
                            15.5: 4, 15.0: 4, 12.8: 4, 12.3: 4, 19.7: 4, 13.8: 4, 14.5: 4, 14.1: 4, 0.7: 4, 14.7: 4,\
                            9.1: 4, 14.8: 4, 12.2: 4, 12.5: 4, 15.8: 4, 16.3: 4, 10.2: 3, 9.4: 3, 1.1: 3, 13.5: 3,  \
                            5.9: 3, 11.3: 3, 17.6: 3, 1.4: 3, 8.4: 3, 11.7: 3, 16.9: 3, 16.8: 3, 10.4: 3, 10.1: 3,  \
                            2.9: 3, 7.1: 3, 18.5: 2, 6.0: 2, 9.5: 2, 5.5: 2, 4.3: 2, 15.6: 2, 3.9: 2, 12.7: 2, 3.5: 2,\
                            5.4: 2, 7.5: 2, 3.4: 2, 20.2: 2, 15.1: 2, 17.4: 2, 6.6: 2, 9.8: 2, 12.4: 2, 10.9: 2, \
                            6.8: 2, 18.8: 2, 7.9: 2, 6.5: 2, 10.6: 2, 6.4: 2, 16.6: 2, 16.7: 2, 13.6: 2, 10.8: 2,\
                            3.1: 2, 2.4: 2, 4.7: 2, 11.9: 2, 17.9: 2, 6.9: 2, 15.9: 2, 15.3: 2, 18.7: 2, 16.1: 2,\
                            15.7: 2, 9.7: 2, 2.0: 1, 5.0: 1, 1.0: 1, 8.0: 1, 13.0: 1, 14.0: 1, 2.2: 1, 18.0: 1,\
                            20.0: 1, 23.0: 1, 8.3: 1, 0.5: 1, 21.8: 1, 8.2: 1, 18.2: 1, 5.2: 1, 7.0: 1, 17.3: 1,\
                            1.5: 1, 0.4: 1, 33.6: 1, 18.3: 1, 0.8: 1, 27.2: 1, 13.9: 1, 3.2: 1, 1.7: 1, 8.1: 1, 27.4:\
                                1, 21.1: 1, 3.7: 1, 7.7: 1, 7.8: 1, 8.6: 1, 4.4: 1, 19.6: 1, 11.1: 1, 1.2: 1, 20.7: 1,\
                            19.8: 1, 23.1: 1, 23.4: 1, 11.5: 1, 6.7: 1, 21.4: 1, 19.9: 1, 9.9: 1, 16.2: 1, 14.9: 1, \
                            11.4: 1, 0.9: 1, 4.1: 1, 10.7: 1, 17.1: 1, 1.8: 1, 26.5: 1, 8.7: 1, 19.2: 1, 5.3: 1,\
                            20.6: 1, 2.1: 1, 4.8: 1, 8.5: 1, 25.4: 1, 5.1: 1, 18.1: 1, 4.2: 1, 34.6: 1, 11.2: 1,\
                            5.7: 1, 24.7: 1, 18.4: 1, 2.5: 1, 19.1: 1, 20.5: 1, 8.8: 1})
        assert(data==expected)

    def test_HetHomRatioDistribution(self):
        #load file
        ac = ADAMContext(self.ss)
        testFile = self.resourceFile("genodata.v3.test.vcf")

        #read variants
        genotypes = ac.loadGenotypes(testFile)
        _, data = HetHomRatioDistribution(ac, genotypes).plot(testMode= True)

        expected = [5.0, 0.6, 0.14285714285714285, 0.16666666666666666, 1.6666666666666667]
        assert(data == expected)

    def test_GenotypeCallRatesDistribution(self):
        #load file
        ac = ADAMContext(self.ss)
        testFile = self.resourceFile("genodata.v3.test.vcf")

        #read variants
        genotypes = ac.loadGenotypes(testFile)
        _, data = GenotypeCallRatesDistribution(ac, genotypes).plot(testMode= True)

        expected = [0.9505208333333334, 0.8776041666666666, 0.8880208333333334, 0.9401041666666666, 0.9348958333333334, 0.9010416666666666]
        assert(data == expected)

    def test_VariantsPerSampleDistribution(self):
        #load file
        ac = ADAMContext(self.ss)
        testFile = self.resourceFile("genodata.v3.test.vcf")

        #read variants
        genotypes = ac.loadGenotypes(testFile)
        _, data = VariantsPerSampleDistribution(ac, genotypes).plot(testMode= True)

        expected = [6, 8, 8, 1, 7, 8]
        assert(data == expected)