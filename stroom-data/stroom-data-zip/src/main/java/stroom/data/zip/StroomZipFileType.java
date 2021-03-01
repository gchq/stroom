package stroom.data.zip;

public enum StroomZipFileType {
    DATA(".dat"),
    CONTEXT(".ctx"),
    META(".meta"),
    MANIFEST(".mf");

    private final String extValue;

    StroomZipFileType(final String extValue) {
        this.extValue = extValue;
    }

    public String getExtension() {
        return extValue;
    }
}
