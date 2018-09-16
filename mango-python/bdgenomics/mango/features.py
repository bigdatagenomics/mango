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

import bdgenomics.mango.pileup as pileup
from bdgenomics.adam.adamContext import ADAMContext
import utils

class FeatureSummary(object):
    """
    QC provides preprocessing functions for visualization
    of various quality control.
    """

    def __init__(self, ac, rdd):
        """
        Initializes a GenomicRDD viz class.
        """
        self.ac = ac
        self.rdd = rdd

    # Takes a bdgenomics.adam.FeatureRDD and visualizes results in pileup format
    def viewPileup(self, contig, start, end, build = 'hg19', showPlot = True):
        contig_trimmed = contig.lstrip(utils.CHR_PREFIX)

        # Filter RDD
        filtered = self.rdd.transform(lambda r: r.filter(((r.contigName == contig) | (r.contigName == contig_trimmed))
                                                           & (r.start < end) & (r.end > start)))

        # convert to GA4GH JSON to be consumed by mango-viz module
        json = self.ac._jvm.org.bdgenomics.mango.converters.GA4GHutil.featureRDDtoJSON(filtered._jvmRdd)

        # visualize
        if (showPlot):
            return pileup.Features(json = json, build=build, contig=contig,start=start,stop=end)


