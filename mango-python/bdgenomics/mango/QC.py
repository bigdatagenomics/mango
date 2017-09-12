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

class QC(object):
    """
    QC provides preprocessing functions for visualization
    of various quality control.
    """


    def __init__(self):
        """
        Initializes a Quality Control class.
        """

    def CoverageDistribution(self, coverageRDD, showPlot = True):

        # make sure coverageRDD is flattened
        flattened = coverageRDD.flatten()

        # compute coverage distribution
        coverageDistribution = flattened.toDF().rdd \
            .map(lambda r: (r["count"], 1)) \
            .reduceByKey(lambda x,y: x + y) \
            .collect()

        coverageDistribution.sort()

        # if showPlot is True, plot the coverage distribution
        if (showPlot):
            import matplotlib.pyplot as plt; plt.rcdefaults()
            import matplotlib.pyplot as plt

            coverage = map(lambda (x,y):x, coverageDistribution)
            counts = map(lambda (x,y):y, coverageDistribution)

            plt.bar(coverage, counts, align='center', alpha=0.5)
            plt.ylabel('Counts')
            plt.title('Target Region Coverage')

            plt.show()

        return coverageDistribution
