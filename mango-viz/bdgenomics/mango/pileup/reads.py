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

import ipywidgets as widgets
from traitlets import Unicode, Int

@widgets.register('bdgenomics.mango.pileup.Reads')
class Reads(widgets.DOMWidget):
    """"""
    _view_name = Unicode('ReadsView').tag(sync=True)
    _model_name = Unicode('ReadsModel').tag(sync=True)
    _view_module = Unicode('pileup').tag(sync=True)
    _model_module = Unicode('pileup').tag(sync=True)
    _view_module_version = Unicode('^0.1.0').tag(sync=True)
    _model_module_version = Unicode('^0.1.0').tag(sync=True)
    json = Unicode('{}').tag(sync=True)
    build = Unicode('hg19').tag(sync=True)
    contig = Unicode('chr1').tag(sync=True)
    start = Int(1).tag(sync=True)
    stop = Int(50).tag(sync=True)
