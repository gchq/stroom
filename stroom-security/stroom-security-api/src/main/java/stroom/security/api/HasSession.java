package stroom.security.api;

public interface HasSession extends HasSessionId {

    void invalidateSession();

    /**
     * Remove this {@link UserIdentity} from the HTTP session. This will require any future requests
     * to re-authenticate with the IDP.
     */
    void removeUserFromSession();

    /**
     * @return True if this {@link UserIdentity} has a session and is an attribute value in that session
     */
    boolean isInSession();
}
