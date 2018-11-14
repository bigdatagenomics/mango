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

from __future__ import print_function
from setuptools import find_packages, setup
from version import version as mango_version

try: # for pip >= 10
    from pip._internal.req import parse_requirements
except ImportError: # for pip <= 9.0.3
    from pip.req import parse_requirements

import os
import sys

# Utility function to read the README file.
# Used for the long_description.
def read(fname):
    return open(os.path.join(os.path.dirname(__file__), fname)).read()

# parse_requirements() returns generator of pip.req.InstallRequirement objects
install_reqs = parse_requirements('requirements.txt', session='hack')

reqs = [str(ir.req) for ir in install_reqs]

long_description = "!!!!! missing pandoc do not upload to PyPI !!!!"
try:
    import pypandoc
    long_description = pypandoc.convert('README.md', 'rst')
except ImportError:
    print("Could not import pypandoc - required to package bdgenomics.mango", file=sys.stderr)
except OSError:
    print("Could not convert - pandoc is not installed", file=sys.stderr)

setup(
    name='bdgenomics.mango',
    version=mango_version,
    description='A scalable genomic visualization tool',
    author='Alyssa Morrow',
    author_email='akmorrow@berkeley.edu',
    url="https://github.com/bdgenomics/mango",
    install_requires=reqs,
    dependency_links=[
        'https://test.pypi.org/simple/bdgenomics-adam/'
    ],
    long_description=long_description,
    packages=find_packages(exclude=['*.test.*']))
