#mango

[![Join the chat at https://gitter.im/bigdatagenomics/mango](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/bigdatagenomics/mango?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
A set of genomic visualization tools built on top of the [ADAM](https://github.com/bigdatagenomics/adam) genomics processing engine. Apache 2 licensed.

mango visualizes reads, variants, and features using [D3](http://d3js.org/).

mango uses [IntervalRDDs](https://github.com/akmorrow13/spark-intervalrdd) to perform fast indexed lookups on interval-keyed data.

![Overall View](https://raw.github.com/bigdatagenomics/mango/master/images/overall.png)

![Long Ranged Views](https://raw.github.com/bigdatagenomics/mango/master/images/long_ranged.png)
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

Run the mango-submit script as follows:
```
bin/mango-submit REFERENCE_FILE.fa -read_files READS_FILE1.adam,READS_FILE2.adam -var_file VARIANTS_FILE.vcf.adam -feat_file FEATURES_FILE.bed
```
Note that the script above visualizes the reads data for two samples at the same time.
For help launching the script, run `bin/mango-submit -h`
````
$ bin/mango-submit -h
Using SPARK_SUBMIT=/Applications/spark-1.4.1-bin-hadoop2.4/bin/spark-submit
reference                                                       : The reference file to view, required
-feat_file VAL                                                  : The feature file to view
-h (-help, --help, -?)                                          : Print help
-parquet_block_size N                                           : Parquet block size (default = 128mb)
-parquet_compression_codec [UNCOMPRESSED | SNAPPY | GZIP | LZO] : Parquet compression codec
-parquet_disable_dictionary                                     : Disable dictionary encoding
-parquet_logging_level VAL                                      : Parquet logging level (default = severe)
-parquet_page_size N                                            : Parquet page size (default = 1mb)
-port N                                                         : The port to bind to for visualization. The default is 8080.
-print_metrics                                                  : Print metrics to the log on completion
-read_files VAL                                                 : A list of reads files to view, separated by commas (,)
-repartition N                                                  : The number of partitions
-var_file VAL                                                   : The variants file to view
 ````
 Now view the mango genomics browser at `localhost:8080` or the port specified:
```
View the visualization at: 8080
Variant Frequency visualization at: /variants
Quit at: /quit

```
