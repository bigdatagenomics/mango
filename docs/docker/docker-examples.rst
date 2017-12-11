Running Mango from Docker
=========================

Running Mango from Docker requires `Docker to be installed <https://docs.docker.com/>`__.

Mango is available on Docker through `UCSC cgl <https://quay.io/organization/ucsc_cgl/>`__ and
has open code available at `cgl-docker-lib <https://github.com/BD2KGenomics/cgl-docker-lib>`__.

Running Mango Browser on Docker
-------------------------------

To run Mango browser example files on Linux in docker run:

.. code:: bash

    docker run -p 8080:8080 \
    	quay.io/ucsc_cgl/mango:latest \
       -- /opt/cgl-docker-lib/mango/example-files/hg19.17.2bit \
			-reads /opt/cgl-docker-lib/mango/example-files/chr17.7500000-7515000.sam.adam \
			-variants /opt/cgl-docker-lib/mango/example-files/ALL.chr17.7500000-7515000.phase3_shapeit2_mvncall_integrated_v5a.20130502.genotypes.vcf \
			-show_genotypes \
			-discover


Note: To run Mango browser on OS X, include the ``-p 8080:8080`` Docker parameter in place of ``--net=host``.

To run Mango browser on other local data, you must first mount these files with the ``Docker -v`` flag.

.. code:: bash

    docker run -p 8080:8080 \
    	-v <example-file-path>:<docker-container-path>
    	quay.io/ucsc_cgl/mango:latest \
       -- <docker-container-path>/hg19.17.2bit \
			-reads <docker-container-path>/chr17.7500000-7515000.sam.adam \
			-variants <docker-container-path>/ALL.chr17.7500000-7515000.phase3_shapeit2_mvncall_integrated_v5a.20130502.genotypes.vcf \
			-show_genotypes \
			-discover


Running Mango Notebook on Docker
--------------------------------

To run Mango notebook on Linux in docker run:

.. code:: bash

    docker run --net=host \
    	--entrypoint=/opt/cgl-docker-lib/mango/bin/mango-notebook \
    	quay.io/ucsc_cgl/mango:latest \
    	-- --ip=0.0.0.0 --allow-root


Note: To run Mango notebook on OS X, include the ``-p 8080:8080`` Docker parameter in place of ``--net=host``.
