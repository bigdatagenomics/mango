Running Mango on Google Cloud
=============================

`Cloud Dataproc <https://cloud.google.com/dataproc/>`__ sets up the basic environment with HDFS and Spark, providing a simple environment to run Mango.

Commands in this section will require users to create an account on `Google Cloud <https://cloud.google.com/>`__ and  install the `gcloud cli <https://cloud.google.com/sdk/gcloud/>`__

Creating a Dataproc Cluster
---------------------------
Download the necessary initialization scripts:

.. code:: bash

    wget https://raw.githubusercontent.com/bigdatagenomics/mango/master/bin/gce/install.sh

Initialize a Google Cloud Storage Bucket

.. code:: bash

    gsutil mb gs://mango-initialization-bucket/

Copy the installation scripts to be used by cloud dataproc

.. code:: bash

    gsutil cp install.sh gs://mango-initialization-bucket


Create the Cloud Dataproc Cluster (modify the fields as appropriate) with the below installation script

.. code:: bash

    gcloud dataproc clusters create <cluster-name> \
        --project <project_id> \
        --bucket <optional_bucket_name> \
        --metadata MINICONDA_VARIANT=2 \
        --master-machine-type=n1-standard-1 \
        --worker-machine-type=n1-standard-1 \
        --master-boot-disk-size=50GB \
        --worker-boot-disk-size=10GB \
        --initialization-actions \
            gs://mango-initialization-bucket/install.sh


After the above steps are completed, ssh into the master node.

.. code:: bash

    gcloud compute ssh <cluster-name>-m

Running Mango Notebook on a Dataproc Cluster
--------------------------------------------

Before mango can run, it is recommended to stage datasets into HDFS if you are trying to view specific files. The created container will share the same hadoop file system with the root master user.

.. code:: bash

    hdfs dfs -put /<local-machine-path> /<hdfs-path>

An example docker startup script is available in the Mango gce `scripts directory <https://github.com/bigdatagenomics/mango/blob/master/bin/gce>`__ for running `mango notebook <https://github.com/bigdatagenomics/mango/blob/master/bin/gce/run-notebook.sh>`__, or for running `mango browser <https://github.com/bigdatagenomics/mango/blob/master/bin/gce/run-browser.sh>`__ [root permissions may be necessary for docker].

.. code:: bash

    wget 'https://github.com/bigdatagenomics/mango/blob/master/bin/gce/run-notebook.sh'

    bash run-notebook.sh --entrypoint=/opt/cgl-docker-lib/mango/bin/mango-notebook

Once the notebook is running, connect to Mango by setting up a tunnel to your local computer via the exposed port in the master node:

.. code:: bash

    gcloud compute ssh <cluster-name>-m -- -N -L localhost:<local-port>:localhost:8888

You can navigate to notebook through your local browser by pointing it towards http://localhost:<local-port>/. Once in the browser notebook environment, navigate to /opt/cgl-docker-lib/mango/example-files/ to try out the example files after configuring the file paths to read relative to the home directory in HDFS. Public datasets can be accessed by referencing google cloud storage at gs://genomics-public-data/.

More information about available public datasets on Google cloud can be found `online <https://cloud.google.com/genomics/v1/public-data>`__

More information on using the dataproc cluster's Spark interface is available through `Google Cloud documentation <https://cloud.google.com/dataproc/docs/concepts/accessing/cluster-web-interfaces>`__
