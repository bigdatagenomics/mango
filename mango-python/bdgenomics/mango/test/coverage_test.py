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
from bdgenomics.mango.coverage import *
from bdgenomics.mango.test import SparkTestCase
from collections import Counter

from bdgenomics.adam.adamContext import ADAMContext


class CoverageTest(SparkTestCase):

    def test_coverage_distribution(self):
        # load file
        ac = ADAMContext(self.ss)
        testFile = self.resourceFile("small.sam")
        # read alignments

        reads = ac.loadAlignments(testFile)

        # convert to coverage
        coverage = reads.toCoverage()

        qc = CoverageDistribution(self.ss, coverage)

        _, cd = qc.plotDistributions(testMode = True, normalize=False)

        assert(len(cd) == 1)

        # all items for first sample
        items = cd.items()[0][1]

        print("Coverage")
        print(cd)
        assert(items[0][1] == 1500)

    def test_example_coverage(self):
        # load file
        ac = ADAMContext(self.ss)
        testFile = self.exampleFile("chr17.7500000-7515000.sam")
        # read alignments
        alignments = ac.loadAlignments(testFile)
        coverage = alignments.toCoverage()

        qc = CoverageDistribution(self.ss, coverage)
        # sum of all coverage
        total = sum(map(lambda x: x[1], qc.collectedCounts.items()[0][1]))

        _, cd1 = qc.plotDistributions(testMode = True, cumulative = False, normalize=False)

        # first sample
        items = cd1.items()[0][1]
        x = items[0]
        assert(x[0] == 1) # 6 locations with read depth 1
        assert(x[1] == 6)

        _, cd2 = qc.plotDistributions(testMode = True, cumulative = False, normalize=True)

        # first sample
        items = cd2.items()[0][1]
        x = items[0]
        assert(x[0] == 1)
        assert(x[1] == 6.0/total) # normalized value

        _, cd3 = qc.plotDistributions(testMode = True, cumulative = True, normalize = True)

        # first sample
        items = cd3.items()[0][1]
        x = items[-1]
        assert(x[0] == 89)
        assert(x[1] > 0.999) # cumulative and normalized, so last value shound be about 1
