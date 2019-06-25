from .genomicfile import GenomicFile


class BedFile(GenomicFile):

    @classmethod
    def _read(cls, filepath_or_buffer, column_names, skiprows):
        return cls.dataframe_lib.read_table(filepath_or_buffer, names=column_names, skiprows=skiprows)

    @classmethod
    def _parse(cls, df):
        raise NotImplementedError("Implement Me!")

    @classmethod
    def _to_json(cls, df):
        raise NotImplementedError("Implement Me!")
