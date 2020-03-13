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

        alignmentViz = AlignmentSummary(self.ss, ac, reads)

        contig = "16"
        start = 26472780
        end = 26482780

        x = alignmentViz.viewPileup(contig, start, end)
        assert(x != None)

    def test_indel_distribution(self):
        # load file
        ac = ADAMContext(self.ss)
        testFile = self.resourceFile("small.sam")
        # read alignments
        reads = ac.loadAlignments(testFile)

        bin_size = 10000000
        summary = AlignmentSummary(self.ss, ac, reads)

        indels = summary.getIndelDistribution(bin_size=10000000)

        _, mDistribution = indels.plot(testMode = True, plotType="M")
        expectedM =  Counter({('1', 16 * bin_size): 225, ('1', 24 * bin_size): 150, ('1', 18 * bin_size): 150, ('1', 2 * bin_size): 150, \
                              ('1', 23 * bin_size): 150, ('1', 1 * bin_size): 75, ('1', 0 * bin_size): 75, ('1', 15 * bin_size): 75, ('1', 20 * bin_size): 75, \
                              ('1', 19 * bin_size): 75, ('1', 5 * bin_size): 75, ('1', 10 * bin_size): 75, ('1', 3 * bin_size): 75, ('1', 8 * bin_size): 75})
        assert(mDistribution == expectedM)

        _, iDistribution = indels.plot(testMode = True, plotType="I")
        expectedI =  Counter({('1', 1 * bin_size): 0, ('1', 0 * bin_size): 0, ('1', 15 * bin_size): 0, ('1', 20 * bin_size): 0, \
                              ('1', 19 * bin_size): 0, ('1', 24 * bin_size): 0, ('1', 18 * bin_size): 0, ('1', 16 * bin_size): 0, ('1', 5 * bin_size): 0,
                              ('1', 10 * bin_size): 0, ('1', 3 * bin_size): 0, ('1', 8 * bin_size): 0, ('1', 2 * bin_size): 0, ('1', 23 * bin_size): 0})
        assert(iDistribution == expectedI)

    def test_indel_distribution_maximal_bin_size(self):
        # load file
        ac = ADAMContext(self.ss)
        testFile = self.resourceFile("small.sam")
        # read alignments
        reads = ac.loadAlignments(testFile)

        summary = AlignmentSummary(self.ss, ac, reads)

        indels = summary.getIndelDistribution(bin_size=1000000000)

        _, mDistribution = indels.plot(testMode = True, plotType="M")
        expectedM =  Counter({('1', 0): 1500})
        assert(mDistribution == expectedM)


    def test_indel_distribution_no_elements(self):
        # load file
        ac = ADAMContext(self.ss)
        testFile = self.resourceFile("small.sam")
        # read alignments
        reads = ac.loadAlignments(testFile)

        summary = AlignmentSummary(self.ss, ac, reads, sample=0.00001)

        indels = summary.getIndelDistribution(bin_size=1000000000)

        _, dDistribution = indels.plot(testMode = True, plotType="D")
        expectedD =  Counter()
        assert(dDistribution == expectedD)

    def test_coverage_distribution(self):
        # load file
        ac = ADAMContext(self.ss)
        testFile = self.resourceFile("small.sam")
        # read alignments
        reads = ac.loadAlignments(testFile)

        summary = AlignmentSummary(self.ss, ac, reads)

        coverage = summary.getCoverageDistribution(bin_size = 1)
        _, cd = coverage.plotDistributions(testMode = True, cumulative = False, normalize=False)

        # first sample
        items = list(cd.popitem()[1])
        assert(len(items) == 1)
        x = items.pop()
        assert(x[0] == 1) # all positions have coverage of 1
        assert(x[0] == 1) # all positions have coverage of 1
        assert(x[1] == 1500)


    def test_fragment_distribution(self):

        # load file
        ac = ADAMContext(self.ss)
        testFile = self.resourceFile("small.sam")
        # read alignments
        reads = ac.loadAlignments(testFile)

        summary = AlignmentSummary(self.ss, ac, reads)

        fragments = summary.getFragmentDistribution()
        _, cd = fragments.plotDistributions(testMode = True, cumulative = False, normalize=False)

        # first sample
        items = list(cd.popitem()[1])
        assert(len(items) == 1)
        x = items[0]
        assert(x[0] == 75)
        assert(x[1] == 20) # all 20 sequences have same length of 75

    def test_mapq_distribution(self):

        # load file
        ac = ADAMContext(self.ss)
        testFile = self.resourceFile("small.sam")
        # read alignments
        reads = ac.loadAlignments(testFile)

        summary = AlignmentSummary(self.ss, ac, reads)

        mapq = summary.getMapQDistribution()
        _, md = mapq.plotDistributions(testMode = True, cumulative = False, normalize = False)

        # first sample
        items = list(md.popitem()[1])
        assert(len(items) == 5) # mapq for 24, 28, 35, 40, 60
        x = items[0]
        assert(x[0] == 24) # 1 read with mapq 24
        assert(x[1] == 1)
