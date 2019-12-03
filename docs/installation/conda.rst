Installing Mango from Conda
===========================


Install Conda
-------------

Installing mango through conda requires conda to be installed.
First, make sure you have `installed conda <https://docs.conda.io/projects/conda/en/latest/user-guide/install/>`__.


Set up channels
---------------

After installing conda you will need to add other channels mango depends on.
It is important to add them in this order so that the priority is set correctly.

.. code:: bash

    conda config --add channels defaults
    conda config --add channels bioconda
    conda config --add channels conda-forge


Install Mango in a conda environment
------------------------------------


To manage dependencies amongst other projects, you can install mango
in a conda environment. First, create a new conda environment:

.. code:: bash

    conda create -y -q -n MyEnv python=3.6 pip

Then install mango to the created environment:

.. code:: bash

    conda install --name MyEnv mango

Then activate your environment:

.. code:: bash

    conda activate MyEnv


**Note**: If not installing Mango into a conda environment, Mango requires SPARK_HOME environment variable to be set.
If you are installing Mango into a conda environment and the SPARK_HOME environment variable is not set,
activating the conda environment will automatically set SPARK_HOME to the pyspark site-packages path.
