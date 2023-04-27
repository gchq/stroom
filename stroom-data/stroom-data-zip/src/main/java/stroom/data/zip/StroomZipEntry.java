package stroom.data.zip;

import stroom.util.io.FileName;

public class StroomZipEntry {

    private static final String SINGLE_ENTRY_ZIP_BASE_NAME = "001";
    public static final String REPO_EXTENSION_DELIMITER = ",";

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
        if (fileName.endsWith(".")) {
            // We can't cope with zip entries that end with `.` as we are splitting base name and extension.
            throw new RuntimeException("Zip entries ending with '.' are not supported");
        }
        if (fn.getExtension().contains(REPO_EXTENSION_DELIMITER)) {
            // We can't cope with zip entries that have extensions that contain `,` as we delimit extensions in the DB.
            throw new RuntimeException("Zip entries with extensions containing ',' are not supported");
        }
        final StroomZipFileType stroomZipFileType = StroomZipFileType.fromExtension(fn.getExtension());
        return new StroomZipEntry(fn.getBaseName(), fn.getFullName(), stroomZipFileType);
    }

    public static StroomZipEntry createFromBaseName(final String baseName, final StroomZipFileType stroomZipFileType) {
        return new StroomZipEntry(baseName, baseName + stroomZipFileType.getDotExtension(), stroomZipFileType);
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
