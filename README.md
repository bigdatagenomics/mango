# mango



Mango is a scalable genomics visualization tool built on top of the [ADAM](https://github.com/bigdatagenomics/adam) genomics processing engine. Apache 2 licensed.

[![Coverage Status](https://coveralls.io/repos/github/bigdatagenomics/mango/badge.svg)](https://coveralls.io/github/bigdatagenomics/mango)


Mango consists of a **notebook** and **browser** form factor, allowing users to visualize reads, variants, and features in a GUI or programmable interface.
The Mango tools use [Pileup.js](https://github.com/hammerlab/pileup.js) for interactive scrolling at genomic loci.

# Documentation

Mango documentation is hosted at [readthedocs](http://bdg-mango.readthedocs.io/en/latest/). Documentation for Mango includes instructions for
running the Mango notebook and the Mango browser locally, remotely, and in the cloud (Amazon EMR and Google Cloud Engine). Documentation
also provides Python API documentation for the Mango notebook.

# Installation from distribution


The Mango tools are published to Maven central. Corresponding python modules are published to Pypi. [Readthedocs](https://bdg-mango.readthedocs.io/en/latest/installation/distribution.html) provides instructions on how to install the Mango tools from the most recent distribution.


# Installation from Source

Instructions for running the Mango tools from source can be found at [readthedocs](https://bdg-mango.readthedocs.io/en/latest/installation/source.html#building-mango-from-source).

You will need to have [Maven](http://maven.apache.org/) installed in order to build mango.
Mango browser also requires [npm > 3.10.10](https://www.npmjs.com/get-npm).

> **Note:** The default configuration is for Hadoop 2.7.3. If building against a different
> version of Hadoop, please edit the build configuration in the `<properties>` section of
> the `pom.xml` file.

```
$ git clone https://github.com/bigdatagenomics/mango.git
$ cd mango
$ mvn clean package -DskipTests
```

If using the Mango notebook, we recommend setting up a [virtual environment](https://virtualenv.pypa.io/en/stable/userguide/#usage) to install required python modules.

To configure your python environment for the Mango notebook, refer to instructions for [Building for Python](https://bdg-mango.readthedocs.io/en/latest/installation/source.html#building-for-python).



# The Mango notebook

The Mango notebook is a set of Python APIs and Jupyter widgets for loading and manipulating genomic data in a Jupyter notebook environment. The Mango APIs can be used for
loading and visualizing raw features, variants and alignments, as well as calculating and viewing aggregate information.

![Mango Python Widgets](https://raw.github.com/bigdatagenomics/mango/master/images/mangoPython_reads.png)

![Mango Python Aggregate](https://raw.github.com/bigdatagenomics/mango/master/images/mangoPython_coverage.png)

## Running the Mango notebook locally

Mango can also be run through the notebook form.

```
./bin/mango-notebook
```

In the jupyter UI, navigate to example-files/notebooks to view example notebooks.


## The Mango Python APIs

Stated above, the Mango notebook provides python APIs for loading and visualizing genomic data in a Jupyter notebook environment. API information for the Mango notebook can be found
in [readthedocs](https://bdg-mango.readthedocs.io/en/latest/mangoPython/api.html).


## Running the Mango notebook on Amazon AWS

The Mango Documentation includes instructions for running the Mango notebook on [Amazon AWS](https://bdg-mango.readthedocs.io/en/latest/cloud/emr.html#running-mango-notebook-on-emr-with-docker).
These instructions can be leveraged to visualize genomic datasets staged in Amazon S3, and include example notebooks for exploring the 1000 Genomes Dataset on Amazon S3. If using the Mango Docker instances,
 an example notebook can be found [on the GitHub](https://github.com/bigdatagenomics/mango/blob/master/example-files/notebooks/aws-1000genomes.ipynb).

## Running the Mango notebook on a Google Dataproc Cluster


The Mango Documentation includes instructions for running the Mango notebook on [Google Cloud Engine](https://bdg-mango.readthedocs.io/en/latest/cloud/google-cloud.html#running-mango-notebook-on-a-dataproc-cluster).
These instructions can be leveraged to visualize genomic datasets staged publically. If using the Mango Docker instances,
an example notebook can be found [on the GitHub](https://github.com/bigdatagenomics/mango/blob/master/example-files/notebooks/gce-1000genomes.ipynb).


# The Mango Browser

The Mango browser provides a GUI to visualize genomic data stored remotely or in the cloud.

The Mango browser uses [IntervalRDDs](https://github.com/bigdatagenomics/utils/tree/master/utils-intervalrdd) to perform fast indexed lookups on interval-keyed data.

![Homepage](https://raw.github.com/bigdatagenomics/mango/master/images/overall.png)

![Reads](https://raw.github.com/bigdatagenomics/mango/master/images/browser.png)


## Running the Mango browser locally

mango is packaged via [appassembler](http://mojo.codehaus.org/appassembler/appassembler-maven-plugin/) and includes all necessary dependencies.

The Mango repository includes example scripts to run the Mango browser on small example files.

To run the example files in the Mango browser, run:

```
 ./example-files/browser-scripts/run-example.sh
```

to see a demonstration of chromosome 17, region 7500000-7515000.


 Now view the mango genomics browser at `localhost:8080` or the port specified:
```
View the visualization at: 8080
Quit at: /quit
```

For help launching the script, run `bin/mango-submit -h`
````
$ bin/mango-submit -h
Using SPARK_SUBMIT=/Applications/spark-1.6.1-bin-hadoop2.4/bin/spark-submit
 reference                                                       : The reference file to view, required
 -cacheSize N                                                    : Bp to cache on driver.
 -coverage VAL                                                   : A list of coverage files to view, separated by commas (,)
 -discover                                                       : This turns on discovery mode on start up.
 -features VAL                                                   : The feature files to view, separated by commas (,)
 -genes VAL                                                      : Gene URL.
 -h (-help, --help, -?)                                          : Print help
 -parquet_block_size N                                           : Parquet block size (default = 128mb)
 -parquet_compression_codec [UNCOMPRESSED | SNAPPY | GZIP | LZO] : Parquet compression codec
 -parquet_disable_dictionary                                     : Disable dictionary encoding
 -parquet_logging_level VAL                                      : Parquet logging level (default = severe)
 -parquet_page_size N                                            : Parquet page size (default = 1mb)
 -port N                                                         : The port to bind to for visualization. The default is 8080.
 -prefetchSize N                                                 : Bp to prefetch in executors.
 -preload VAL                                                    : Chromosomes to prefetch, separated by commas (,).
 -print_metrics                                                  : Print metrics to the log on completion
 -reads VAL                                                      : A list of reads files to view, separated by commas (,)
 -show_genotypes                                                 : Shows genotypes if available in variant files.
 -test                                                           : For debugging purposes.
 -variants VAL                                                   : A list of variants files to view, separated by commas (,). Vcf files require a
                                                                   corresponding tbi index.
 ````

## Running the Mango browser on Amazon AWS

The Mango Documentation includes instructions for running the Mango browser on [Amazon AWS](https://bdg-mango.readthedocs.io/en/latest/cloud/emr.html#running-the-mango-browser-on-emr-with-docker).
These instructions can be leveraged to visualize genomic datasets staged in Amazon S3.



# The Mango Widgets

[The Mango Widgets](https://bdg-mango.readthedocs.io/en/latest/jupyterWidgets/usage.html) are standalone Jupyter widgets for interacting with [Pileup.js](https://github.com/hammerlab/pileup.js) in a Jupyter notebook environment. API documentation for the Mango widgets can be found in [readthedocs](https://bdg-mango.readthedocs.io/en/latest/jupyterWidgets/api.html).
