package stroom.security.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.impl.exception.AuthenticationException;
import stroom.security.shared.AppPermissionResource;
import stroom.security.shared.User;
import stroom.security.shared.UserAndPermissions;

import javax.inject.Inject;

class AppPermissionResourceImpl implements AppPermissionResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(AppPermissionResourceImpl.class);

    private final SecurityContext securityContext;
    private final UserAndPermissionsHelper userAndPermissionsHelper;
    private final AuthenticationConfig authenticationConfig;

    @Inject
    AppPermissionResourceImpl(final SecurityContext securityContext,
                              final UserAndPermissionsHelper userAndPermissionsHelper,
                              final AuthenticationConfig authenticationConfig) {
        this.securityContext = securityContext;
        this.userAndPermissionsHelper = userAndPermissionsHelper;
        this.authenticationConfig = authenticationConfig;
    }

    @Override
    public UserAndPermissions getUserAndPermissions() {
        final UserIdentity userIdentity = CurrentUserState.current();
        if (userIdentity == null) {
            return null;
        }
        User user = null;
        if (userIdentity instanceof UserIdentityImpl) {
            user = ((UserIdentityImpl) userIdentity).getUser();
        }
        if (user == null) {
            return null;
        }

        final boolean preventLogin = authenticationConfig.isPreventLogin();
        if (preventLogin) {
            if (!securityContext.isAdmin()) {
                throw new AuthenticationException("Stroom is down for maintenance. Please try again later.");
            }
        }

        return new UserAndPermissions(user.getName(), userAndPermissionsHelper.get(user));
    }
}
