#!/bin/bash
# Publish an updated version of bdgenomics.mango.pileup on NPM.

set +x
set -o errexit

# Ensure a clean dist directory
rm -rf lib

jupyter labextension install .

# Once we have more confidence in it, this script should just run `npm publish`.
echo
echo "Now run npm publish"
