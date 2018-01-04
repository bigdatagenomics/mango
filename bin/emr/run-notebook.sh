set -ex

# make sure docker is installed. If not, throw error
docker -v
rc=$?; if [[ $rc != 0 ]]; then
        echo "Docker is not installed. Have you run install.sh?"
        exit $rc
fi

# Split args into Spark and notebook args
DD=False  # DD is "double dash"
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
export AWS_SDK=/usr/share/aws

# get UUID name for docker container
uuid=$(uuidgen)
DOCKER_CONTAINER_NAME=mango_notebook_${uuid}

# s3 commands:
# --conf spark.driver.extraClassPath=${HADOOP_LZO}/*:${AWS_SDK}/*:/usr/share/aws/emr/emrfs/conf:/usr/share/aws/emr/emrfs/lib/*:/usr/share/aws/emr/emrfs/auxlib/* \

# s3a commands
# --conf spark.driver.extraClassPath=/usr/lib/hadoop/hadoop-aws*:${AWS_SDK}/* \
EXTRA_CLASSPATH=spark.driver.extraClassPath=/usr/lib/hadoop/hadoop-aws*:${AWS_SDK}/*

# TODO: REMOVE
# exposing the 2 jars
# export SPARK_CLASSPATH='/usr/lib/hadoop/hadoop-aws-2.7.3-amzn-5.jar:/usr/share/aws/aws-java-sdk/aws-java-sdk-1.11.221.jar'

sudo docker run \
      --name ${DOCKER_CONTAINER_NAME} \
      --net=host \
      -v ${SPARK_HOME}:${SPARK_HOME} \
      -v ${SPARK_CONF_DIR}:${SPARK_CONF_DIR} \
      -v ${HADOOP_HOME}:${HADOOP_HOME} \
      -v ${HADOOP_HDFS}:${HADOOP_HDFS} \
      -v ${HADOOP_YARN}:${HADOOP_YARN} \
      -v ${AWS_SDK}:${AWS_SDK} \
      -v ${HADOOP_LZO}:${HADOOP_LZO} \
      -v ${HADOOP_CONF_DIR}:${HADOOP_CONF_DIR} \
      -v ${HADOOP_MAPREDUCE}:${HADOOP_MAPREDUCE} \
      -e SPARK_CLASSPATH=${SPARK_CLASSPATH} \
      -e SPARK_HOME=${SPARK_HOME} \
      -e HADOOP_HOME=${HADOOP_HOME} \
      -e SPARK_CONF_DIR=${SPARK_CONF_DIR} \
      -e HADOOP_CONF_DIR=${HADOOP_CONF_DIR} \
      -e SPARK_DIST_CLASSPATH="/usr/lib/hadoop/etc/hadoop:/usr/lib/hadoop/lib/*:/usr/lib/hadoop/.//*:/usr/lib/hadoop-hdfs/./:/usr/lib/hadoop-hdfs/lib/*:/usr/lib/hadoop-hdfs/.//*:/usr/lib/hadoop-yarn/lib/*:/usr/lib/hadoop-yarn/.//*:/usr/lib/hadoop-mapreduce/lib/*:/usr/lib/hadoop-mapreduce/.//*" \
      -p 8888:8888 \
      -i \
      -t \
      $ENTRYPOINT \
      quay.io/ucsc_cgl/mango:latest \
      --master yarn \
      --conf spark.hadoop.fs.s3.impl=org.apache.hadoop.fs.s3.S3FileSystem \
      --conf fs.s3.awsAccessKeyId=${AWS_ACCESS_KEY} \
      --conf fs.s3.awsSecretAccessKey=${AWS_SECRET} \
      --packages org.apache.parquet:parquet-avro:1.8.2 \
      --packages net.fnothaft:jsr203-s3a:0.0.1 \
      --conf spark.hadoop.hadoopbam.bam.enable-bai-splitter=true \
      --conf spark.driver.extraClassPath=${EXTRA_CLASSPATH} \
      --conf spark.executor.extraClassPath=${EXTRA_CLASSPATH} \
      $PRE_DD_ARGS \
      -- --ip=0.0.0.0 --allow-root \
      $POST_DD_ARGS
