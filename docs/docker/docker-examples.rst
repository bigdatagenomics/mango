Running Mango from Docker
=========================

Running Mango from Docker requires `Docker to be installed <https://docs.docker.com/>`__.

Mango is available on Docker through `biocontainers <https://quay.io/repository/biocontainers/mango>`__ and
has open code available at `cgl-docker-lib <https://github.com/BD2KGenomics/cgl-docker-lib>`__.

Running Mango Browser on Docker
-------------------------------

To run Mango browser example files on Linux in Docker run:

# TODO 10/28/2019 why does :latest not work?

To run Mango browser on local data, you must first mount these files with the ``Docker -v`` flag. For example, if you have local files stored at ``<example-file-path>``:

.. code:: bash

    docker run -it -p 8080:8080 \
        -v <example-file-path>:<docker-container-path> \
        --entrypoint=mango-submit \
        quay.io/biocontainers/mango:0.0.5--py_1 \
        -- <docker-container-path>/hg19.genome \
        -reads <docker-container-path>/chr17.7500000-7515000.sam.adam \
        -variants <docker-container-path>/ALL.chr17.7500000-7515000.phase3_shapeit2_mvncall_integrated_v5a.20130502.genotypes.vcf

To create a reference, see `Building a Genome <../browser/genomes.html>`__.


Running Mango Notebook on Docker
--------------------------------

To run Mango notebook on Linux in Docker run:

.. code:: bash

    docker run --net=host -it -p 8888:8888 \
        -e SPARK_HOME=/usr/local/lib/python3.6/site-packages/pyspark \
    	--entrypoint=mango-notebook \
    	quay.io/biocontainers/mango:0.0.5--py_1 \
    	-- --ip=0.0.0.0 --allow-root

**Note:** You must set ``SPARK_HOME`` to run the mango notebook.

**Note:** To run the Mango notebook on OS X, remove ``--net=host``.

To view a number of ipython notebook examples, see `our github <https://github.com/bigdatagenomics/mango/tree/master/example-files/notebooks>`__.



Creating a Mango genome using Docker
------------------------------------

To run create a mango genome on Linux in Docker run:

.. code:: bash

    docker run --net=host -it -p 8888:8888 \
        -v <host-src>:<container-dest> \
    	--entrypoint=make_genome \
    	quay.io/biocontainers/mango:0.0.5--py_1 hg19 <container-dest>

The genome file will be saved to ``<host-src>``.

**Note:** To run the make_genome on OS X, remove ``--net=host``.


# TODO test widgets not working, SPARK_HOME needs to be explicitly set


