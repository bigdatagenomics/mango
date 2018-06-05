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

import sys
from collections import Counter
from collections import defaultdict

from bdgenomics.mango.test import SparkTestCase
from bdgenomics.adam.adamContext import ADAMContext
from bdgenomics.mango.variantViz import VariantEffectCounts
from bdgenomics.mango.variantViz import VariantCountByGene
from bdgenomics.mango.variantViz import QDDist
from bdgenomics.mango.variantViz import VariantEffectAlleleFreq

class QCTest(SparkTestCase):

    def test_VariantEffectCounts(self):
        # load file
        ac = ADAMContext(self.ss)
        testFile = self.resourceFile("exome_chr22.adam")
        # read alignments
        variants = ac.loadVariants(testFile)

        #bin_size = 10000000
        qc = VariantEffectCounts(self.ss, variants)

        result = qc.plot(testMode = True)

        print(result)
        expected =  {u'3_prime_UTR_variant': 2243,
                                u'5_prime_UTR_premature_start_codon_gain_variant': 4488,
                                u'5_prime_UTR_variant': 6801,
                                u'conservative_inframe_deletion': 554,
                                u'conservative_inframe_insertion': 351,
                                u'disruptive_inframe_deletion': 812,
                                u'disruptive_inframe_insertion': 328,
                                u'frameshift_variant': 2572,
                                u'missense_variant': 70823,
                                u'non_coding_transcript_exon_variant': 9538,
                                u'splice_acceptor_variant': 858,
                                u'splice_donor_variant': 1026,
                                u'splice_region_variant': 14907,
                                u'start_lost': 185,
                                u'stop_gained': 1579,
                                u'stop_lost': 55,
                                u'stop_retained_variant': 32,
                                u'synonymous_variant': 42892}

        assert(result == expected)


    def test_VariantCountByGene(self):
        # load file
        ac = ADAMContext(self.ss)
        testFile = self.resourceFile("exome_chr22.adam")
        # read alignments
        variants = ac.loadVariants(testFile)
        qc = VariantCountByGene(self.ss, variants)
        result = qc.plot(testMode = True)
        print(result)
        expected = [250,71,11,13,57,62,183,172,109,40,10,9,77,7,75,149,7,75,60,247,85,60,33,243,31,70,74,63,48,96,107
            ,50,16,34,24,20,95,76,47,6,38,165,48,82,54,130,7,50,59,61,22,42,70,136,98,18,97,85,92,181,29,25,19,93,60,
                    31,94,43,35,86,11,58,49,72,156,55,61,25,98,49,71,93,65,12,51,17,75,100,104,41,45,21,20,37,79,10,43,
                    97,17,39,50,17,94,111,42,29,167,26,72,112,27,23,43,41,221,89,45,68,43,38,39,52,64,122,26,34,35,63,
                    44,151,70,41,67,188,30,55,106,58,54,38,130,269,32,20,65,130,28,72,34,156,40,111,41,143,48,35,75,93,
                    77,194,225,198,57,30,26,41,72,84,23,465,79,46,81,172,42,15,166,85,63,74,35,332,43,26,116,72,120,379,
                    56,111,87,81,49,52,90,138,120,70,58,102,61,62,144,29,260,13,98,131,107,34,66,32,44,138,61,354,35,
                    88,120,19,105,129,69,52,16,119,73,41,12,221,84,61,240,122,33,48,48,187,11,22,64,52,59,76,204,325,
                    45,53,78,152,46,79,287,63,28,64,45,4,74,45,27,36,84,22,23,85,122,74,88,22,3,179,94,17,89,101,29,
                    16,91,27,89,23,252,6,546,22,113,112,91,98,91,82,75,138,58,36,49,94,63,171,92,29,154,23,86,107,5,
                    134,1,40,83,21,174,21,84,77,28,3,33,93,75,48,32,77,21,97,14,41,49,165,273,23,165,65,22,105,52,60,
                    68,56,79,142,65,121,146,25,48,44,81,100,24,69,49,68,76,142,88,65,33,51,86,54,56,33,34,74,11,13,91,
                    121,55,92,84,51,75,138,139,38,37,74,51,316,65,57,38,108,53,30,30,110,26,77,145,67,61,36,46,20,69,
                    33,109,20,36,16,59,171,124,278,42,81,104,70,32,71,58,15,126,585,47,32,32,171,49,66,76,164]
        assert(result == expected)


    def test_QDDist(self):
        # load file
        ac = ADAMContext(self.ss)
        testFile = self.resourceFile("exome_chr22.adam")
        # read alignments
        variants = ac.loadVariants(testFile)
        qc = QDDist(self.ss, variants)
        result = qc.plot(testMode = True)
        print(result)
        expected = ([0.0,1.0,2.0,3.0,4.0,5.0,6.0,7.0,8.0,9.0,10.0,11.0,12.0,13.0,14.0,15.0,16.0,17.0,18.0,19.0,20.0,
                     21.0,22.0,23.0,24.0,25.0,26.0,27.0,28.0,29.0,30.0,31.0,32.0,33.0,34.0,35.0,36.0,37.0,39.0],
                    [438,651,696,874,1136,1615,2305,3509,5024,7012,9585,11389,9548,5076,3049,1865,1166,882,637,430,
                     259,172,114,91,104,56,44,49,34,63,28,37,33,20,6,3,3,1,1])
        assert(result == expected)


    def test_VariantEffectAlleleFreq(self):
        # load file
        ac = ADAMContext(self.ss)
        testFile = self.resourceFile("exome_chr22.adam")
        # read alignments
        variants = ac.loadVariants(testFile)
        qc = VariantEffectAlleleFreq(self.ss, variants)
        result = qc.plot(testMode = True)
        print(result)
        expected = ([0.0,0.0001,0.0002,0.0003,0.0004,0.0005,0.0006,0.0007,0.0008,0.0009,0.001,0.0011,0.0012,
                     0.0013,0.0014,0.0015,0.0016,0.0017,0.0018,0.0019],
                    [1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 3, 80])
        assert(result == expected)