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
from .genomicfile import GenomicFile


class BedFile(GenomicFile):

    @classmethod
    def _read(cls, filepath_or_buffer, column_names, skiprows):
        return cls.dataframe_lib.read_table(filepath_or_buffer, names=column_names, skiprows=skiprows)
        
    @classmethod
    def _parse(cls, df):
        def _buildrow(row):
            chromosomes = row["chrom"]
            chrom_start = row["chromStart"]
            chrom_end = row["chromEnd"]
            bed_content = ' "referenceName":"{}", "start":"{}", "end":"{}"'.format(str(chromosomes), str(chrom_start), str(chrom_end))
            return bed_content
        return df.apply(_buildrow, axis = 1).squeeze()
    
    @classmethod
    def _to_json(cls, df):
        bed_content_series = cls._parse(df)
        json_ga4gh = "{\"features\":["
        json_ga4gh += "{" + bed_content_series.str.cat(sep="},{")[:-2]
        json_ga4gh = json_ga4gh + '"}]}'
        return json_ga4gh
    
    @classmethod
    def _visualization(cls, df):
        return 'featureJson'
