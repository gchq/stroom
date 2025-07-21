package stroom.security.user.api;

import stroom.security.shared.FindUserContext;
import stroom.util.shared.UserRef;

import java.util.Optional;

public interface UserRefLookup {

    /**
     * Look up a user by their Stroom user uuid.
     * This is user may or may not be enabled and is NOT a deleted user.
     */
    default Optional<UserRef> getByUuid(final String userUuid) {
        return getByUuid(userUuid, null);
    }

    /**
     * Look up a user by their Stroom user uuid but limit to the context of the lookup.
     */
    Optional<UserRef> getByUuid(String userUuid, FindUserContext context);
}
