package stroom.data.zip;

import stroom.util.io.FileName;

public class StroomZipEntry {

    private static final String SINGLE_ENTRY_ZIP_BASE_NAME = "001";

    public static final StroomZipEntry SINGLE_DATA_ENTRY =
            createFromBaseName(SINGLE_ENTRY_ZIP_BASE_NAME, StroomZipFileType.DATA);
    public static final StroomZipEntry SINGLE_META_ENTRY =
            createFromBaseName(SINGLE_ENTRY_ZIP_BASE_NAME, StroomZipFileType.META);

    private final String baseName;
    private final String fullName;
    private final StroomZipFileType stroomZipFileType;

    public StroomZipEntry(final String baseName,
                          final String fullName,
                          final StroomZipFileType stroomZipFileType) {
        this.baseName = baseName;
        this.fullName = fullName;
        this.stroomZipFileType = stroomZipFileType;
    }

    public static StroomZipEntry createFromFileName(final String fileName) {
        final FileName fn = FileName.parse(fileName);
        return new StroomZipEntry(fn.getBaseName(), fn.getFullName(), StroomZipFileType.DATA);
    }

    public static StroomZipEntry createFromBaseName(final String stem, final StroomZipFileType stroomZipFileType) {
        return new StroomZipEntry(stem, stem + stroomZipFileType.getExtension(), stroomZipFileType);
    }

    public String getBaseName() {
        return baseName;
    }

    public StroomZipFileType getStroomZipFileType() {
        return stroomZipFileType;
    }

    public String getFullName() {
        return fullName;
    }

    @Override
    public String toString() {
        return fullName;
    }
}
