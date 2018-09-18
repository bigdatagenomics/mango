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
Coverage
========
.. currentmodule:: bdgenomics.mango.coverage
.. autosummary::
   :toctree: _generate/

   CoverageDistribution
"""

import collections
import matplotlib.pyplot as plt
import numpy as np
plt.rcdefaults()


class CoverageDistribution(object):
    """ CoverageDistribution class.
    Plotting functionality for visualizing coverage distributions of multi-sample cohorts.
    """

    def __init__(self, ss, coverageRDD):
        """
        Initializes a CoverageDistribution class.
        Computes the coverage distribution of a CoverageRDD. This RDD can have data for multiple samples.

        Args:
            param ss: global SparkSession
            param coverageRDDs: A list of bdgenomics.adam.rdd.CoverageRDD objects
        """
        self.sc = ss.sparkContext
        self.rdd = coverageRDD

        # Assign each RDD with counter. Reduce and collect.
        # map to a key of (Id, count), 1. Count by "count" to get CDF estimator
        # We do "end-start" to get the number of times we see that count.
        collectedCoverage = coverageRDD.toDF().rdd.map(lambda r: ((r["sampleId"], int(r["count"])), (int(r["end"])-int(r["start"])))) \
            .reduceByKey(lambda x,y: x+y) \
            .collect()  # (id, count), number of times that count appears)

        # restructure each record so key is sampleId
        x = map(lambda x: (x[0][0], (x[0][1], x[1])), collectedCoverage)
        # create dictionary where keys are the sampleId
        self.collectedCoverage = collections.defaultdict(set)
        for k, v in x:
            self.collectedCoverage[k].add(v)


    def plotDistributions(self, normalize = False, cumulative = False, xScaleLog = False, yScaleLog = False, testMode = False,):
        """
        Plots final distribution values and returns the plotted distribution as a Counter object.

        Args:
            param normalize: normalizes readcounts to sum to 1
            param cumulative: plots CDF of reads
            param xScaleLog: rescales xaxis to log
            param yScaleLog: rescales yaxis to log
            param testMode: if true, does not generate plot. Used for testing.
        """

        coverageDistributions = {}

        # set up plot, if not in test mode
        if (not testMode): # For testing: do not run plots if testMode

            title =  'Target Region Coverage'
            if cumulative:
                title = 'Cumulative ' + title
            if normalize:
                title = 'Normalized ' + title
            plt.ylabel('Fraction' if normalize else 'Counts')
            plt.xlabel('Coverage Depth')

            # log scales, if applicable
            if (xScaleLog):
                plt.xscale('log')
            if (yScaleLog):
                plt.yscale('log')

            plt.title(title)


        # iterate through each sample
        for label, data in self.collectedCoverage.iteritems():

            sData = sorted(data)
            coverage = map(lambda p: p[0], sData)
            counts   = map(lambda p: p[1], sData)

            if normalize:
                # replace coverage distribution counts with normalized values
                sumCounts = float(sum(counts))
                counts = [i/sumCounts for i in counts]

            if cumulative:
                # calculate cumulative sum of coverage counts
                counts = np.cumsum(counts)

            # re-write manipulated data
            coverageDistributions[label]=zip(coverage, counts)

            if (not testMode): # For testing: do not run plots if testMode
                plt.plot(coverage, counts, label = label)
                plt.legend(loc=2, shadow = True, bbox_to_anchor=(1.05, 1))
                plt.show()

        return coverageDistributions
