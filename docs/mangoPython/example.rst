Mango Python Examples
=====================

Running Mango Notebook Locally
------------------------------

There are example files for running Mango notebook on region chr17:7500000-7515000
of a single sample. Once Mango and Mango python is built, you can run the following command
to view Mango notebook.

.. code:: bash

    ./bin/mango-notebook

Mango notebook depends on Jupyter notebook. To install all dependencies for Mango notebook in a virtual environment, see installation/sources.


One Mango notebook is running, you can view local results at localhost:10000 `virtualenv <localhost:10000>`__
or the open port assigned by Jupyter notebook. There are three notebooks that can be viewed as examples in the `Mango repository <https://github.com/bigdatagenomics/mango>`:

- `example-files/notebooks/mango-python-alignment.ipynb <https://github.com/bigdatagenomics/mango/blob/master/example-files/notebooks/mango-python-alignment.ipynb>`__
- `example-files/notebooks/mango-python-coverage.ipynb <https://github.com/bigdatagenomics/mango/blob/master/example-files/notebooks/mango-python-coverage.ipynb>`__
- `example-files/notebooks/mango-viz.ipynb <https://github.com/bigdatagenomics/mango/blob/master/example-files/notebooks/mango-viz.ipynb>`__


Running Mango with Parameters
------------------------------
The Mango Notebook can be run with `Apache Spark parameters <https://spark.apache.org/docs/latest/configuration.html>`__ and `Jupyter notebook parameters <http://jupyter-notebook.readthedocs.io/en/stable/config.html>`__.
To run Mango notebook with user specified parameters, run

.. code:: bash

    ./bin/mango-submit <Spark-parameters> -- <Jupyter-notebook-parameters>
