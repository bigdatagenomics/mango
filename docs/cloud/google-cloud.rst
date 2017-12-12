Running Mango on Google Cloud
=============================

`Cloud Dataproc <https://cloud.google.com/dataproc/>`__ sets up the basic environment with HDFS and Spark, providing a simple environment to run Mango.

Commands in this section will require users to create an account on `Google Cloud <https://cloud.google.com/>`__ and  install the `gcloud cli <https://cloud.google.com/sdk/gcloud/>`__

Creating a Dataproc Cluster
---------------------------
Download the necessary initialization scripts:

.. code:: bash

    wget https://gist.githubusercontent.com/Georgehe4/6bb1c142a9f68f30f38d80cd9407120a/raw/9b903e3b8746ee8f25911fe98925b53e9777002f/mango_install.sh

Initialize a Google Cloud Storage Bucket

.. code:: bash

    gsutil mb gs://mango-initialization-bucket/

Copy the installation scripts to be used by cloud dataproc

.. code:: bash

    gsutil cp mango_install.sh gs://mango-initialization-bucket



Create the Cloud Dataproc Cluster (modify the fields as appropriate) with the loaded installation script

.. code:: bash

    gcloud dataproc clusters create <cluster-name> \
        --project <project_id> \
        --bucket <bucket_name> \
        --metadata MINICONDA_VARIANT=2 \
        --master-machine-type=n1-standard-2 \
        --worker-machine-type=n1-standard-1 \
        --master-boot-disk-size=100GB \
        --worker-boot-disk-size=50GB \
        --initialization-actions \
            gs://mango-initialization-bucket/mango_install.sh


Once the above steps are completed, simply ssh into the master node to run Mango.

.. code:: bash
    
    gcloud compute ssh <cluster-name>-m


