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
from bdgenomics.mango.io.readers import read_bed
import bdgenomics.mango.pileup as pileup
from bdgenomics.mango.pileup.track import *
from bdgenomics.mango.io.test import IOTestCase


class BedFileTest(IOTestCase):
    global filename
    filename = "chr17.582500-594500.bed"

    
    def test_required_columns(self):
        dataframe = read_bed(self.exampleFile(filename))
        dataframe_columns = list(dataframe.columns)
        for name in ("chrom", "chromStart", "chromEnd"):
            assert(name in dataframe_columns)
    
    def test_column_type(self):
        dataframe = read_bed(self.exampleFile("chr17.582500-594500.bed"))

        chromosomes = list(dataframe["chrom"])
        chromStart = list(dataframe["chromStart"])
        chromEnd = list(dataframe["chromEnd"])

        for i in range(len(chromStart)):
            assert(type(chromStart[i] == int))

        for i in range(len(chromEnd)):
            assert(type(chromEnd[i] == int))

        for i in range(len(chromosomes)):
            assert(type(chromosomes[i] == int))
    
    def test_validate_num_rows(self):
        file = self.exampleFile(filename)
        dataframe = read_bed(file)

        with open(file, "r") as ins:
            lines = []
            for line in ins:
                lines.append(line)
            assert(len(lines)== len(dataframe.index))

    def test_to_json(self):
        dataframe = read_bed(self.exampleFile(filename))

        def is_valid_json(string):
            try:
                json_object = json.loads(string)
            except ValueError:
                return False
            return True

        assert(type(dataframe._mango_to_json) == str)
        assert(is_valid_json(dataframe._mango_to_json) == True)

    def test_visualization(self):
        dataframe = read_bed(self.exampleFile(filename))

        assert(dataframe._pileup_visualization == "featureJson")
        tracks=[Track(viz="features", label="my features", source=pileup.sources.DataFrameSource(dataframe))]
        reads = pileup.PileupViewer(locus="chr22:10436-10564", reference="hg19", tracks=tracks)
        assert(str(type(reads)) == '<class \'bdgenomics.mango.pileup.pileupViewer.PileupViewer\'>')


# Run tests
if __name__ == '__main__':
    unittest.main()
