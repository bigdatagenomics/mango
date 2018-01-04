set -ex

# make sure docker is installed. If not, throw error
docker -v
rc=$?; if [[ $rc != 0 ]]; then
        echo "Docker is not installed. Have you run install.sh?"
        exit $rc
fi

docker run \
       --net=host \
       -v ${SPARK_HOME}:${SPARK_HOME} \
       -v ${SPARK_CONF_DIR}:${SPARK_CONF_DIR} \
       -v ${HADOOP_HOME}:${HADOOP_HOME} \
       -v ${HADOOP_CONF_DIR}:${HADOOP_CONF_DIR} \
       -e SPARK_HOME=${SPARK_HOME} \
       -e HADOOP_HOME=${HADOOP_HOME} \
       -e SPARK_CONF_DIR=${SPARK_CONF_DIR} \
       -e HADOOP_CONF_DIR=${HADOOP_CONF_DIR} \
       -p 8080:8080 \
       quay.io/ucsc_cgl/mango:latest \
       --master yarn \
       --packages org.apache.parquet:parquet-avro:1.8.2 \
       --packages net.fnothaft:jsr203-s3a:0.0.1 \
       --conf spark.hadoop.hadoopbam.bam.enable-bai-splitter=true \
       -- "$@"
