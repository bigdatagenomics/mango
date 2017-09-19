# get current local directory
DIR=$( cd $( dirname ${BASH_SOURCE[0]} ) && pwd )

# reference 2 bit does not need 'file:' because it is only read from the master
bin/mango-submit ${DIR}/hg19.17.2bit \
-genes http://www.biodalliance.org/datasets/ensGene.bb \
-reads ./example-files/chr17.7500000-7515000.sam.adam \
-variants ./example-files/ALL.chr17.7500000-7515000.phase3_shapeit2_mvncall_integrated_v5a.20130502.genotypes.vcf \
-show_genotypes \
-discover \
-port 8080

