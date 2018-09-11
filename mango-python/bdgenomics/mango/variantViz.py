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


def findMostSigEffect(effectList):
    """
    Find and return the most deleterious effect from a list of Sequence Ontology terms
    :param effectList: list of sequence ontology (so) terms
    :return: so_term from effectList with most deleterious effect
    """

    # so_terms, listed in order of least to most clinically significant (deleterious), for purpose of selecting the
    # most important effect of a given variant.
    so_terms = [
     'intergenic_region',
     'downstream_gene_variant',
      'upstream_gene_variant',
     'sequence_feature',
     'structural_interaction_variant',
     'intron_variant',
     'non_coding_transcript_exon_variant',
     '3_prime_UTR_variant',
     '5_prime_UTR_variant',
     'synonymous_variant',
     'conservative_inframe_insertion',
     'conservative_inframe_deletion',
     'initiator_codon_variant',
     'protein_protein_contact',
     'non_coding_transcript_variant',
     'stop_retained_variant',
     '5_prime_UTR_premature_start_codon_gain_variant',
     'disruptive_inframe_deletion',
     'disruptive_inframe_insertion',
     'missense_variant',
     'stop_lost',
     'start_lost',
     'splice_donor_variant',
     'splice_acceptor_variant',
     'splice_region_variant',
     'frameshift_variant',
     'stop_gained']
    so_to_rank = dict(zip(so_terms, range(1,len(so_terms)+1)))
    rank_to_so = {v: k for k, v in so_to_rank.iteritems()}
    return rank_to_so[max( [ so_to_rank[item] for item in effectList] )]

class VariantFreqPopCompareScatter(object):
    """
    Scattergram comparing allele frequencies of variants in two populations
    """
    def __init__(self, ss, variantRDD, pop1='AF_NFE', pop2='AF_AFR'):
        """
        :param ss: SparkContext
        :param variantRDD: bdgenomics variantRDD
        :param pop1: population one name, default AF_NFE
        :param pop2: population two name, default AF_AFR
        """
        curr = variantRDD.toDF().rdd.map(lambda w: w.asDict()).map(lambda p: p['annotation']).filter(
            lambda x: x['attributes'][pop1] != ".").filter(
            lambda x: x['attributes'][pop2] != ".").map(lambda d: ((float(d['attributes'][pop1])), float(d['attributes'][pop2]))   ).collect()
        self.x = [ i[0] for i in curr ]
        self.y = [ i[1] for i in curr ]
        self.pop1 = pop1
        self.pop2 = pop2

    def plot(self):
        plt.rcdefaults()
        plt.title("Population Frequency\ncomparing populations " + self.pop1 + " and " + self.pop2)
        mag = [ math.log(max( ( (z[0]-z[1]/(z[0]+.000000000001)), (z[1]-z[0])/(z[1]+.000000000001)))+1) for z in zip(self.x,self.y) ]
        plt.scatter(self.x,self.y,c=mag)
        plt.xlabel(self.pop1)
        plt.ylabel(self.pop2)
        plt.show()


class VariantEffectAlleleFreq(object):
    """
    Plots Cumulative Distribution of Variant Effect versus Allele Frequency
    """
    def __init__(self, ss, variantRDD, pop, annot_list=['missense_variant', 'synonymous_variant'], bins=[.00001,.0001,.01,.025,.05,.075,.1,.15,.2,.25,.3,.35,.4,.45,.5]):
        """
        :param ss: the global SparkContext
        :param variantRDD: a bdgenomics.adam.rdd.AlignmentRDD object
        :param pop: population code from which to select allele frequency
        :param annot_list: a list of text sequence ontology terms
        :param bins: integer number of bins in Histogram, or a list of bin boundaries
        """

        def get_effect(d):
            return findMostSigEffect(  effect for transcript in d['transcriptEffects'] for effect in transcript['effects']  )

        data_bins = []
        data_weights = []

        for annot in annot_list:
            curr = variantRDD.toDF().rdd.map(lambda w: w.asDict()).map(lambda p: p['annotation']).filter(
                lambda x: x['attributes'][pop] != ".").map(
                lambda d: (get_effect(d), math.floor(float(d['attributes'][pop])*10000)/10000)).filter(
                lambda f: f[0] == annot).countByValue()
            currlist = curr.items()
            currbins = [ item[0][1] for item in currlist ]
            currweights = [ item[1] for item in currlist ]
            data_bins.append(currbins)
            data_weights.append(currweights)

        self.data_bins = data_bins
        self.data_weights = data_weights
        self.annot_list = annot_list
        self.bins = bins
        self.sc = ss.sparkContext

    def plot(self, testMode = False):
        if (not testMode):
            plt.rcdefaults()
            plt.title("Allele Frequency Distribution by Functional Category")
            plt.hist(self.data_bins, weights=self.data_weights, bins=self.bins, normed=1, histtype = 'step', label=self.annot_list)
            plt.legend()
            plt.yscale('log')
            plt.xlabel("Allele Frequency")
            plt.ylabel("Proportion of Variants (normalized)")
            plt.show()
        return ( sorted(self.data_bins[0])[0:20], sorted(self.data_weights[0][0:20]) )


