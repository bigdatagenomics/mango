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

from collections import Counter, OrderedDict
from cigar import Cigar
import matplotlib.pyplot as plt
import numpy as np
import matplotlib.ticker as ticker
import math
from pyspark.sql import Row
plt.rcdefaults()
plt.rcParams['figure.figsize'] = [10, 8]

default_bins=100

class VariantDensityDistribution(object):
    """ VariantDensityDistribution class.
    VariantDensityDistribution computes a distribution of positionally binned variant counts.
    """
    def __init__(self,ss,variantRDD, start, end, contigName):
        """
        Initializes a VariantDensityDistribution class.
        Computes a distribution of positionally binned variant counts.

        Args:
            :param ss: SparkContext
            :param variantRDD: bdgenomics variantRDD
            :param start: genomic start position
            :param end:  genomic stop position
            :param contigName: contig name
        """

        binSize = 1000000
        self.data = variantRDD.toDF().filter(
            (variantRDD.toDF().start > start) &
            (variantRDD.toDF().start < end) &
            (variantRDD.toDF().contigName == contigName) ).rdd.map(
              lambda w: w.asDict()).map(
               lambda x: (x['contigName'], int(math.floor(float(x['start'])/binSize)))).countByValue()

    def plot(self):
        """
        Returns positionally binned counts of variants suitable for plotting

        Returns:
          A tuple of two lists, the first a list of the start position for each genomic bin
          and the second a list of corresponding variant counts for each bin.
        """
        data = [ (z[0][1],z[1]) for z in self.data.items() ]
        data_sorted = sorted(data, key = lambda tup: tup[0])
        return ([ z[0] for z in data_sorted ], [ z[1] for z in data_sorted ])

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
        self.data =  df.where( (df.alleles[0] == u'ALT') | (df.alleles[1] == u'ALT')).groupBy("sampleid").count().collect()

    def plot(self, testMode = False):
        """
        Returns a list of variant counts per sample suitable for plotting.

        Returns:
          A list of variant counts per sample.
        """
        counts = [ sample.asDict()['count'] for sample in self.data ]
        #plt.hist(self.counts,bins=default_bins)
        #plt.title("Variants per Sample")
        #plt.xlabel("Variants")
        #plt.ylabel("Samples")
        #plt.show()
        return counts


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

    def plot(self):
        """
        Computes and returns a list of per sample Het/Hom ratios suitable for plotting

        Returns:
          a list containing the Het/Hom ratio statistic for each sample in the dataset
        """
        hetHomRatio = [ float(self.hets[sampleid])/float(self.homs[sampleid]) for sampleid in self.homs.keys() ]
        return hetHomRatio


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

    def plot(self):
        """
        Computes and returns a list of per sample call rates suitable for plotting.

        Returns:
           A list of per sample call rates.
        """
        callRatePerSample = [ float(self.called_map[sampleid])/ \
                              ( float(self.missing_map[sampleid])+float(self.called_map[sampleid])) \
                              for sampleid in self.called_map.keys() ]

        return callRatePerSample


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

        self.data = variantRDD.toDF().rdd.map(lambda w: w.asDict()).map(lambda p: p['annotation']).map(
            lambda x: (math.floor(float(x['attributes']['QD'])*binConstant)/binConstant)).countByValue()

    def plot(self):
        """
        Computes and returns binned QD score plot data suitable for plotting as a Counter object.

        Returns:
            Computed binned QD score data as a Counter object.
        """
        return Counter(dict(self.data.items()))
