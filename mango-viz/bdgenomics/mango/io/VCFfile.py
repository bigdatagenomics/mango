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
#Contributors:
#VCF.py
#Kamil Slowikowski
#October 30, 2013

from .genomicfile import GenomicFile
from collections import OrderedDict
import gzip

class VCFFile(GenomicFile):

    global VCF_HEADER
    global NUM_SAMPLES
    VCF_HEADER = ['CHROM', 'POS', 'ID', 'REF', 'ALT', 'QUAL', 'FILTER', 'INFO', 'FORMAT']

    @classmethod
    def _read(cls, filename, large = True):
        """Open an optionally gzipped VCF file and return a pandas.DataFrame with
        each INFO field included as a column in the dataframe.

        Note: Using large=False with large VCF files. It will be painfully slow.
        :param filename:    An optionally gzipped VCF file.
        :param large:       Use this with large VCF files to skip the ## lines and
                            leave the INFO fields unseparated as a single column.
        """
        print("this is the filename", filename)
        if large:
            # Set the proper argument if the file is compressed.
            comp = 'gzip' if filename.endswith('.gz') else None
            # Count how many comment lines should be skipped.
            comments = cls._count_comments(filename)
            # Return a simple DataFrame without splitting the INFO column.
            df = cls.dataframe_lib.read_table(filename, compression=comp, skiprows=range(comments))
            NUM_SAMPLES = len(df.columns) - len(VCF_HEADER)
            df.columns = VCF_HEADER + [i for i in range(1, NUM_SAMPLES+1)]
            return df

    @classmethod
    def _parse(cls, df):
        references = list(df["CHROM"])
        chrom_starts = list(df["POS"])
        chrom_ends = [item + 1 for item in chrom_starts]
        reference_bases = list(df["REF"])
        alternate_bases = list(df["ALT"])
        ids = list(df["ID"])
        return (chrom_starts, chrom_ends, references, reference_bases, alternate_bases, ids)
    
    @classmethod
    def _to_json(cls, df):
        chrom_starts, chrom_ends, chromosomes, reference_bases, alternate_bases, ids = cls._parse(df)
        json_ga4gh = "{\"variants\":["
        for i in range(len(chromosomes)+1):
            if i < len(chromosomes):
                bed_content = '"referenceName":{}, "start":{}, "end":{}, "referenceBases":{}, "alternateBases":{}, "id":{}'.format("\""+str(chromosomes[i])+"\"", "\""+str(chrom_starts[i])+"\"", "\""+str(chrom_ends[i])+"\"", "\""+str(reference_bases[i])+"\"", "\""+str(alternate_bases[i])+"\"", "\""+str(ids[i])+"\"")
                json_ga4gh = json_ga4gh + "{" + bed_content + "},"
            else:
                json_ga4gh = json_ga4gh[:len(json_ga4gh)-1]


        #ending json
        json_ga4gh = json_ga4gh + "]}"
        return json_ga4gh
        
    
    @classmethod
    def _visualization(cls, df):
        return 'variantJson'

    @classmethod
    def _count_comments(cls, filename):
        """Count comment lines (those that start with "#") in an optionally
        gzipped file.
        :param filename:  An optionally gzipped file.
        """
        comments = 0
        fn_open = gzip.open if filename.endswith('.gz') else open
        with fn_open(filename) as fh:
            for line in fh:
                if line.startswith('#'):
                    comments += 1
                else:
                    break
        return comments
