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
============
PileupViewer
============
.. currentmodule:: bdgenomics.mango.pileup.pileupViewer
.. autosummary::
   :toctree: _generate/

   PileupViewer
"""

import ipywidgets as widgets
from traitlets import Unicode, Int, List
from .track import Track, track_list_serialization
import uuid
import json
from urllib.parse import unquote




@widgets.register('bdgenomics.mango.pileup.PileupViewer')
class PileupViewer(widgets.DOMWidget):
    """ Widget wrapper for pileup.js viewer in Jupyter notebooks.
    """

    _view_name = Unicode('PileupViewerView').tag(sync=True)
    _model_name = Unicode('PileupViewerModel').tag(sync=True)
    _view_module = Unicode('pileup').tag(sync=True)
    _model_module = Unicode('pileup').tag(sync=True)
    _view_module_version = Unicode('^0.1.0').tag(sync=True)
    _model_module_version = Unicode('^0.1.0').tag(sync=True)

    # Attributes
    # locus with placeholder
    locus=Unicode('chr1:1-50').tag(sync=True)
    svg=Unicode('').tag(sync=True)
    # message for updating js
    msg=Unicode('').tag(sync=True)
    # string of reference genome.
    reference = Unicode('hg19').tag(sync=True)
    # Array of track elements
    tracks = List(Track()).tag(sync=True, **track_list_serialization)
    id = uuid.uuid1().int

    def goto(self, locus):
        self.locus=locus

    def zoomOut(self):
        self.msg = "zoomOut"
        self.msg = ""

    def zoomIn(self):
        self.msg = "zoomIn"
        self.msg = ""

    def getSVG(self):
        """ Sends request to javascript to convert to svg
        Needs to run separately from saveSVG because
        js cannot message to kernel until cell is completed.
        """
        self.msg = "toSVG"
        self.msg = ""


    def saveSVG(self, filepath):
        """ Saves svg to filepath
        Args:
            :param filepath: path to save svg file to
        """

        if len(self.svg) == 0:
            raise Exception("No SVG to save.")

        decoded = unquote(self.svg)
        svg_txt = decoded.replace("data:image/svg+xml;charset=utf-8,", "")

        with open(filepath, "w") as f: f.write(svg_txt)


