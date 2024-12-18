package stroom.security.shared;

import stroom.util.shared.UserRef;

/**
 * The identity of a user
 */
public interface HasUserRef {

    /**
     * @return The UserRef that identifies a user in Stroom for authorisation purposes, as
     * distinct from their unique identifier on an IDP.
     */
    UserRef getUserRef();
}
