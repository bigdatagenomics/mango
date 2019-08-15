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
    VCF_HEADER = ['CHROM', 'POS', 'ID', 'REF', 'ALT', 'QUAL', 'FILTER', 'INFO']

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
            return cls.dataframe_lib.read_table(filename, compression=comp, skiprows=range(comments),
                                names=VCF_HEADER, usecols=range(8))

        # Each column is a list stored as a value in this dict. The keys for this
        # dict are the VCF column names and the keys in the INFO column.
        result = OrderedDict()
        # Parse each line in the VCF file into a dict.
        for i, line in enumerate(cls.lines(filename)):
            for key in line.keys():
                # This key has not been seen yet, so set it to None for all
                # previous lines.
                if key not in result:
                    result[key] = [None] * i
            # Ensure this row has some value for each column.
            for key in result.keys():
                result[key].append(line.get(key, None))

        return cls.dataframe_lib.DataFrame(result)

    @classmethod
    def _parse(cls, df):
        return 'class method parse function not implemented'
    
    @classmethod
    def _to_json(cls, df):
        #need to see whether there is a json connection from vcf to mango
        return 'not implemented, is it necessary?'
    
    @classmethod
    def _visualization(cls, df):
        return 'vcf'

    @classmethod
    def parse(cls, line):
        """
        Parse a single VCF line and return an OrderedDict.

        """
        result = OrderedDict()

        fields = line.rstrip().split('\t')

        # Read the values in the first seven columns.
        for i, col in enumerate(VCF_HEADER[:7]):
            result[col] = cls._get_value(fields[i])

        # INFO field consists of "key1=value;key2=value;...".
        infos = fields[7].split(';')

        for i, info in enumerate(infos, 1):
            # info should be "key=value".
            try:
                key, value = info.split('=')
            # But sometimes it is just "value", so we'll make our own key.
            except ValueError:
                key = 'INFO{}'.format(i)
                value = info
            # Set the value to None if there is no value.
            result[key] = cls._get_value(value)

        return result

    
    @classmethod
    def lines(cls, filename):
        """Open an optionally gzipped VCF file and generate an OrderedDict for
        each line.
        """
        fn_open = gzip.open if filename.endswith('.gz') else open

        with fn_open(filename) as fh:
            for line in fh:
                if line.startswith('#'):
                    continue
                else:
                    yield cls.parse(line)

    @classmethod
    def _get_value(cls, value):
        """Interpret null values and return ``None``. Return a list if the value
        contains a comma.
        """
        if not value or value in ['', '.', 'NA']:
            return None
        if ',' in value:
            return value.split(',')
        return value

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
