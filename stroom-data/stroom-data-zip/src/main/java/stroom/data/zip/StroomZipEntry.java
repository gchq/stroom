package stroom.data.zip;

public class StroomZipEntry {

    private final String fullName;
    private final String baseName;
    private final StroomZipFileType stroomZipFileType;

    private StroomZipEntry(final String fullName,
                           final String baseName,
                           final StroomZipFileType stroomZipFileType) {
        this.baseName = baseName;
        this.fullName = fullName;
        this.stroomZipFileType = stroomZipFileType;
    }

    public static StroomZipEntry create(final String baseName,
                                        final StroomZipFileType stroomZipFileType) {
        return create(null, baseName, stroomZipFileType);
    }

    public static StroomZipEntry create(final String fullName,
                                        final String baseName,
                                        final StroomZipFileType stroomZipFileType) {
        String full = fullName;
        if (full == null && baseName != null && stroomZipFileType != null) {
            full = baseName + stroomZipFileType.getExtension();
        }
        return new StroomZipEntry(full, baseName, stroomZipFileType);
    }

    public boolean equalsBaseName(StroomZipEntry other) {
        if (this.baseName == null && other.baseName == null) {
            return false;
        }
        if (this.baseName == null) {
            return this.fullName.startsWith(other.baseName);
        }
        if (other.baseName == null) {
            return other.fullName.startsWith(this.baseName);
        }
        return this.baseName.equals(other.baseName);
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
