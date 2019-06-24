Mango Python Examples
=====================

Running Mango Notebook Locally
------------------------------

Once Mango and Mango python is built, you can run the following command
to view Mango notebook.

.. code:: bash

    ./bin/mango-notebook

Mango notebook depends on Jupyter notebook.
To install all dependencies for Mango notebook in a virtual environment,
see `installation instructions <../installation/source.html>`__.


One Mango notebook is running, you can view local results at localhost:<port>, where <port> is
the open port assigned by Jupyter notebook. There are three notebooks that can be viewed as examples in the `Mango repository <https://github.com/bigdatagenomics/mango>`__:

- `example-files/notebooks/mango-python-alignment.ipynb <https://github.com/bigdatagenomics/mango/blob/master/example-files/notebooks/mango-python-alignment.ipynb>`__
- `example-files/notebooks/mango-python-coverage.ipynb <https://github.com/bigdatagenomics/mango/blob/master/example-files/notebooks/mango-python-coverage.ipynb>`__
- `example-files/notebooks/mango-viz.ipynb <https://github.com/bigdatagenomics/mango/blob/master/example-files/notebooks/mango-viz.ipynb>`__


Running the Mango Notebook with Parameters
------------------------------------------
The Mango Notebook can be run with `Apache Spark parameters <https://spark.apache.org/docs/latest/configuration.html>`__ and `Jupyter notebook parameters <http://jupyter-notebook.readthedocs.io/en/stable/config.html>`__.
To run Mango notebook with user specified parameters, run

.. code:: bash

    ./bin/mango-notebook <Spark-parameters> -- <Jupyter-notebook-parameters>


Running the Mango Notebook on YARN
----------------------------------

YARN is a resource management system for clusters.
The Mango notebook can run on `YARN <https://hadoop.apache.org/docs/current/hadoop-yarn/hadoop-yarn-site/YARN.html>`__ clusters, and requires jars for org.apache.parquet:parquet-hadoop:1.8.3.
To run the Mango browser on YARN, download parquet-hadoop jar:


Then include the jar in spark.driver.extraClassPath:

.. code:: bash

  wget http://central.maven.org/maven2/org/apache/parquet/parquet-hadoop/1.8.3/parquet-hadoop-1.8.3.jar

.. code:: bash

    ./bin/mango-notebook --master yarn \
      --conf spark.driver.extraClassPath=<path_to_jar>/parquet-hadoop-1.8.3.jar \
       -- <Jupyter-notebook-parameters>
