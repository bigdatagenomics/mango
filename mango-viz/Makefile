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

define help

Supported targets: prepare, develop, clean, test, check_build_reqs.

endef
export help
help:
	@printf "$$help"

# This Makefile uses bash features like printf and <()
SHELL=bash
python=python2.7
jupyter=jupyter
pip=pip
npm=npm
node=node
mango_version:=$(shell $(python) version.py)
sdist_name:=mango.pileup-$(mango_version).tar.gz
tests=bdgenomics/mango/pileup/
extras=
current_commit:=$(shell git log --pretty=oneline -n 1 -- $(pwd) | cut -f1 -d " ")
dirty:=$(shell (git diff --exit-code && git diff --cached --exit-code) > /dev/null || printf -- --DIRTY)

green=\033[0;32m
normal=\033[0m\n
red=\033[0;31m

develop:
	- $(pip) install -e .$(extras)
	- $(pip) install jupyter==1.0.0
	- $(jupyter) nbextension enable --py widgetsnbextension
	- $(jupyter) nbextension install --py --symlink --user bdgenomics.mango.pileup
	- $(jupyter) nbextension enable bdgenomics.mango.pileup --py --user


sdist: dist/$(sdist_name)
dist/$(sdist_name):
	@test -f dist/$(sdist_name) && mv dist/$(sdist_name) dist/$(sdist_name).old || true
	$(python) setup.py sdist bdist_egg
	@test -f dist/$(sdist_name).old \
			&& ( cmp -s <(tar -xOzf dist/$(sdist_name)) <(tar -xOzf dist/$(sdist_name).old) \
					 && mv dist/$(sdist_name).old dist/$(sdist_name) \
					 && printf "$(green)No significant changes to sdist, reinstating backup.$(normal)" \
					 || rm dist/$(sdist_name).old ) \
			|| true
clean_sdist:
	- rm -rf dist


test: check_build_reqs
	pushd ../example-files/notebooks \
		&& jupyter nbconvert --to python mango-viz.ipynb
	mkdir -p target
	$(python) -m pytest -vv --junitxml target/pytest-reports/tests.xml $(tests)

# clean
clean:
	rm -rf bdgenomics/mango/pileup/static/
	rm -rf dist/
	$(jupyter) nbextension uninstall pileup
	$(npm) uninstall pileup

# check for node, jupyter and npm
check_build_reqs:
	@$(python) -c 'import jupyter' \
		|| ( printf "$(red)Build requirements are missing. Run 'make prepare' to install them.$(normal)" ; false )

	@$(npm) --version > /dev/null \
		|| ( printf "$(red)npm is not installed. Please install npm: https://www.npmjs.com/get-npm" ; false)

	@$(node) --version > /dev/null \
		|| ( printf "$(red)node is not installed. Please install node: https://nodejs.org/en/download/package-manager/" ; false)


# installs necessary requirements
prepare:
	$(pip) install pytest==2.8.3
	$(pip) install jupyter==1.0.0
	$(pip) install ipywidgets==7.0.0

	# install pileup.js to node_modules
	cd bdgenomics/mango/js; \
		mkdir node_modules; \
		cd node_modules; \
		$(npm) install --save pileup@0.6.9
