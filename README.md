#mango

A scalable genome browser built on top of the [ADAM](https://github.com/bigdatagenomics/adam) genomics processing engine. Apache 2 licensed.

[![Coverage Status](https://coveralls.io/repos/github/bigdatagenomics/mango/badge.svg)](https://coveralls.io/github/bigdatagenomics/mango)

mango visualizes reads, variants, and features using [Pileup.js](https://github.com/hammerlab/pileup.js).

mango uses [IntervalRDDs](https://github.com/bigdatagenomics/utils/tree/master/utils-intervalrdd) to perform fast indexed lookups on interval-keyed data.

![Homepage](https://raw.github.com/bigdatagenomics/mango/master/images/overall.png)

![Reads](https://raw.github.com/bigdatagenomics/mango/master/images/browser.png)
# Getting Started

## Installation
You will need to have [Maven](http://maven.apache.org/) installed in order to build mango.

> **Note:** The default configuration is for Hadoop 2.6.0. If building against a different
> version of Hadoop, please edit the build configuration in the `<properties>` section of
> the `pom.xml` file.

```
$ git clone https://github.com/bigdatagenomics/mango.git
$ cd mango
$ mvn clean package -DskipTests
```
## Running mango
mango is packaged via [appassembler](http://mojo.codehaus.org/appassembler/appassembler-maven-plugin/) and includes all necessary dependencies.

Running an example script:
```
From the main folder of mango, run ./example-files/run-example.sh to see a demonstration of chromosome 17, region 7500000-7515000.
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
 Now view the mango genomics browser at `localhost:8080` or the port specified:
```
View the visualization at: 8080
Quit at: /quit
```
Note that for logging, you must use the /quit url for the log to be produced.
