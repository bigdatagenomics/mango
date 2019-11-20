set -ex

# Split args into Spark and notebook args
DD=False  # DD is "double dash"
ENTRYPOINT=TRUE
PRE_DD=()
POST_DD=()

# by default, runs mango notebook
# to override to mango-submit,
# run docker with --entrypoint=/opt/cgl-docker-lib/mango/bin/mango-submit
ENTRYPOINT="--entrypoint=mango-notebook"
for ARG in "$@"; do
  shift
  if [[ $ARG == "--" ]]; then
    DD=True
    POST_DD=( "$@" )
    break
  fi
  if [[ $ARG == '--entrypoint='* ]]; then
       ENTRYPOINT=${ARG#(--entrypoint=): }
  else
       PRE_DD+=("$ARG")
  fi
done

PRE_DD_ARGS="${PRE_DD[@]}"
POST_DD_ARGS="${POST_DD[@]}"

export SPARK_HOME=/usr/lib/spark
export SPARK_CONF_DIR=/usr/lib/spark/conf
export HADOOP_HOME=/usr/lib/hadoop
export HADOOP_LIBEXEC_DIR=$HADOOP_HOME/libexec
export HADOOP_CONF_DIR=$HADOOP_HOME/etc/hadoop
export HADOOP_HDFS=/usr/lib/hadoop-hdfs
export HADOOP_YARN=/usr/lib/hadoop-yarn
export HADOOP_MAPREDUCE=/usr/lib/hadoop-mapreduce
export PYSPARK_DRIVER_PYTHON=/opt/conda/bin/jupyter
export HIVE_CONF_DIR=$HIVE_DIR/conf

# Sets java classes required for hadoop yarn, mapreduce, hive associated with the cluster
export SPARK_DIST_CLASS_PATH="/usr/lib/hadoop/etc/hadoop:/usr/lib/hadoop/lib/*:/usr/lib/hadoop/.//*:/usr/lib/hadoop-hdfs/./:/usr/lib/hadoop-hdfs/lib/*:/usr/lib/hadoop-hdfs/.//*:/usr/lib/hadoop-yarn/lib/*:/usr/lib/hadoop-yarn/.//*:/usr/lib/hadoop-mapreduce/lib/*:/usr/lib/hadoop-mapreduce/.//*"

sudo docker run \
       --net=host \
       -v ${SPARK_HOME}:${SPARK_HOME} \
       -v ${SPARK_CONF_DIR}:${SPARK_CONF_DIR} \
       -v ${HADOOP_HOME}:${HADOOP_HOME} \
       -v ${HADOOP_CONF_DIR}:${HADOOP_CONF_DIR} \
       -v ${HADOOP_HDFS}:${HADOOP_HDFS} \
       -v ${HADOOP_YARN}:${HADOOP_YARN} \
       -v ${HADOOP_MAPREDUCE}:${HADOOP_MAPREDUCE} \
       -e SPARK_HOME=${SPARK_HOME} \
       -e HADOOP_HOME=${HADOOP_HOME} \
       -e SPARK_CONF_DIR=${SPARK_CONF_DIR} \
       -e HADOOP_CONF_DIR=${HADOOP_CONF_DIR} \
       -e HIVE_CONF_DIR=${HIVE_CONF_DIR} \
       -e SPARK_DIST_CLASSPATH=${SPARK_DIST_CLASSPATH}} \
       $ENTRYPOINT \
       -p 8888:8888 \
       quay.io/bigdatagenomics/mango:latest \
       --master yarn \
       --jars gs://mango-initialization-bucket/google-cloud-nio-0.22.0-alpha-shaded.jar \
       $PRE_DD_ARGS \
       -- --ip=0.0.0.0 --allow-root \
       $POST_DD_ARGS
