from .genomicfile import GenomicFile


class BedFile(GenomicFile):

    @classmethod
    def _read(cls, filepath_or_buffer, column_names, skiprows):
        return cls.dataframe_lib.read_table(filepath_or_buffer, names=column_names, skiprows=skiprows)

    @classmethod
    def _parse(cls, df):
        #check whether correct column names are passed into dataframe
        df_cols = list(df.columns)
        valid_columns = True
        for name in ("chrom", "chromStart", "chromEnd"):
            if name not in df_cols:
                valid_columns = False

        if not valid_columns:
            #assume no names passed in and take first 3 columns as chrom, chromStart, chromEnd
            chrom, chrom_start, chom_end = df_cols[:3]

            chrom_starts = [int(chrom_start)] + (list(df[chrom_start]))
            chrom_ends = [int(chom_end)] + (list(df[chom_end]))
            chromosomes = [chrom] + (list(df[chrom]))

            return build_json_from_bed(chrom_starts, chrom_ends, chromosomes)
        else:
            chromosomes = list(df["chrom"])
            chrom_starts = list(df["chromStart"])
            chrom_ends = list(df["chromEnd"])
        return (chrom_starts, chrom_ends, chromosomes)

    @classmethod
    def _to_json(cls, df):
        chrom_starts, chrom_ends, chromosomes = cls._parse(df)
        json_ga4gh = "{\"features\":["
        for i in range(len(chromosomes)+1):
            if i < len(chromosomes):
                bed_content = "\"referenceName\":{}, \"start\":{}, \"end\":{}".format("\""+chromosomes[i]+"\"", "\""+str(chrom_starts[i])+"\"", "\""+str(chrom_ends[i])+"\"")
                json_ga4gh = json_ga4gh + "{" + bed_content + "},"
            else:
                json_ga4gh = json_ga4gh[:len(json_ga4gh)-1]


        #ending json
        json_ga4gh = json_ga4gh + "]}"
        return json_ga4gh
    
    @classmethod
    def _visualization(cls, df):
        return 'featureJson'
