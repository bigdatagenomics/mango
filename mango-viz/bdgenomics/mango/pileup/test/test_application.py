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
import bdgenomics.mango.pileup as pileup


class MangoVizTest(unittest.TestCase):

    def test_features(self):
        start = 120000
        stop = 121000
        x = pileup.Features(json = "{}", build='hg19', contig='chr1',start=start,stop=stop)
        assert(x.start == start)
        assert(x.stop == stop)

    def test_variants(self):
        start = 120000
        stop = 121000
        x = pileup.Features(json = "{}", build='hg19', contig='chr1',start=start,stop=stop)
        assert(x.start == start)
        assert(x.stop == stop)

    def test_reads(self):
        start = 120000
        stop = 121000
        x = pileup.Reads(json = "{}", build='hg19', contig='chr17',start=start,stop=stop)
        assert(x.start == start)
        assert(x.stop == stop)


# Run tests
if __name__ == '__main__':
    unittest.main()