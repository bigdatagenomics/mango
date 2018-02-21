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

    def test_coverage_distribution(self):
        # load file
        ac = ADAMContext(self.ss)
        testFile = self.resourceFile("small.sam")
        # read alignments

        reads = ac.loadAlignments(testFile)

        # convert to coverage
        coverage = reads.toCoverage()

        qc = CoverageDistribution(self.ss, coverage)

        cd = qc.plot(testMode = True)

        assert(len(cd) == 1)
        assert(cd.pop()[1] == 1500)


    def test_normalized_coverage_distribution(self):
        # load file
        ac = ADAMContext(self.ss)
        testFile = self.resourceFile("small.sam")
        # read alignments

        reads = ac.loadAlignments(testFile)

        # convert to coverage
        coverage = reads.toCoverage()

        qc = CoverageDistribution(self.ss, coverage)

        cd = qc.plot(testMode = True, normalize = True)

        assert(len(cd) == 1)
        assert(cd.pop()[1] == 1)


    def test_cumulative_coverage_distribution(self):
        # load file
        ac = ADAMContext(self.ss)
        testFile = self.resourceFile("small.sam")
        # read alignments

        reads = ac.loadAlignments(testFile)

        # convert to coverage
        coverage = reads.toCoverage()

        qc = CoverageDistribution(self.ss, coverage)

        cd = qc.plot(testMode = True, cumulative = True)

        assert(len(cd) == 1)
        assert(cd.pop()[1] == 1500)


    def test_example_alignments(self):
        # load file
        ac = ADAMContext(self.ss)
        testFile = self.exampleFile("chr17.7500000-7515000.sam.adam")
        # read alignments

        reads = ac.loadAlignments(testFile)

        # convert to coverage
        coverage = reads.toCoverage()

        qc = CoverageDistribution(self.ss, coverage)

        cd = qc.plot(testMode = True, cumulative = True)

        assert(len(cd) == 1)
        assert(cd.pop()[1] == 6.0)


    def test_example_coverage(self):
        # load file
        ac = ADAMContext(self.ss)
        testFile = self.exampleFile("chr17.7500000-7515000.sam.coverage.adam")
        # read alignments

        coverage = ac.loadCoverage(testFile)

        qc = CoverageDistribution(self.ss, coverage)

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
        spark = self.ss
        testMode = True
        coverageFile = self.exampleFile("chr17.7500000-7515000.sam.coverage.adam")

        # this file is converted from mango-python.coverage.ipynb in the Makefile
        testCoverageFile = self.exampleFile("notebooks/mango-python-coverage.py")
        execfile(testCoverageFile)

    def test_alignment_example(self):
        # these variables are read into mango-python.py
        spark = self.ss
        testMode = True
        alignmentFile = self.exampleFile("chr17.7500000-7515000.sam.adam")

        # this file is converted from mango-python-alignment.ipynb in the Makefile
        testAlignmentFile = self.exampleFile("notebooks/mango-python-alignment.py")
        execfile(testAlignmentFile)
