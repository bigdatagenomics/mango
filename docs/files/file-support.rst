Supported Files
===============


Supported File Types
--------------------

Mango supports the following file types:

+------------+------------+---------------------------+
| Data Type  |       Supported File Types             |
+============+============+===========================+
| Alignments | Parquet, bam, indexed bam, sam         |
+------------+------------+---------------------------+
| Variants   | Parquet, vcf, indexed vcf, vcf.gz      |
+------------+------------+---------------------------+
| Features   | Parquet, bed, narrowPeak               |
+------------+------------+---------------------------+
| Genome     | Parquet, twoBit\ :sup:`*`\, fa, fasta  |
+------------+------------+---------------------------+

\ :sup:`*`\ TwoBit files must be staged locally for access.

Accessing http files through Mango
-----------------------------------

Mango can copy and read http files. To do so, in mango-submit, set ``spark.local.dir`` to a path in the user's home directory:

.. code:: bash

    ./bin/mango-submit
        --conf spark.local.dir=<user home>/spark-tmp

This will allow Spark to access temporary http files.


Accessing s3a files through Mango
---------------------------------

To access s3a files when running on AWS, you need the net.fnothaft:jsr203-s3a package, and the bam splitter to be enabled:

.. code:: bash

    ./bin/mango-submit \
            --packages org.apache.parquet:parquet-avro:1.8.2 \
            --packages net.fnothaft:jsr203-s3a:0.0.2 \
            --conf spark.hadoop.hadoopbam.bam.enable-bai-splitter=true \
            -- hg19.2bit \
            -reads s3a://1000genomes/phase1/data/NA12878/exome_alignment/NA12878.mapped.illumina.mosaik.CEU.exome.20110411.bam
