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

# generic data source for pileup.js
class Source:
    # dictionary containing source elements (viz, source, sourceOptions, label)
    dict_ = {}
    # name that pileup.js uses to identify sources
    name = None


# Generic data sources
class GA4GHSource(Source):
    def __init__(self, endpoint, readGroupId, callSetIds = None):
        """ Initializes GA4GHSource.

        Args:
            param str: url endpoint
            param str: read group id
            param str: optional call set ID for variants
        
        """

        self.dict_ = {
            'endpoint': endpoint,
            'readGroupId': readGroupId
        }


# For data stored as JSON strings
class jsonString(Source):
    def __init__(self, json):   
        """ Initializes GA4GH JSON.

        Args:
            param str: json in GA4GH format
        
        """

        self.dict_ = json


# can be used for TwoBit, vcf, BigBedDataSource, or BamDataSource
class FileSource(Source):  
    def __init__(self, url, indexUrl = None):
        """ Initializes file sources.

        Args:
            param str: url to file
            param str: indexUrl to index file
        
        """
        
        self.dict_ = {
            'url': url,
            'indexUrl': indexUrl
        }

##### Specific data sources build from generic data sources #####
# file sources
class BamDataSource(FileSource): 
    name = 'bam'
    
class VcfDataSource(FileSource): 
    name = 'vcf'
    
class TwoBitDataSource(FileSource): 
    name = 'twoBit'

class BigBedDataSource(FileSource): 
    name = 'bigBed'

    
# json built sources
class GA4GHAlignmentJson(jsonString): 
    name = 'alignmentJson'
    
class GA4GHVariantJson(jsonString): 
    name = 'variantJson'
    
class GA4GHFeatureJson(jsonString): 
    name = 'featureJson'
    
    
# GA4GH Sources
class GA4GHAlignmentSource(GA4GHSource): 
    name = 'GAReadAlignment'
    
class GA4GHVariantSource(GA4GHSource): 
    name = 'GAVariant'
    
class GA4GHFeatureSource(GA4GHSource): 
    name = 'GAFeature'


# dictionary of visualizations and corresponding data sources
vizNames = {
    'coverage': [BamDataSource.name, GA4GHFeatureJson.name],
    'pileup': [BamDataSource.name, GA4GHAlignmentJson.name, GA4GHAlignmentSource.name],
    'features': [BigBedDataSource.name, GA4GHFeatureJson.name, GA4GHFeatureSource.name],
    'variants': [VcfDataSource.name, GA4GHVariantJson.name, GA4GHVariantSource.name],
    'genome':[TwoBitDataSource.name],
    'genes': [BigBedDataSource.name],
    'scale':[],
    'location': []
}

# dictionary of source ids accepted by pileup and corresponding source classes
sourceNames = {
    BamDataSource.name:         BamDataSource,
    VcfDataSource.name:         VcfDataSource,
    TwoBitDataSource.name:      TwoBitDataSource,
    BigBedDataSource.name:      BigBedDataSource,
    GA4GHAlignmentJson.name:    GA4GHAlignmentJson,
    GA4GHFeatureJson.name:      GA4GHFeatureJson,
    GA4GHVariantJson.name:      GA4GHVariantJson,
    GA4GHAlignmentSource.name:  GA4GHAlignmentSource,
    GA4GHVariantSource.name:    GA4GHVariantSource,
    GA4GHFeatureSource.name:    GA4GHFeatureSource,
}

