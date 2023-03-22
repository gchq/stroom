package stroom.security.shared;

/**
 * The identity of a
 */
public interface HasStroomUserIdentity {

    /**
     * @return The UUID that identifies a user in Stroom for authorisation purposes, as
     * distinct from their unique identifier on an IDP.
     */
    String getUuid();

    // Maybe we ought to have things like the id (aka name), displayName and fullName
}
