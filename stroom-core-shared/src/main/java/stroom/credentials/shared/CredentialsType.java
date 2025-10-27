package stroom.credentials.shared;

/**
 * The type of credentials being stored.
 */
public enum CredentialsType {
    USERNAME_PASSWORD("Username / Password"),
    ACCESS_TOKEN("Access Token"),
    PRIVATE_CERT("Private Key");

    /** What to display in drop-down lists */
    private final String displayName;

    /** Private constructor */
    CredentialsType(final String displayName) {
        this.displayName = displayName;
    }

    /** Returns the name to display in drop-down lists */
    public String getDisplayName() {
        return displayName;
    }
}
