package stroom.security.impl;

import stroom.security.shared.User;
import stroom.security.user.api.UserRefLookup;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;

import java.util.Optional;

public class UserRefLookupImpl implements UserRefLookup {

    private final UserCache userCache;

    @Inject
    public UserRefLookupImpl(final UserCache userCache) {
        this.userCache = userCache;
    }

    @Override
    public Optional<UserRef> getByUuid(final String userUuid) {
        return userCache.getByUuid(userUuid)
                .map(User::asRef);
    }

    @Override
    public UserRef decorate(final UserRef userRef) {
        if (userRef == null) {
            return null;
        } else {
            return userCache.getByRef(userRef)
                    .map(User::asRef)
                    .orElse(userRef);
        }
    }
}
