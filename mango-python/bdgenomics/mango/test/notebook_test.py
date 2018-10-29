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

from bdgenomics.adam.adamContext import ADAMContext


class NotebookTest(SparkTestCase):

    def test_example(self):
        # these variables are read into mango-python.py
        spark = self.ss
        testMode = True
        alignmentFile = self.exampleFile("chr17.7500000-7515000.sam.adam")
        variantFile = self.exampleFile("snv.chr17.7502100-7502500.vcf")
        featureFile = self.exampleFile("chr17.582500-594500.bed")

        # this file is converted from ipynb in make test
        testFile = self.exampleFile("notebooks/mango-viz.py")
        exec(open(testFile).read())

    def test_coverage_example(self):
        # these variables are read into mango-python.py
        spark = self.ss
        testMode = True
        alignmentFile = self.exampleFile("chr17.7500000-7515000.sam.adam")

        # this file is converted from mango-python.coverage.ipynb in the Makefile
        testCoverageFile = self.exampleFile("notebooks/mango-python-coverage.py")
        exec(open(testCoverageFile).read())

    def test_alignment_example(self):
        # these variables are read into mango-python.py
        spark = self.ss
        testMode = True
        alignmentFile = self.exampleFile("chr17.7500000-7515000.sam.adam")

        # this file is converted from mango-python-alignment.ipynb in the Makefile
        testAlignmentFile = self.exampleFile("notebooks/mango-python-alignment.py")
        exec(open(testAlignmentFile).read())

    def test_variants_example(self):
        # these variables are read into mango-python.py
        spark = self.ss
        testMode = True
        vcfFile = self.exampleFile("genodata.v3.vcf")

        # this file is converted from mango-python-alignment.ipynb in the Makefile
        testVariantFile = self.exampleFile("notebooks/mango-python-variants.py")
        exec(open(testVariantFile).read())
