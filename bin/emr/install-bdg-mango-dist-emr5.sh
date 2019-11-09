#!/bin/bash
set -x -e

# AWS EMR bootstrap script for
# for installing Mango (https://github.com/bigdatagenomics/mango) on AWS EMR 5+
#
# 2019-11-08  - Alyssa Morrow akmorrow@berkeley.edu
#

# check for master node
IS_MASTER=false
if grep isMaster /mnt/var/lib/info/instance.json | grep true;
then
  IS_MASTER=true

fi

export SPARK_HOME=/usr/lib/spark

# required to read files from s3a. Hack for pyspark requirements.
mkdir -p ${HOME}/.ivy2/jars
wget -O ${HOME}/.ivy2/jars/jsr203-s3a-0.0.2.jar http://central.maven.org/maven2/net/fnothaft/jsr203-s3a/0.0.2/jsr203-s3a-0.0.2.jar

# Install conda
# TODO: conda 4.7 takes forever trying to resolve packages.
wget https://repo.anaconda.com/miniconda/Miniconda3-4.6.14-Linux-x86_64.sh -O ${HOME}/miniconda.sh \
    && /bin/bash ~/miniconda.sh -b -p $HOME/conda

echo 'export PATH=$HOME/conda/bin:$PATH' >> $HOME/.bashrc && source $HOME/.bashrc

conda clean -tipsy
conda clean -afy

conda config -f --add channels defaults
conda config -f --add channels bioconda
conda config -f --add channels conda-forge

# install mango through conda env
conda install mango

# cleanup
rm ~/miniconda.sh

# download 1000 genomes example notebook
mkdir -p ${HOME}/mango/notebooks
wget -O ${HOME}/mango/notebooks/aws-1000genomes.ipynb https://raw.githubusercontent.com/bigdatagenomics/mango/master/example-files/notebooks/aws-1000genomes.ipynb


# download scripts for EMR browser and notebook
mkdir -p ${HOME}/mango/scripts
wget -O ${HOME}/mango/scripts/run-notebook-emr.sh https://raw.githubusercontent.com/bigdatagenomics/mango/master/bin/emr/run-notebook-emr.sh
wget -O ${HOME}/mango/scripts/run-browser-emr.sh https://raw.githubusercontent.com/bigdatagenomics/mango/master/bin/emr/run-browser-emr.sh
chmod u+x ${HOME}/mango/scripts/*

echo "Mango Distribution bootstrap action finished"
