package stroom.security.impl;

import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerConstants;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.api.UserIdentityFactory;
import stroom.security.api.exception.AuthenticationException;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.HasUserRef;
import stroom.security.shared.User;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.PermissionException;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

@Singleton
class SecurityContextImpl implements SecurityContext {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SecurityContextImpl.class);

    private final ThreadLocal<Boolean> checkTypeThreadLocal = ThreadLocal.withInitial(() -> Boolean.TRUE);

    private final UserDocumentPermissionsCache userDocumentPermissionsCache;
    private final UserDocumentCreatePermissionsCache userDocumentCreatePermissionsCache;
    private final UserGroupsCache userGroupsCache;
    private final UserCache userCache;
    private final UserAppPermissionsCache userAppPermissionsCache;
    private final UserIdentityFactory userIdentityFactory;

    @Inject
    SecurityContextImpl(
            final UserDocumentPermissionsCache userDocumentPermissionsCache,
            final UserDocumentCreatePermissionsCache userDocumentCreatePermissionsCache,
            final UserGroupsCache userGroupsCache,
            final UserCache userCache,
            final UserAppPermissionsCache userAppPermissionsCache,
            final UserIdentityFactory userIdentityFactory) {
        this.userDocumentPermissionsCache = userDocumentPermissionsCache;
        this.userDocumentCreatePermissionsCache = userDocumentCreatePermissionsCache;
        this.userGroupsCache = userGroupsCache;
        this.userCache = userCache;
        this.userAppPermissionsCache = userAppPermissionsCache;
        this.userIdentityFactory = userIdentityFactory;
    }

    @Override
    public UserIdentity getUserIdentity() {
        return CurrentUserState.current();
    }

    private UserIdentity assertUserIdentity() {
        // Get the current user.
        final UserIdentity userIdentity = CurrentUserState.current();

        // If there is no logged in user then throw an exception.
        if (userIdentity == null) {
            throw new AuthenticationException("No user is currently logged in");
        }

        return userIdentity;
    }

    @Override
    public UserRef getUserRef() {
        // Get the current user.
        final UserIdentity userIdentity = assertUserIdentity();
        return getUserRef(userIdentity);
    }

    private UserRef getUserRef(final UserIdentity userIdentity) {
        if (userIdentity instanceof final HasUserRef hasUserRef) {
            return hasUserRef.getUserRef();
        } else {
            throw new AuthenticationException(LogUtil.message(
                    "Expecting a stroom user identity (i.e. {}), but got {}",
                    HasUserRef.class.getSimpleName(),
                    userIdentity.getClass().getSimpleName()));
        }
    }

    @Override
    public boolean isAdmin() {
        return hasAppPermission(AppPermission.ADMINISTRATOR);
    }

    private boolean isAdmin(final UserIdentity userIdentity) {
        return hasAppPermission(userIdentity, AppPermission.ADMINISTRATOR);
    }

    @Override
    public boolean isProcessingUser() {
        // Get the current user.
        final UserIdentity userIdentity = assertUserIdentity();

        // If the user is the internal processing user then they automatically have permission.
        return isProcessingUser(userIdentity);
    }

    private boolean isProcessingUser(final UserIdentity userIdentity) {
        return userIdentityFactory.isServiceUser(userIdentity);
    }

    @Override
    public boolean isUseAsRead() {
        return CurrentUserState.isElevatePermissions();
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
    public boolean hasAppPermission(final AppPermission permission) {
        // Get the current user.
        final UserIdentity userIdentity = assertUserIdentity();
        return hasAppPermission(userIdentity, permission);
    }

    private boolean hasAppPermission(final UserIdentity userIdentity, final AppPermission permission) {
        // If the user is the internal processing user then they automatically have permission.
        if (isProcessingUser(userIdentity)) {
            return true;
        }

        // See if the user has permission.
        final UserRef userRef = getUserRef(userIdentity);
        return hasAppPermission(userRef, permission);
    }

    private boolean hasAppPermission(final UserRef userRef, final AppPermission permission) {
        // See if the user has an explicit permission.
        if (hasUserAppPermission(userRef, permission)) {
            return true;
        }

        // See if the user belongs to a group that has permission.
        return hasGroupAppPermission(userRef, permission, new HashSet<>());
    }

    private boolean hasGroupAppPermission(final UserRef userRef,
                                          final AppPermission permission,
                                          final Set<UserRef> examined) {
        final Set<UserRef> userGroups = userGroupsCache.getGroups(userRef);
        if (userGroups != null) {
            for (final UserRef userGroup : userGroups) {
                if (hasUserAppPermission(userGroup, permission)) {
                    return true;
                }

                // Recurse into parent groups.
                if (!examined.contains(userGroup)) {
                    examined.add(userGroup);
                    if (hasGroupAppPermission(userGroup, permission, examined)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasUserAppPermission(final UserRef userRef, final AppPermission permission) {
        final Set<AppPermission> userAppPermissions = userAppPermissionsCache.get(userRef);
        if (userAppPermissions != null) {
            return userAppPermissions.contains(permission) ||
                   userAppPermissions.contains(AppPermission.ADMINISTRATOR);
        }
        return false;
    }

    @Override
    public boolean hasDocumentPermission(final DocRef docRef, final DocumentPermission permission) {
        // Get the current user.
        final UserIdentity userIdentity = assertUserIdentity();

        // Let administrators do anything.
        if (isAdmin(userIdentity)) {
            return true;
        }

        // If we are currently allowing users with only `Use` permission to `Read` (elevate permissions) then
        // test for `Use` instead of `Read`.
        final DocumentPermission perm = DocumentPermission.VIEW.equals(permission) &&
                                        CurrentUserState.isElevatePermissions()
                ? DocumentPermission.USE
                : permission;

        final UserRef userRef = getUserRef(userIdentity);
        return hasDocumentPermission(userRef, docRef, perm);
    }

    private boolean hasDocumentPermission(final UserRef userUuid,
                                          final DocRef docRef,
                                          final DocumentPermission permission) {
        // See if the user has an explicit permission.
        if (hasUserDocumentPermission(userUuid, docRef, permission)) {
            return true;
        }

        // See if the user belongs to a group that has permission.
        return hasGroupDocumentPermission(userUuid, docRef, permission, new HashSet<>());
    }

    private boolean hasGroupDocumentPermission(final UserRef userRef,
                                               final DocRef docRef,
                                               final DocumentPermission permission,
                                               final Set<UserRef> examined) {
        final Set<UserRef> userGroups = userGroupsCache.getGroups(userRef);
        if (userGroups != null) {
            for (final UserRef userGroup : userGroups) {
                if (hasUserDocumentPermission(userGroup, docRef, permission)) {
                    return true;
                }

                // Recurse into parent groups.
                if (!examined.contains(userGroup)) {
                    examined.add(userGroup);
                    if (hasGroupDocumentPermission(userGroup, docRef, permission, examined)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasUserDocumentPermission(final UserRef userRef,
                                              final DocRef docRef,
                                              final DocumentPermission permission) {
        return userDocumentPermissionsCache.hasDocumentPermission(userRef, docRef, permission);
    }

    @Override
    public boolean hasDocumentCreatePermission(final DocRef folderRef, final String documentType) {
        // Get the current user.
        final UserIdentity userIdentity = assertUserIdentity();

        // Let administrators do anything.
        if (isAdmin(userIdentity)) {
            return true;
        }

        final UserRef userRef = getUserRef(userIdentity);
        return hasDocumentCreatePermission(userRef, folderRef, documentType);
    }

    private boolean hasDocumentCreatePermission(final UserRef userUuid,
                                                final DocRef folderRef,
                                                final String documentType) {
        // See if the user has an explicit permission.
        if (hasUserDocumentCreatePermission(userUuid, folderRef, documentType)) {
            return true;
        }

        // See if the user belongs to a group that has permission.
        return hasGroupDocumentCreatePermission(userUuid, folderRef, documentType, new HashSet<>());
    }

    private boolean hasGroupDocumentCreatePermission(final UserRef userRef,
                                                     final DocRef folderRef,
                                                     final String documentType,
                                                     final Set<UserRef> examined) {
        final Set<UserRef> userGroups = userGroupsCache.getGroups(userRef);
        if (userGroups != null) {
            for (final UserRef userGroup : userGroups) {
                if (hasUserDocumentCreatePermission(userGroup, folderRef, documentType)) {
                    return true;
                }

                // Recurse into parent groups.
                if (!examined.contains(userGroup)) {
                    examined.add(userGroup);
                    if (hasGroupDocumentCreatePermission(userGroup, folderRef, documentType, examined)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean hasUserDocumentCreatePermission(final UserRef userUuid,
                                                    final DocRef folderRef,
                                                    final String documentType) {
        boolean result = userDocumentCreatePermissionsCache.hasDocumentCreatePermission(
                userUuid,
                folderRef,
                documentType);

        // See if we need to check if the user has `all` create permissions.
        if (!result && !ExplorerConstants.ALL_CREATE_PERMISSIONS.equals(documentType)) {
            result = userDocumentCreatePermissionsCache.hasDocumentCreatePermission(
                    userUuid,
                    folderRef,
                    ExplorerConstants.ALL_CREATE_PERMISSIONS);
        }

        return result;
    }

    /**
     * Run the supplied code as the specified user.
     */
    @Override
    public <T> T asUserResult(final UserIdentity userIdentity, final Supplier<T> supplier) {
        final T result;
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

    @Override
    public <T> T asUserResult(final UserRef userRef, final Supplier<T> supplier) {
        ensureValidUser(userRef);
        final UserIdentity userIdentity = new BasicUserIdentity(userRef);
        return asUserResult(userIdentity, supplier);
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

    @Override
    public void asUser(final UserRef userRef, final Runnable runnable) {
        ensureValidUser(userRef);
        final UserIdentity userIdentity = new BasicUserIdentity(userRef);
        asUser(userIdentity, runnable);
    }

    private void ensureValidUser(final UserRef userRef) {
        final Optional<User> optional = userCache.getByRef(userRef);
        if (optional.isEmpty()) {
            throwPermissionException(userRef, "User '" + userRef.toDisplayString() + "' not found");
        } else if (!optional.get().isEnabled()) {
            throwPermissionException(userRef, "User '" + userRef.toDisplayString() + "' is not enabled");
        }
    }

    private void throwPermissionException(final UserRef userRef, final String message) {
        try {
            throw new PermissionException(userRef, message);
        } catch (final PermissionException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
    }

    /**
     * Run the supplied code as the internal processing user.
     */
    @Override
    public <T> T asProcessingUserResult(final Supplier<T> supplier) {
        return asUserResult(userIdentityFactory.getServiceUserIdentity(), supplier);
    }

    /**
     * Run the supplied code as the internal processing user.
     */
    @Override
    public void asProcessingUser(final Runnable runnable) {
        asUser(userIdentityFactory.getServiceUserIdentity(), runnable);
    }

    /**
     * Allow the current user to read items that they only have 'Use' permission on.
     */
    @Override
    public <T> T useAsReadResult(final Supplier<T> supplier) {
        final T result;
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
    public void secure(final AppPermission permission, final Runnable runnable) {
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
    public <T> T secureResult(final AppPermission permission, final Supplier<T> supplier) {
        final T result;

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
        final T result;

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

    private void checkAppPermission(final AppPermission permission) {
        final Boolean currentCheckType = checkTypeThreadLocal.get();
        try {
            // Don't check any further permissions.
            checkTypeThreadLocal.set(Boolean.FALSE);
            if (!hasAppPermission(permission)) {
                throw new PermissionException(
                        getUserRef(),
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
            if (getUserIdentity() == null) {
                throw new PermissionException(
                        getUserRef(),
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
    public boolean inGroup(final String groupName) {
        return inGroup(getUserRef(), groupName, new HashSet<>());
    }

    private boolean inGroup(final UserRef userRef,
                            final String groupName,
                            final Set<UserRef> examined) {
        final Set<UserRef> userGroups = userGroupsCache.getGroups(userRef);
        if (userGroups != null) {
            for (final UserRef userGroup : userGroups) {
                if (userGroup.getSubjectId().equals(groupName)) {
                    return true;
                }

                // Recurse into parent groups.
                if (!examined.contains(userGroup)) {
                    examined.add(userGroup);
                    if (inGroup(userGroup, groupName, examined)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
