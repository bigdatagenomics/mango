#!/bin/bash
# Generate code coverage data for posting to Coveralls.
# Output is coverage/lcov.info

set -o errexit
set -x

# Instrument the source code with Istanbul's __coverage__ variable.
rm -rf coverage/*  # Clear out everything to ensure a hermetic run.
istanbul instrument --output coverage src

# Run the tests using mocha-phantomjs & mocha-phantomjs-istanbul
# This produces coverage/coverage.json
phantomjs \
  ./node_modules/mocha-phantomjs/lib/mocha-phantomjs.coffee \
  test/coverage.html \
  spec '{"hooks": "mocha-phantomjs-istanbul", "coverageFile": "coverage/coverage.json"}'

if [ $CI ]; then
  # Convert the JSON coverage to LCOV for coveralls.
  istanbul report --include coverage/*.json lcovonly

  # Post the results to coveralls.io
  set +o errexit
  cat coverage/lcov.info | coveralls

  echo ''  # reset exit code -- failure to post coverage shouldn't be an error.

else
  # Convert the JSON coverage to HTML for viewing
  istanbul report --include coverage/*.json html
  set +x

  echo 'To browse coverage, run:'
  echo
  echo '  open coverage/index.html'
  echo
fi
