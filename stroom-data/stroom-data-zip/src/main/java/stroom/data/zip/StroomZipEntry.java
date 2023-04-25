package stroom.data.zip;

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
        final int index = fileName.lastIndexOf(".");
        if (index != -1) {
            final String stem = fileName.substring(0, index);
            final String extension = fileName.substring(index);
            return new StroomZipEntry(stem, fileName, StroomZipFileType.fromExtension(extension));
        }
        return new StroomZipEntry(fileName, fileName, StroomZipFileType.DATA);
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
