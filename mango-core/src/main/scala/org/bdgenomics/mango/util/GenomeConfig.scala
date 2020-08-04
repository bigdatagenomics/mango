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

package org.bdgenomics.mango.core.util

import java.io._
import java.nio.file.{ Path, Files }
import java.util.Properties
import java.util.stream.Collectors
import ga4gh.SequenceAnnotations
import org.bdgenomics.adam.models.{ ReferenceRegion, SequenceDictionary, SequenceRecord }
import java.net.URL
import scala.io.Source
import sys.process._
import grizzled.slf4j.Logging
import java.util.zip.{ ZipEntry, ZipOutputStream, ZipFile, ZipInputStream, GZIPInputStream }

import org.bdgenomics.utils.interval.array.IntervalArray

/**
 * Genome for a specific build.
 *
 * @param id id ie (hg19, mm10, etc.)
 * @param twoBitPath Remote path to twobit file
 * @param genes IntervalArray of genes stored in ga4gh features, indexed by reference region
 * @param chromSizes SequenceDictionary of chromosome sizes
 */
case class Genome(id: String, twoBitPath: String, genes: Option[IntervalArray[ReferenceRegion, SequenceAnnotations.Feature]], chromSizes: SequenceDictionary)

/**
 * GenomeConfig stores information about current build in mango-cli.
 * GenomeConfig loads a zipped .genome file, containing:
 * - properties.txt file: contains metadata for reference, genes, chromsizes, and cytoband
 * - chrom.sizes file: downloaded from http://hgdownload.cse.ucsc.edu/goldenPath/${GENOME}/bigZips/${GENOME}.chrom.sizes
 * - cytoBand.txt file: downloaded from http://hgdownload.cse.ucsc.edu/goldenpath/${GENOME}/database/cytoBand.txt.gz
 * - refSeq.txt file: downloaded from http://hgdownload.soe.ucsc.edu/goldenPath/${GENOME}/database/refGene.txt.gz
 */
object GenomeConfig extends Logging {

  // File names/prefixes in zip
  private val PROPERTIES_FILE = "properties.txt"
  private val CHROMSIZES_SUFFIX = ".chrom.sizes"
  private val REFSEQ_FILE = "refGene.txt.gz"
  private val CYTOBAND_FILE = "cytoband.txt.gz"

  // properties file keys
  // remote files
  private val TWOBIT_KEY = "sequenceLocation"

  // local files in zipped genome
  private val CHROMSIZES_KEY = "chromSizes"
  private val REFGENE_KEY = "refGene"
  private val CYTOBAND_KEY = "cytoband"

  /**
   * Downloads, zips, and saves a new genome.
   * @param genome name of genome to be downloaded from UCSC downloads (see http://hgdownload.cse.ucsc.edu/downloads.html for available genomes)
   * @param outputPath path to save zipped genome to
   */
  def saveZippedGenome(genome: String, outputPath: String): Unit = {

    // make new .genome directory
    val path = s"${outputPath}/${genome}.genome"
    val tmpPath = Files.createTempDirectory(s"${genome}.genome").toFile.getAbsolutePath

    val outputDir = new File(tmpPath)
    outputDir.mkdirs()

    // specify build
    val sequenceLocation = s"http://hgdownload.cse.ucsc.edu/goldenPath/${genome}/bigZips/${genome}.2bit"

    // Should throw FileNotFoundException if 2bit does not exist
    try {
      new URL(sequenceLocation).getContent()
    }

    // download chromsizes to output
    // Should throw FileNotFoundException if chrom.sizes does not exist
    new URL(s"http://hgdownload.cse.ucsc.edu/goldenPath/${genome}/bigZips/${genome}.chrom.sizes") #> new File(s"${tmpPath}/${genome}.chrom.sizes") !!

    // write properties file
    val propertiesFile = new File(s"${outputDir}/${PROPERTIES_FILE}")
    val bw = new BufferedWriter(new FileWriter(propertiesFile))

    bw.write(s"${TWOBIT_KEY}=${sequenceLocation}\n")
    bw.write(s"${CHROMSIZES_KEY}=${genome}${CHROMSIZES_SUFFIX}\n")

    // try getting genes
    val geneUrl = s"http://hgdownload.soe.ucsc.edu/goldenPath/${genome}/database/refGene.txt.gz"
    val xenoGeneUrl = s"http://hgdownload.soe.ucsc.edu/goldenPath/${genome}/database/xenoRefGene.txt.gz"
    val out = s"${tmpPath}/refGene.txt.gz"

    try {

      new URL(geneUrl) #> new File(out) !!

      bw.write(s"${REFGENE_KEY}=${REFSEQ_FILE}\n")

    } catch {
      case e: Exception => {
        try {

          new URL(xenoGeneUrl) #> new File(out) !!

          bw.write(s"${REFGENE_KEY}=${REFSEQ_FILE}\n")
        } catch {
          case e: Exception => {
            logger.warn(s"No refGene file found at ${geneUrl} or ${xenoGeneUrl}")
          }
        }

      }
    }

    // try getting cytoband file
    val cytobandUrl = s"http://hgdownload.cse.ucsc.edu/goldenpath/${genome}/database/cytoBand.txt.gz"

    try {
      val out = s"${tmpPath}/cytoBand.txt.gz"
      new URL(cytobandUrl) #> new File(out) !!

      bw.write(s"${CYTOBAND_KEY}=${CYTOBAND_FILE}\n")
    } catch {
      case e: Exception => {
        logger.warn(s"No refGene file found at ${cytobandUrl} for genome ${genome}")
      }
    }

    bw.flush()
    bw.close()

    // zip temporary path
    zip(tmpPath, path)
    // delete tmp path
    deleteDirectory(tmpPath)
  }

