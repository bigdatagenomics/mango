#!/bin/bash
set -x -e

# AWS EMR bootstrap script for
# for installing Mango (https://github.com/bigdatagenomics/mango) on AWS EMR 5+
#
# 2018-10-29  - Alyssa Morrow akmorrow@berkeley.edu
#

# parameter that specifies version
VERSION=$1
OUTPUT_DIR=/home/hadoop

# check for master node
IS_MASTER=false
if grep isMaster /mnt/var/lib/info/instance.json | grep true;
then
  IS_MASTER=true

fi

export SPARK_HOME=/usr/lib/spark

# required to read files from s3a. Hack for pyspark requirements.
mkdir -p ${OUTPUT_DIR}/.ivy2/jars
wget -O ${OUTPUT_DIR}/.ivy2/jars/jsr203-s3a-0.0.2.jar http://central.maven.org/maven2/net/fnothaft/jsr203-s3a/0.0.2/jsr203-s3a-0.0.2.jar



# if version is a SNAPSHOT, pull from the sonotype repository
if [[ ${VERSION} == *"SNAPSHOT"* ]]; then

    classifier=-bin
    type=tar.gz
    repo=snapshots

    base="https://oss.sonatype.org/content/repositories/snapshots/org/bdgenomics/mango/mango-distribution"
    timestamp=`curl -s "${base}/${VERSION}/maven-metadata.xml" | xmllint --xpath "string(//timestamp)" -`
    buildnumber=`curl -s "${base}/${VERSION}/maven-metadata.xml" | xmllint --xpath "string(//buildNumber)" -`
    MANGO_DIST="${base}/${VERSION}/mango-distribution-${VERSION%-SNAPSHOT}-${timestamp}-${buildnumber}${classifier}.${type}"

else
    REPO_PREFIX="https://search.maven.org/remotecontent?filepath=org/bdgenomics/mango/mango-distribution"
    MANGO_DIST=${REPO_PREFIX}/${VERSION}/mango-distribution-${VERSION}-bin.tar.gz
fi

echo $MANGO_DIST

# pull and extract distribution code
wget -O ${OUTPUT_DIR}/mango-distribution-bin.tar.gz $MANGO_DIST
tar xzvf ${OUTPUT_DIR}/mango-distribution-bin.tar.gz --directory ${OUTPUT_DIR}
rm ${OUTPUT_DIR}/mango-distribution-bin.tar.gz

# rename mango for EMR easy access
mv ${OUTPUT_DIR}/mango-distribution* ${OUTPUT_DIR}/mango

mkdir -p ${OUTPUT_DIR}/mango/notebooks

# download 1000 genomes example notebook
wget -O ${OUTPUT_DIR}/mango/notebooks/aws-1000genomes.ipynb https://raw.githubusercontent.com/bigdatagenomics/mango/master/example-files/notebooks/aws-1000genomes.ipynb


# install mango python libraries and enable extension
sudo pip install bdgenomics.mango.pileup==${VERSION}
sudo pip install bdgenomics.mango==${VERSION}
/usr/local/bin/jupyter nbextension enable --py widgetsnbextension
/usr/local/bin/jupyter nbextension install --py --symlink --user bdgenomics.mango.pileup
/usr/local/bin/jupyter nbextension enable bdgenomics.mango.pileup --user --py

echo "Mango Distribution bootstrap action finished"
