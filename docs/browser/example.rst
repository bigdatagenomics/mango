Mango Browser Examples
======================

Mango browser is an HTML based genome browser that runs on local, remote, and cloud staged files.
The Mango Browser utilizes `Apache Spark <https://spark.apache.org/>`__ and `scalatra <http://scalatra.org/>`__.

See `file support <../files/file-support.html>`__ for file types that are supported in the Mango Browser.


Mango Browser Options
---------------------

The Mango browser uses the `mango-submit <https://github.com/bigdatagenomics/mango/blob/master/bin/mango-submit>`__ script to start an Apache Spark session and launch the Mango Browser.
The mango-submit script can be found in the `Mango Distribution <../installation/distribution.html>`__ or in the `Mango Github repository <https://github.com/bigdatagenomics/mango>`__.

To see options that can be run with the Mango Submit script, run:

.. code:: bash

    ./mango-submit -h

You will see a list of options:

.. code:: bash

   genome                                                          : Path to compressed .genome file. To build a new genome file, run bin/make_genome.
   -cacheSize N                                                    : Bp to cache on driver.
   -coverage VAL                                                   : A list of coverage files to view, separated by commas (,)
   -debugFrontend                                                  : For debugging purposes. Sets front end in source code to avoid recompilation.
   -discover                                                       : This turns on discovery mode on start up.
   -features VAL                                                   : The feature files to view, separated by commas (,)
   -h (-help, --help, -?)                                          : Print help
   -parquetIsBinned                                                : This turns on binned parquet pre-fetch warmup step
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
   -repartition                                                    : Repartitions data to default number of partitions.
   -test                                                           : For debugging purposes.
   -variants VAL                                                   : A list of variants files to view, separated by commas (,). Vcf files require a
                                                                     corresponding tbi index.

Note that a genome file is always required when running the Mango Browser.
`See how to build a genome <./genomes.html>`__.

Running Mango Browser Locally
-----------------------------

The `Mango Github repository <https://github.com/bigdatagenomics/mango>`__ contains example scripts and data files for running Mango browser on region chr17:7500000-7515000
of a single aligment sample. Once `Mango is built <../installation/source.html>`__, you can run the following command from the root mango directory to view Mango browser:

.. code:: bash

    ./example-files/browser-scripts/run-example


This file contains the following command:

.. code:: bash

  bin/mango-submit ./example-files/hg19.genome \
    -reads ./example-files/chr17.7500000-7515000.sam \
    -variants ./example-files/ALL.chr17.7500000-7515000.phase3_shapeit2_mvncall_integrated_v5a.20130502.genotypes.vcf

This file specifies the required genome reference file:

.. code:: bash

  ./example-files/hg19.genome

An optional alignment file:

.. code:: bash

  -reads ./example-files/chr17.7500000-7515000.sam

An optional variant file:

.. code:: bash

  -variants ./example-files/ALL.chr17.7500000-7515000.phase3_shapeit2_mvncall_integrated_v5a.20130502.genotypes.vcf


Once the example script is running, navigate to localhost:8080 to view the Mango browser. Navigate to ``chr17:7500000-7515000``
to view data.

Running Mango Browser with Parameters
-------------------------------------

Mango can accept `Apache Spark <https://spark.apache.org/docs/latest/configuration.html>`__ parameters, as well as Mango parameters shown above.

To run Mango browser with user specified Apache Spark parameters, run

.. code:: bash

    ./bin/mango-submit <Spark-parameters> -- <Mango-parameters>

``<Spark-parameters>`` include `Apache Spark specific configuration settings <https://spark.apache.org/docs/latest/configuration.html>`__.

``<Mango-parameters>`` are shown in the output of ``./bin/mango-submit``.

Note that a `genome file <./genomes.html>`__ is required to run the Mango browser.

Running example files on a cluster with HDFS
--------------------------------------------

The Mango browser can run files that are staged on Hadoop Distributed File System (`HDFS <https://hadoop.apache.org/docs/r1.2.1/hdfs_design.html#Introduction>`__).

To run the example files on a cluster with hdfs, first put example-files on hdfs:

.. code:: bash

    hdfs dfs -put example-files


Then, run mango-submit:

.. code:: bash

    ./bin/mango-submit ./example-files/hg19.genome \
           -genes http://www.biodalliance.org/datasets/ensGene.bb \
           -reads hdfs:///<path_to_examples>/example-files/chr17.7500000-7515000.sam \
           -variants hdfs:///<path_to_examples>/example-files/ALL.chr17.7500000-7515000.phase3_shapeit2_mvncall_integrated_v5a.20130502.genotypes.vcf \



Running on Apache YARN
----------------------

YARN is a resource management system for clusters.
The Mango browser can run on `YARN <https://hadoop.apache.org/docs/current/hadoop-yarn/hadoop-yarn-site/YARN.html>`__ clusters, and requires package ``org.apache.parquet:parquet-avro:1.8.3``.
To run the Mango browser on YARN, include parquet-avro as a package on start-up:

.. code:: bash

    ./bin/mango-submit --packages org.apache.parquet:parquet-avro:1.8.3 \
            --master yarn-client \
            <mango-parameters>
