package stroom.data.zip;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static java.util.Map.entry;

public enum StroomZipFileType {
    MANIFEST(1, ".mf", new String[]{".mf", ".manifest"}),
    META(2, ".meta", new String[]{".hdr", ".header", ".meta", ".met"}),
    CONTEXT(3, ".ctx", new String[]{".ctx", ".context"}),
    DATA(4, ".dat", new String[]{".dat"});

    private static final Map<String, StroomZipFileType> EXTENSION_MAP = Map.ofEntries(
            entry(".mf", StroomZipFileType.MANIFEST),
            entry(".manifest", StroomZipFileType.MANIFEST),
            entry(".hdr", StroomZipFileType.META),
            entry(".header", StroomZipFileType.META),
            entry(".meta", StroomZipFileType.META),
            entry(".met", StroomZipFileType.META),
            entry(".ctx", StroomZipFileType.CONTEXT),
            entry(".context", StroomZipFileType.CONTEXT),
            entry(".dat", StroomZipFileType.DATA)
    );

    public static final Map<Integer, StroomZipFileType> TYPE_MAP = Map.of(
            MANIFEST.id, MANIFEST,
            META.id, META,
            CONTEXT.getId(), CONTEXT,
            DATA.id, DATA);

    /**
     * We need to be able to sort by type so we hold a numeric id that allows meta to be found before accompanying data.
     */
    private final int id;
    private final String extension;
    private final String[] recognisedExtensions;

    StroomZipFileType(final int id,
                      final String extension,
                      final String[] recognisedExtensions) {
        this.id = id;
        this.extension = extension;
        this.recognisedExtensions = recognisedExtensions;
    }

    /**
     * We need to be able to sort by type so we hold a numeric id that allows meta to be found before accompanying data.
     *
     * @return The id of the type.
     */
    public int getId() {
        return id;
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
     * There is some variation in the extensions used in source files so allow for some alternatives to be recognised.
     *
     * @return An array of some alternative extension names.
     */
    public String[] getRecognisedExtensions() {
        return recognisedExtensions;
    }

    public static StroomZipFileType fromExtension(final String extension) {
        Optional<StroomZipFileType> optional = Optional.empty();
        if (extension != null && !extension.isEmpty()) {
            optional = Optional.ofNullable(EXTENSION_MAP.get(extension.toLowerCase(Locale.ROOT)));
        }
        return optional.orElse(StroomZipFileType.DATA);
    }
}
