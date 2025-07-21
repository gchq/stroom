package stroom.security.impl;

import stroom.security.api.SecurityContext;
import stroom.security.api.UserService;
import stroom.security.shared.AppPermission;
import stroom.security.shared.FindUserContext;
import stroom.security.shared.User;
import stroom.security.user.api.UserRefLookup;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Optional;

public class UserRefLookupImpl implements UserRefLookup {

    private final UserCache userCache;
    private final SecurityContext securityContext;
    private final Provider<UserService> userServiceProvider;

    @Inject
    public UserRefLookupImpl(final UserCache userCache,
                             final SecurityContext securityContext,
                             final Provider<UserService> userServiceProvider) {
        this.userCache = userCache;
        this.securityContext = securityContext;
        this.userServiceProvider = userServiceProvider;
    }

    @Override
    public Optional<UserRef> getByUuid(final String userUuid,
                                       final FindUserContext context) {
        // If the current user has manage users permission then hit the cache to get user info.
        if (securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)) {
            return userCache.getByUuid(userUuid).map(User::asRef);
        }
        // The current user is not allowed to manage users so limit user visibility.
        return Optional.ofNullable(userServiceProvider.get().getUserByUuid(userUuid, context));
    }
}
