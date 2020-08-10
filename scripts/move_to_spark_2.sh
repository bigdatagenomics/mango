#!/bin/bash

set +x

grep -q "spark2" pom.xml
if [[ $? == 0 ]];
then
    echo "POM is already set up for Spark 2 (Spark 2 artifacts have -spark2 suffix in artifact names)."
    echo "Cowardly refusing to move to Spark 2 a second time..."

    exit 1
fi

svp="\${scala.version.prefix}"

find . -name "pom.xml" -exec sed \
    -e "/spark.version/ s/3.0.0/2.4.6/g" \
    -e "/spark.version.prefix/ s/-spark3_/-spark2_/g" \
    -i.spark2.bak '{}' \;
