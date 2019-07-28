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
from bdgenomics.mango.io import *

class BedFileTest(unittest.TestCase):

    dataframe = io.read_bed("../../../examples/data/chr17.582500-594500.bed")

    def test_columns():
        dataframe_columns = list(dataframe.columns)
        for name in ("chrom", "chromStart", "chromEnd"):
            assert(name in dataframe_columns)

    def test_correct_format():
        chromosomes = list(dataframe["chrom"])
        chromStart = list(dataframe["chromStart"])
        chromEnd = list(dataframe["chromEnd"])
        for item in chromsomes:
            assert(type(item) == str)
        for item in chromStart:
            assert(type(item) == int)
        for item in chromEnd:
            assert(type(item) == int)
        
    def test_to_json():
        assert(type(dataframe._mango_to_json) == str)
        

    
# Run tests
if __name__ == '__main__':
    unittest.main()