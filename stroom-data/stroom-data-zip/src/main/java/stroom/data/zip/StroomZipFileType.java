package stroom.data.zip;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static java.util.Map.entry;

public enum StroomZipFileType {
    MANIFEST(0, "mf"),
    META(1, "meta"),
    CONTEXT(2, "ctx"),
    DATA(3, "dat");

    private static final Map<String, StroomZipFileType> EXTENSION_MAP = Map.ofEntries(
            entry("mf", StroomZipFileType.MANIFEST),
            entry("manifest", StroomZipFileType.MANIFEST),
            entry("hdr", StroomZipFileType.META),
            entry("header", StroomZipFileType.META),
            entry("meta", StroomZipFileType.META),
            entry("met", StroomZipFileType.META),
            entry("ctx", StroomZipFileType.CONTEXT),
            entry("context", StroomZipFileType.CONTEXT),
            entry("dat", StroomZipFileType.DATA)
    );

    private final int index;
    private final String extension;

    StroomZipFileType(final int index,
                      final String extension) {
        this.index = index;
        this.extension = extension;
    }

    public int getIndex() {
        return index;
    }

    /**
     * The official extension for the file type.
     *
     * @return The official extension for the file type.
     */
    public String getExtension() {
        return extension;
    }

    /**
     * Convenience method to add `.`
     */
    public String getDotExtension() {
        return "." + extension;
    }

    public static StroomZipFileType fromExtension(final String extension) {
        Optional<StroomZipFileType> optional = Optional.empty();
        if (extension != null && !extension.isEmpty()) {
            optional = Optional.ofNullable(EXTENSION_MAP.get(extension.toLowerCase(Locale.ROOT)));
        }
        return optional.orElse(StroomZipFileType.DATA);
    }
}
