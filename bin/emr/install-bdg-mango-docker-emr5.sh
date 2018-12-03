#!/bin/bash
set -x -e

# AWS EMR bootstrap script for
# for installing Mango (https://github.com/bigdatagenomics/mango) on AWS EMR 5+
#
# 2016-3-12  - Alyssa Morrow akmorrow@berkeley.edu, initial version
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
  wget -O /home/hadoop/mango-scripts/run-browser.sh https://raw.githubusercontent.com/bigdatagenomics/mango/master/bin/emr/run-browser.sh
  chmod u+x /home/hadoop/mango-scripts/run-browser.sh

  # Download the file for Mango notebook
  wget -O /home/hadoop/mango-scripts/run-notebook.sh https://raw.githubusercontent.com/bigdatagenomics/mango/master/bin/emr/run-notebook.sh
  chmod u+x /home/hadoop/mango-scripts/run-notebook.sh

fi

# update python to 3.5
sudo yum -y install python35

echo "Mango Docker bootstrap action finished"
