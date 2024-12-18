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
}
