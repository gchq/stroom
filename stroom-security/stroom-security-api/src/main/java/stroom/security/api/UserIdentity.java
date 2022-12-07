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
     * value this should just return the id.
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

    // TODO: 28/11/2022 Potentially worth introducing scopes, e.g. a datafeed scope so only tokens
    //  with the datafeed scope can send data. Similarly we could have a scope per resource so people
    //  can create tokens that are very limited in what they can do. May need an 'api-all' scope to
    //  allow people to hit any resource.
//    /**
//     * @return The set of scopes that this user identity has. Scopes add restrictions
//     * on top of the things that a user has permission to do.
//     */
//    default Set<String> getScopes() {
//        return Collections.emptySet();
//    };
}
