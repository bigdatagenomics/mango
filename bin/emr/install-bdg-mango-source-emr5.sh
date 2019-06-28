#!/bin/bash
set -x -e

# AWS EMR bootstrap script for
# for installing Mango (https://github.com/bigdatagenomics/mango) on AWS EMR 5+
#
# 2019-06-28  - Alyssa Morrow akmorrow@berkeley.edu
#


# check for master node
IS_MASTER=false
if grep isMaster /mnt/var/lib/info/instance.json | grep true;
then
  IS_MASTER=true

fi

# install git and maven
sudo yum install git

# install maven 3.3.9
wget http://mirror.olnevhost.net/pub/apache/maven/maven-3/3.3.9/binaries/apache-maven-3.3.9-bin.tar.gz
tar xvf apache-maven-3.3.9-bin.tar.gz
sudo mv apache-maven-3.3.9  /usr/local/apache-maven

export M2_HOME=/usr/local/apache-maven
export M2=$M2_HOME/bin
export PATH=$M2:$PATH

export SPARK_HOME=/usr/lib/spark

# required to read files from s3a. Hack for pyspark requirements.
mkdir -p /home/hadoop/.ivy2/jars
wget -O /home/hadoop/.ivy2/jars/jsr203-s3a-0.0.2.jar http://central.maven.org/maven2/net/fnothaft/jsr203-s3a/0.0.2/jsr203-s3a-0.0.2.jar


# install node.js and npm
sudo yum install -y gcc-c++ make
curl -sL https://rpm.nodesource.com/setup_6.x | sudo -E bash -
sudo yum install nodejs

# pull and build source code
cd /home/hadoop
git clone https://github.com/bigdatagenomics/mango.git
cd mango
mvn clean package -DskipTests

# install mango python libraries and enable extension
sudo pip install bdgenomics.mango.pileup
sudo pip install bdgenomics.mango
/usr/local/bin/jupyter nbextension enable --py widgetsnbextension
/usr/local/bin/jupyter nbextension install --py --symlink --user bdgenomics.mango.pileup
/usr/local/bin/jupyter nbextension enable bdgenomics.mango.pileup --user --py

echo "Mango Distribution bootstrap action finished"
