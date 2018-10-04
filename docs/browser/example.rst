Mango Browser Examples
======================

See `file support <file:///Users/akmorrow/ADAM/mango/docs/_build/html/files/file-support.html>`__ for files that can be viewed in the Mango Browser.


Running Mango Browser Locally
-----------------------------

There are example files for running Mango browser on region chr17:7500000-7515000
of a single sample. Once Mango is built, you can run the following command
to view Mango browser.

.. code:: bash

    ./example-files/browser-scripts/run-example

One Mango browser is set up, navigate to `localhost:8080 <localhost:8080 />`__
to view the browser.

Running Mango Browser with Parameters
-------------------------------------

To run Mango browser with user specified parameters, run

.. code:: bash

    ./bin/mango-submit <Spark-parameters> -- <Mango-parameters>

``<Spark-parameters>`` include `Apache Spark specific configuration settings <https://spark.apache.org/docs/latest/configuration.html>`__.

``<Mango-parameters>`` are shown in the output of ``./bin/mango-submit``.

Note that a reference in twobit or fasta form is required.

Running example files on a cluster with HDFS
--------------------------------------------

To run the example files on a cluster with hdfs, first first put example-files on hdfs.

.. code:: bash

    hdfs dfs -put example-files


Then, run mango-submit:

.. code:: bash

    ./bin/mango-submit ./example-files/hg19.17.2bit \
           -genes http://www.biodalliance.org/datasets/ensGene.bb \
           -reads hdfs:///<path_to_examples>/example-files/chr17.7500000-7515000.sam.adam \
           -variants hdfs:///<path_to_examples>/example-files/ALL.chr17.7500000-7515000.phase3_shapeit2_mvncall_integrated_v5a.20130502.genotypes.vcf \
