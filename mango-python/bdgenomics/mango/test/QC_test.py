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
from bdgenomics.mango.QC import CoverageDistribution, AlignmentDistribution
from bdgenomics.mango.test import SparkTestCase
from collections import Counter

from bdgenomics.adam.adamContext import ADAMContext


class QCTest(SparkTestCase):

    def test_alignment_distribution(self):
        # load file
        ac = ADAMContext(self.sc)
        testFile = self.resourceFile("small.sam")
        # read alignments
        reads = ac.loadAlignments(testFile)

        qc = AlignmentDistribution(self.sc, reads, bin_size=10000000)

        mDistribution = qc.plot(testMode = True, plotType="M")
        expectedM =  Counter({(0, 16): 225, (0, 24): 150, (0, 18): 150, (0, 2): 150, (0, 23): 150, (0, 1): 75, (0, 0): 75, (0, 15): 75, (0, 20): 75, (0, 19): 75, (0, 5): 75, (0, 10): 75, (0, 3): 75, (0, 8): 75})
        assert(mDistribution == expectedM)

        iDistribution = qc.plot(testMode = True, plotType="I")
        expectedI =  Counter({(0, 1): 0, (0, 0): 0, (0, 15): 0, (0, 20): 0, (0, 19): 0, (0, 24): 0, (0, 18): 0, (0, 16): 0, (0, 5): 0, (0, 10): 0, (0, 3): 0, (0, 8): 0, (0, 2): 0, (0, 23): 0})
        assert(iDistribution == expectedI)

    def test_alignment_distribution_maximal_bin_size(self):
        # load file
        ac = ADAMContext(self.sc)
        testFile = self.resourceFile("small.sam")
        # read alignments
        reads = ac.loadAlignments(testFile)

        qc = AlignmentDistribution(self.sc, reads, bin_size=1000000000)

        mDistribution = qc.plot(testMode = True, plotType="M")
        expectedM =  Counter({(0, 0): 1500})
        assert(mDistribution == expectedM)

    def test_multiple_alignment_distribution(self):
        # load file
        ac = ADAMContext(self.sc)
        testFile = self.resourceFile("small.sam")
        # read alignments
        reads = ac.loadAlignments(testFile)

        qc = AlignmentDistribution(self.sc, [reads,reads], bin_size=100000000)

        mDistribution = qc.plot(testMode = True, plotType="M")
        expectedM =  Counter({(0, 1): 600, (1, 1): 600, (0, 0): 525, (1, 0): 525, (1, 2): 375, (0, 2): 375})
        assert(mDistribution == expectedM)

    def test_coverage_distribution(self):
        # load file
        ac = ADAMContext(self.sc)
        testFile = self.resourceFile("small.sam")
        # read alignments

        reads = ac.loadAlignments(testFile)

        # convert to coverage
        coverage = reads.toCoverage()

        qc = CoverageDistribution(self.sc, coverage)

        cd = qc.plot(testMode = True)

        assert(len(cd) == 1)
        assert(cd.pop()[1] == 1500)


    def test_normalized_coverage_distribution(self):
        # load file
        ac = ADAMContext(self.sc)
        testFile = self.resourceFile("small.sam")
        # read alignments

        reads = ac.loadAlignments(testFile)

        # convert to coverage
        coverage = reads.toCoverage()

        qc = CoverageDistribution(self.sc, coverage)

        cd = qc.plot(testMode = True, normalize = True)

        assert(len(cd) == 1)
        assert(cd.pop()[1] == 1)


    def test_cumulative_coverage_distribution(self):
        # load file
        ac = ADAMContext(self.sc)
        testFile = self.resourceFile("small.sam")
        # read alignments

        reads = ac.loadAlignments(testFile)

        # convert to coverage
        coverage = reads.toCoverage()

        qc = CoverageDistribution(self.sc, coverage)

        cd = qc.plot(testMode = True, cumulative = True)

        assert(len(cd) == 1)
        assert(cd.pop()[1] == 1500)


    def test_example_alignments(self):
        # load file
        ac = ADAMContext(self.sc)
        testFile = self.exampleFile("chr17.7500000-7515000.sam.adam")
        # read alignments

        reads = ac.loadAlignments(testFile)

        # convert to coverage
        coverage = reads.toCoverage()

        qc = CoverageDistribution(self.sc, coverage)

        cd = qc.plot(testMode = True, cumulative = True)

        assert(len(cd) == 1)
        assert(cd.pop()[1] == 6.0)


    def test_example_coverage(self):
        # load file
        ac = ADAMContext(self.sc)
        testFile = self.exampleFile("chr17.7500000-7515000.sam.coverage.adam")
        # read alignments

        coverage = ac.loadCoverage(testFile)

        qc = CoverageDistribution(self.sc, coverage)

        cd1 = qc.plot(testMode = True, cumulative = True)

        assert(len(cd1) == 1)
        x = cd1.pop()
        assert(x[1] == 6)
        assert(x[2] == 38)

        cd2 = qc.plot(testMode = True, cumulative = False)

        assert(len(cd2) == 1)
        x = cd2.pop()
        assert(x[1] == 6)
        assert(x[2] == 32)

        cd3 = qc.plot(testMode = True, cumulative = True, normalize = True)
        total = float(sum(qc.collectedCoverage[0].values()))

        assert(len(cd3) == 1)
        x = cd3.pop()
        assert(x[1] == 6.0/total)
        assert(x[2] == 38.0/total)

        cd4 = qc.plot(testMode = True, normalize = True)

        assert(len(cd4) == 1)
        x = cd4.pop()
        assert(x[1] == 6.0/total)
        assert(x[2] == 32.0/total)

    def test_coverage_example(self):
        # these variables are read into mango-python.py
        sc = self.sc
        testMode = True
        coverageFile = self.exampleFile("chr17.7500000-7515000.sam.coverage.adam")

        # this file is converted from mango-python.coverage.ipynb in the Makefile
        testCoverageFile = self.exampleFile("mango-python-coverage.py")
        execfile(testCoverageFile)

    def test_alignment_example(self):
        # these variables are read into mango-python.py
        sc = self.sc
        testMode = True
        alignmentFile = self.exampleFile("chr17.7500000-7515000.sam.adam")

        # this file is converted from mango-python-alignment.ipynb in the Makefile
        testAlignmentFile = self.exampleFile("mango-python-alignment.py")
        execfile(testAlignmentFile)
