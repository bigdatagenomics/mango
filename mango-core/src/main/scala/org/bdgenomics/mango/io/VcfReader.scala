/**
 * Licensed to Big Data Genomics (BDG) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The BDG licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bdgenomics.mango.io

import org.apache.spark.SparkContext
import org.bdgenomics.adam.rdd.variant.{ GenotypeDataset, VariantContextDataset }
import org.bdgenomics.adam.rdd.ADAMContext._
import org.bdgenomics.adam.models.{ ReferenceRegion, VariantContext }
import org.bdgenomics.mango.core.util.ResourceUtils
import org.bdgenomics.mango.models.LazyMaterialization
import org.bdgenomics.utils.misc.Logging

object VcfReader extends GenomicReader[VariantContext, VariantContextDataset] with Logging {

  def getBamIndex(path: String): String = {
    path + ".bai"
  }

  def loadLocal(fp: String, regions: Iterable[ReferenceRegion]): Iterator[VariantContext] = {
    // https://github.com/samtools/htsjdk/blob/master/src/main/java/htsjdk/variant/example/PrintVariantsExample.java
    throw new Exception("Not implemented")


  }

  def loadHttp(url: String, regions: Iterable[ReferenceRegion]): Iterator[VariantContext] = {
    throw new Exception("Not implemented")
  }

  /**
   * Loads data from bam files (indexed or unindexed) from s3.
   * @param regions Iterable of ReferenceRegions to load
   * @param fp filepath to load from
   * @return Alignment dataset from the file over specified ReferenceRegion
   */
  def loadS3(path: String, regions: Iterable[ReferenceRegion]): Iterator[VariantContext] = {
    throw new Exception("Not implemented")
    // https://docs.aws.amazon.com/AmazonS3/latest/dev/RetrievingObjectUsingJava.html

    // https://www.programcreek.com/java-api-examples/?api=com.amazonaws.services.s3.AmazonS3URI

    // https://github.com/samtools/htsjdk/issues/635
  }

  /**
   * Loads data from bam files (indexed or unindexed) from HDFS.
   * @param sc SparkContext
   * @param regions Iterable of ReferenceRegions to load
   * @param fp filepath to load from
   * @return Alignment dataset from the file over specified ReferenceRegion
   */
  def loadHDFS(sc: SparkContext, fp: String, regions: Iterable[ReferenceRegion]): VariantContextDataset = {
    val variantContext = if (sc.isPartitioned(fp)) {

      // finalRegions includes references both with and without "chr" prefix
      val finalRegions: Iterable[ReferenceRegion] = regions ++ regions
        .map(x => ReferenceRegion(x.referenceName.replaceFirst("""^chr""", """"""),
          x.start,
          x.end,
          x.strand))

      // load new dataset or retrieve from cache
      val data: GenotypeDataset = sc.loadPartitionedParquetGenotypes(fp)
      //        case Some(ds) => { // if dataset found in datasetCache
      //          ds
      //        }
      //        case _ => {
      //          // load dataset into cache and use use it
      //          datasetCache(fp) = sc.loadPartitionedParquetGenotypes(fp)
      //          datasetCache(fp)
      //        }
      //      } TODO

      val maybeFiltered: GenotypeDataset = if (finalRegions.nonEmpty) {
        data.filterByOverlappingRegions(finalRegions)
      } else data

      maybeFiltered.toVariantContexts()

    } else {
      val pred = {
        val prefixRegions: Iterable[ReferenceRegion] = regions.map(r => LazyMaterialization.getReferencePredicate(r)).flatten
        Some(ResourceUtils.formReferenceRegionPredicate(prefixRegions))
      }
      sc.loadParquetGenotypes(fp, optPredicate = pred).toVariantContexts()

    }
    variantContext
  }
}