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

from traitlets import TraitType
import six


class Track(TraitType):
    """A trait for a pileupTrack. Contains name, label, format and source.
    """
    # pileup.viz, pileup.format, file or data, track name
    info_text = 'a pileup track (requires names for pileup.viz, pileup.format, pileup.source, and optional pileup.label)'

    viz = None
    format = None
    source = None
    label = None

    def __init__(self, **kwargs):
        for key, value in kwargs.items():
            setattr(self, key, value)

def track_to_json(pyTrack, manager):
    """Serialize a Python date object.
    Attributes of this dictionary are to be passed to the JavaScript Date
    constructor.
    """
    if pyTrack is None:
        return None
    else:
        return dict(
            viz=pyTrack.viz,
            format=pyTrack.format,
            source=pyTrack.source,
            label=pyTrack.label
        )

def track_from_json(js, manager):
    """Deserialize a Javascript date."""
    if js is None:
        return None
    else:
        return Track(
            viz = js['viz'],
            format = js['format'],
            source = js['source'],
            label = js['label'])

def tracks_to_json(pyTracks, manager):
    """Serialize a Python date object.
    Attributes of this dictionary are to be passed to the JavaScript Date
    constructor.
    """
    if pyTracks is None:
        return None
    else:
        return [track_to_json(x, manager) for x in pyTracks]


def tracks_from_json(js, manager):
    """Deserialize a Javascript date."""
    if js is None:
        return None
    else:
        return [this.track_from_json(j, manager) for j in js]


track_serialization = {
    'from_json': track_from_json,
    'to_json': track_to_json
}

track_list_serialization = {
    'from_json': tracks_from_json,
    'to_json': tracks_to_json
}
