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

class GenomicVizRDD(object):
    """
    QC provides preprocessing functions for visualization
    of various quality control.
    """

    def __init__(self, ss, build = 'hg19'):
        """
        Initializes a GenomicRDD viz class.
        """
        self.ac = ADAMContext(ss)
        self.build = build
        self.chrPrefix = 'chr'


    # Takes a bdgenomics.AlignmentRecordRDD and visualizes results
    def ViewAlignments(self, alignmentRecordRDD, contig, start, end, showPlot = True):
        contig_trimmed = contig.lstrip(self.chrPrefix)

        # Filter RDD
        filtered = alignmentRecordRDD.transform(lambda r: r.filter(((r.contigName == contig) | (r.contigName == contig_trimmed))
                                                                   & (r.start < end) & (r.end > start) & (r.readMapped)))

        # convert to GA4GH JSON to be consumed by mango-viz module
        json = self.ac._jvm.org.bdgenomics.mango.converters.GA4GHutil.alignmentRecordRDDtoJSON(filtered._jvmRdd)

        # visualize
        if (showPlot):
            return pileup.Reads(json = json, build=self.build, contig=contig,start=start,stop=end)



    # Takes a bdgenomics.adam.VariantContextRDD and visualizes results
    def ViewVariants(self, variantRDD, contig, start, end, showPlot = True):
        contig_trimmed = contig.lstrip(self.chrPrefix)

        # Filter RDD
        filtered = variantRDD.transform(lambda r: r.filter(((r.contigName == contig) | (r.contigName == contig_trimmed))
                                                           & (r.start < end) & (r.end > start)))

        # convert to GA4GH JSON to be consumed by mango-viz module
        json = self.ac._jvm.org.bdgenomics.mango.converters.GA4GHutil.variantRDDtoJSON(filtered._jvmRdd)

        # visualize
        if (showPlot):
            return pileup.Variants(json = json, build=self.build, contig=contig,start=start,stop=end)


    # Takes a bdgenomics.adam.FeatureRDD and visualizes results
    def ViewFeatures(self, featureRDD, contig, start, end, showPlot = True):
        contig_trimmed = contig.lstrip(self.chrPrefix)

        # Filter RDD
        filtered = featureRDD.transform(lambda r: r.filter(((r.contigName == contig) | (r.contigName == contig_trimmed))
                                                           & (r.start < end) & (r.end > start)))

        # convert to GA4GH JSON to be consumed by mango-viz module
        json = self.ac._jvm.org.bdgenomics.mango.converters.GA4GHutil.featureRDDtoJSON(filtered._jvmRdd)

        # visualize
        if (showPlot):
            return pileup.Features(json = json, build=self.build, contig=contig,start=start,stop=end)


