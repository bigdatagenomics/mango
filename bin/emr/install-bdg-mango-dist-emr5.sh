#!/bin/bash
set -x -e

# AWS EMR bootstrap script for
# for installing Mango (https://github.com/bigdatagenomics/mango) on AWS EMR 5+
#
# 2018-10-29  - Alyssa Morrow akmorrow@berkeley.edu
#

# parameter that specifies version
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

# Install conda
wget https://repo.continuum.io/miniconda/Miniconda3-4.2.12-Linux-x86_64.sh -O ${OUTPUT_DIR}/miniconda.sh \
    && /bin/bash ~/miniconda.sh -b -p $HOME/conda

echo '\nexport PATH=$HOME/conda/bin:$PATH' >> $HOME/.bashrc && source $HOME/.bashrc

conda config --set always_yes yes --set changeps1 no

conda install conda=4.2.13

conda config -f --add channels defaults
conda config -f --add channels bioconda
conda config -f --add channels conda-forge

# install mango through conda
conda instal mango

# cleanup
rm ~/miniconda.sh
echo bootstrap_conda.sh completed. PATH now: $PATH

# download 1000 genomes example notebook
mkdir -p ${OUTPUT_DIR}/mango/notebooks
wget -O ${OUTPUT_DIR}/mango/notebooks/aws-1000genomes.ipynb https://raw.githubusercontent.com/bigdatagenomics/mango/master/example-files/notebooks/aws-1000genomes.ipynb

echo "Mango Distribution bootstrap action finished"
