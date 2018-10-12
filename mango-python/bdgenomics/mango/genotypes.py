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
=========
Genotypes
=========
.. currentmodule:: bdgenomics.mango.genotypes
.. autosummary::
   :toctree: _generate/

   GenotypeSummary
   VariantsPerSampleDistribution
"""

from bdgenomics.mango.pileup.track import *

from .distribution import HistogramDistribution
import matplotlib.pyplot as plt
plt.rcdefaults()

class GenotypeSummary(object):
    """GenotypeSummary class.
    GenotypeSummary provides visualizations for genotype based summaries.
    """

    def __init__(self, spark, ac, genotypeRdd, sample = 1.0):
        """
        Initializes an GenotypeSummary class.

        Args:
            :param spark: SparkSession
            :param ac: ADAMContext
            :param genotypeRdd: GenotypeRDD
            :param sample: fraction of reads to sample from
        """

        # sample must be between 0 and 1
        if sample <= 0 or sample > 1:
            raise Exception('sample {} should be > 0 and <= 1'.format(self.sample))

        self.ss = spark
        self.ac = ac
        self.sample = sample
        # sample rdd
        self.genotypeRdd = genotypeRdd

        # where to store collected data
        self.variantsPerSampleDistribution = None

    def getVariantsPerSampleDistribution(self):
        """
        Computes distribution of variant counts per sample.

        Returns:
           VariantsPerSampleDistribution object
        """
        if self.variantsPerSampleDistribution == None:
            print("Computing coverage variants per sample distribution...")
            # pre-sample before computing coverage

            self.variantsPerSampleDistribution = VariantsPerSampleDistribution(self.ss, \
                                                                               self.genotypeRdd, \
                                                                               sample = self.sample)

        return self.variantsPerSampleDistribution


class VariantsPerSampleDistribution(HistogramDistribution):
    """ VariantsPerSampleDistribution class.
    VariantsPerSampleDistribution computes a distribution of the count of variants per sample.
    """

    def __init__(self, ss, genotypeRDD, sample = 1.0):
        """
        Initializes a VariantsPerSampleDistributionn class.
        Computes the coverage distribution of a CoverageRDD. This RDD can have data for multiple samples.

        Args:
            :param ss: global SparkSession.
            :param genotypeRDD: A bdgenomics.adam.rdd.GenotypeRDD object.
            :param sample: Fraction to sample CoverageRDD. Should be between 0 and 1
        """

        self.sc = ss.sparkContext
        self.sample = sample

        self.rdd = genotypeRDD.toDF().rdd \
            .filter(lambda r: ( (r.alleles[0] == u'ALT') | (r.alleles[1] == u'ALT'))) \
            .map(lambda r: ((r["sampleId"]), 1))

        HistogramDistribution.__init__(self)
