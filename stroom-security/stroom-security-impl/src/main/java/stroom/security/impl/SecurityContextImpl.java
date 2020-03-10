package stroom.security.impl;

import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.impl.exception.AuthenticationException;
import stroom.security.shared.DocumentPermissionNames;
import stroom.security.shared.PermissionException;
import stroom.security.shared.PermissionNames;
import stroom.security.shared.User;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

@Singleton
class SecurityContextImpl implements SecurityContext {
    private final ThreadLocal<Boolean> checkTypeThreadLocal = ThreadLocal.withInitial(() -> Boolean.TRUE);

    private final UserDocumentPermissionsCache userDocumentPermissionsCache;
    private final UserGroupsCache userGroupsCache;
    private final UserAppPermissionsCache userAppPermissionsCache;
    private final UserCache userCache;

    @Inject
    SecurityContextImpl(
            final UserDocumentPermissionsCache userDocumentPermissionsCache,
            final UserGroupsCache userGroupsCache,
            final UserAppPermissionsCache userAppPermissionsCache,
            final UserCache userCache) {
        this.userDocumentPermissionsCache = userDocumentPermissionsCache;
        this.userGroupsCache = userGroupsCache;
        this.userAppPermissionsCache = userAppPermissionsCache;
        this.userCache = userCache;
    }

    @Override
    public String getUserId() {
        final UserIdentity userIdentity = getUserIdentity();
        if (userIdentity == null) {
            return null;
        }
        return userIdentity.getId();
    }

    @Override
    public UserIdentity getUserIdentity() {
        return CurrentUserState.current();
    }

    @Override
    public UserIdentity createIdentity(final String userId) {
        Objects.requireNonNull(userId, "Null user id provided");
        final Optional<User> optional = userCache.get(userId);
        if (optional.isEmpty()) {
            throw new AuthenticationException("Unable to find user with id=" + userId);
        }
        return new UserIdentityImpl(optional.get(), userId, null, null);
    }

    @Override
    public boolean isLoggedIn() {
        return getUserIdentity() != null;
    }

    @Override
    public boolean isAdmin() {
        return hasAppPermission(PermissionNames.ADMINISTRATOR);
    }

    User getUser(final UserIdentity userIdentity) {
        if (!(userIdentity instanceof UserIdentityImpl)) {
            throw new AuthenticationException("Expecting a real user identity");
        }
        return ((UserIdentityImpl) userIdentity).getUser();
    }

    private void pushUser(final UserIdentity userIdentity) {
        CurrentUserState.push(userIdentity);
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
        final UserIdentity userIdentity = getUserIdentity();

        // If there is no logged in user then throw an exception.
        if (userIdentity == null) {
            throw new AuthenticationException("No user is currently logged in");
        }

        // If the user is the internal processing user then they automatically have permission.
        if (ProcessingUserIdentity.INSTANCE.equals(userIdentity)) {
            return true;
        }

        // See if the user has permission.
        final User user = getUser(userIdentity);
        boolean result = hasAppPermission(user, permission);

        // If the user doesn't have the requested permission see if they are an admin.
        if (!result && !PermissionNames.ADMINISTRATOR.equals(permission)) {
            result = hasAppPermission(user, PermissionNames.ADMINISTRATOR);
        }

        return result;
    }

    private boolean hasAppPermission(final User user, final String permission) {
        // See if the user has an explicit permission.
        if (hasUserAppPermission(user, permission)) {
            return true;
        }

        // See if the user belongs to a group that has permission.
        final List<User> userGroups = userGroupsCache.get(user.getUuid());
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
        final UserIdentity userIdentity = getUserIdentity();

        // If there is no logged in user then throw an exception.
        if (userIdentity == null) {
            throw new AuthenticationException("No user is currently logged in");
        }

        // If we are currently allowing users with only `Use` permission to `Read` (elevate permissions) then test for `Use` instead of `Read`.
        String perm = permission;
        if (CurrentUserState.isElevatePermissions() && DocumentPermissionNames.READ.equals(perm)) {
            perm = DocumentPermissionNames.USE;
        }

        final User user = getUser(userIdentity);
        return hasDocumentPermission(user, documentUuid, perm);
    }

    private boolean hasDocumentPermission(final User user, final String documentUuid, final String permission) {
        // See if the user has an explicit permission.
        if (hasUserDocumentPermission(user.getUuid(), documentUuid, permission)) {
            return true;
        }

        // See if the user belongs to a group that has permission.
        final List<User> userGroups = userGroupsCache.get(user.getUuid());
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
    public <T> T asUserResult(final UserIdentity userIdentity, final Supplier<T> supplier) {
        T result;
        boolean success = false;
        try {
            pushUser(userIdentity);
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
    public void asUser(final UserIdentity userIdentity, final Runnable runnable) {
        boolean success = false;
        try {
            pushUser(userIdentity);
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
            pushUser(ProcessingUserIdentity.INSTANCE);
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
            pushUser(ProcessingUserIdentity.INSTANCE);
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
