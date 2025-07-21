package stroom.util.cert;

/**
 * The format of the Distinguished Name as used in LDAP or X509 certificates.
 */
public enum DNFormat {
    /**
     * Legacy OpenSSL '/' delimited DN format.
     */
    OPEN_SSL("/"),
    /**
     * LDAP ',' delimited DN format.
     */
    LDAP(","),
    ;

    private final String delimiter;

    DNFormat(String delimiter) {
        this.delimiter = delimiter;
    }

    public String getDelimiter() {
        return this.delimiter;
    }
}
