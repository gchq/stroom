package stroom.security.user.api;

import stroom.util.shared.UserRef;

import java.util.Optional;

public interface UserRefLookup {

    /**
     * Look up a user by their Stroom user uuid.
     */
    Optional<UserRef> getByUuid(String userUuid);
}
