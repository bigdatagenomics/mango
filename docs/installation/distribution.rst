Running Mango from Distribution
===============================

Fetching Mango Distribution
---------------------------

Mango is packaged as an
`überjar <https://maven.apache.org/plugins/maven-shade-plugin/>`__ and
includes all necessary dependencies, except for Apache Hadoop and Apache
Spark.


To fetch Mango distribution, run:

.. code:: bash

      wget -O mango-distribution-0.0.1-bin.tar.gz https://search.maven.org/remotecontent?filepath=org/bdgenomics/mango/mango-distribution/0.0.1/mango-distribution-0.0.1-bin.tar.gz
      tar xzvf mango-distribution-0.0.1-bin.tar.gz




From the distribution directory, you can run Mango notebook or Mango browser:

First, make sure your SPARK_HOME env variable is set:

.. code:: bash

      export SPARK_HOME=<PATH_TO_SPARK>


Then run Mango notebook or Mango browser:

.. code:: bash

      cd mango-distribution-0.0.1
      ./bin/mango-notebook
      ./bin/mango-browser


Mango Python
-------------

To run Mango in a python notebook, install bdgenomics.mango.pileup, a Jupyter Widget:


.. code:: bash

    pip install bdgenomics.mango.pileup
    jupyter nbextension enable --py --sys-prefix bdgenomics.mango.pileup  # can be skipped for notebook version 5.3 and above


And bdgenomics.mango:

.. code:: bash

    pip install bdgenomics.mango
