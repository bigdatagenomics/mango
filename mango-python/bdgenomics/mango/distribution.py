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
=================
CountDistribution
=================
.. currentmodule:: bdgenomics.mango.distribution
.. autosummary::
   :toctree: _generate/

   CountDistribution
"""

import collections
import matplotlib.pyplot as plt
import numpy as np
plt.rcdefaults()

class CountDistribution:
    """ Abstract CountDistribution class.
    Plotting functionality for visualizing count distributions of multi-sample cohorts.
    """

    ss = None
    rdd = None
    sample = 1.0


    def __init__(self):
        """
        Initializes a Distribution class.
        Computes the distribution of an rdd with records of the form (key: (sample ID, count), value: numObservations).
        Length is usually just a 1, and is used for reduceByKey().

        Args:
            param ss: global SparkSession.
            param rdd: An RDD mapped as (key: (sample ID, count), value: numObservations)
            param sample: Fraction to sample rdd. Should be between 0 and 1
        """

        # sample must be between 0 and 1
        if self.sample <= 0 or self.sample > 1:
            raise Exception('sample {} should be > 0 and <= 1'.format(self.sample))

        # sample RDD
        if self.sample < 1:
            self.rdd = self.rdd.sample(False, self.sample)


        # Assign each RDD with counter. Reduce and collect.
        collectedCounts = self.rdd.reduceByKey(lambda x,y: x+y) \
            .collect()  # (id, count), number of times that count appears)

        # function that re-calculates coverage based on sampling
        approximateCounts = lambda counts, sample: int(counts * 1.0/sample)

        # restructure each record so record structure is  (key: sampleId, value: (coverage, count))
        x = map(lambda x: (x[0][0], (x[0][1], approximateCounts(x[1], self.sample))), collectedCounts)
        # create dictionary where keys are the sampleId
        self.collectedCounts = collections.defaultdict(set)
        for k, v in x:
            self.collectedCounts[k].add(v)


    def plotDistributions(self, normalize = True, cumulative = False, testMode = False, **kwargs):
        """
        Plots final distribution values and returns the plotted distribution as a Counter object.

        Args:
            param normalize: normalizes readcounts to sum to 1
            param cumulative: plots CDF of reads
            param testMode: if true, does not generate plot. Used for testing.
            param **kwargs: can hold figsize

        Returns:
            matplotlib axis to plot and computed data
        """

        countDistributions = {}

        # iterate through each sample
        for label, data in self.collectedCounts.iteritems():

            sData = sorted(data)
            values = map(lambda p: p[0], sData)
            counts   = map(lambda p: p[1], sData)

            if normalize:
                # replace distribution counts with normalized values
                sumCounts = float(sum(counts))
                counts = [i/sumCounts for i in counts]

            if cumulative:
                # calculate cumulative sum of counts
                counts = np.cumsum(counts)

            # re-write manipulated data
            countDistributions[label]=zip(values, counts)

            if (not testMode): # For testing: do not run plots if testMode
                figsize = kwargs.get('figsize',(10, 5))
                bar_plt = kwargs.get('bar', False)
                f, ax = plt.subplots(figsize=figsize)
                if (bar_plt):
                    ax.bar(values, counts, 1, label = label)
                else:
                    ax.plot(values, counts, label = label)
                ax.legend(loc=2, shadow = True, bbox_to_anchor=(1.05, 1))
                return ax, countDistributions
            else:
                return None, countDistributions
