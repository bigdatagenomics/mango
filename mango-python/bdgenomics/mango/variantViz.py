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
plt.rcdefaults()


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

def most_sig_effect(effectList):
    return rank_to_so[max( [ so_to_rank[item] for item in effectList] )]

class VariantEffectAlleleFreq(object):

    def __init__(self, ss, variantRDD, annot_list=[], bins=100):

        def jp3(dd1):
            effectList = [ effect for transcript in dd1['transcriptEffects'] for effect in transcript['effects'] ]
            mostSigEffect = most_sig_effect(effectList)
            return mostSigEffect

        if len(annot_list) == 0:
            annot_list = ['frameshift_variant','stop_gained', 'missense_variant', 'synonymous_variant']

        data_bins = []
        data_weights = []


        for annot in annot_list:
              curr = variantRDD.toDF().rdd.map(lambda w: w.asDict()).map(lambda p: p['annotation']).filter(
                  lambda x: x['attributes']['AF_NFE'] != ".").map(
                  lambda d: (jp3(d), math.floor(float(d['attributes']['AF_NFE'])*10000)/10000)).filter(
                  lambda f: f[0] == annot).countByValue()
              #data_list.append(curr)
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

    def plot(self):
        plt.rcdefaults()
        plt.title("Allele Frequency Distribution by Functional Category")
        plt.hist(self.data_bins, weights=self.data_weights, bins=self.bins, normed=1, histtype = 'step', label=self.annot_list)
        plt.legend()
        plt.yscale('log')
        plt.show()



class VariantCountByPop(object):
    def __init__(self, ss, variantRDD, annot_list=[], bins=100):

        def jp3(dd1):
            effectList = [ effect for transcript in dd1['transcriptEffects'] for effect in transcript['effects'] ]
            mostSigEffect = most_sig_effect(effectList)
            return mostSigEffect

        if len(annot_list) == 0:
            annot_list = ['frameshift_variant','stop_gained', 'missense_variant', 'synonymous_variant']

        data_bins = []
        data_weights = []

        pop_list = [ 'AF_NFE', 'AF_AFR']
        for pop in pop_list:
           curr = variantRDD.toDF().rdd.map(lambda w: w.asDict()).map(lambda p: p['annotation']).filter(
             lambda x: x['attributes'][pop] != ".").map(
             lambda d: (jp3(d), math.floor(float(d['attributes'][pop])*100000)/100000)).filter(lambda f: f[1] > 0).countByValue()
           currlist = curr.items()
           currbins = [ item[0][1] for item in currlist ]
           currweights = [ item[1] for item in currlist ]
           data_bins.append(currbins)
           data_weights.append(currweights)




        self.data_bins = data_bins
        self.data_weights = data_weights
        self.annot_list = annot_list
        self.pop_list=pop_list
        self.bins = bins
        self.sc = ss.sparkContext

    def plot(self):
        plt.rcdefaults()
        plt.title("Allele Frequency Distribution by Population")
        plt.hist(self.data_bins, weights=self.data_weights, bins=self.bins, histtype = 'step', label=self.pop_list)
        plt.legend()
        plt.yscale('log')
        #plt.xscale('log')
        plt.show()



class VariantEffectCounts(object):

    def __init__(self, ss, variantRDD, includeList = []):
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


    def plot(self):

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

class VariantCountByGene(object):
    def __init__(self,ss,variantRDD, so_term='missense_variant', bins=100):

      def compute(dd1):
        effectList = [ effect for transcript in dd1['transcriptEffects'] for effect in transcript['effects'] ]
        geneList = [ transcript['geneId'] for transcript in dd1['transcriptEffects'] ]
        if len(geneList)==0:
          geneList = list("NoGene")
        mostSigEffect = most_sig_effect(effectList)
        return [mostSigEffect, geneList[0]]

      y= variantRDD.toDF().rdd.map(lambda w: w.asDict()).map(lambda p: p['annotation']).filter(
        lambda x: x['attributes']['AF_NFE'] != ".").map(
        lambda d: ( compute(d)[0],compute(d)[1])).filter(
        lambda f: f[0] == so_term and f[1] != 'NoGene').countByValue()

      self.data = y.values()
      self.bins = bins
      self.so_term = so_term

    def plot(self):
        plt.hist(self.data, bins=100)
        plt.title("Count of " + self.so_term + " per gene")
        plt.show()

