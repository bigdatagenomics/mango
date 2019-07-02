#!/bin/bash
set -x -e

# AWS EMR bootstrap script for
# for installing Mango (https://github.com/bigdatagenomics/mango) on AWS EMR 5+
#
# 2019-7-01  - Alyssa Morrow akmorrow@berkeley.edu, Mango 0.0.3
#

# check for master node
IS_MASTER=false
if grep isMaster /mnt/var/lib/info/instance.json | grep true;
then
  IS_MASTER=true
fi

sudo yum install -y docker
sudo service docker start
sudo usermod -a -G docker hadoop

# pull image
sudo docker pull quay.io/ucsc_cgl/mango:latest

if [ "$IS_MASTER" = true ]; then
  mkdir -p /home/hadoop/mango-scripts

  # Download the EMR script for Mango Browser
  wget -O /home/hadoop/mango-scripts/run-browser-docker.sh https://raw.githubusercontent.com/bigdatagenomics/mango/master/bin/emr/run-browser-docker.sh
  chmod u+x /home/hadoop/mango-scripts/run-browser-docker.sh

  # Download the file for Mango notebook
  wget -O /home/hadoop/mango-scripts/run-notebook-docker.sh https://raw.githubusercontent.com/bigdatagenomics/mango/master/bin/emr/run-notebook-docker.sh
  chmod u+x /home/hadoop/mango-scripts/run-notebook-docker.sh

  # Download the file for creating Mango genomes
  wget -O /home/hadoop/mango-scripts/make-genome-docker.sh https://raw.githubusercontent.com/bigdatagenomics/mango/master/bin/emr/make-genome-docker.sh
  chmod u+x /home/hadoop/mango-scripts/make-genome-docker.sh

fi

# update python to 3.5
sudo yum -y install python35

echo "Mango Docker bootstrap action finished"
