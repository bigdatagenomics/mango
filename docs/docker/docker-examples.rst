Running Mango from Docker
=========================

Running Mango from Docker requires `Docker to be installed <https://docs.docker.com/>`__.

Mango is available on Docker through `UCSC cgl <https://quay.io/organization/ucsc_cgl/>`__ and
has open code available at `cgl-docker-lib <https://github.com/BD2KGenomics/cgl-docker-lib>`__.

Running Mango Browser on Docker
-------------------------------

To run Mango browser example files on Linux in Docker run:

.. code:: bash

    docker run -it
        --net=host \
        -p 8080:8080 \
        quay.io/ucsc_cgl/mango:latest \
        -- /opt/cgl-docker-lib/mango/example-files/hg19.genome \
        -reads /opt/cgl-docker-lib/mango/example-files/chr17.7500000-7515000.sam.adam \
        -variants /opt/cgl-docker-lib/mango/example-files/ALL.chr17.7500000-7515000.phase3_shapeit2_mvncall_integrated_v5a.20130502.genotypes.vcf


**Note:** To run the Mango browser on OS X, remove ``--net=host``.

To run Mango browser on local data, you must first mount these files with the ``Docker -v`` flag. For example, if you have local files stored at ``<example-file-path>``:

.. code:: bash

    docker run -it -p 8080:8080 \
        -v <example-file-path>:<docker-container-path>
        quay.io/ucsc_cgl/mango:latest \
        -- <docker-container-path>/hg19.genome \
        -reads <docker-container-path>/chr17.7500000-7515000.sam.adam \
        -variants <docker-container-path>/ALL.chr17.7500000-7515000.phase3_shapeit2_mvncall_integrated_v5a.20130502.genotypes.vcf

To create a reference, see `Building a Genome <../browser/genomes.html>`__.


Running Mango Notebook on Docker
--------------------------------

To run Mango notebook on Linux in Docker run:

.. code:: bash

    docker run --net=host -it -p 8888:8888 \
    	--entrypoint=/opt/cgl-docker-lib/mango/bin/mango-notebook \
    	quay.io/ucsc_cgl/mango:latest \
    	-- --ip=0.0.0.0 --allow-root


**Note:** To run the Mango notebook on OS X, remove ``--net=host``.

To view a number of ipython notebook examples, navigate to: ``/opt/cgl-docker-lib/mango/example-files``.
