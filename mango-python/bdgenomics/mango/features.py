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

r"""
========
Features
========
.. currentmodule:: bdgenomics.mango.features
.. autosummary::
   :toctree: _generate/

   FeatureSummary
"""

import bdgenomics.mango.pileup as pileup
from bdgenomics.mango.pileup.track import *
from bdgenomics.adam.adamContext import ADAMContext
from .utils import *

class FeatureSummary(object):
    """ FeatureSummary class.
    FeatureSummary provides scrollable visualization of features based on genomic regions.
    """

    def __init__(self, ac, rdd):
        """
        Initializes a GenomicRDD viz class.

        Args:
            :param ac: ADAMContext
            :param rdd: AlignmentRecordRDD
        """
        self.ac = ac
        self.rdd = rdd

    # Takes a bdgenomics.adam.FeatureRDD and visualizes results in pileup format
    def viewPileup(self, contig, start, end, reference = 'hg19', label = "Features", showPlot = True):
        """
        Visualizes a portion of this FeatureRDD in a scrollable pileup widget

        Args:
            :param contig: contig of locus to view
            :param start: start position of locus to view
            :param end: end position of locus to view
            reference: genome build. Default is hg19
            label: name of feature track
            showPlot: Disables widget, used for testing. Default is true.

        Returns:
            pileup view for features
        """

        contig_trimmed = contig.lstrip(CHR_PREFIX)

        # Filter RDD
        filtered = self.rdd.transform(lambda r: r.filter(((r.contigName == contig) | (r.contigName == contig_trimmed))
                                                           & (r.start < end) & (r.end > start)))

        # convert to GA4GH JSON to be consumed by mango-viz module
        json = self.ac._jvm.org.bdgenomics.mango.converters.GA4GHutil.featureRDDtoJSON(filtered._jvmRdd)

        # visualize
        if (showPlot):
            # make feature track
            tracks=[Track(viz="features", label=label, source=pileup.sources.GA4GHFeatureJson(json))]
            locus="%s:%i-%i" % (contig, start, end)
            return pileup.PileupViewer(locus=locus, reference=reference, tracks=tracks)


