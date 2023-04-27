package stroom.util.io;

import java.util.Objects;

public class FileName {

    private final String fullName;
    private final String baseName;
    private final String extension;

    private FileName(final String fullName, final String baseName, final String extension) {
        this.fullName = fullName;
        this.baseName = baseName;
        this.extension = extension;
    }

    public static FileName parse(final String fileName) {
        Objects.requireNonNull(fileName, "fileName is null");
        final int i = fileName.lastIndexOf('.');
        if (i != -1) {
            final String baseName = fileName.substring(0, i);
            final String extension = fileName.substring(i);
            return new FileName(fileName, baseName, extension);
        }
        return new FileName(fileName, fileName, "");
    }

    public String getFullName() {
        return fullName;
    }

    public String getBaseName() {
        return baseName;
    }

    public String getExtension() {
        return extension;
    }
}
