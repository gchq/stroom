package stroom.ai.shared;

import stroom.docref.HasDisplayValue;

public enum KeyStoreType implements HasDisplayValue {
    JCEKS("JCEKS", "jceks"),
    JKS("JKS", "jks"),
    PKCS12("PKCS12", "p12");

    private final String displayValue;
    private final String fileExtension;

    KeyStoreType(final String displayValue,
                 final String fileExtension) {
        this.displayValue = displayValue;
        this.fileExtension = fileExtension;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }

    public String getFileExtension() {
        return fileExtension;
    }
}
