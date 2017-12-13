export SPARK_HOME=/usr/lib/spark
export SPARK_CONF_DIR=/usr/lib/spark/conf
export HADOOP_HOME=/usr/lib/hadoop
export HADOOP_LIBEXEC_DIR=$HADOOP_HOME/libexec
export HADOOP_CONF_DIR=$HADOOP_HOME/etc/hadoop
export HADOOP_HDFS=/usr/lib/hadoop-hdfs
export HADOOP_YARN=/usr/lib/hadoop-yarn
export HADOOP_MAPREDUCE=/usr/lib/hadoop-mapreduce
export HIVE_DIR=/usr/lib/hive
export CONDA_DIR=/opt/conda
export PYSPARK_DRIVER_PYTHON=/opt/conda/bin/jupyter
export HIVE_CONF_DIR=$HIVE_DIR/conf

docker run \
       --net=host \
       -v ${SPARK_HOME}:${SPARK_HOME} \
       -v ${SPARK_CONF_DIR}:${SPARK_CONF_DIR} \
       -v ${HADOOP_HOME}:${HADOOP_HOME} \
       -v ${HADOOP_CONF_DIR}:${HADOOP_CONF_DIR} \
       -v ${HADOOP_HDFS}:${HADOOP_HDFS} \
       -v ${HADOOP_YARN}:${HADOOP_YARN} \
       -v ${CONDA_DIR}:${CONDA_DIR} \
       -v ${HADOOP_MAPREDUCE}:${HADOOP_MAPREDUCE} \
       -v ${HIVE_DIR}:${HIVE_DIR} \
       -e SPARK_HOME=${SPARK_HOME} \
       -e HADOOP_HOME=${HADOOP_HOME} \
       -e SPARK_CONF_DIR=${SPARK_CONF_DIR} \
       -e HADOOP_CONF_DIR=${HADOOP_CONF_DIR} \
       -e HIVE_DIR=${HIVE_DIR} \
       -e HIVE_CONF_DIR=${HIVE_CONF_DIR} \
       -e SPARK_DIST_CLASSPATH="/usr/lib/hadoop/etc/hadoop:/usr/lib/hadoop/lib/*:/usr/lib/hadoop/.//*:/usr/lib/hadoop-hdfs/./:/usr/lib/hadoop-hdfs/lib/*:/usr/lib/hadoop-hdfs/.//*:/usr/lib/hadoop-yarn/lib/*:/usr/lib/hadoop-yarn/.//*:/usr/lib/hadoop-mapreduce/lib/*:/usr/lib/hadoop-mapreduce/.//*" \
       --entrypoint=/opt/cgl-docker-lib/mango/bin/mango-notebook \
       -p 8888:8888 \
       quay.io/ucsc_cgl/mango:latest \
       --master yarn\
       -- --ip=0.0.0.0 --allow-root