Running Mango from Docker
=========================

Running Mango from Docker requires `Docker to be installed <https://docs.docker.com/>`__.

Mango is available on Docker and is available at `quay.io <https://quay.io/repository/bigdatagenomics/mango>`__.

To pull the Mango docker container, run:

.. code:: bash

   docker pull quay.io/bigdatagenomics/mango:latest


Running Mango Browser on Docker
-------------------------------

To run Mango browser example files on Linux in Docker run:

To run Mango browser on local data, you must first mount these files with the ``Docker -v`` flag. For example, if you have local files stored at ``<example-file-path>``:

.. code:: bash

    LOCAL_EXAMPLE_FILES=<path_to_example_files>
    DOCKER_EXAMPLE_FILES=<path_in_docker_container>

    docker run -it -p 8080:8080 \
        -v $LOCAL_EXAMPLE_FILES:$DOCKER_EXAMPLE_FILES \
        --entrypoint=mango-submit \
        quay.io/bigdatagenomics/mango:latest \
        -- $DOCKER_EXAMPLE_FILES/<genome_build_filepath> \
        -reads $DOCKER_EXAMPLE_FILES/<alignment_filepaths> \
        -variants $DOCKER_EXAMPLE_FILES/<variant_filepaths>

To create a genome build (<genome_build_filepath>), see `Building a Genome <#creating-a-mango-genome-using-docker>`__.


Running Mango Notebook on Docker
--------------------------------

To run Mango notebook on Linux in Docker run:

.. code:: bash

    docker run --net=host -it -p 8888:8888 \
    	--entrypoint=mango-notebook \
    	quay.io/bigdatagenomics/mango:latest \
    	-- --ip=0.0.0.0 --allow-root

**Note:** To run the Mango notebook on OS X, remove ``--net=host``.

To view a number of ipython notebook examples, see `our github <https://github.com/bigdatagenomics/mango/tree/master/example-files/notebooks>`__.



Creating a Mango genome using Docker
------------------------------------

To run create a mango genome on Linux in Docker run:

.. code:: bash

    LOCAL_LOCATION=<host_src>
    DOCKER_LOCATION=<docker_src>
    GENOME_BUILD=<genome_name> # i.e. hg19, mm10, etc.

    docker run --net=host -it -p 8888:8888 \
        -v $LOCAL_LOCATION:$DOCKER_LOCATION \
    	--entrypoint=make_genome \
    	quay.io/bigdatagenomics/mango:latest $GENOME_BUILD $DOCKER_LOCATION

The genome file will be saved to ``<host_src>``.

**Note:** To run the make_genome on OS X, remove ``--net=host``.


