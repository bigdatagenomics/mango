#!/bin/bash
set -x -e

# AWS EMR bootstrap script for
# for installing Mango (https://github.com/bigdatagenomics/mango) on AWS EMR 5+
#
# 2018-10-29  - Alyssa Morrow akmorrow@berkeley.edu
#

# parameter that specifies version
VERSION=$1

# check for master node
IS_MASTER=false
if grep isMaster /mnt/var/lib/info/instance.json | grep true;
then
  IS_MASTER=true

fi


export SPARK_HOME=/usr/lib/spark

# required to read files from s3a. Hack for pyspark requirements.
mkdir -p /home/hadoop/.ivy2/jars
wget -O /home/hadoop/.ivy2/jars/jsr203-s3a-0.0.2.jar http://central.maven.org/maven2/net/fnothaft/jsr203-s3a/0.0.2/jsr203-s3a-0.0.2.jar

# pull and extract distribution code
wget -O /home/hadoop/mango-distribution-bin.tar.gz https://search.maven.org/remotecontent?filepath=org/bdgenomics/mango/mango-distribution/${VERSION}/mango-distribution-${VERSION}-bin.tar.gz
tar xzvf /home/hadoop/mango-distribution-bin.tar.gz --directory /home/hadoop/
rm /home/hadoop/mango-distribution-bin.tar.gz


mkdir -p /home/hadoop/mango-distribution-${VERSION}/notebooks

# download 1000 genomes example notebook
wget -O /home/hadoop/mango-distribution-${VERSION}/notebooks/aws-1000genomes.ipynb https://raw.githubusercontent.com/bigdatagenomics/mango/master/example-files/notebooks/aws-1000genomes.ipynb


# install mango python libraries and enable extension
sudo pip install bdgenomics.mango.pileup
sudo pip install bdgenomics.mango
/usr/local/bin/jupyter nbextension enable --py widgetsnbextension
/usr/local/bin/jupyter nbextension install --py --symlink --user bdgenomics.mango.pileup
/usr/local/bin/jupyter nbextension enable bdgenomics.mango.pileup --user --py

echo "Mango Distribution bootstrap action finished"