  /**
   * Loads zipped genome of form {GENOME}.genome.
   *
   * @param zippedConfigPath absolute path to genome
   * @return Genome object
   */
  def loadZippedGenome(zippedConfigPath: String): Genome = {

    // parse genome id from config path. ie mm10, hg19, etc.
    val id = zippedConfigPath.split("/").last.split("\\.").head

    val zipFile = new ZipFile(zippedConfigPath)

    // read in zip file
    val fileInputStream = new FileInputStream(zippedConfigPath)
    val zipInputStream = new ZipInputStream(fileInputStream)
    var zipEntry = zipInputStream.getNextEntry()

    var twoBitPath: String = null

    var genes: Option[IntervalArray[ReferenceRegion, SequenceAnnotations.Feature]] = None
    var chromSizes: SequenceDictionary = null

    while (zipEntry != null) {

      // read properties file
      if (zipEntry.getName.contains(PROPERTIES_FILE)) {

        // parse properties from compressed properties file
        val inputStream = zipFile.getInputStream(zipEntry)
        val properties = new Properties()
        properties.load(inputStream)

        twoBitPath = properties.getProperty(TWOBIT_KEY)

        inputStream.close()

        // read chromsizes  file
      } else if (zipEntry.getName.contains(s"${id}${CHROMSIZES_SUFFIX}")) {

        val inputStream = zipFile.getInputStream(zipEntry)
        val reader = new BufferedReader(new InputStreamReader(inputStream))

        chromSizes = loadChromSizes(reader)

        inputStream.close()

        // read refseq file
      } else if (zipEntry.getName.contains(REFSEQ_FILE)) {

        val inputStream = zipFile.getInputStream(zipEntry)
        val in = new GZIPInputStream(inputStream)

        val lines = Source.fromInputStream(in).getLines()

        // map to reference region for interval array
        // sort array
        val geneArray = RefSeqFile.strToGenes(lines).map(r => (ReferenceRegion(r.getReferenceName, r.getStart, r.getEnd), r))
          .sortBy(_._1)

        // get max gene width for intervalArray
        val maxWidth = geneArray.map(r => r._1.length()).max

        genes = Some(IntervalArray(geneArray, maxWidth, true))

        // close streams
        inputStream.close()
        in.close()
      }

      zipEntry = zipInputStream.getNextEntry
    }

    // close input streams
    fileInputStream.close()
    zipInputStream.close()

    if (twoBitPath == null || chromSizes == null) {
      throw new RuntimeException(s"Genome file ${id} was malformed.")
    }

    new Genome(id, twoBitPath, genes, chromSizes)

  }

  /**
   * Load chrom.sizes text file from a buffered reader.
   *
   * @param reader BufferedReader
   * @return SequenceDictionary of chromosomes and sizes
   */
  private def loadChromSizes(reader: BufferedReader): SequenceDictionary = {
    // parse records from file
    val lines: String = reader.lines().collect(Collectors.joining("\n"))

    val sequenceRecords =
      lines.split("\n").map(line => {
        SequenceRecord(line.split("\t")(0), line.split("\t")(1).toLong)
      })

    // load sequence records into globalDict
    new SequenceDictionary(sequenceRecords.toVector)
  }

  /**
   * Deletes directory.
   *
   * @param directory absolute path to directory
   */
  private def deleteDirectory(directory: String): Unit = {
    val dir = scala.reflect.io.File(directory)
    if (dir.isDirectory && dir.exists) {
      dir.deleteRecursively()
    }
  }

  /**
   * Gets list of files in directory.
   *
   * @param dir absolute path to retrieve files from
   * @return Iterable of files in directory
   */
  private def getListOfFiles(dir: Path): Iterable[File] = {
    val d = dir.toFile
    if (d.exists && d.isDirectory) {
      d.listFiles.filter(_.isFile)
    } else {
      Iterable[File]()
    }
  }

  /**
   * Zips path
   *
   * @param inPath input path
   * @param outPath output path
   */
  private def zip(inPath: String, outPath: String) = {

    val in = new File(inPath).toPath
    val out = new File(outPath).toPath

    val zip = new ZipOutputStream(Files.newOutputStream(out))

    val files = getListOfFiles(in)

    files.foreach { f =>
      val file = f.toPath

      zip.putNextEntry(new ZipEntry(f.getName()))
      Files.copy(file, zip)
      zip.closeEntry()
    }
    zip.close()
  }

}
