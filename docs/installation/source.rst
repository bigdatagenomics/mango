Building Mango from Source
==========================

You will need to have `Apache Maven <http://maven.apache.org/>`__
version 3.1.1 or later installed in order to build Mango.

    **Note:** The default configuration is for Hadoop 2.7.3. If building
    against a different version of Hadoop, please pass
    ``-Dhadoop.version=<HADOOP_VERSION>`` to the Maven command. Mango
    will cross-build for both Spark 1.x and 2.x, but builds by default
    against Spark 1.6.3. To build for Spark 2, run the
    ``./scripts/move_to_spark2.sh`` script.

.. code:: bash

    git clone https://github.com/bigdatagenomics/mango.git
    cd mango
    export MAVEN_OPTS="-Xmx512m -XX:MaxPermSize=128m"
    mvn clean package -DskipTests

Outputs

::

    ...
    [INFO] BUILD SUCCESS
    [INFO] ------------------------------------------------------------------------
    [INFO] Total time: 04:30 min
    [INFO] Finished at: 2017-12-11T10:35:57-08:00
    [INFO] Final Memory: 61M/1655M
    [INFO] ------------------------------------------------------------------------

Running Mango
-------------

Mango is packaged as an
`überjar <https://maven.apache.org/plugins/maven-shade-plugin/>`__ and
includes all necessary dependencies, except for Apache Hadoop and Apache
Spark.

Building for Python
-------------------

To build and test `Mango’s Python bindings <#python>`__, enable the
``python`` profile:

.. code:: bash

    mvn package -P python

This will enable the ``mango-python`` and ``mango-viz`` module as part of the Mango build.
This module uses Maven to invoke a Makefile that builds a Python egg and
runs tests. To build this module, we require either an active `virtualenv <https://virtualenv.pypa.io/en/stable/>`__ environment.
Note: conda environments do work with jupyter widgets, so are not recommended.

To setup and activate a `virtual environment
<https://virtualenv.pypa.io/en/stable/userguide/#usage>`__, run:

.. code:: bash

    virtualenv -n venv-mango python=2.7 anaconda
    source activate venv-mango


Additionally, to run tests, the PySpark dependencies must be on the
Python module load path and the Mango JARs must be built and provided to
PySpark. This can be done with the following bash commands:

.. code:: bash

    # add pyspark to the python path
    PY4J_ZIP="$(ls -1 "${SPARK_HOME}/python/lib" | grep py4j)"
    export PYTHONPATH=${SPARK_HOME}/python:${SPARK_HOME}/python/lib/${PY4J_ZIP}:${PYTHONPATH}


    # put mango jar on the pyspark path
    ASSEMBLY_DIR="${MANGO_HOME}/mango-assembly/target"
    ASSEMBLY_JAR="$(ls -1 "$ASSEMBLY_DIR" | grep "^mango-assembly[0-9A-Za-z\_\.-]*\.jar$" | grep -v javadoc | grep -v sources || true)"
    export PYSPARK_SUBMIT_ARGS="--jars ${ASSEMBLY_DIR}/${ASSEMBLY_JAR} --driver-class-path ${ASSEMBLY_DIR}/${ASSEMBLY_JAR} pyspark-shell"


This assumes that the `Mango JARs have already been
built. Additionally, we require
`pytest <https://docs.pytest.org/en/latest/>`__ to be installed. The
mango-python makefile can install this dependency. Once you have an
active virtualenv environment, run:

.. code:: bash

    cd mango-python
    make prepare

    cd mango-viz
    make prepare
