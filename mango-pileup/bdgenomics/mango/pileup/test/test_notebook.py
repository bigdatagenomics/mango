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

import unittest
from bdgenomics.mango.pileup.test import PileupTestCase

class MangoVizExampleTest(PileupTestCase):

    def test_notebook_example(self):

        # these variables are read into mango-tutorial.py
        bedFile = self.exampleFile("chr17.582500-594500.bed")
        alignmentJsonFile = self.dataFile("alignments.ga4gh.chr17.1-250.json") # TODO
        vcfFile = self.dataFile("genodata.v3.vcf")
        testMode = True

    # this file is converted from mango-python-alignment.ipynb in the Makefile
        pileupFile = self.notebookFile("pileup-tutorial.py")
        exec(open(pileupFile).read())


# Run tests
if __name__ == '__main__':
    unittest.main()
