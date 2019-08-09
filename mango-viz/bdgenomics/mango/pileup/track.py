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

r"""
=====
Track
=====
.. currentmodule:: bdgenomics.mango.pileup.track

Tracks specify what visualization will be drawn. 

.. autosummary::
    :toctree: _generate/

    Track
    track_to_json
    track_from_json
    tracks_to_json
    tracks_from_json
"""

from traitlets import TraitType
import six
from .sources import *


class Track(TraitType):
    """A trait for a pileupTrack, requires a viz string of (coverage, pileup, features, variants, genome, genes, scale, or location)
    and a DataSource.
    """
    # pileup.viz, pileup.source, file or data, track name
    info_text = 'a pileup track (requires names for pileup.viz, pileup.source, pileup.source, and optional pileup.label)'

    #: visualization specified in pileup.js. Can be, for example, features, pileup, variants, etc.
    viz = None
    #: Datasource consumed by pileup.js. Can be, for example, BamDataSource, VcfDataSource, etc.
    source = None
    #: Options for source. Includes index files, etc.
    sourceOptions = None
    #: Label for this track.
    label = None

    def __init__(self, **kwargs):
        """ Initializes track. 

        Args:
            :param kwargs: Should contain viz, optional source, optional label.

        """
        for key, value in kwargs.items():
            
            if key == "viz":
                if value not in vizNames.keys():
                    raise RuntimeError('Invalid track visualization %s. Available tracks include %s' % (value, vizNames.keys()))
                setattr(self, key, value)
                
            elif key == "source":
                if value.name not in vizNames[kwargs["viz"]]:
                    raise RuntimeError('Invalid data source %s for track %s' 
                                       % (value.name, kwargs["viz"]))

                self.source = kwargs["source"].name
                self.sourceOptions = kwargs["source"].dict_
            else:
                setattr(self, key, value)
        
              

def track_to_json(pyTrack, manager):
    """Serialize a Track.
    Attributes of this dictionary are to be passed to the JavaScript Date
    constructor.

    Args:
        :param Track: Track object
        :param any: manager. Used for widget serialization.

    Returns:
        dict of Track elements (viz, source, sourceOptions and label)

    """
    if pyTrack is None:
        return None
    else:
        return dict(
            viz=pyTrack.viz,
            source=pyTrack.source,
            sourceOptions=pyTrack.sourceOptions,
            label=pyTrack.label
        )

def track_from_json(js, manager):
    """Deserialize a Track from JSON.

    Args:
        :param (str): json for Track containing viz, source, sourceOptions and label
        :param (any): manager. Used for widget serialization.

    Returns:
        Track: pileup Track built from json

    """
    if js is None:
        return None
    else:
        return Track(
            viz = js['viz'],
            source = sourceNames[js['source']], # need to go from source name to class
            sourceOptions = js['sourceOptions'],
            label = js['label'])

def tracks_to_json(pyTracks, manager):
    """Serialize a Python date object.
    Attributes of this dictionary are to be passed to the JavaScript Date
    constructor.

    Args:
        :param (List): List of Tracks
        :param (any): manager. Used for widget serialization.

    Returns:
        List of dict of Track elements (viz, source, sourceOptions and label)

    """

    if pyTracks is None:
        return None
    else:
        return [track_to_json(x, manager) for x in pyTracks]


def tracks_from_json(js, manager):
    """Deserialize a list of Tracks from JSON.

    Args:
        :param (str): json for list of Tracks containing viz, source, sourceOptions and label
        :param (any): manager. Used for widget serialization.

    Returns:
        List: List of pileup Track built from json

    """

    if js is None:
        return None
    else:
        return [track_from_json(j, manager) for j in js]


# used to serialize and deserialize tracks in javascript
track_serialization = {
    'from_json': track_from_json,
    'to_json': track_to_json
}

# used to serialize and deserialize track lists in javascript
track_list_serialization = {
    'from_json': tracks_from_json,
    'to_json': tracks_to_json
}
