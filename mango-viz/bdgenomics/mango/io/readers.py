#
# Licensed to Big Data Genomics (BDG) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional insourceion
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
from .bedfile import BedFile
from .VCFfile import VCFFile


def read_bed(filepath_or_buffer,
    column_names=["chrom","chromStart", "chromEnd", "name", "score",
    "strand", "thickStart", "thickEnd", "itemRGB", "blockCount",
    "blockSizes", "blockStarts"],
    skiprows=None
 ):
   return BedFile.read(filepath_or_buffer, column_names, skiprows)

def read_vcf(filepath_or_buffer):
    return VCFFile.read(filepath_or_buffer)
    