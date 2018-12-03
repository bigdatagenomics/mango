Running Mango from Amazon EMR
=============================

`Amazon EMR <https://aws.amazon.com/emr/>`__ provides a pre-built Hadoop and Spark distribution that allows users to easily deploy and test Mango.

Before you Start
----------------

`Set up an EC2 key pair <https://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-key-pairs.html#having-ec2-create-your-key-pair>`__.

You will also need to install the AWS cli to ssh into your machines:

1. `Install the AWS cli <https://docs.aws.amazon.com/cli/latest/userguide/installing.html>`__
2. `Configure the AWS cli <https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-getting-started.html>`__


Running Mango through Docker
============================

This section explains how to run the Mango browser and the Mango notebook through Docker on Amazon EMR.

Creating a Cluster
------------------

Through the aws command line, create a new cluster:

.. code:: bash

  aws emr create-cluster
  --release-label emr-5.18.0 \
  --name 'emr-5.18.0 Mango example' \
  --applications Name=Hadoop Name=Hive Name=Spark  \
  --ec2-attributes KeyName=<your-ec2-key>,InstanceProfile=EMR_EC2_DefaultRole \
  --service-role EMR_DefaultRole \
  --instance-groups \
    InstanceGroupType=MASTER,InstanceCount=1,InstanceType=c3.4xlarge \
    InstanceGroupType=CORE,InstanceCount=4,InstanceType=c3.4xlarge \
  --region <your_region> \
  --log-uri s3://<your-s3-bucket>/emr-logs/ \
  --bootstrap-actions \
  Name='Install Mango', Path="s3://bdg-mango/install-bdg-mango-docker-emr5.sh"


The bootstrap action will download docker and required scripts, available on your master node in EMR in directory /home/hadoop/mango-scripts.


Enabling a Web Connection
--------------------------
To view the Spark UI, notebook, and browser, you must setup a web connection for the cluster. To do so, navigate to your Amazon EMR Page and click on **Enable Web Connection** and follow these instructions.

.. image:: ../img/EMR/enable_web_connection.png

Note that for accessing the recommended 8157 port for FoxyProxy (as well as port 22 for ssh), you will have to expose these ports in the security group for the master node.
To do this, navigate to **Security and access** in your Cluster EMR manager. Click on **Security groups for Master**. Add a inbound new rule for ssh port 22 and a new TCP rule for
the port configured in FoxyProxy inbound to <YOUR_PUBLIC_IP_ADDRESS>/32.

Alternatively,  `you can set up an ssh tunnel on the master node <https://docs.aws.amazon.com/emr/latest/ManagementGuide/emr-ssh-tunnel-local.html>`__.

Connecting to Cluster
---------------------
To ssh into your cluster, navigate to your EMR cluster in AWS console and click on 'ssh'. This will give you the command you need to ssh into the cluster.

Accessing the Web UI
--------------------

Click on "Enable Web Connection" in the AWS cluster console and run the ssh command for accessing the UIs through your browser. The command line argument will look like this:

.. code:: bash

 ssh -i ~/MyKey.pem -ND <PORT_NUM> hadoop@<PUBLIC_MASTER_DNS>

Where <PORT_NUM> is the configured port in FoxyProxy, and hadoop@<PUBLIC_MASTER_DNS> is the address you use
to ssh into the master cluster node. Let this run throughout your session.

Testing your Configuration
--------------------------

You should now be able to access the Hadoop UI.
The Hadoop UI is located at:

.. code:: bash

  <PUBLIC_MASTER_DNS>:8088

You can access Spark applications through this UI when they are running.


Running the Mango Browser on EMR with Docker
--------------------------------------------

To run Mango Browser on EMR on top of Docker, you will first need to download a reference (staged either locally or on HDFS). For example, first get the chr17 reference:

.. code:: bash

  wget http://hgdownload.cse.ucsc.edu/goldenpath/hg19/chromosomes/chr17.fa.gz
  gunzip chr17.fa.gz
  hdfs dfs -put chr17.fa

