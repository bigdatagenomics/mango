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

import matplotlib.pyplot as plt;
plt.rcdefaults()

class QC(object):
    """
    QC provides preprocessing functions for visualization
    of various quality control.
    """


    def __init__(self):
        """
        Initializes a Quality Control class.
        """

    def CoverageDistribution(self, coverageRDDs, showPlot=True, normalize = False, cummulative = False,):
        """
        Computes the coverage distribution of a coverageRDD.
        :param bdgenomics.adam.rdd.CoverageRDD coverageRDDs: A list of coverageRDDs
        :param bool showPlot: whether to plot the coverage distribution.
        :param bool normalize: whether to normalize the distribution.
        :param bool cummulative: whether to plot the cummulative distribution.
        :return: list of coverage distributions per input coverageRDD.
        :rtype: list of list of (string, string) tuples
        """
        coverageDistributions = []
        if (not isinstance(coverageRDDs, list)):
            coverageRDDs = [coverageRDDs]
        for coverageRDD in coverageRDDs:
            coverageDistribution = coverageRDD.flatten().toDF().rdd \
                .map(lambda r: (r["count"], 1)) \
                .reduceByKey(lambda x, y: x + y) \
                .sortByKey() \
                .collect()

            if normalize:
                total = float(sum([d[1] for d in coverageDistribution]))
                coverageDistribution = map(lambda(x, y): (int(x),y/total), coverageDistribution)

            if cummulative:
                def incrementTotal(i):
                    self.cummulativeSum += i
                    return self.cummulativeSum
                self.cummulativeSum = 0.0
                coverageDistribution = map(lambda(x, y): (x, incrementTotal(y)), coverageDistribution)


        if showPlot:
            title =  'Target Region Coverage'
            if cummulative:
                title = 'Cummulative ' + title
            if normalize:
                title = 'Normalized ' + title
            plt.ylabel('Counts')
            plt.xlabel('Coverage')
            plt.title(title)
            for count, coverageDistribution in enumerate(coverageDistributions):
                coverage = map(lambda(x, y): x, coverageDistribution)
                counts = map(lambda(x, y): y, coverageDistribution)
                plt.plot(coverage, counts, marker = 'o', label = "Coverage " + str(count + 1))
            plt.legend(loc=2, shadow = True, bbox_to_anchor=(1.05, 1))
            plt.show()

        return coverageDistributions
