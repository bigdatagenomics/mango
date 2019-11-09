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
export HADOOP_CONF_DIR=/usr/lib/hadoop/etc/hadoop/

# required paths to use s3a
export SPARK_DIST_CLASSPATH="/usr/lib/hadoop/etc/hadoop:/usr/lib/hadoop/lib/*:/usr/lib/hadoop/.//*:/usr/lib/hadoop-hdfs/./:/usr/lib/hadoop-hdfs/lib/*:/usr/lib/hadoop-hdfs/.//*:/usr/lib/hadoop-yarn/lib/*:/usr/lib/hadoop-yarn/.//*:/usr/lib/hadoop-mapreduce/lib/*:/usr/lib/hadoop-mapreduce/.//*" \

mango-submit \
       --master yarn \
       --conf spark.hadoop.fs.s3a.impl=org.apache.hadoop.fs.s3a.S3AFileSystem \
       --conf fs.s3a.connection.maximum=50000 \
       --conf spark.kryoserializer.buffer.max=2040 \
       --packages net.fnothaft:jsr203-s3a:0.0.2 \
       --conf spark.hadoop.hadoopbam.bam.enable-bai-splitter=true \
       ${PRE_DD_ARGS} \
       --  \
	   ${POST_DD_ARGS}
