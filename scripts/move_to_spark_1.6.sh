#!/bin/bash

set +x

find . -name "pom.xml" -exec sed \
     -e "/scala\.version/ s/2\.11\.7/2.10.4/g" \
     -e "/scala\.version\.prefix/ s/2\.11/2.10/g" \
     -e "/spark\.version/ s/2\.0\.0/1.6.1/g" \
     -e "/spark\.version\.prefix/ s/-spark2\_/_/g" \
     -i.spark2.bak '{}' \;
