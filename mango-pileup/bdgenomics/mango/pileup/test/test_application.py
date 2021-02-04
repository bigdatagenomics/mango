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
from bdgenomics.mango.pileup.test import PileupTestCase

class MangoVizTest(PileupTestCase):

    def test_genotypes(self):
        track = pileup.Track(viz="genotypes", label="myGenotypes", source=pileup.sources.VcfDataSource('{}'))

        x = pileup.PileupViewer(chrom="chr22",start=21340030,stop=21340150, reference="hg19", tracks=[track])
        assert(x.reference == 'hg19')
        assert(x.tracks[0] == track)

    def test_features(self):
        track = pileup.Track(viz="features", label="myFeatures", source=pileup.sources.GA4GHFeatureJson('{}'))

        x = pileup.PileupViewer(chrom="chr17",start=1,stop=250, reference="hg19", tracks=[track])
        assert(x.reference == 'hg19')
        assert(x.tracks[0] == track)

    def test_variants(self):
        track = pileup.Track(viz="variants", label="myVariants", source=pileup.sources.GA4GHVariantJson('{}'))

        x = pileup.PileupViewer(chrom="chr17",start=1,stop=250, reference="hg19", tracks=[track])
        assert(x.reference == 'hg19')
        assert(x.tracks[0] == track)

    def test_pileup(self):
        track = pileup.Track(viz="pileup", label="myReads", source=pileup.sources.GA4GHAlignmentJson('{}'))

        x = pileup.PileupViewer(chrom="chr17",start=1,stop=250, reference="hg19", tracks=[track])
        assert(x.reference == 'hg19')
        assert(x.tracks[0] == track)

    def test_genes(self):
        track = pileup.Track(viz="genes", label="myGenes", source=pileup.sources.BigBedDataSource('fakeGenes.bb'))

        x = pileup.PileupViewer(chrom="chr17",start=1,stop=250, reference="hg19", tracks=[track])
        assert(x.reference == 'hg19')
        assert(x.tracks[0] == track)

    def test_pileup(self):
        track = pileup.Track(viz="pileup", label="myReads", source=pileup.sources.GA4GHAlignmentJson('{}'))

        x = pileup.PileupViewer(chrom="chr17",start=1,stop=250, reference="hg19", tracks=[track])
        assert(x.reference == 'hg19')
        assert(x.tracks[0] == track)

    def test_genes(self):
        track = pileup.Track(viz="genes", label="myGenes", source=pileup.sources.BigBedDataSource('fakeGenes.bb'))

        x = pileup.PileupViewer(chrom="chr17",start=1,stop=250, reference="hg19", tracks=[track])
        assert(x.reference == 'hg19')
        assert(x.tracks[0] == track)

    # TODO: browser can't be used in these tests.
    def test_zoomOut(self):
        track = pileup.Track(viz="genes", label="myGenes", source=pileup.sources.BigBedDataSource('fakeGenes.bb'))
        x = pileup.PileupViewer(chrom="chr17",start=1,stop=250, reference="hg19", tracks=[track])
        x.zoomOut()

    def test_zoomIn(self):
        track = pileup.Track(viz="genes", label="myGenes", source=pileup.sources.BigBedDataSource('fakeGenes.bb'))
        x = pileup.PileupViewer(chrom="chr17",start=1,stop=250, reference="hg19", tracks=[track])
        x.zoomIn()

    def test_toSVG(self):
        track = pileup.Track(viz="genes", label="myGenes", source=pileup.sources.BigBedDataSource('fakeGenes.bb'))
        x = pileup.PileupViewer(chrom="chr17",start=1,stop=250, reference="hg19", tracks=[track])
        x.getSVG()

# Run tests
if __name__ == '__main__':
    unittest.main()
