#
# Licensed to Big Data Genomics (BDG) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The BDG licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
# 	http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

r"""
===============================
bdgenomics.mango.pileup Package
===============================
.. currentmodule:: bdgenomics.mango.pileup

Each widget instance calls the `PileupViewer` to draw an interactive widget for genomic data.

.. automodule:: bdgenomics.mango.pileup.pileupViewer
.. automodule:: bdgenomics.mango.pileup.sources
.. automodule:: bdgenomics.mango.pileup.track

"""

from ._version import version_info, __version__

from .pileupViewer import *

def _jupyter_nbextension_paths():
    return [{
        'section': 'notebook',
        'src': 'static',
        'dest': 'pileup',
        'require': 'pileup/extension'
    }]
