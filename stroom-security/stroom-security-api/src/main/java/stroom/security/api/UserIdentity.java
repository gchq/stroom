package stroom.security.api;

import java.util.Optional;

public interface UserIdentity {

    /**
     * @return The unique identifier for the user. In the case of an Open ID Connect user
     * this would be the subject (sub) from the token which is normally in the form of a UUID.
     * For the internal IDP this would likely be a more human friendly username.
     */
    String getId();

    /**
     * @return The non-unique username for the user, e.g. 'jbloggs'. In the absence of a specific
     * value this should return the id.
     */
    default String getPreferredUsername() {
        return getId();
    }

    /**
     * @return The user's full name if known, e.g. 'Joe Bloggs'.
     */
    default Optional<String> getFullName() {
        return Optional.empty();
    }
}
