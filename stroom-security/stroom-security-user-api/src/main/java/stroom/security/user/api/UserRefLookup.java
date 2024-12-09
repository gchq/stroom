package stroom.security.user.api;

import stroom.util.shared.UserRef;

import java.util.Optional;

public interface UserRefLookup {

    /**
     * Look up a user by their Stroom user uuid.
     * This is user may or may not be enabled and is NOT a deleted user.
     */
    Optional<UserRef> getByUuid(String userUuid);

    /**
     * Lookup the passed userRef and return a fully populated {@link UserRef}.
     * If the userRef cannot be found, the passed {@link UserRef} will be returned
     * unchanged.
     * If null is passed, it will return null.
     */
    UserRef decorate(final UserRef userRef);
}
