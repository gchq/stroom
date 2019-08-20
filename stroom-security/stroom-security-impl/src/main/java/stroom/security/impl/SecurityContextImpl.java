package stroom.security.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserTokenUtil;
import stroom.security.impl.exception.AuthenticationException;
import stroom.security.shared.DocumentPermissionNames;
import stroom.security.shared.PermissionException;
import stroom.security.shared.PermissionNames;
import stroom.security.shared.User;
import stroom.security.shared.UserAppPermissions;
import stroom.security.shared.UserToken;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@Singleton
class SecurityContextImpl implements SecurityContext {
    private final ThreadLocal<Boolean> checkTypeThreadLocal = ThreadLocal.withInitial(() -> Boolean.TRUE);

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityContextImpl.class);
    private static final String USER = "user";
    private static final UserToken INTERNAL_PROCESSING_USER_TOKEN = UserTokenUtil.processingUser();
    private static final User INTERNAL_PROCESSING_USER = new User.Builder()
            .id(0)
            .uuid("0")
            .name(INTERNAL_PROCESSING_USER_TOKEN.getUserId())
            .group(false)
            .build();

    private final UserDocumentPermissionsCache userDocumentPermissionsCache;
    private final UserGroupsCache userGroupsCache;
    private final UserAppPermissionsCache userAppPermissionsCache;
    private final UserCache userCache;
    private final ApiTokenCache apiTokenCache;

    @Inject
    SecurityContextImpl(
            final UserDocumentPermissionsCache userDocumentPermissionsCache,
            final UserGroupsCache userGroupsCache,
            final UserAppPermissionsCache userAppPermissionsCache,
            final UserCache userCache,
            final ApiTokenCache apiTokenCache) {
        this.userDocumentPermissionsCache = userDocumentPermissionsCache;
        this.userGroupsCache = userGroupsCache;
        this.userAppPermissionsCache = userAppPermissionsCache;
        this.userCache = userCache;
        this.apiTokenCache = apiTokenCache;
    }

    User getUser() {
        return CurrentUserState.currentUser();
    }

    @Override
    public UserToken getUserToken() {
        return CurrentUserState.currentUserToken();
    }

    @Override
    public String getUserId() {
        final User userRef = getUser();
        if (userRef == null) {
            return null;
        }
        return userRef.getName();
    }

    @Override
    public String getApiToken() {
        return apiTokenCache.get(getUserId());
    }

    @Override
    public boolean isLoggedIn() {
        return getUser() != null;
    }

    @Override
    public boolean isAdmin() {
        return hasAppPermission(PermissionNames.ADMINISTRATOR);
    }

    private void pushUser(final UserToken token) {
        User userRef = null;

        if (token != null) {
            final String type = token.getType();
            final String name = token.getUserId();

            if (INTERNAL_PROCESSING_USER_TOKEN.getType().equals(type)) {
                if (INTERNAL_PROCESSING_USER_TOKEN.getUserId().equals(name)) {
                    userRef = INTERNAL_PROCESSING_USER;
                } else {
                    LOGGER.error("Unexpected system user '" + name + "'");
                    throw new AuthenticationException("Unexpected system user '" + name + "'");
                }
            } else if (USER.equals(type)) {
                if (name.length() > 0) {
                    final Optional<User> optional = userCache.get(name);
                    if (optional.isEmpty()) {
                        final String message = "Unable to push user '" + name + "' as user is unknown";
                        LOGGER.error(message);
                        throw new AuthenticationException(message);
                    } else {
                        userRef = optional.get();
                    }
                }
            } else {
                LOGGER.error("Unexpected token type '" + type + "'");
                throw new AuthenticationException("Unexpected token type '" + type + "'");
            }
        }

        CurrentUserState.push(token, userRef);
    }

    private void popUser() {
        CurrentUserState.pop();
    }

    private void elevatePermissions() {
        CurrentUserState.elevatePermissions();
    }

    private void restorePermissions() {
        CurrentUserState.restorePermissions();
    }

    @Override
    public boolean hasAppPermission(final String permission) {
        // Get the current user.
        final User userRef = getUser();

        // If there is no logged in user then throw an exception.
        if (userRef == null) {
            throw new AuthenticationException("No user is currently logged in");
        }

        // If the user is the internal processing user then they automatically have permission.
        if (INTERNAL_PROCESSING_USER.equals(userRef)) {
            return true;
        }

        // See if the user has permission.
        boolean result = hasAppPermission(userRef, permission);

        // If the user doesn't have the requested permission see if they are an admin.
        if (!result && !PermissionNames.ADMINISTRATOR.equals(permission)) {
            result = hasAppPermission(userRef, PermissionNames.ADMINISTRATOR);
        }

        return result;
    }

    private boolean hasAppPermission(final User userRef, final String permission) {
        // See if the user has an explicit permission.
        if (hasUserAppPermission(userRef, permission)) {
            return true;
        }

        // See if the user belongs to a group that has permission.
        final List<User> userGroups = userGroupsCache.get(userRef.getUuid());
        if (userGroups != null) {
            for (final User userGroup : userGroups) {
                if (hasUserAppPermission(userGroup, permission)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasUserAppPermission(final User userRef, final String permission) {
        final UserAppPermissions userAppPermissions = userAppPermissionsCache.get(userRef);
        if (userAppPermissions != null) {
            return userAppPermissions.getUserPermissons().contains(permission);
        }
        return false;
    }

    @Override
    public boolean hasDocumentPermission(final String documentType, final String documentUuid, final String permission) {
        // Let administrators do anything.
        if (isAdmin()) {
            return true;
        }

        // Get the current user.
        final User userRef = getUser();

        // If there is no logged in user then throw an exception.
        if (userRef == null) {
            throw new AuthenticationException("No user is currently logged in");
        }

        // If we are currently allowing users with only `Use` permission to `Read` (elevate permissions) then test for `Use` instead of `Read`.
        String perm = permission;
        if (CurrentUserState.isElevatePermissions() && DocumentPermissionNames.READ.equals(perm)) {
            perm = DocumentPermissionNames.USE;
        }

        return hasDocumentPermission(userRef, documentUuid, perm);
    }

    private boolean hasDocumentPermission(final User userRef, final String documentUuid, final String permission) {
        // See if the user has an explicit permission.
        if (hasUserDocumentPermission(userRef.getUuid(), documentUuid, permission)) {
            return true;
        }

        // See if the user belongs to a group that has permission.
        final List<User> userGroups = userGroupsCache.get(userRef.getUuid());
        if (userGroups != null) {
            for (final User userGroup : userGroups) {
                if (hasUserDocumentPermission(userGroup.getUuid(), documentUuid, permission)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasUserDocumentPermission(final String userUuid, final String documentUuid, final String permission) {
        final UserDocumentPermissions userDocumentPermissions = userDocumentPermissionsCache.get(userUuid);
        if (userDocumentPermissions != null) {
            return userDocumentPermissions.hasDocumentPermission(documentUuid, permission);
        }
        return false;
    }

    /**
     * Run the supplied code as the specified user.
     */
    @Override
    public <T> T asUserResult(final UserToken userToken, final Supplier<T> supplier) {
        T result;
        boolean success = false;
        try {
            pushUser(userToken);
            success = true;
            result = supplier.get();
        } finally {
            if (success) {
                popUser();
            }
        }
        return result;
    }

    /**
     * Run the supplied code as the specified user.
     */
    @Override
    public void asUser(final UserToken userToken, final Runnable runnable) {
        boolean success = false;
        try {
            pushUser(userToken);
            success = true;
            runnable.run();
        } finally {
            if (success) {
                popUser();
            }
        }
    }

    /**
     * Run the supplied code as the internal processing user.
     */
    @Override
    public <T> T asProcessingUserResult(final Supplier<T> supplier) {
        T result;
        boolean success = false;
        try {
            pushUser(UserTokenUtil.processingUser());
            success = true;
            result = supplier.get();
        } finally {
            if (success) {
                popUser();
            }
        }
        return result;
    }

    /**
     * Run the supplied code as the internal processing user.
     */
    @Override
    public void asProcessingUser(final Runnable runnable) {
        boolean success = false;
        try {
            pushUser(UserTokenUtil.processingUser());
            success = true;
            runnable.run();
        } finally {
            if (success) {
                popUser();
            }
        }
    }

    /**
     * Allow the current user to read items that they only have 'Use' permission on.
     */
    @Override
    public <T> T useAsReadResult(final Supplier<T> supplier) {
        T result;
        boolean success = false;
        try {
            elevatePermissions();
            success = true;
            result = supplier.get();
        } finally {
            if (success) {
                restorePermissions();
            }
        }
        return result;
    }

    /**
     * Allow the current user to read items that they only have 'Use' permission on.
     */
    @Override
    public void useAsRead(final Runnable runnable) {
        boolean success = false;
        try {
            elevatePermissions();
            success = true;
            runnable.run();
        } finally {
            if (success) {
                restorePermissions();
            }
        }
    }

    /**
     * Secure the supplied code with the supplied application permission.
     */
    @Override
    public void secure(final String permission, final Runnable runnable) {
        // Initiate current check type.
        final Boolean currentCheckType = checkTypeThreadLocal.get();

        // If we aren't currently checking anything then just proceed.
        if (Boolean.FALSE.equals(currentCheckType)) {
            runnable.run();
        } else {
            // If the current user is an administrator then don't do any security checking.
            if (permission == null || checkAdmin()) {
                try {
                    // Don't check any further permissions.
                    checkTypeThreadLocal.set(Boolean.FALSE);
                    runnable.run();
                } finally {
                    // Reset the current check type.
                    checkTypeThreadLocal.set(currentCheckType);
                }
            } else {
                // We must be logged in to access a secured service.
                checkLogin();
                checkAppPermission(permission);

                runnable.run();
            }
        }
    }

    /**
     * Secure the supplied code with the supplied application permission.
     */
    @Override
    public <T> T secureResult(final String permission, final Supplier<T> supplier) {
        T result;

        // Initiate current check type.
        final Boolean currentCheckType = checkTypeThreadLocal.get();

        // If we aren't currently checking anything then just proceed.
        if (Boolean.FALSE.equals(currentCheckType)) {
            result = supplier.get();
        } else {
            // If the current user is an administrator then don't do any security checking.
            if (permission == null || checkAdmin()) {
                try {
                    // Don't check any further permissions.
                    checkTypeThreadLocal.set(Boolean.FALSE);
                    result = supplier.get();
                } finally {
                    // Reset the current check type.
                    checkTypeThreadLocal.set(currentCheckType);
                }
            } else {
                // We must be logged in to access a secured service.
                checkLogin();
                checkAppPermission(permission);

                result = supplier.get();
            }
        }

        return result;
    }

    /**
     * Secure the supplied code to ensure that there is a current authenticated user.
     */
    @Override
    public void secure(final Runnable runnable) {
        // Initiate current check type.
        final Boolean currentCheckType = checkTypeThreadLocal.get();

        // If we aren't currently checking anything then just proceed.
        if (Boolean.FALSE.equals(currentCheckType)) {
            runnable.run();
        } else {
            // If the current user is an administrator then don't do any security checking.
            if (checkAdmin()) {
                try {
                    // Don't check any further permissions.
                    checkTypeThreadLocal.set(Boolean.FALSE);
                    runnable.run();
                } finally {
                    // Reset the current check type.
                    checkTypeThreadLocal.set(currentCheckType);
                }
            } else {
                // We must be logged in to access a secured service.
                checkLogin();

                runnable.run();
            }
        }
    }

    /**
     * Secure the supplied code to ensure that there is a current authenticated user.
     */
    @Override
    public <T> T secureResult(final Supplier<T> supplier) {
        T result;

        // Initiate current check type.
        final Boolean currentCheckType = checkTypeThreadLocal.get();

        // If we aren't currently checking anything then just proceed.
        if (Boolean.FALSE.equals(currentCheckType)) {
            result = supplier.get();
        } else {
            // If the current user is an administrator then don't do any security checking.
            if (checkAdmin()) {
                try {
                    // Don't check any further permissions.
                    checkTypeThreadLocal.set(Boolean.FALSE);
                    result = supplier.get();
                } finally {
                    // Reset the current check type.
                    checkTypeThreadLocal.set(currentCheckType);
                }
            } else {
                // We must be logged in to access a secured service.
                checkLogin();

                result = supplier.get();
            }
        }

        return result;
    }

    /**
     * Run the supplied code regardless of whether there is a current user and also allow all inner code to run
     * insecurely even if it is often secured when executed from other entry points.
     */
    @Override
    public void insecure(final Runnable runnable) {
        secure(null, runnable);
    }

    /**
     * Run the supplied code regardless of whether there is a current user and also allow all inner code to run
     * insecurely even if it is often secured when executed from other entry points.
     */
    @Override
    public <T> T insecureResult(final Supplier<T> supplier) {
        return secureResult(null, supplier);
    }

    private void checkAppPermission(final String permission) {
        final Boolean currentCheckType = checkTypeThreadLocal.get();
        try {
            // Don't check any further permissions.
            checkTypeThreadLocal.set(Boolean.FALSE);
            if (!hasAppPermission(permission)) {
                throw new PermissionException("User does not have the required permission (" + permission + ")", getUserId());
            }
        } finally {
            checkTypeThreadLocal.set(currentCheckType);
        }
    }

    private void checkLogin() {
        final Boolean currentCheckType = checkTypeThreadLocal.get();
        try {
            // Don't check any further permissions.
            checkTypeThreadLocal.set(Boolean.FALSE);
            if (!isLoggedIn()) {
                throw new PermissionException("A user must be logged in to call service");
            }
        } finally {
            checkTypeThreadLocal.set(currentCheckType);
        }
    }

    private boolean checkAdmin() {
        final Boolean currentCheckType = checkTypeThreadLocal.get();
        try {
            // Don't check any further permissions.
            checkTypeThreadLocal.set(Boolean.FALSE);
            return isAdmin();
        } finally {
            checkTypeThreadLocal.set(currentCheckType);
        }
    }
}