Now that you have a reference, you can run Mango browser:

.. code:: bash

  /home/hadoop/mango-scripts/run-browser.sh <SPARK_ARGS> -- hdfs:///user/hadoop/chr17.fa \
    -reads s3a://1000genomes/phase1/data/NA19685/exome_alignment/NA19685.mapped.illumina.mosaik.MXL.exome.20110411.bam

Note: s3a latency slows down Mango browser. For interactive queries, you can first `transfer s3a files to HDFS <https://docs.aws.amazon.com/emr/latest/ReleaseGuide/UsingEMR_s3distcp.html>`__.



You can then run Mango browser on HDFS files:

.. code:: bash

  ./run-browser.sh <SPARK_ARGS> -- hdfs:///user/hadoop/chr17.fa \
    -genes http://www.biodalliance.org/datasets/ensGene.bb \
    -reads hdfs:///user/hadoop/NA19685.mapped.illumina.mosaik.MXL.exome.20110411.bam


Note: The first time Docker may take a while to set up.

Navigate to <PUBLIC_MASTER_DNS>:8080 to access the browser.

In the browser, navigate to a gene (ie. TP53, chr17-chr17:7,510,400-7,533,590) with exome data to view results.


Running Mango Notebook on EMR with Docker
-----------------------------------------

To run Mango Notebook on EMR on top of Docker, run the run-notebook script:

.. code:: bash

  # Run the Notebook
  /home/hadoop/run-notebook.sh <SPARK_ARGS> -- <NOTEBOOK_ARGS>

Where <SPARK_ARGS> are Spark specific arguments and <NOTEBOOK_ARGS> are Jupyter notebook specific arguments.
For example:

.. code:: bash

  ./run-notebook.sh --master yarn --num-executors 64 --executor-memory 30g --

Note: It will take a couple minutes on startup for the Docker configuration to complete.


Navigate to <PUBLIC_MASTER_DNS>:8888 to access the notebook. Type in the Jupyter notebook token provided in the terminal. An example notebook for EMR can be found at /opt/cgl-docker-lib/mango/example-files/notebooks/aws-1000genomes.ipynb.

Accessing files in the Mango notebook from HDFS
-----------------------------------------------
Mango notebook and Mango browser can also access files from HDFS on EMR. To do so, first put the files in HDFS:

.. code:: bash

  hdfs dfs -put <my_file.bam>

You can then reference the file through the following code in Mango notebook:

.. code:: bash

  ac.loadAlignments('hdfs:///user/hadoop/<my_file.bam>')

Running Mango Standalone
========================

This section explains how to run the Mango browser and the Mango notebook without Docker on EMR.

Creating a Cluster
------------------

Through the AWS command line, create a new cluster:

.. code:: bash

  VERSION=0.0.2

  aws emr create-cluster
  --release-label emr-5.18.0 \
  --name 'emr-5.18.0 Mango example' \
  --applications Name=Hadoop Name=Hive Name=Spark Name=JupyterHub  \
  --ec2-attributes KeyName=<your-ec2-key>,InstanceProfile=EMR_EC2_DefaultRole \
  --service-role EMR_DefaultRole \
  --instance-groups \
    InstanceGroupType=MASTER,InstanceCount=1,InstanceType=c3.4xlarge \
    InstanceGroupType=CORE,InstanceCount=4,InstanceType=c3.4xlarge \
  --region <your_region> \
  --log-uri s3://<your-s3-bucket>/emr-logs/ \
  --bootstrap-actions \
  Name='Install Mango', Path="s3://bdg-mango/install-bdg-mango-dist-emr5.sh",Args=[$VERSION]

Where $VERSION specifies the Mango version available in the `Maven central repository <https://search.maven.org/search?q=g:org.bdgenomics.mango>`__.

