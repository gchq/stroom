package stroom.security.impl;

import stroom.docref.HasUuid;
import stroom.security.api.ClientSecurityUtil;
import stroom.security.api.HasJws;
import stroom.security.api.ProcessingUserIdentityProvider;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.impl.exception.AuthenticationException;
import stroom.security.shared.DocumentPermissionNames;
import stroom.security.shared.PermissionNames;
import stroom.security.shared.User;
import stroom.util.shared.PermissionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.client.Invocation;

@Singleton
class SecurityContextImpl implements SecurityContext {

    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityContextImpl.class);

    private final ThreadLocal<Boolean> checkTypeThreadLocal = ThreadLocal.withInitial(() -> Boolean.TRUE);

    private final UserDocumentPermissionsCache userDocumentPermissionsCache;
    private final UserGroupsCache userGroupsCache;
    private final UserAppPermissionsCache userAppPermissionsCache;
    private final UserCache userCache;
    private final ProcessingUserIdentityProvider processingUserIdentityProvider;
    private final UserIdentityFactory userIdentityFactory;

    @Inject
    SecurityContextImpl(
            final UserDocumentPermissionsCache userDocumentPermissionsCache,
            final UserGroupsCache userGroupsCache,
            final UserAppPermissionsCache userAppPermissionsCache,
            final UserCache userCache,
            final ProcessingUserIdentityProvider processingUserIdentityProvider,
            final UserIdentityFactory userIdentityFactory) {
        this.userDocumentPermissionsCache = userDocumentPermissionsCache;
        this.userGroupsCache = userGroupsCache;
        this.userAppPermissionsCache = userAppPermissionsCache;
        this.userCache = userCache;
        this.processingUserIdentityProvider = processingUserIdentityProvider;
        this.userIdentityFactory = userIdentityFactory;
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
        return new BasicUserIdentity(optional.get().getUuid(), userId);
    }

    @Override
    public boolean isLoggedIn() {
        return getUserIdentity() != null;
    }

    @Override
    public boolean isAdmin() {
        return hasAppPermission(PermissionNames.ADMINISTRATOR);
    }

    @Override
    public boolean isProcessingUser() {
        // Get the current user.
        final UserIdentity userIdentity = getUserIdentity();

        // If there is no logged in user then throw an exception.
        if (userIdentity == null) {
            throw new AuthenticationException("No user is currently logged in");
        }

        // If the user is the internal processing user then they automatically have permission.
        return processingUserIdentityProvider.isProcessingUser(userIdentity);
    }

    @Override
    public boolean isUseAsRead() {
        return CurrentUserState.isElevatePermissions();
    }

    private String getUserUuid(final UserIdentity userIdentity) {
        if (!(userIdentity instanceof HasUuid)) {
            throw new AuthenticationException("Expecting a real user identity");
        }
        return ((HasUuid) userIdentity).getUuid();
    }

    private void pushUser(final UserIdentity userIdentity) {
        // Before we push the user see if we need to refresh the user token.
        userIdentityFactory.refresh(userIdentity);
        // Push the user.
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
        if (processingUserIdentityProvider.isProcessingUser(userIdentity)) {
            return true;
        }

        // See if the user has permission.
        final String userUuid = getUserUuid(userIdentity);
        boolean result = hasAppPermission(userUuid, permission);

        // If the user doesn't have the requested permission see if they are an admin.
        if (!result && !PermissionNames.ADMINISTRATOR.equals(permission)) {
            result = hasAppPermission(userUuid, PermissionNames.ADMINISTRATOR);
        }

        return result;
    }

    private boolean hasAppPermission(final String userUuid, final String permission) {
        // See if the user has an explicit permission.
        if (hasUserAppPermission(userUuid, permission)) {
            return true;
        }

        // See if the user belongs to a group that has permission.
        final Set<String> userGroupUuids = userGroupsCache.get(userUuid);
        if (userGroupUuids != null) {
            for (final String userGroupUuid : userGroupUuids) {
                if (hasUserAppPermission(userGroupUuid, permission)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasUserAppPermission(final String userUuid, final String permission) {
        final Set<String> userAppPermissions = userAppPermissionsCache.get(userUuid);
        if (userAppPermissions != null) {
            return userAppPermissions.contains(permission);
        }
        return false;
    }

    @Override
    public boolean hasDocumentPermission(final String documentUuid, final String permission) {
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

        // If we are currently allowing users with only `Use` permission to `Read` (elevate permissions) then
        // test for `Use` instead of `Read`.
        String perm = permission;
        if (CurrentUserState.isElevatePermissions() && DocumentPermissionNames.READ.equals(perm)) {
            perm = DocumentPermissionNames.USE;
        }

        final String userUuid = getUserUuid(userIdentity);
        return hasDocumentPermission(userUuid, documentUuid, perm);
    }

    private boolean hasDocumentPermission(final String userUuid, final String documentUuid, final String permission) {
        // See if the user has an explicit permission.
        if (hasUserDocumentPermission(userUuid, documentUuid, permission)) {
            return true;
        }

        // See if the user belongs to a group that has permission.
        final Set<String> userGroupUuids = userGroupsCache.get(userUuid);
        if (userGroupUuids != null) {
            for (final String userGroupUuid : userGroupUuids) {
                if (hasUserDocumentPermission(userGroupUuid, documentUuid, permission)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasUserDocumentPermission(final String userUuid,
                                              final String documentUuid,
                                              final String permission) {
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
        return asUserResult(processingUserIdentityProvider.get(), supplier);
    }

    /**
     * Run the supplied code as the internal processing user.
     */
    @Override
    public void asProcessingUser(final Runnable runnable) {
        asUser(processingUserIdentityProvider.get(), runnable);
    }

    /**
     * Run the supplied code as an admin user.
     */
    @Override
    public <T> T asAdminUserResult(final Supplier<T> supplier) {
        return asUserResult(createIdentity(User.ADMIN_USER_NAME), supplier);
    }

    /**
     * Run the supplied code as an admin user.
     */
    @Override
    public void asAdminUser(final Runnable runnable) {
        asUser(createIdentity(User.ADMIN_USER_NAME), runnable);
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
                throw new PermissionException(getUserId(),
                        "User does not have the required permission (" + permission + ")");
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
                throw new PermissionException(
                        getUserId(),
                        "A user must be logged in to call service");
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

    @Override
    public void addAuthorisationHeader(final Invocation.Builder builder) {
        final UserIdentity userIdentity = getUserIdentity();
        if (userIdentity == null) {
            LOGGER.debug("No user is currently logged in");

        } else if (!(userIdentity instanceof HasJws)) {
            LOGGER.debug("Current user has no JWS");
            throw new RuntimeException("Current user has no token");

        } else {
            userIdentityFactory.refresh(userIdentity);
            final String jws = ((HasJws) userIdentity).getJws();
            if (jws == null) {
                LOGGER.debug("The JWS is null for user '{}'", userIdentity.getId());
            } else {
                LOGGER.debug("The JWS is '{}' for user '{}'", jws, userIdentity.getId());
                ClientSecurityUtil.addAuthorisationHeader(builder, jws);
            }
        }
    }
}
