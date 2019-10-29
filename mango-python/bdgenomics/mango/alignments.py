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
==========
Alignments
==========
.. currentmodule:: bdgenomics.mango.alignments
.. autosummary::
   :toctree: _generate/

   AlignmentSummary
   FragmentDistribution
   MapQDistribution
   IndelDistribution

"""

import bdgenomics.mango.pileup as pileup
from bdgenomics.mango.pileup.track import *
from bdgenomics.adam.adamContext import ADAMContext

from .coverage import *
from .utils import *

from collections import Counter, OrderedDict
from .distribution import CountDistribution
from cigar import Cigar
import matplotlib.pyplot as plt
plt.rcdefaults()

class AlignmentSummary(object):
    """AlignmentSummary class.
    AlignmentSummary provides scrollable visualization of alignments based on genomic regions.
    """

    def __init__(self, spark, ac, alignmentDataset, sample = 1.0):
        """
        Initializes an AlignmentSummary class.

        Args:
            :param spark: SparkSession
            :param ac: ADAMContext
            :param alignmentDataset: bdgenomics.adam.Dataset.AlignmentRecoDatasetataset
            :param sample: fraction of reads to sample from
        """

        # sample must be between 0 and 1
        if sample <= 0 or sample > 1:
            raise Exception('sample {} should be > 0 and <= 1'.format(self.sample))

        self.ss = spark
        self.ac = ac
        self.sample = sample
        # sample dataset
        self.dataset = alignmentDataset

        # where to store collected data
        self.coverageDistribution = None
        self.fragmentDistribution = None
        self.mapQDistribution = None
        self.indelDistribution = None

    def getCoverageDistribution(self, bin_size = 10):
        """
        Computes coverage distribution for this AlignmentDataset.

        Args:
            :param bin_size: size to bin coverage by

        Returns:
           CoverageDistribution object
        """
        if self.coverageDistribution == None:
            print("Computing coverage distributions...")
            # pre-sample before computing coverage
            sampledDataset = self.dataset.transform(lambda dataset: dataset.sample(False, self.sample))
            self.coverageDistribution = CoverageDistribution(self.ss,   \
                                                             sampledDataset.toCoverage(), \
                                                             sample = self.sample, \
                                                             bin_size = bin_size,
                                                             pre_sampled = True)

        return self.coverageDistribution

    def getFragmentDistribution(self):
        """
        Computes fragment distribution for this AlignmentDataset.

        Returns:
           FragmentDistribution object
        """
        if self.fragmentDistribution == None:
            print("Computing fragment distributions...")
            self.fragmentDistribution = FragmentDistribution(self.ss, self.dataset, sample=self.sample)

        return self.fragmentDistribution


    def getMapQDistribution(self):
        """
        Computes mapping quality distribution for this AlignmentDataset.

        Returns:
           MapQDistribution object
        """
        if self.mapQDistribution == None:
            print("Computing MapQ distributions...")
            self.mapQDistribution = MapQDistribution(self.ss, self.dataset, sample=self.sample)

        return self.mapQDistribution


    def getIndelDistribution(self, bin_size=10000000):
        """
        Computes insertion and deletion distribution for this AlignmentDataset

        Returns:
           IndelDistribution object
        """
        if self.indelDistribution == None:
            print("Computing Indel distributions...")
            self.indelDistribution = IndelDistribution(self.ss, self.dataset, bin_size=bin_size, sample=self.sample)

        return self.indelDistribution


    def viewPileup(self, contig, start, end, reference = 'hg19', label = "Reads", multipleGroupNames = False, showPlot = True):
        """
        Visualizes a portion of this AlignmentDataset in a scrollable pileup widget

        Args:
            :param contig: contig of locus to view
            :param start: start position of locus to view
            :param end: end position of locus to view
            :param reference: genome build. Default is hg19
            :param label: name of alignment track
            :param multipleGroupName: determines whether to different show tracks for each group name.
            If false, coalesces into one track.
            :param showPlot: Disables widget, used for testing. Default is true.

        Returns:
            pileup view for alignments
        """
        contig_trimmed, contig_full = formatContig(contig)

        # Filter Dataset
        filtered = self.dataset.transform(lambda r: r.filter(((r.referenceName == contig_full) | (r.referenceName == contig_trimmed))
                                                                   & (r.start < end) & (r.end > start) & (r.readMapped)))

        # convert to GA4GH JSON to be consumed by mango-viz module
        json_map = self.ac._jvm.org.bdgenomics.mango.converters.GA4GHutil.alignmentRecordDatasetToJSON(filtered._jvmRdd,
                                                                                                   multipleGroupNames)

        # visualize
        if (showPlot):
        # make pileup tracks
            tracks = []
            # iterate through all group names and make a track for each.
            for groupName in json_map:
                # set label if only one track. Otherwise, set to group names.
                thisLabel = groupName if multipleGroupNames else label

                # create new track for this recordGroupName. files with unspecified recordGroup name
                # have a default label of "1"
                track=Track(viz="pileup", label=thisLabel, source=pileup.sources.GA4GHAlignmentJson(json_map[groupName]))
                tracks.append(track)

            locus="%s:%i-%i" % (contig, start, end)
            return pileup.PileupViewer(locus=locus, reference=reference, tracks=tracks)


class FragmentDistribution(CountDistribution):
    """ FragmentDistribution class.
    Plotting functionality for visualizing fragment distributions of multi-sample cohorts.
    """

    def __init__(self, ss, alignmentDataset, sample = 1.0):
        """
        Initializes a FragmentDistribution class.
        Computes the fragment distribution of a AlignmentDataset. This Dataset can have data for multiple samples.

        Args:
            :param ss: global SparkSession.
            :param alignmentDataset: bdgenomics.adam.Dataset.AlignmentDataset
            :param sample: Fraction to sample AlignmentDataset. Should be between 0 and 1
        """

        self.sc = ss.sparkContext
        self.sample = sample
        self.rdd = alignmentDataset.toDF().rdd \
            .map(lambda r: ((r["readGroupSampleId"], len(r["sequence"])), 1))

        CountDistribution.__init__(self)


class MapQDistribution(CountDistribution):
    """ MapQDistribution class.
    Plotting functionality for visualizing mapping quality distributions of multi-sample cohorts.
    """

    def __init__(self, ss, alignmentDataset, sample = 1.0):
        """
        Initializes a MapQDistribution class.
        Computes the mapping quality distribution of an AlignmentDataset. This Dataset can have data for multiple samples.

        Args:
            :param ss: global SparkSession.
            :param alignmentDataset: A bdgenomics.adam.dataset.AlignmentDataset object.
            :param sample: Fraction to sample AlignmentDataset. Should be between 0 and 1
        """

        self.sc = ss.sparkContext
        self.sample = sample

        # filter out reads that are not mappped
        self.rdd = alignmentDataset.transform(lambda r: r.filter(r.readMapped)).toDF().rdd \
            .map(lambda r: ((r["readGroupSampleId"], int(r["mappingQuality"] or 0)), 1))

        CountDistribution.__init__(self)


class IndelDistribution(object):
    """ IndelDistribution class.
    IndelDistribution calculates indel distributions on an AlignmentDataset.
    """

    def __init__(self, ss, alignmentDataset, sample=1.0, bin_size=10000000):
        """
        Initializes a IndelDistribution class.
        Computes the insertiona and deletion distribution of alignmentDataset.

        Args:
            :param SparkSession: the global SparkSession
            :param alignmentDataset: A bdgenomics.adam.dataset.AlignmentDataset object
            :param bin_size: Division size per bin
        """
        bin_size = int(bin_size)
        self.bin_size = bin_size
        self.sc = ss.sparkContext
        self.sample = sample

        # filter alignments without a position
        filteredAlignments = alignmentDataset.transform(lambda x: x.sample(False, self.sample)) \
            .transform(lambda x: x.filter(x["start"] >= 0))

        # Assign alignments with counter for contigs. Reduce and collect.
        mappedDistributions = filteredAlignments.toDF().rdd \
            .map(lambda r: ((r["referenceName"], r["start"] - r["start"]%bin_size), \
                            Counter(dict([(y,x) for x,y in Cigar(r["cigar"]).items()])))) \
            .reduceByKey(lambda x,y: x+y)

        self.alignments = mappedDistributions.collect()

    def plot(self, testMode = False, plotType="I", **kwargs):
        """
        Plots final distribution values and returns the plotted distribution as a counter object.

        Args:
            :param xScaleLog: rescales xaxis to log
            :param yScaleLog: rescales yaxis to log
            :param testMode: if true, does not generate plot. Used for testing.
            :param plotType: Cigar type to plot, from ['I', 'H', 'D', 'M', 'S']

        Returns:
            matplotlib axis to plot and computed data
        """
        chromosomes = Counter()


        # count contig type at each location
        for index, counts in self.alignments:
            chromosomes[index] += counts[plotType]

        if (not testMode): # For testing: do not run plots if testMode
            # get distinct chromosome names for plot
            keys = sorted(list(set(map(lambda x: x[0][0], self.alignments))))

            # offset for max chr
            offset = 0

            # holds xtick information
            midPoints = []

            # make figure plot
            figsize = kwargs.get('figsize',(10, 5))
            f, ax = plt.subplots(figsize=figsize)

            # for all chromosomes
            for key in keys:

                # filter by chromosome key
                values = [x for x in chromosomes.items() if x[0][0] == key]

                # get positions
                positions = list(map(lambda x: x[0][1] + offset, values))

                # get counts
                counts = list(map(lambda x: x[1], values))

                ax.plot(positions, counts, ls='', marker='.')

                # set label for chromosome
                midPoint = min(positions) + (max(positions) - min(positions))/2
                midPoints.append(midPoint)

                offset = max(positions)


            ax.set_xticks(midPoints)
            ax.set_xticklabels(keys, rotation=-90, size=8.5)
            return ax, chromosomes

        else:
            return None, chromosomes
