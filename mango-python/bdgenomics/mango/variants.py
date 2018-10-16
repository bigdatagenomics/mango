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
Variants
========
.. currentmodule:: bdgenomics.mango.variants
.. autosummary::
   :toctree: _generate/

   VariantSummary
"""

import bdgenomics.mango.pileup as pileup
import matplotlib.pyplot as plt
import math
from bdgenomics.mango.pileup.track import *
from bdgenomics.adam.adamContext import ADAMContext
from collections import Counter, OrderedDict
from .utils import *

default_bins=100

class VariantSummary(object):
    """ VariantSummary class.
    VariantSummary provides scrollable visualization of variants based on genomic regions.
    """

    def __init__(self, ac, rdd):
        """
        Initializes a GenomicRDD viz class.
        """
        self.ac = ac
        self.rdd = rdd

    # Takes a bdgenomics.adam.VariantContextRDD and visualizes results
    def viewPileup(self, contig, start, end, reference = 'hg19', label = "Variants", showPlot = True):
        """
        Visualizes a portion of this VariantRDD in a scrollable pileup widget

        Args:
            :param contig: contig of locus to view
            :param start: start position of locus to view
            :param end: end position of locus to view
            reference: genome build. Default is hg19
            label: name of variant track
            showPlot: Disables widget, used for testing. Default is true.

        Returns:
            pileup view for variants
        """
        contig_trimmed = contig.lstrip(CHR_PREFIX)

        # Filter RDD
        filtered = self.rdd.transform(lambda r: r.filter(((r.contigName == contig) | (r.contigName == contig_trimmed))
                                                           & (r.start < end) & (r.end > start)))

        # convert to GA4GH JSON to be consumed by mango-viz module
        json = self.ac._jvm.org.bdgenomics.mango.converters.GA4GHutil.variantRDDtoJSON(filtered._jvmRdd)

        # visualize
        if (showPlot):
            # make variant track
            tracks=[Track(viz="variants", label=label, source=pileup.sources.GA4GHVariantJson(json))]
            locus="%s:%i-%i" % (contig, start, end)
            return pileup.PileupViewer(locus=locus, reference=reference, tracks=tracks)


class VariantsPerSampleDistribution(object):
    """ VariantsPerSampleDistribution class.
    VariantsPerSampleDistribution computes a distribution of the count of variants per sample.
    """
    def __init__(self,ss,genotypeRDD):
        """
        Initializes a VariantsPerSampleDistribution class.
        Computes the counts of variants per sample from a genotypeRDD.

        :param ss: SparkContext
        :param genotypeRDD: bdgenomics genotypeRDD
        """
        df =  genotypeRDD.toDF()
        data =  df.where( (df.alleles[0] == u'ALT') | (df.alleles[1] == u'ALT')).groupBy("sampleid").count().collect()
        self.counts = [ sample.asDict()['count'] for sample in data ]

    def plot(self, testMode = False, **kwargs):
        """
        Plots final distribution values and returns the plotted distribution as a list

        Returns:
          matplotlib axis to plot and computed data
        """

        if(not testMode):
            figsize = kwargs.get('figsize',(10,5))
            fig, ax = plt.subplots(figsize=figsize)
            bins = kwargs.get('bins',default_bins)
            hist = ax.hist(self.counts, bins=bins)
            return ax, self.counts
        else:
            return None, self.counts


class HetHomRatioDistribution(object):
    """ HetHomRatioDistribution class.
    HetHomRatioDistribution computes a distribution of (Heterozygote/Homozygote) ratios from a genotypeRDD.
    """
    def __init__(self,ss,genotypeRDD):
        """
        Initializes HetHomRatioDistribution class.
        Retrieves the call rate and missing rate for each sample in a genotypeRDD

        Args:
            :param ss: SparkContext
            :param genotypeRDD: bdgenomics genotypeRDD
        """

        df =  genotypeRDD.toDF()
        data_het =  df.where( (df.alleles[0] == u'REF') & (df.alleles[1] == u'ALT')).groupBy("sampleid").count().collect()
        self.hets =  { sample.asDict()['sampleid']:sample.asDict()['count'] for sample in data_het }
        data_hom =  df.where( (df.alleles[0] == u'ALT') & (df.alleles[1] == u'ALT')).groupBy("sampleid").count().collect()
        self.homs =  { sample.asDict()['sampleid']:sample.asDict()['count'] for sample in data_hom }

    def plot(self, testMode = False, **kwargs):
        """
        Plots final distribution values and returns the plotted distribution as a list

        Returns:
          matplotlib axis to plot and computed data
        """

        hetHomRatio = []
        for sampleid in self.homs.keys():
            if sampleid in self.hets:
                hetHomRatio.append(float(self.hets[sampleid])/float(self.homs[sampleid]))

        if(not testMode):
            figsize = kwargs.get('figsize',(10,5))
            fig, ax = plt.subplots(figsize=figsize)
            hist = ax.hist(hetHomRatio,bins=100)
            return ax, hetHomRatio
        else:
            return None, hetHomRatio


class GenotypeCallRatesDistribution(object):
    """ GenotypeCallRatesDistribution class.
    GenotypeCallRatesDistribution computes a distribution of per-sample genotype call rates from a genotypeRDD.
    """
    def __init__(self,ss,genotypeRDD):
        """
        Initializes a GenotypeCallRatesDistribution class.
        Retrieves counts of called and missing genotypes from a genotypeRDD.

        Args:
            :param ss: SparkContext
            :param genotypeRDD: bdgenomics genotypeRDD
        """

        df =  genotypeRDD.toDF()
        called_df =  df.where( (df.alleles[0] != u'NO_CALL')).groupBy("sampleid").count().collect()
        self.called_map =  { sample.asDict()['sampleid']:sample.asDict()['count'] for sample in called_df }

        missing_df =  df.where( (df.alleles[0] == u'NO_CALL')).groupBy("sampleid").count().collect()
        self.missing_map =  { sample.asDict()['sampleid']:sample.asDict()['count'] for sample in missing_df }

    def plot(self, testMode = False, **kwargs):
        """
        Plots final distribution values and returns the plotted distribution as a list

        Args:
         :param testMode: if true, does not generate plot. Used for testing.

        Returns:
           matplotlib axis to plot and computed data
        """
        callRatePerSample = [ float(self.called_map[sampleid])/ \
                              ( float(self.missing_map[sampleid])+float(self.called_map[sampleid])) \
                              for sampleid in self.called_map.keys() ]


        if(not testMode):
            figsize = kwargs.get('figsize',(10,5))
            fig, ax = plt.subplots(figsize=figsize)
            hist = ax.hist(callRatePerSample,bins=100)
            return ax, callRatePerSample
        else:
            return None, callRatePerSample


class QualityByDepthDistribution(object):
    """ QualityByDepthDistribution class.
    QualityByDepthDistribution calculates the Quality By Depth (QD) score distribution from a variantRDD.
    """
    def __init__(self,ss,variantRDD):
        """
        Initializes a QualityByDepthDistribution class.
        Computes the distribution of QD scores from a variantRDD.

        Args:
            :param ss: SparkContext
            :param variantRDD: bdgenomics variantRDD
        """

        # bin QD score by 0.1 QD units by take the floor(x*10) / 10
        binConstant = 10

        data = variantRDD.toDF().rdd.map(lambda w: w.asDict()).map(lambda p: p['annotation']).map(
            lambda x: (math.floor(float(x['attributes']['QD'])*binConstant)/binConstant)).countByValue()
        self.counterData = Counter(dict(data.items()))

    def plot(self,testMode = False, **kwargs):
        """
        Plots final distribution values and returns the plotted distribution as a counter object.

        Args:
          :param testMode: if true, does not generate plot. Used for testing.

        Returns:
            matplotlib axis to plot and computed data
        """

        if(not testMode):
            figsize = kwargs.get('figsize',(10,5))
            fig, ax = plt.subplots(figsize=figsize)
            hist = ax.hist(self.counterData.keys(),weights=self.counterData.values(),bins=100)
            return ax, self.counterData
        else:
            return None, self.counterData





