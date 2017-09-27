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
plt.rcdefaults()


## Plots distribution for CoverageRDD
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
        collectedCoverage = unionRDD.map(lambda r: (r[0][0], Counter({r[0][1]:r[1]}))) \
            .reduceByKey(lambda x,y:x+y) \
            .sortByKey() \
            .map(lambda r: r[1]).collect()

        # we have to run a local sort. Creates a list of OrderedDict
        f = lambda x: OrderedDict(sorted(x.items()))
        self.collectedCoverage = [f(x) for x in collectedCoverage]


    def plot(self, normalize = False, cumulative = False, xScaleLog = False, yScaleLog = False, testMode = False):
        """
        Plots final distribution values
        :param normalize: normalizes readcounts to sum to 1
        :param cumulative: plots CDF of reads
        :param xScaleLog: rescales xaxis to log
        :param yScaleLog: rescales yaxis to log
        :param testMode: if true, does not generate plot. Used for testing.
        """

        coverageDistributions = []

        for coverageDistribution in self.collectedCoverage:

            copiedDistribution = coverageDistribution.copy()

            if normalize:
                total = float(sum(copiedDistribution.values()))

                # replace coverage distribution counts with normalized values
                for key in copiedDistribution:
                    copiedDistribution[key] /= total

            if cumulative:
                cumulativeSum = 0.0

                # Keep adding up reads for cumulative
                for key in copiedDistribution.keys():
                    cumulativeSum += copiedDistribution[key]
                    copiedDistribution[key] = cumulativeSum

            coverageDistributions.append(copiedDistribution)

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

## Plots alignment distribution for AlignmentRDD using the cigar string
class AlignmentDistribution(object):
    """
    QC provides preprocessing functions for visualization
    of various quality control.
    """

    def __init__(self, alignmentRDD):
        """
        Initializes a AlignmentDistribution class.
        Computes the coverage distribution of multiple coverageRDDs.
        :param bdgenomics.adam.rdd.AlignmentRDD alignmentRDD: A single alignment RDD
        """
        cigars = alignmentRDD.toDF().rdd.map(lambda r: r["cigar"])
        self.alignments = cigars.flatMap(lambda r: Cigar(r).items()) \
                    .map(lambda a: ((a[1],a[0]),1)) \
                    .filter(lambda a: a[0][0] is not None) \
                    .reduceByKey(lambda x,y:x+y) \
                    .map(lambda r:(r[0][0], Counter({r[0][1]:r[1]}))) \
                    .reduceByKey(lambda x,y:x+y) \
                    .collect()

    def plot(self, normalize = False, cumulative = False, xScaleLog = False, yScaleLog = False, testMode = False):
        """
        Plots final distribution values
        :param normalize: normalizes readcounts to sum to 1
        :param cumulative: plots CDF of reads
        :param xScaleLog: rescales xaxis to log
        :param yScaleLog: rescales yaxis to log
        :param testMode: if true, does not generate plot. Used for testing.
        """
        alignmentDistributions = []

        for cigarString, alignmentDistribution in self.alignments:

            copiedDistribution = alignmentDistribution.copy()

            if normalize:
                total = float(sum(copiedDistribution.values()))

                # replace coverage distribution counts with normalized values
                for key in copiedDistribution:
                    copiedDistribution[key] /= total

            if cumulative:
                cumulativeSum = 0.0

                # Keep adding up reads for cumulative
                for key in copiedDistribution.keys():
                    cumulativeSum += copiedDistribution[key]
                    copiedDistribution[key] = cumulativeSum

            alignmentDistributions.append((cigarString, copiedDistribution))

        if (not testMode): # For testing: do not run plots if testMode

            title =  'Target Region Alignment'
            if cumulative:
                title = 'Cumulative ' + title
            if normalize:
                title = 'Normalized ' + title
            plt.ylabel('Fraction' if normalize else 'Counts')
            plt.xlabel('Length per cigar')

            # log scales, if applicable
            if (xScaleLog):
                plt.xscale('log')
            if (yScaleLog):
                plt.yscale('log')

            plt.title(title)

            for cigarString, alignmentDistribution in alignmentDistributions:
                coverage = alignmentDistribution.keys()
                counts = alignmentDistribution.values()
                plt.plot(coverage, counts, label = cigarString)
            plt.legend(loc=2, shadow = True, bbox_to_anchor=(1.05, 1))
            plt.show()

        return alignmentDistributions