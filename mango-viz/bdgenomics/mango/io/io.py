from .bedfile import BedFile


def read_bed(filepath_or_buffer,
    column_names=["chrom","chromStart", "chromEnd", "name", "score",
    "strand", "thickStart", "thickEnd", "itemRGB", "blockCount",
    "blockSizes", "blockStarts"],
    skiprows=None
 ):
   return BedFile.read(filepath_or_buffer, column_names, skiprows)
