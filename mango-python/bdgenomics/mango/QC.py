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

from collections import Counter
import matplotlib.pyplot as plt
plt.rcdefaults()

class CoverageDistribution(object):
    """
    QC provides preprocessing functions for visualization
    of various quality control.
    """

    def __init__(self, sc, coverageRDDs):
        """
        Initializes a CoverageDistribution class.
        Computes the coverage distribution of multiple coverageRDDs.
        :param SparkContext
        :param bdgenomics.adam.rdd.CoverageRDD coverageRDDs: A list of coverageRDDs
        """
        self.sc = sc

        # If single RDD, convert to list
        if (not isinstance(coverageRDDs, list)):
            coverageRDDs = [coverageRDDs]

        # Assign each RDD with counter. Reduce and collect.
        mappedDistributions = [c.flatten().toDF().rdd.map(lambda r: ((i, int(r["count"])), 1)).reduceByKey(lambda x,y: x+y) for i,c in enumerate(coverageRDDs)]
        unionRDD = self.sc.union(mappedDistributions)
        self.collectedCoverage = unionRDD.map(lambda r: (r[0][0], Counter({r[0][1]:r[1]}))) \
            .reduceByKey(lambda x,y:x+y) \
            .sortByKey() \
            .map(lambda r: r[1]).collect()


    def plot(self, normalize = False, cumulative = False, xScaleLog = False, yScaleLog = False, testMode = False):

        coverageDistributions = []

        for coverageDistribution in self.collectedCoverage:
            if normalize:
                total = float(sum(coverageDistribution.itervalues()))
                for key in coverageDistribution:
                    coverageDistribution[key] /= total

            if cumulative:
                cumulativeSum = 0.0
                keys = coverageDistribution.keys()
                keys.sort()
                for key in keys:
                    cumulativeSum += coverageDistribution[key]
                    coverageDistribution[key] = cumulativeSum

            coverageDistributions.append(coverageDistribution)

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


            for count, coverageDistribution in enumerate(coverageDistributions):
                coverage = coverageDistribution.keys()
                counts = coverageDistribution.values()
                plt.plot(coverage, counts, label = "Coverage " + str(count + 1))
            plt.legend(loc=2, shadow = True, bbox_to_anchor=(1.05, 1))
            plt.show()

        return coverageDistributions