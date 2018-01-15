# TODO: make sure docker is installed. If not, throw error

# sudo docker run \
#        --net=host \
#        -v ${SPARK_HOME}:${SPARK_HOME} \
#        -v ${SPARK_CONF_DIR}:${SPARK_CONF_DIR} \
#        -v ${HADOOP_HOME}:${HADOOP_HOME} \
#        -v ${HADOOP_CONF_DIR}:${HADOOP_CONF_DIR} \
#        -e SPARK_HOME=${SPARK_HOME} \
#        -e HADOOP_HOME=${HADOOP_HOME} \
#        -e SPARK_CONF_DIR=${SPARK_CONF_DIR} \
#        -e HADOOP_CONF_DIR=${HADOOP_CONF_DIR} \
#        --entrypoint=/opt/cgl-docker-lib/mango/bin/mango-notebook \
#        -p 8888:8888 \
#        quay.io/ucsc_cgl/mango:latest \
#        --master yarn \
#        --packages org.apache.parquet:parquet-avro:1.8.2 \
#        --packages net.fnothaft:jsr203-s3a:0.0.1 \
#        --conf spark.hadoop.hadoopbam.bam.enable-bai-splitter=true \
#        -- --ip=0.0.0.0 --allow-root


set -ex

# Split args into Spark and notebook args
DD=False  # DD is "double dash"
ENTRYPOINT=TRUE
PRE_DD=()
POST_DD=()

# by default, runs mango browser (mango-submit)
# to override to mango-notebook,
# run docker with --entrypoint=/opt/cgl-docker-lib/mango/bin/mango-notebook
ENTRYPOINT="--entrypoint=/opt/cgl-docker-lib/mango/bin/mango-notebook"
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


export SPARK_HOME=/usr/lib/spark
export SPARK_CONF_DIR=/usr/lib/spark/conf
export HADOOP_HOME=/usr/lib/hadoop
export HADOOP_LIBEXEC_DIR=$HADOOP_HOME/libexec
export HADOOP_CONF_DIR=$HADOOP_HOME/etc/hadoop
export HADOOP_HDFS=/usr/lib/hadoop-hdfs
export HADOOP_YARN=/usr/lib/hadoop-yarn
export HADOOP_MAPREDUCE=/usr/lib/hadoop-mapreduce
export HADOOP_LZO=/usr/lib/hadoop-lzo/lib

# ENV Variables for EMR/AWS
export AWS_SDK = /usr/share/aws/aws-java-sdk

# TODO: These need to be added to EMR --attributes
export HIVE_DIR=/usr/lib/hive
export HIVE_CONF_DIR=$HIVE_DIR/conf
# -v ${HIVE_DIR}:${HIVE_DIR} \
# -e HIVE_DIR=${HIVE_DIR} \
# -e HIVE_CONF_DIR=${HIVE_CONF_DIR} \

# get UUID name for docker container
uuid=$(uuidgen)
DOCKER_CONTAINER_NAME=mango_notebook_${uuid}


sudo docker run \
      --name ${DOCKER_CONTAINER_NAME} \
      --net=host \
      -v /usr/share/aws:/usr/share/aws \
      -v ${HADOOP_LZO}:${HADOOP_LZO} \
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
      -p 8888:8888 \
      -i \
      -t \
      $ENTRYPOINT \
      quay.io/ucsc_cgl/mango:latest \
      --master yarn \
      --packages org.apache.parquet:parquet-avro:1.8.2 \
      --packages net.fnothaft:jsr203-s3a:0.0.1 \
      --conf spark.hadoop.hadoopbam.bam.enable-bai-splitter=true \
      --conf spark.driver.extraClassPath=${HADOOP_LZO}/*:${AWS_SDK}/*:/usr/share/aws/emr/emrfs/conf:/usr/share/aws/emr/emrfs/lib/*:/usr/share/aws/emr/emrfs/auxlib/* \
      $PRE_DD_ARGS \
      -- --ip=0.0.0.0 --allow-root \
      $POST_DD_ARGS
