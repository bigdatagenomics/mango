export SPARK_HOME=/spark
bin/mango-submit \
--conf spark.kryoserializer.buffer.max=512m \
--master spark://spark-master:7077 \
-- \
/code/S288C_reference_sequence_R64-2-1_20150113.fasta \
-discover \
# -features ./S288C_reference_genome_R64-2-1_20150113/saccharomyces_cerevisiae_R64-2-1_20150113.gff.bed \
