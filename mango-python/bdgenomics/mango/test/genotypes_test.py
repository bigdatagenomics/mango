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
from bdgenomics.mango.genotypes import *

from bdgenomics.adam.adamContext import ADAMContext


class GenotypesTest(SparkTestCase):

    def test_VariantsPerSampleDistribution(self):
        ac = ADAMContext(self.ss)
        testFile = self.resourceFile("genodata.v3.test.vcf")

        genotypes = ac.loadGenotypes(testFile)
        _, data = VariantsPerSampleDistribution(self.ss, genotypes).plotDistributions(testMode= True)

        expected = [6, 8, 8, 1, 7, 8]
        assert(sum(data) == sum(expected))


    def test_VariantsPerSampleDistributionSampling(self):
        ac = ADAMContext(self.ss)
        testFile = self.resourceFile("genodata.v3.test.vcf")

        genotypes = ac.loadGenotypes(testFile)
        _, data = VariantsPerSampleDistribution(self.ss, genotypes, sample=0.9).plotDistributions(testMode= True)

        expected = [6, 8, 8, 1, 7, 8]

        # estimated counts should be around real counts
        dev = 8
        assert(sum(expected) > sum(data) - dev and sum(expected) < sum(data) + dev)


    def test_GenotypeSummary(self):
        ac = ADAMContext(self.ss)
        testFile = self.resourceFile("genodata.v3.test.vcf")

        genotypes = ac.loadGenotypes(testFile)
        gs =  GenotypeSummary(self.ss, ac, genotypes)

        _, data = gs.getVariantsPerSampleDistribution().plotDistributions(testMode= True)

        expected = [6, 8, 8, 1, 7, 8]
        assert(sum(data) == sum(expected))
