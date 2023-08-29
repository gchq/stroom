package stroom.util.shared;

/**
 * Implementing classes/interfaces can provide a user identity for audit purposes
 */
public interface HasAuditableUserIdentity {

    /**
     * @return The user identity as used for audit purposes, e.g. in DB tables
     * or audit events.
     */
    String getUserIdentityForAudit();

    /**
     * Helper method for deriving an auditable user identity from the unique user name
     * and the possibly not unique and optional display name
     */
    static String fromUserNames(final String name, final String displayName) {
        // GWT so no Objects.requireNonNullElse()
        if (displayName != null) {
            return displayName;
        } else {
            return name;
        }
    }
}
