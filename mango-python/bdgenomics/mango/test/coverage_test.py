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

        _, cd = qc.plotDistributions(testMode = True)

        assert(len(cd) == 1)
        assert(cd.pop()[1] == 1500)

    def test_example_coverage(self):
        # load file
        ac = ADAMContext(self.ss)
        testFile = self.exampleFile("chr17.7500000-7515000.sam")
        # read alignments

        coverage = ac.loadCoverage(testFile)

        qc = CoverageDistribution(self.ss, coverage)

        _, cd1 = qc.plotDistributions(testMode = True, cumulative = True)

        # first sample
        items = cd1.items()[0][1]
        assert(len(items) == 1)
        x = items.pop()
        assert(x[1] == 6)
        assert(x[2] == 38)

        _, cd2 = qc.plotDistributions(testMode = True, cumulative = False)

        # first sample
        items = cd2.items()[0][1]
        assert(len(items) == 1)
        x = items.pop()
        assert(x[1] == 6)
        assert(x[2] == 32)

        _, cd3 = qc.plotDistributions(testMode = True, cumulative = True, normalize = True)
        total = float(sum(qc.collectedCoverage[0].values()))

        # first sample
        items = cd3.items()[0][1]
        assert(len(items) == 1)
        x = items.pop()
        assert(x[1] == 6.0/total)
        assert(x[2] == 38.0/total)

        _, cd4 = qc.plotDistributions(testMode = True, normalize = True)

        # first sample
        items = cd4.items()[0][1]
        assert(len(items) == 1)
        x = items.pop()
        assert(x[1] == 6.0/total)
        assert(x[2] == 32.0/total)
