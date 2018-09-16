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
from bdgenomics.mango.alignments import *

from bdgenomics.adam.adamContext import ADAMContext


class AlignmentTest(SparkTestCase):

    def test_visualize_alignments(self):

        # load file
        ac = ADAMContext(self.ss)
        testFile = self.resourceFile("small.sam")

        # read alignments
        reads = ac.loadAlignments(testFile)

        alignmentViz = AlignmentSummary(ac, reads)

        contig = "16"
        start = 26472780
        end = 26482780

        x = alignmentViz.viewPileup(contig, start, end)
        assert(x != None)

    def test_alignment_distribution(self):
        # load file
        ac = ADAMContext(self.ss)
        testFile = self.resourceFile("small.sam")
        # read alignments
        reads = ac.loadAlignments(testFile)

        bin_size = 10000000
        qc = AlignmentDistribution(self.ss, reads, bin_size=10000000)

        mDistribution = qc.plot(testMode = True, plotType="M")
        print(mDistribution)
        expectedM =  Counter({('1', 16 * bin_size): 225, ('1', 24 * bin_size): 150, ('1', 18 * bin_size): 150, ('1', 2 * bin_size): 150, \
                              ('1', 23 * bin_size): 150, ('1', 1 * bin_size): 75, ('1', 0 * bin_size): 75, ('1', 15 * bin_size): 75, ('1', 20 * bin_size): 75, \
                              ('1', 19 * bin_size): 75, ('1', 5 * bin_size): 75, ('1', 10 * bin_size): 75, ('1', 3 * bin_size): 75, ('1', 8 * bin_size): 75})
        assert(mDistribution == expectedM)

        iDistribution = qc.plot(testMode = True, plotType="I")
        print(iDistribution)
        expectedI =  Counter({('1', 1 * bin_size): 0, ('1', 0 * bin_size): 0, ('1', 15 * bin_size): 0, ('1', 20 * bin_size): 0, \
                              ('1', 19 * bin_size): 0, ('1', 24 * bin_size): 0, ('1', 18 * bin_size): 0, ('1', 16 * bin_size): 0, ('1', 5 * bin_size): 0,
                              ('1', 10 * bin_size): 0, ('1', 3 * bin_size): 0, ('1', 8 * bin_size): 0, ('1', 2 * bin_size): 0, ('1', 23 * bin_size): 0})
        assert(iDistribution == expectedI)

    def test_alignment_distribution_maximal_bin_size(self):
        # load file
        ac = ADAMContext(self.ss)
        testFile = self.resourceFile("small.sam")
        # read alignments
        reads = ac.loadAlignments(testFile)

        qc = AlignmentDistribution(self.ss, reads, bin_size=1000000000)

        mDistribution = qc.plot(testMode = True, plotType="M")
        expectedM =  Counter({('1', 0): 1500})
        assert(mDistribution == expectedM)


    def test_alignment_distribution_no_elements(self):
        # load file
        ac = ADAMContext(self.ss)
        testFile = self.resourceFile("small.sam")
        # read alignments
        reads = ac.loadAlignments(testFile)

        qc = AlignmentDistribution(self.ss, reads, bin_size=1000000000, sample=0.00001)

        mDistribution = qc.plot(testMode = True, plotType="D")
        expectedM =  Counter({('1', 0): 0})
        assert(mDistribution == expectedM)