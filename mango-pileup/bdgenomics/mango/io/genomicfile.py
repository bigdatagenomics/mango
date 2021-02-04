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

import os

if os.environ.get("MANGO_USE_PANDAS", "False").title() == "False":
    import modin.pandas as pd
else:
    import pandas as pd

class GenomicFile(object):
    dataframe_lib = pd

    @classmethod
    def read(cls, *args, **kwargs):
        df = cls._read(*args, **kwargs)
        df._mango_parse = cls._parse(df)
        df._mango_to_json = cls._to_json(df)
        df._pileup_visualization = cls._visualization(df)
        return df

    @classmethod
    def _read(cls, *args, **kwargs):
        raise NotImplementedError("Must be implemented in children classes")

    @classmethod
    def _parse(cls, df):
        raise NotImplementedError("Must be implemented in children classes")

    @classmethod
    def _to_json(cls, df):
        raise NotImplementedError("Must be implemented in children classes")
    @classmethod
    def _visualization(cls, df):
        raise NotImplementedError("Must be implemented in children classes")

    @classmethod
    def from_pandas(cls, df):
        df._mango_parse = cls._parse(df)
        df._mango_to_json = cls._to_json(df)
        df._pileup_visualization = cls._visualization(df)
        return df
