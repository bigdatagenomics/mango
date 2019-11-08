FROM continuumio/miniconda3:4.6.14

RUN conda config -f --add channels defaults
RUN conda config -f --add channels bioconda
RUN conda config -f --add channels conda-forge

RUN conda install --yes mango=0.0.5=py_3

ENV SPARK_HOME /opt/conda/lib/python3.6/site-packages/pyspark

RUN jupyter nbextension enable --py widgetsnbextension
RUN jupyter nbextension install --overwrite --py --symlink --user bdgenomics.mango.pileup
RUN jupyter nbextension enable bdgenomics.mango.pileup --user --py
