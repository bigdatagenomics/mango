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

from bdgenomics.mango.coverage import CoverageDistribution
from bdgenomics.mango.test import SparkTestCase

from bdgenomics.adam.adamContext import ADAMContext


class DistributionTest(SparkTestCase):

    def test_normalized_count_distribution(self):
        # load file
        ac = ADAMContext(self.ss)
        testFile = self.resourceFile("small.sam")
        # read alignments

        reads = ac.loadAlignments(testFile)

        # convert to coverage
        coverage = reads.toCoverage()

        qc = CoverageDistribution(self.ss, coverage)

        _, cd = qc.plotDistributions(testMode = True, normalize = True)
        items = list(cd.popitem()[1])
        assert(len(items) == 1)
        assert(items.pop()[1] == 1.0)

        _, cd = qc.plotDistributions(testMode = True, normalize = False)
        items = list(cd.popitem()[1])
        assert(len(items) == 1)
        assert(items.pop()[1] == 1500)


    def test_cumulative_count_distribution(self):
        # load file
        ac = ADAMContext(self.ss)
        testFile = self.resourceFile("small.sam")
        # read alignments

        reads = ac.loadAlignments(testFile)

        # convert to coverage
        coverage = reads.toCoverage()

        qc = CoverageDistribution(self.ss, coverage)

        _, cd = qc.plotDistributions(testMode = True, cumulative = True, normalize = False)

        # first sample
        items = list(cd.popitem()[1])
        assert(len(items) == 1)
        assert(items.pop()[1] == 1500)

        _, cd = qc.plotDistributions(testMode = True, cumulative = False, normalize = False)

        # first sample
        items = list(cd.popitem()[1])
        assert(len(items) == 1)
        assert(items.pop()[1] == 1500)

    def test_fail_on_invalid_sample(self):
        # load file
        ac = ADAMContext(self.ss)
        testFile = self.resourceFile("small.sam")
        # read alignments

        reads = ac.loadAlignments(testFile)

        # convert to coverage
        coverage = reads.toCoverage()

        with self.assertRaises(Exception):
            CoverageDistribution(self.ss, coverage, sample = 1.2)
            CoverageDistribution(self.ss, coverage, sample = 0)

    def test_sampling(self):
        # load file
        ac = ADAMContext(self.ss)
        testFile = self.resourceFile("small.sam")
        # read alignments

        reads = ac.loadAlignments(testFile)

        # convert to coverage
        coverage = reads.toCoverage()

        cd1 = CoverageDistribution(self.ss, coverage, sample = 0.9)
        sum1 = sum(map(lambda x: x[1], cd1.collectedCounts.popitem()[1]))
        cd2 = CoverageDistribution(self.ss, coverage, sample = 1.0)
        sum2 = sum(map(lambda x: x[1], cd2.collectedCounts.popitem()[1]))

        # estimated counts should be around real counts
        dev = 500
        assert(sum1 > sum2 - dev and sum1 < sum2 + dev)
