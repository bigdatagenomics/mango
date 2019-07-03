set -ex

# Split args into Spark and notebook args
DD=False  # DD is "double dash"
PRE_DD=()
POST_DD=()

for ARG in "$@"; do
 shift
 if [[ $ARG == "--" ]]; then
   DD=True
   POST_DD=( "$@" )
   break
 fi
  PRE_DD+=("$ARG")
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
export HADOOP_LZO=/usr/lib/hadoop-lzo/lib

# ENV Variables for EMR/AWS
export AWS_SDK=/usr/share/aws

export HIVE_SDK=/usr/share/java/Hive-JSON-Serde

# s3/s3a requirements
export EXTRA_CLASSPATH=${HADOOP_LZO}/*:${AWS_SDK}/aws-java-sdk/*:${AWS_SDK}/emr/emrfs/conf:${AWS_SDK}/emr/emrfs/lib/*:${AWS_SDK}/emr/emrfs/auxlib/*:${AWS_SDK}/emr/security/conf:${AWS_SDK}/emr/security/lib/*:${AWS_SDK}/hmclient/lib/aws-glue-datacatalog-spark-client.jar:${AWS_SDK}/sagemaker-spark-sdk/lib/sagemaker-spark-sdk.jar:${HIVE_SDK}/*.jar

export SPARK_DIST_CLASSPATH="/usr/lib/hadoop/etc/hadoop:/usr/lib/hadoop/lib/*:/usr/lib/hadoop/.//*:/usr/lib/hadoop-hdfs/./:/usr/lib/hadoop-hdfs/lib/*:/usr/lib/hadoop-hdfs/.//*:/usr/lib/hadoop-yarn/lib/*:/usr/lib/hadoop-yarn/.//*:/usr/lib/hadoop-mapreduce/lib/*:/usr/lib/hadoop-mapreduce/.//*"

/home/hadoop/mango/bin/mango-notebook \
      --master yarn \
      --conf spark.hadoop.fs.s3a.impl=org.apache.hadoop.fs.s3a.S3AFileSystem \
      --packages net.fnothaft:jsr203-s3a:0.0.2 \
      --conf fs.s3a.connection.maximum=50000 \
      --conf spark.hadoop.hadoopbam.bam.enable-bai-splitter=true \
      --conf spark.driver.extraClassPath=${EXTRA_CLASSPATH} \
      --conf spark.executor.extraClassPath=${EXTRA_CLASSPATH} \
      ${PRE_DD_ARGS} \
      -- --ip=0.0.0.0 --allow-root \
      ${POST_DD_ARGS}
