#!/bin/bash

set +x

find . -name "pom.xml" -exec sed \
     -e "/scala\.version/ s/2\.10\.4/2.11.7/g" \
     -e "/scala\.version\.prefix/ s/2\.10/2.11/g" \
     -e "/spark\.version/ s/1\.6\.1/2.0.0/g" \
     -e "/spark\.version\.prefix/ s/\_/-spark2_/g" \
     -i.spark2.bak '{}' \;
