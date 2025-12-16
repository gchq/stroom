package stroom.credentials.shared;

import stroom.docref.HasDisplayValue;

/**
 * The type of credentials being stored.
 */
public enum CredentialType implements HasDisplayValue {
    USERNAME_PASSWORD("Username / Password"),
    ACCESS_TOKEN("Access Token"),
    KEY_PAIR("Key Pair"),
    KEY_STORE("Key Store");

    private final String displayValue;

    CredentialType(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }
}
