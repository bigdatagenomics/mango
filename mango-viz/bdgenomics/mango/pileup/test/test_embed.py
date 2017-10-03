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

    def test_embed_features(self):
        w = pileup.Features(json = "{}", build='hg19', contig='chr1',start=1,stop=20)
        state = dependency_state(w, drop_defaults=True)
        data = embed_data(views=w, drop_defaults=True, state=state)

        state = data['manager_state']['state']
        views = data['view_specs']

        assert len(views) == 1

        model_names = [s['model_name'] for s in state.values()]
        assert 'FeatureModel' in model_names


    def test_embed_alignments(self):
        w = pileup.Reads(json = "{}", build='hg19', contig='chr1',start=1,stop=20)
        state = dependency_state(w, drop_defaults=True)
        data = embed_data(views=w, drop_defaults=True, state=state)

        state = data['manager_state']['state']
        views = data['view_specs']

        assert len(views) == 1

        model_names = [s['model_name'] for s in state.values()]
        assert 'ReadsModel' in model_names


    def test_embed_variants(self):
        w = pileup.Variants(json = "{}", build='hg19', contig='chr1',start=1,stop=20)
        state = dependency_state(w, drop_defaults=True)
        data = embed_data(views=w, drop_defaults=True, state=state)

        state = data['manager_state']['state']
        views = data['view_specs']

        assert len(views) == 1

        model_names = [s['model_name'] for s in state.values()]
        assert 'VariantModel' in model_names

    def test_embed_data_two_widgets(self):
        w1 = pileup.Variants(json = "{}", build='hg19', contig='chr1',start=1,stop=20)
        w2 = pileup.Features(json = "{}", build='hg19', contig='chr1',start=1,stop=20)

        jslink((w1, 'start'), (w2, 'start'))
        state = dependency_state([w1, w2], drop_defaults=True)
        data = embed_data(views=[w1, w2], drop_defaults=True, state=state)

        state = data['manager_state']['state']
        views = data['view_specs']

        assert len(views) == 2

        model_names = [s['model_name'] for s in state.values()]
        assert 'VariantModel' in model_names
        assert 'FeatureModel' in model_names

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

        w = pileup.Reads(json = "{}", build='hg19', contig='chr1',start=1,stop=20)
        state = dependency_state(w, drop_defaults=True)
        snippet = embed_snippet(views=w, drop_defaults=True, state=state)
        parser = Parser()
        parser.feed(snippet)
        assert parser.states == ['widget-state', 'check-widget-state', 'widget-view', 'check-widget-view']


    def test_minimal_reads_html(self):
        w = pileup.Reads(json = "{}", build='hg19', contig='chr1',start=1,stop=20)
        output = StringIO()
        state = dependency_state(w, drop_defaults=True)
        embed_minimal_html(output, views=w, drop_defaults=True, state=state)
        content = output.getvalue()
        assert content.splitlines()[0] == '<!DOCTYPE html>'



    def test_minimal_features_html(self):
        w = pileup.Features(json = "{}", build='hg19', contig='chr1',start=1,stop=20)
        output = StringIO()
        state = dependency_state(w, drop_defaults=True)
        embed_minimal_html(output, views=w, drop_defaults=True, state=state)
        content = output.getvalue()
        assert content.splitlines()[0] == '<!DOCTYPE html>'



    def test_minimal_variants_html(self):
        w = pileup.Variants(json = "{}", build='hg19', contig='chr1',start=1,stop=20)
        output = StringIO()
        state = dependency_state(w, drop_defaults=True)
        embed_minimal_html(output, views=w, drop_defaults=True, state=state)
        content = output.getvalue()
        print("CONTENT!!!!!!!!!!!!!!!!!!")
        print(content)
        assert content.splitlines()[0] == '<!DOCTYPE html>'