The bootstrap action will download Mango distribution code, and an example notebook file for the Mango notebook will
be available at /home/hadoop/mango-distribution-${VERSION}/notebooks/aws-1000genomes.ipynb.

Finally, make sure you set your SPARK_HOME env:

.. code:: bash

  export SPARK_HOME=/usr/lib/spark


Running Mango Browser on EMR
----------------------------

To run Mango Browser on EMR, you will first need to download a reference (staged either locally or on HDFS). For example, first get the hg19 2bit reference:

.. code:: bash

  wget http://hgdownload.cse.ucsc.edu/goldenPath/hg19/bigZips/hg19.2bit

Now that you have a reference, you can run Mango browser:

.. code:: bash

  /home/hadoop//mango-distribution-${VERSION}/bin/mango-submit <SPARK_ARGS>  \
    --packages net.fnothaft:jsr203-s3a:0.0.2 \
    -- /<absolute_local_path_to_reference_file>/hg19.2bit \
    -genes http://www.biodalliance.org/datasets/ensGene.bb \
    -reads s3a://1000genomes/phase1/data/NA19685/exome_alignment/NA19685.mapped.illumina.mosaik.MXL.exome.20110411.bam \
    -port 8081

Note: Pulling data from s3a has high latency, and thus slows down Mango browser. For interactive queries, you can first `transfer s3a files to HDFS <https://docs.aws.amazon.com/emr/latest/ReleaseGuide/UsingEMR_s3distcp.html>`__.
The package net.fnothaft:jsr203-s3a:0.0.2 used above is required for loading files from s3a. This is not required if you are only accessing data from HDFS.

If you have not `established a web connection <#enabling-a-web-connection>`__, set up an `ssh tunnel on the master node to view the browser at port 8081 <https://docs.aws.amazon.com/emr/latest/ManagementGuide/emr-ssh-tunnel-local.html>`__.

In the browser, navigate to a gene (ie. TP53, chr17-chr17:7,510,400-7,533,590) with exome data to view results.


Running Mango Notebook on EMR
-----------------------------

To run Mango Notebook on EMR, run the mango-notebook script:

.. code:: bash

  # set CLASSPATH for Spark
  EXTRA_CLASSPATH=/usr/lib/hadoop-lzo/lib/*:/usr/lib/hadoop/hadoop-aws.jar:/usr/share/aws/aws-java-sdk/*:/usr/share/aws/emr/emrfs/conf:/usr/share/aws/emr/emrfs/lib/*:/usr/share/aws/emr/emrfs/auxlib/*:/usr/share/aws/emr/security/conf:/usr/share/aws/emr/security/lib/*:/usr/share/aws/hmclient/lib/aws-glue-datacatalog-spark-client.jar:/usr/share/java/Hive-JSON-Serde/hive-openx-serde.jar:/usr/share/aws/sagemaker-spark-sdk/lib/sagemaker-spark-sdk.jar:/usr/share/aws/emr/s3select/lib/emr-s3-select-spark-connector.jar


  /home/hadoop/mango-distribution-${VERSION}/bin/mango-notebook \
        --packages net.fnothaft:jsr203-s3a:0.0.2 \
  	    --conf spark.hadoop.fs.s3a.impl=org.apache.hadoop.fs.s3a.S3AFileSystem \
        --conf fs.s3a.connection.maximum=50000 \
        --conf spark.driver.extraClassPath=file:////home/hadoop/.ivy2/jars/net.fnothaft_jsr203-s3a-0.0.2.jar:${EXTRA_CLASSPATH} \
        --conf spark.executor.extraClassPath=${EXTRA_CLASSPATH} \
        -- --no-browser \
        <NOTEBOOK_ARGS>

Note that the extra NOTEBOOK_ARGS run the notebook detached from the browser so you can
`set up an ssh tunnel on the master node to view the notebook <https://docs.aws.amazon.com/emr/latest/ManagementGuide/emr-ssh-tunnel-local.html>`__.
