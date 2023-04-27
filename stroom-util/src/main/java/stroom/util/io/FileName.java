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

    public static FileName fromParts(final String baseName, final String extension) {
        if (baseName == null) {
            if (extension == null || extension.length() == 0) {
                return new FileName("", "", "");
            } else {
                return new FileName("." + extension, "", extension);
            }
        } else {
            if (extension == null || extension.length() == 0) {
                return new FileName(baseName, baseName, "");
            } else {
                return new FileName(baseName + "." + extension, baseName, extension);
            }
        }
    }

    public static FileName parse(final String fileName) {
        Objects.requireNonNull(fileName, "fileName is null");
        final int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex != -1) {
            final String baseName = fileName.substring(0, dotIndex);
            final String extension = fileName.substring(dotIndex + 1);
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
