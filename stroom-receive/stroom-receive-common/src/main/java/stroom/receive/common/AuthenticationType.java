package stroom.receive.common;

import stroom.docref.HasDisplayValue;

public enum AuthenticationType implements HasDisplayValue {
    DATA_FEED_KEY("Data feed key"),
    TOKEN("OAuth Token or API Key"),
    CERTIFICATE("Client certificate"),
    ;

    private final String displayValue;

    AuthenticationType(final String displayValue) {
        this.displayValue = displayValue;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }

    @Override
    public String toString() {
        return "AuthenticationType{" +
               "displayValue='" + displayValue + '\'' +
               '}';
    }
}
