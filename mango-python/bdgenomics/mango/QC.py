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
        :param coverageRDDs: A list of bdgenomics.adam.rdd.CoverageRDD objects
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


    def plot(self, normalize = False, cumulative = False, xScaleLog = False, yScaleLog = False, testMode = False, labels = []):
        """
        Plots final distribution values and returns the plotted distribution as a Counter object.
        :param normalize: normalizes readcounts to sum to 1
        :param cumulative: plots CDF of reads
        :param xScaleLog: rescales xaxis to log
        :param yScaleLog: rescales yaxis to log
        :param testMode: if true, does not generate plot. Used for testing.
        """


        # If single RDD, convert to list
        if (not isinstance(labels, list)):
            labels = [labels]

        # Make sure there are enough labels for the RDDs.
        # If not, add them
        if (len(labels) != len(self.collectedCoverage)):
            print("No labels set. Assigning labels to dataset...")
            labels = map(lambda count: "Coverage " + str(count + 1), range(len(self.collectedCoverage)))

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
                plt.plot(coverage, counts, label = labels[count])
            plt.legend(loc=2, shadow = True, bbox_to_anchor=(1.05, 1))
            plt.show()

        return coverageDistributions


## Plots alignment distribution for AlignmentRDD using the cigar string
class AlignmentDistribution(object):
    """
    QC provides preprocessing functions for visualization
    of various quality control.
    """

    def __init__(self, sc, alignmentRDD, sample=1.0, bin_size=10000000):
        """
        Initializes a AlignmentDistribution class.
        Computes the alignment distribution of multiple coverageRDDs.
        :param SparkContext: the global SparkContext
        :param alignmentRDDs: A list of bdgenomics.adam.rdd.AlignmentRDD objects
        :param int bin_size: Division size per bin
        """
        bin_size = int(bin_size)
        self.bin_size = bin_size
        self.sc = sc

        # filter alignments without a position
        filteredAlignments = alignmentRDD.transform(lambda x: x.sample(False, 1.0)) \
            .toDF().rdd.filter(lambda r: r["start"] != None)

        # Assign alignments with counter for contigs. Reduce and collect.
        mappedDistributions = filteredAlignments \
            .map(lambda r: ((r["contigName"], r["start"] - r["start"]%bin_size), \
                            Counter(dict([(y,x) for x,y in Cigar(r["cigar"]).items()])))) \
            .reduceByKey(lambda x,y: x+y)

        self.alignments = mappedDistributions.collect()

    def plot(self, xScaleLog = False, yScaleLog = False, testMode = False, plotType="I"):
        """
        Plots final distribution values and returns the plotted distribution as a counter object.
        :param xScaleLog: rescales xaxis to log
        :param yScaleLog: rescales yaxis to log
        :param testMode: if true, does not generate plot. Used for testing.
        :param plotType: Cigar type to plot, from ['I', 'H', 'D', 'M', 'S']
        """
        chromosomes = Counter()


        # count contig type at each location
        for index, counts in self.alignments:
            chromosomes[index] += counts[plotType]

        if (not testMode): # For testing: do not run plots if testMode
            title =  'Target Region Alignment for Type %s with bin size %d' % (plotType, self.bin_size)
            plt.ylabel('Counts')
            plt.xlabel('Chromosome number')

            # log scales, if applicable
            if (xScaleLog):
                plt.xscale('log')
            if (yScaleLog):
                plt.yscale('log')
            plt.title(title)

            # get distinct chromosome names for plot
            keys = sorted(list(set(map(lambda x: x[0][0], self.alignments))))

            # offset for max chr
            offset = 0

            # holds xtick information
            midPoints = []

            # for all chromosomes
            for key in keys:

                # filter by chromosome key
                values = [x for x in chromosomes.items() if x[0][0] == key]

                # get positions
                positions = map(lambda x: x[0][1] + offset, values)

                # get counts
                counts = map(lambda x: x[1], values)

                plt.plot(positions, counts, ls='', marker='.')

                # set label for chromosome
                midPoint = min(positions) + (max(positions) - min(positions))/2
                midPoints.append(midPoint)

                offset = max(positions)

            plt.xticks(midPoints,  keys, rotation=-90, size=8.5)
            plt.show()

        return chromosomes
