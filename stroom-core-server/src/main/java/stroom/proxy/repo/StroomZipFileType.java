package stroom.proxy.repo;

public enum StroomZipFileType {
    Data(".dat"), Context(".ctx"), Meta(".meta"), Manifest(".mf");

    private final String extValue;

    StroomZipFileType(final String extValue) {
        this.extValue = extValue;
    }

    public String getExtension() {
        return extValue;
    }
}
