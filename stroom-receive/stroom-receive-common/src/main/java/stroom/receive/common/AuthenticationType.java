package stroom.receive.common;

import stroom.docref.HasDisplayValue;

public enum AuthenticationType implements HasDisplayValue {
    /**
     * Authenticates using a Stroom Data Feed Key.
     */
    DATA_FEED_KEY("Data feed key"),
    /**
     * An OAuth token or a Stroom API Key
     */
    TOKEN("OAuth Token"),
    /**
     * Either authenticates the X509 certificate on the request
     * or authenticates the DN from a header if .receive.x509CertificateDnHeader is set.
     */
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
