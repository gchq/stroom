package stroom.data.zip;

public enum StroomZipFileType {
    MANIFEST(1, ".mf", new String[]{".mf", ".manifest"}),
    META(2, ".meta", new String[]{".hdr", ".header", ".meta", ".met"}),
    CONTEXT(3, ".ctx", new String[]{".ctx", ".context"}),
    DATA(4, ".dat", new String[]{".dat"});

    private final int id;
    private final String extValue;
    private final String[] recognisedExtensions;

    StroomZipFileType(final int id,
                      final String extValue,
                      final String[] recognisedExtensions) {
        this.id = id;
        this.extValue = extValue;
        this.recognisedExtensions = recognisedExtensions;
    }

    public int getId() {
        return id;
    }

    public String getExtension() {
        return extValue;
    }

    public String[] getRecognisedExtensions() {
        return recognisedExtensions;
    }
}
