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

# from bdgenomics.adam.rdd import AlignmentRecordRDD, \
#     CoverageRDD, \
#     FeatureRDD, \
#     FragmentRDD, \
#     GenotypeRDD, \
#     NucleotideContigFragmentRDD, \
#     VariantRDD


class QC(object):
    """
    QC provides preprocessing functions for visualization
    of various quality control.
    """


    def __init__(self, sc):
        """
        Initializes an ADAMContext using a SparkContext.

        :param pyspark.context.SparkContext sc: The currently active
        SparkContext.
        """

        self._sc = sc
        self._jvm = sc._jvm


    def CoverageDistribution(self, coverageRDD):

        # TODO: convert to PDF
        return coverageRDD.toDF().count()
