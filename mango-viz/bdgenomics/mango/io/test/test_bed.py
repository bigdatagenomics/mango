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
from bdgenomics.mango.io import *
import bdgenomics.mango.pileup as pileup
from bdgenomics.mango.pileup.track import *

class BedFileTest(unittest.TestCase):
    global dataframe 
    dataframe = read_bed("../../../../examples/data/chr17.582500-594500.bed")
    
    def test_required_columns(self):
        dataframe_columns = list(dataframe.columns)
        for name in ("chrom", "chromStart", "chromEnd"):
            assert(name in dataframe_columns)

    def test_column_type(self):
        #dataframe = read_bed("../../../../examples/data/chr17.582500-594500.bed")
        chromosomes = list(dataframe["chrom"])
        chromStart = list(dataframe["chromStart"])
        chromEnd = list(dataframe["chromEnd"])

        d1, d2, d3 = dataframe._mango_parse
        for i in range(len(chromStart)):
            assert(type(d1[i] == int))
            assert(d1[i]==chromStart[i])

        for i in range(len(chromEnd)):
            assert(type(d2[i] == int))
            assert(d2[i] == chromEnd[i])

        for i in range(len(chromosomes)):
            assert(type(d3[i] == int))
            assert(d3[i] == chromosomes[i])

    
    def test_validate_num_rows(self):
        #dataframe = read_bed("../../../../examples/data/chr17.582500-594500.bed")
        filename = "../../../../examples/data/chr17.582500-594500.bed"
        with open(filename, "r") as ins:
            lines = []
            for line in ins:
                lines.append(line)
            assert(len(lines)== len(dataframe.index))

    def test_to_json(self):

        def is_valid_json(string):
            try:
                json_object = json.loads(string)
            except ValueError:
                return False
            return True

        #dataframe = read_bed("../../../../examples/data/chr17.582500-594500.bed")
        assert(type(dataframe._mango_to_json) == str)
        assert(is_valid_json(dataframe._mango_to_json) == True)

    def test_visualization(self):
        assert(dataframe._pileup_visualization == "featureJson")
        tracks=[Track(viz="features", label="my features", source=pileup.sources.DataFrameSource(dataframe))]
        reads = pileup.PileupViewer(locus="chr22:10436-10564", reference="hg19", tracks=tracks)
        assert(str(type(reads)) == '<class \'bdgenomics.mango.pileup.pileupViewer.PileupViewer\'>')


    
        

# Run tests
if __name__ == '__main__':
    unittest.main()
    