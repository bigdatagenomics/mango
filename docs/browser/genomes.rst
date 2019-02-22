Building a Genome
=================

The Mango browser requires a remote `TwoBit reference file <https://genome.ucsc.edu/goldenpath/help/twoBit.html>`__, a local
 `gene file <http://hgdownload.soe.ucsc.edu/goldenPath/hg19/database/refGene.txt.gz>`__`, and a local `chromosome size file
 <http://hgdownload.cse.ucsc.edu/goldenPath/hg19/bigZips/hg19.chrom.sizes>`__` to run. These
files are downloaded from `UCSC Genome Browser downloads <http://hgdownload.cse.ucsc.edu>`__.


Assembling a Default Genome File
--------------------------------

To assemble a genome file, run:

.. code:: bash

    ./bin/make_genome <GENOME_NAME> <OUTPUT_LOCATION>


This will download required files for the genome build, compress them, and save them to <OUTPUT_LOCATION>. All builds accessible
from the `UCSC Downloads page <http://hgdownload.cse.ucsc.edu/goldenPath>`__ are supported.

The hg19.genome build exists in the example-files folder as a reference.


Assembling a Custom Genome File
-------------------------------

If you need to assemble a custom genome file that is not supported by the make_genome executable, you can assemble one as follows.

First specify a folder ,<YOUR_GENOME>.genome, and include the following files:

- cytoBand.txt: UCSC formatted cytoBand file.
- <YOUR_GENOME>.chrom.sizes: tab delimited file of chromosome names and sizes.
- refGene.txt: UCSC formatted tab delimited text file of gene information.
- properties.txt: File containing metainformation for your genome.


The properties.txt file should be formatted as follows:

.. code:: bash

sequenceLocation=http://<path_to_remote_2bit_file>.2bit
refGene=refGene.txt
chromSizes=<YOUR_GENOME>.chrom.sizes
cytoband=cytoBand.txt

Note that the sequenceLocation parameter must link to a remote `TwoBit file <https://genome.ucsc.edu/goldenpath/help/twoBit.html>`__.

