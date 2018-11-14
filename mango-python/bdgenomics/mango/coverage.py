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
from .distribution import CountDistribution
plt.rcdefaults()

class CoverageDistribution(CountDistribution):
    """ CoverageDistribution class.
    Plotting functionality for visualizing coverage distributions of multi-sample cohorts.
    """

    def __init__(self, ss, coverageRDD, sample = 1.0, bin_size = 10, pre_sampled = False):
        """
        Initializes a CoverageDistribution class.
        Computes the coverage distribution of a CoverageRDD. This RDD can have data for multiple samples.

        Args:
            :param ss: global SparkSession.
            :param coverageRDD: A bdgenomics.adam.rdd.CoverageRDD object.
            :param sample: Fraction to sample CoverageRDD. Should be between 0 and 1

        """

        self.sc = ss.sparkContext
        self.sample = sample
        self.rdd = coverageRDD.toDF().rdd \
            .map(lambda r: ((r["optSampleId"], r["count"] - r["count"]%bin_size), (int(r["end"])-int(r["start"]))))

        CountDistribution.__init__(self)
