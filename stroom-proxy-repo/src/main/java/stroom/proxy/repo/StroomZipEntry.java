package stroom.proxy.repo;

public class StroomZipEntry {
    private final String baseName;
    private final String fullName;
    private final StroomZipFileType stroomZipFileType;

    public StroomZipEntry(String fullName, final String baseName, final StroomZipFileType stroomZipFileType) {
        this.baseName = baseName;
        this.stroomZipFileType = stroomZipFileType;
        if (fullName == null && baseName != null && stroomZipFileType != null) {
            this.fullName = baseName + stroomZipFileType.getExtension();
        } else {
            this.fullName = fullName;
        }
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
