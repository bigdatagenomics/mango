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

class VariantsGenomicRegion(object):
    """
    Plot variant density across chromosome region
    """
    def __init__(self,ss,variantRDD, start, end, contigName, binSize=1000000):
        """
        :param ss: SparkContext 
        :param variantRDD: bdgenomics variantRDD
        :param start: genomic start position
        :param end:  genomic stop position
        :param contigName: contig name
        :param binSize: region bin size defaults to 1000000  
        """
        data = variantRDD.toDF().filter(
            (variantRDD.toDF().start > start) &
            (variantRDD.toDF().start < end) &
            (variantRDD.toDF().contigName == contigName) ).rdd.map(
              lambda w: w.asDict()).map(
               lambda x: (x['contigName'], int(math.floor(float(x['start'])/binSize)))).countByValue()
        self.data = data
        self.contigName=contigName
    def plot(self):
        x = [ z[0][1] for z in self.data.items() ]
        data = [ (z[0][1],z[1]) for z in self.data.items() ]
        data_sorted = sorted(data, key = lambda tup: tup[0])
        plt.plot([ z[0] for z in data_sorted ], [ z[1] for z in data_sorted ])
        plt.title("Variant density on chromosome " + self.contigName)
        plt.xlabel("position (MB)")
        plt.ylabel("Variant Count / MB")
        plt.show()

class VariantCountPerSample(object):
    """
    Plot Histogram of variant count per sample
    """
    def __init__(self,ss,genotypeRDD):
        """
        :param ss: SparkContext
        :param genotypeRDD: bdgenomics genotypeRDD 
        """
        df =  genotypeRDD.toDF()
        data =  df.where( (df.alleles[0] == u'ALT') | (df.alleles[1] == u'ALT')).groupBy("sampleid").count().collect()
        counts = [ sample.asDict()['count'] for sample in data ]
        self.counts = counts
    def plot(self, testMode = False):
        if (not testMode):
          plt.hist(self.counts,bins=default_bins)
          plt.title("Variants per Sample")
          plt.xlabel("Variants")
          plt.ylabel("Samples")
          plt.show()
        return self.counts


class InsertionCountPerSample(object):
    """
    Plot Histogram of variant count per sample
    """
    def __init__(self,ss,genotypeRDD):
        """
        :param ss: SparkContext 
        :param genotypeRDD: bdgenomics genotypeRDD
        """
        df =  genotypeRDD.toDF()
        data =  df.where( ( ( len(df.alternateAllele ) > 1 )) & (  (df.alleles[0] == u'ALT') | (df.alleles[1] == u'ALT'))).groupBy("sampleid").count().collect()
        counts = [ sample.asDict()['count'] for sample in data ]
        self.counts = counts
    def plot(self):
        plt.hist(self.counts,bins=default_bins)
        plt.title("Variants per Sample")
        plt.xlabel("Variants")
        plt.ylabel("Samples")
        plt.show()


class HetHomRatioPerSample(object):
    """
    Plot Histogram of Het/Hom ratio per sample
    """
    def __init__(self,ss,genotypeRDD):
        """
        :param ss: SparkContext 
        :param genotypeRDD: bdgenomics genotypeRDD
        """
        df =  genotypeRDD.toDF()
        data_het =  df.where( (df.alleles[0] == u'REF') & (df.alleles[1] == u'ALT')).groupBy("sampleid").count().collect()
        hets =  { sample.asDict()['sampleid']:sample.asDict()['count'] for sample in data_het }
        data_hom =  df.where( (df.alleles[0] == u'ALT') & (df.alleles[1] == u'ALT')).groupBy("sampleid").count().collect()
        homs =  { sample.asDict()['sampleid']:sample.asDict()['count'] for sample in data_hom }
        self.het_hom_ratio = [ float(hets[sampleid])/float(homs[sampleid]) for sampleid in homs.keys() ]

    def plot(self, testMode = False):
        if (not testMode):
          plt.hist(self.het_hom_ratio,bins=default_bins)
          plt.title("Histogram of Het/Hom Ratio")
          plt.xlabel("Het/Hom Ratio")
          plt.ylabel("Sample Count")
          plt.show()
        return self.het_hom_ratio


class CallRatePerSample(object):
    """
    Plot Histogram of call rate per sample
    """
    def __init__(self,ss,genotypeRDD):
        """
        :param ss: SparkContext 
        :param genotypeRDD: bdgenomics genotypeRDD
        """
        df =  genotypeRDD.toDF()
        data_called =  df.where( (df.alleles[0] != u'NO_CALL')).groupBy("sampleid").count().collect()
        called =  { sample.asDict()['sampleid']:sample.asDict()['count'] for sample in data_called }
        data_missing =  df.where( (df.alleles[0] == u'NO_CALL')).groupBy("sampleid").count().collect()
        missing =  { sample.asDict()['sampleid']:sample.asDict()['count'] for sample in data_missing }
        self.call_rate = [ float(called[sampleid])/(float(missing[sampleid]+float(called[sampleid]))) for sampleid in called.keys() ]

    def plot(self, testMode = False):
        if (not testMode):
          plt.hist(self.call_rate,bins=default_bins)
          plt.title("Histogram of Call Rate per sample")
          plt.xlabel("Call Rate")
          plt.ylabel("Sample Count")
          plt.show()
        return self.call_rate

class QDDist(object):
    """
    Plot Histogram of Quality By Depth (QD) score from a variant dataset
    """
    def __init__(self,ss,variantRDD):
        """
        :param ss: SparkContext 
        :param variantRDD: bdgenomics variantRDD
        """
        data = variantRDD.toDF().rdd.map(lambda w: w.asDict()).map(lambda p: p['annotation']).map(
            lambda x: math.floor(float(x['attributes']['QD'])*10)/10).countByValue()
        self.scores = [ x[0] for x in data.items() ]
        self.weights = [ x[1] for x in data.items() ]

    def plot(self, testMode = False):
        if (not testMode):
          plt.hist(self.scores,weights=self.weights, bins=80)
          plt.title("Quality By Depth (QD) Score Distribution")
          plt.xlabel("QD")
          plt.ylabel("Variant Sites")
          plt.show()
        return (self.scores, self.weights)
