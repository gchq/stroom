package stroom.data.zip;

public enum StroomZipFileType {
    MANIFEST(1, ".mf", new String[]{".mf", ".manifest"}),
    META(2, ".meta", new String[]{".hdr", ".header", ".meta", ".met"}),
    CONTEXT(3, ".ctx", new String[]{".ctx", ".context"}),
    DATA(4, ".dat", new String[]{".dat"});

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
}
