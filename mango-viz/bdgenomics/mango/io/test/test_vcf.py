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
import json
import pandas as pd
from bdgenomics.mango.io.readers import read_vcf
import bdgenomics.mango.pileup as pileup
from bdgenomics.mango.pileup.track import *
from bdgenomics.mango.io.test import IOTestCase

class VCFFileTest(IOTestCase):

    def test_required_columns(self):
        dataframe = read_vcf(self.exampleFile("genodata.v3.vcf"))
        VCF_HEADER = ['CHROM', 'POS', 'ID', 'REF', 'ALT', 'QUAL', 'FILTER', 'INFO', 'FORMAT']
        dataframe_columns = list(dataframe.columns)
        for name in VCF_HEADER:
            assert(name in dataframe_columns)

    def test_column_type(self):
        dataframe = read_vcf(self.exampleFile("genodata.v3.vcf"))
        VCF_HEADER = ['CHROM', 'POS', 'ID', 'REF', 'ALT', 'QUAL', 'FILTER', 'INFO', 'FORMAT']
        def enforce_type(item, type_x):
            assert(type(item) == type_x)

        for head in VCF_HEADER:
            current_type = str
            if (head == 'POS'):
                current_type = "<class 'numpy.int64'>";
            elif (head == 'QUAL'):
                current_type = "<class 'numpy.float64'>";                
            result = [enforce_type, list(dataframe[head])]

    def test_to_json(self):
        dataframe = read_vcf(self.exampleFile("genodata.v3.vcf"))

        def is_valid_json(string):
            try:
                json_object = json.loads(string)
            except ValueError:
                return False
            return True

        assert(type(dataframe._mango_to_json) == str)
        print(dataframe._mango_to_json)
        assert(is_valid_json(dataframe._mango_to_json) == True)

    def test_visualization(self):
        dataframe = read_vcf(self.exampleFile("genodata.v3.vcf"))

        assert(dataframe._pileup_visualization == "variantJson")
        tracks=[Track(viz="variants", label="my variants", source=pileup.sources.DataFrameSource(dataframe))]
        reads = pileup.PileupViewer(locus="chr22:10436-10564", reference="hg19", tracks=tracks)
        assert(str(type(reads)) == '<class \'bdgenomics.mango.pileup.pileupViewer.PileupViewer\'>')


    # Run tests
if __name__ == '__main__':
    unittest.main()
