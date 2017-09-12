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
from bdgenomics.mango.QC import QC
from bdgenomics.mango.test import SparkTestCase

from bdgenomics.adam.adamContext import ADAMContext
from bdgenomics.adam.rdd import CoverageRDD


class QCTest(SparkTestCase):


    def test_coverage_distribution(self):

        # load file
        ac = ADAMContext(self.sc)
        testFile = self.resourceFile("small.sam")
        # read alignments

        reads = ac.loadAlignments(testFile)

        # convert to coverage
        coverage = reads.toCoverage()
        # coverage = CoverageRDD(reads._jvmRdd.toCoverage(), self.sc) # TODO change once API is fixed

        qc = QC()

        cd = qc.CoverageDistribution(coverage, False)

        assert(len(cd) == 1)
        assert(cd.pop() == (1.0, 1500))

