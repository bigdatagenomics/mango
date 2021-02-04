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


import json
import os
import re
import tempfile
import shutil

import traitlets

from ipywidgets.widgets import Widget, jslink, HBox, widget_serialization
from ipywidgets.embed import embed_data, embed_snippet, embed_minimal_html, dependency_state

try:
    from io import StringIO
except ImportError:
    from StringIO import StringIO

try:
    # Python 3
    from html.parser import HTMLParser
except ImportError:
    # Python 2
    from HTMLParser import HTMLParser

import bdgenomics.mango.pileup as pileup


class CaseWidget(Widget):
    """Widget to test dependency traversal"""

    a = traitlets.Instance(Widget, allow_none=True).tag(sync=True, **widget_serialization)
    b = traitlets.Instance(Widget, allow_none=True).tag(sync=True, **widget_serialization)

    _model_name = traitlets.Unicode('CaseWidgetModel').tag(sync=True)

    other = traitlets.Dict().tag(sync=True, **widget_serialization)




class TestEmbed:

    def teardown(self):
        for w in tuple(Widget.widgets.values()):
            w.close()

    def test_embed_pileup(self):

        track = pileup.Track(viz="features", label="myFeatures", source=pileup.sources.GA4GHFeatureJson('{}'))

        w = pileup.PileupViewer(chrom="chr17",start=1,stop=250, reference="hg19", tracks=[track])
        state = dependency_state(w, drop_defaults=True)
        data = embed_data(views=w, drop_defaults=True, state=state)

        state = data['manager_state']['state']
        views = data['view_specs']

        assert len(views) == 1

        model_names = [s['model_name'] for s in state.values()]
        assert 'PileupViewerModel' in model_names


    def test_embed_data_two_widgets(self):
        feature_track = pileup.Track(viz="features", label="myFeatures", source=pileup.sources.GA4GHFeatureJson('{}'))
        variant_track = pileup.Track(viz="variants", label="myVariants", source=pileup.sources.GA4GHVariantJson('{}'))

        w1 = pileup.PileupViewer(chrom="chr17",start=1,stop=250, reference="hg19", tracks=[feature_track])
        w2 = pileup.PileupViewer(chrom="chr17",start=1,stop=250, reference="hg19", tracks=[variant_track])

        jslink((w1, 'reference'), (w2, 'reference'))
        state = dependency_state([w1, w2], drop_defaults=True)
        data = embed_data(views=[w1, w2], drop_defaults=True, state=state)

        state = data['manager_state']['state']
        views = data['view_specs']

        assert len(views) == 2

        model_names = [s['model_name'] for s in state.values()]
        widget_names = list(filter(lambda x: x == 'PileupViewerModel', model_names))
        assert len(widget_names) == 2

    def test_snippet(self):

        class Parser(HTMLParser):
            state = 'initial'
            states = []

            def handle_starttag(self, tag, attrs):
                attrs = dict(attrs)
                if tag == 'script' and attrs.get('type', '') == "application/vnd.jupyter.widget-state+json":
                    self.state = 'widget-state'
                    self.states.append(self.state)
                elif tag == 'script' and attrs.get('type', '') == "application/vnd.jupyter.widget-view+json":
                    self.state = 'widget-view'
                    self.states.append(self.state)

            def handle_endtag(self, tag):
                self.state = 'initial'

            def handle_data(self, data):
                if self.state == 'widget-state':
                    manager_state = json.loads(data)['state']
                    assert len(manager_state) == 2
                    self.states.append('check-widget-state')
                elif self.state == 'widget-view':
                    view = json.loads(data)
                    assert isinstance(view, dict)
                    self.states.append('check-widget-view')

        track = pileup.Track(viz="variants", label="myVariants", source=pileup.sources.GA4GHVariantJson('{}'))

        w = pileup.PileupViewer(chrom="chr17",start=1,stop=250, reference="hg19", tracks=[track])

        state = dependency_state(w, drop_defaults=True)
        snippet = embed_snippet(views=w, drop_defaults=True, state=state)
        parser = Parser()
        parser.feed(snippet)
        assert parser.states == ['widget-state', 'check-widget-state', 'widget-view', 'check-widget-view']


    def test_minimal_pileup_html(self):
        track = pileup.Track(viz="pileup", label="myReads", source=pileup.sources.GA4GHAlignmentJson('{}'))

        w = pileup.PileupViewer(chrom="chr17",start=1,stop=250, reference="hg19", tracks=[track])
        output = StringIO()
        state = dependency_state(w, drop_defaults=True)
        embed_minimal_html(output, views=w, drop_defaults=True, state=state)
        content = output.getvalue()
        assert content.splitlines()[0] == '<!DOCTYPE html>'