class VariantDistribByPop(object):
    """
    Plot Allele Frequency Distribution by Population
    """
    def __init__(self, ss, variantRDD, pop_list=[ 'AF_NFE', 'AF_AFR'], bins=[.00001,.0001,.001,.01,.025,.05,.075,.1,.15,.2,.25,.3,.35,.4,.45,.5]):
        """
        :param ss: SparkContext 
        :param variantRDD: bdgenomics VariantRDD
        :param pop_list: list of populations, defaults to [ 'AF_NFE', 'AF_AFR']
        :param bins: integer number of bins in Histogram, or a list of bin boundaries
        """

        def getMostSigEffect(dd1):
           """
           Return most significant effect from list of transcript annotations
           """
           effectList = [ effect for transcript in dd1['transcriptEffects'] for effect in transcript['effects'] ]
           mostSigEffect = findMostSigEffect(effectList)
           return mostSigEffect

        data_bins = []
        data_weights = []

        def getmaf(x):
            if(x<0.5):
                return x
            else:
              return 1 - x

        for pop in pop_list:
           curr = variantRDD.toDF().rdd.map(lambda w: w.asDict()).map(lambda p: p['annotation']).filter(
             lambda x: x['attributes'][pop] != ".").map(
             lambda d: (getMostSigEffect(d), math.floor( getmaf(float(d['attributes'][pop])) *100000)/100000)).filter(lambda f: f[1] > 0).countByValue()
           currlist = curr.items()
           currbins = [ item[0][1] for item in currlist ]
           currweights = [ item[1] for item in currlist ]
           data_bins.append(currbins)
           data_weights.append(currweights)

        self.data_bins = data_bins
        self.data_weights = data_weights
        self.pop_list=pop_list
        self.sc = ss.sparkContext
        self.bins = bins

    def plot(self):
        plt.rcdefaults()
        plt.title("Allele Frequency Distribution by Population")
        plt.hist(self.data_bins, weights=self.data_weights, bins=self.bins, histtype = 'step', label=self.pop_list)
        plt.legend()
        plt.yscale('log')
        plt.xlabel("Minor Allele Frequency")
        plt.ylabel("Variant Count")
        plt.show()

class VariantEffectCounts(object):
    """
    Plot Barchart of Variant Effect Type Counts
    """
    def __init__(self, ss, variantRDD, includeList = []):
        # Default list of effect types to include in chart
        if not includeList:
            includeList = [
                u'non_coding_transcript_exon_variant',
                u'3_prime_UTR_variant',
                u'5_prime_UTR_variant',
                u'synonymous_variant',
                u'stop_retained_variant',
                u'conservative_inframe_insertion',
                u'conservative_inframe_deletion',
                u'5_prime_UTR_premature_start_codon_gain_variant',
                u'disruptive_inframe_deletion',
                u'disruptive_inframe_insertion',
                u'missense_variant',
                u'stop_lost',
                u'start_lost',
                u'splice_donor_variant',
                u'splice_acceptor_variant',
                u'splice_region_variant',
                u'frameshift_variant',
                u'stop_gained'
            ]

        effectCounts = variantRDD.toDF().select("annotation").rdd.map(lambda w: w[0].asDict()['transcriptEffects']) \
            .flatMap(lambda m: m).map(lambda g: g.asDict()['effects']).flatMap(lambda a: a).filter(lambda i: i in includeList).countByValue()

        self.sc = ss.sparkContext
        self.effectCounts = effectCounts

    def plot(self, testMode = False):

        if (not testMode):
          plt.rcdefaults()
          fig, ax = plt.subplots()
          effects = self.effectCounts.keys()
          y_pos = np.arange(len(effects))
          counts = [ self.effectCounts[item] for item in effects]
          ax.barh(y_pos, counts, align='center', color='green', ecolor='black')
          ax.set_yticks(y_pos)
          ax.set_yticklabels(effects)
          ax.xaxis.set_label_text("Variant Count")
          plt.title("Variant Count By Functional Consequence")
          plt.show()

        return dict(self.effectCounts)

class VariantCountByGene(object):
    """
    Plot Histogram of variant count by Gene
    """
    def __init__(self,ss,variantRDD, so_term='missense_variant', bins=default_bins):
        """
        :param ss: SparkContext 
        :param variantRDD: bdgenomics VariantRDD
        :param so_term: sequence ontology term to count within genes
        :param bins: number of bins in histograms
        """

        def get_effect(d):
         return findMostSigEffect(  effect for transcript in d['transcriptEffects'] for effect in transcript['effects']  )

        def get_gene(d):
          geneList = [ transcript['geneId'] for transcript in d['transcriptEffects'] ]
          if len(geneList)==0:
            geneList = list("NoGene")
          return geneList[0]

        y= variantRDD.toDF().rdd.map(lambda w: w.asDict()).map(lambda p: p['annotation']).filter(
          lambda x: x['attributes']['AF_NFE'] != ".").map(
          lambda d: ( get_effect(d),get_gene(d))).filter(
          lambda f: f[0] == so_term and f[1] != 'NoGene').countByValue()

        self.data = y.values()
        self.bins = bins
        self.so_term = so_term

    def plot(self, testMode = False):

        if (not testMode):
          plt.hist(self.data, self.bins)
          plt.title("Count of " + self.so_term + " per gene")
          plt.xlabel("Number of Variants")
          plt.ylabel("Number of Genes")
          plt.show()
        return self.data

class VariantsGenomicRegion(object):
    """
    Plot variant density across chromosome region
    """
    def __init__(self,ss,variantRDD, start, end, contigName):
        """
        :param ss: SparkContext 
        :param variantRDD: bdgenomics variantRDD
        :param start: genomic start position
        :param end:  genomic stop position
        :param contigName: contig name
        """
        data = variantRDD.toDF().filter(
            (variantRDD.toDF().start > start) &
            (variantRDD.toDF().start < end) &
            (variantRDD.toDF().contigName == contigName) ).rdd.map(
              lambda w: w.asDict()).map(
               lambda x: (x['contigName'], int(math.floor(float(x['start'])/1000000)))).countByValue()
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
