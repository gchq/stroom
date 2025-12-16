package stroom.ai.shared;

import stroom.docref.HasDisplayValue;

public enum KeyStoreType implements HasDisplayValue {
    JCEKS("JCEKS"),
    JKS("JKS"),
    DKS("DKS"),
    PKCS11("PKCS11"),
    PKCS12("PKCS12");

    private final String displayValue;

    KeyStoreType(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
