Examples
==========================


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

Running on a cluster with HDFS
------------------------------

To run the example files on a cluster with hdfs, first first put example-files on hdfs.

.. code:: bash

    hdfs dfs -put example-files


Running Mango Notebook Locally
------------------------------

There are example files for running Mango notebook on region chr17:7500000-7515000
of a single sample. Once Mango amdn Mango python is built, you can run the following command
to view Mango notebook.

.. code:: bash

    ./bin/mango-notebook

Mango notebook depends on Jupyter notebook. To install all dependencies for Mango notebook in a virtual environment, see installation/sources.


One Mango notebook is running, you can view local results at localhost:10000 `virtualenv <localhost:10000>`__
or the open port assigned by Jupyter notebook. There are three notebooks that can be viewed:

- mango-python-alignment.ipynb
- mango-python-coverage.ipynb
- mango-viz.ipynb


Running Mango with Parameters
------------------------------
To run Mango notebook with user specified parameters, run
.. code:: bash

    ./bin/mango-submit <Spark-parameters> -- <Mango-notebook-parameters>

<Mango-notebook-parameters> are option and pertain to `Jupyter notebook parameters <http://jupyter-notebook.readthedocs.io/en/stable/config.html>`_.
