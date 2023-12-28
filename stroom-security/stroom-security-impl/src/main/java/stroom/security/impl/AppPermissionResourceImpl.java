package stroom.security.impl;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.api.exception.AuthenticationException;
import stroom.security.shared.AppPermissionResource;
import stroom.security.shared.ChangeSet;
import stroom.security.shared.ChangeUserRequest;
import stroom.security.shared.HasStroomUserIdentity;
import stroom.security.shared.PermissionNames;
import stroom.security.shared.User;
import stroom.security.shared.UserAndPermissions;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.SimpleUserName;

import java.util.List;
import javax.inject.Inject;
import javax.inject.Provider;

@AutoLogged(OperationType.MANUALLY_LOGGED)
class AppPermissionResourceImpl implements AppPermissionResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AppPermissionResourceImpl.class);

    private final Provider<SecurityContext> securityContextProvider;
    private final Provider<UserService> userServiceProvider;
    private final Provider<UserAppPermissionService> userAppPermissionServiceProvider;
    private final Provider<UserAndPermissionsHelper> userAndPermissionsHelperProvider;
    private final Provider<AuthenticationConfig> authenticationConfigProvider;
    private final Provider<AuthorisationEventLog> authorisationEventLogProvider;
    private final Provider<UserGroupsCache> userGroupsCacheProvider;
    private final Provider<UserAppPermissionsCache> userAppPermissionsCacheProvider;

    @Inject
    AppPermissionResourceImpl(final Provider<SecurityContext> securityContextProvider,
                              final Provider<UserService> userServiceProvider,
                              final Provider<UserAppPermissionService> userAppPermissionServiceProvider,
                              final Provider<UserAndPermissionsHelper> userAndPermissionsHelperProvider,
                              final Provider<AuthenticationConfig> authenticationConfigProvider,
                              final Provider<AuthorisationEventLog> authorisationEventLogProvider,
                              final Provider<UserGroupsCache> userGroupsCacheProvider,
                              final Provider<UserAppPermissionsCache> userAppPermissionsCacheProvider) {
        this.securityContextProvider = securityContextProvider;
        this.userServiceProvider = userServiceProvider;
        this.userAppPermissionServiceProvider = userAppPermissionServiceProvider;
        this.userAndPermissionsHelperProvider = userAndPermissionsHelperProvider;
        this.authenticationConfigProvider = authenticationConfigProvider;
        this.authorisationEventLogProvider = authorisationEventLogProvider;
        this.userGroupsCacheProvider = userGroupsCacheProvider;
        this.userAppPermissionsCacheProvider = userAppPermissionsCacheProvider;
    }

    @Override
    @AutoLogged(OperationType.VIEW)
    public UserAndPermissions getUserAndPermissions() {
        final SecurityContext securityContext = securityContextProvider.get();
        final UserIdentity userIdentity = securityContext.getUserIdentity();
        if (userIdentity == null) {
            LOGGER.debug("Null userIdentity");
            return null;
        } else {
            if (userIdentity instanceof final HasStroomUserIdentity hasStroomUserIdentity) {
                final boolean preventLogin = authenticationConfigProvider.get().isPreventLogin();
                if (preventLogin) {
                    final boolean isAdmin = securityContext.isAdmin();
                    LOGGER.debug("Preventing login for all but admin, isAdmin: {}", isAdmin);
                    if (!securityContext.isAdmin()) {
                        throw new AuthenticationException("Stroom is down for maintenance. Please try again later.");
                    }
                }
                final UserAndPermissions userAndPermissions = new UserAndPermissions(
                        new SimpleUserName(
                                userIdentity.getSubjectId(),
                                userIdentity.getDisplayName(),
                                userIdentity.getFullName().orElse(null),
                                securityContext.getUserUuid()),
                        userAndPermissionsHelperProvider.get().get(hasStroomUserIdentity.getUuid()));

                LOGGER.debug("Returning {}", userAndPermissions);
                return userAndPermissions;
            } else {
                LOGGER.debug(LogUtil.message("Wrong type of user, expecting: {}, got: {}",
                        HasStroomUserIdentity.class.getSimpleName(),
                        userIdentity.getClass().getSimpleName()));
                return null;
            }
        }
    }

    @Override
    @AutoLogged(OperationType.VIEW)
    public UserAndPermissions fetchUserAppPermissions(final User user) {
        return new UserAndPermissions(
                user,
                userAndPermissionsHelperProvider.get().get(user.getUuid()));
    }

    @Override
    @AutoLogged(OperationType.VIEW)
    public List<String> fetchAllPermissions() {
        return List.of(PermissionNames.PERMISSIONS);
    }

    @Override
    @AutoLogged(OperationType.MANUALLY_LOGGED)
    public Boolean changeUser(final ChangeUserRequest action) {
        final User user = action.getUser();
        if (user != null) {

            // Modify linked users and user groups
            final ChangeSet<User> linkedUsers = action.getChangedLinkedUsers();
            if (linkedUsers != null) {
                if (linkedUsers.getAddSet() != null && linkedUsers.getAddSet().size() > 0) {
                    for (final User add : linkedUsers.getAddSet()) {
                        if (user.isGroup()) {
                            if (!add.isGroup()) {
                                addUserToGroup(add, user);
                            }
                        } else {
                            if (add.isGroup()) {
                                addUserToGroup(user, add);
                            }
                        }

                        // Clear cached user groups for this user.
                        userGroupsCacheProvider.get().remove(add.getUuid());
                    }
                }

                if (linkedUsers.getRemoveSet() != null && linkedUsers.getRemoveSet().size() > 0) {
                    for (final User remove : linkedUsers.getRemoveSet()) {
                        if (user.isGroup()) {
                            if (!remove.isGroup()) {
                                removeUserFromGroup(remove, user);
                            }
                        } else {
                            if (remove.isGroup()) {
                                removeUserFromGroup(user, remove);
                            }
                        }

                        // Clear cached user groups for this user.
                        userGroupsCacheProvider.get().remove(remove.getUuid());
                    }
                }

                // Clear cached user groups for this user.
                userGroupsCacheProvider.get().remove(user.getUuid());
            }

            // Modify user/user group feature permissions.
            final ChangeSet<String> appPermissionChangeSet = action.getChangedAppPermissions();
            if (appPermissionChangeSet != null) {
                if (appPermissionChangeSet.getAddSet() != null && appPermissionChangeSet.getAddSet().size() > 0) {
                    for (final String permission : appPermissionChangeSet.getAddSet()) {
                        addPermission(user, permission);
                    }
                }

                if (appPermissionChangeSet.getRemoveSet() != null && appPermissionChangeSet.getRemoveSet().size() > 0) {
                    for (final String permission : appPermissionChangeSet.getRemoveSet()) {
                        removePermission(user, permission);
                    }
                }

                // Clear cached application permissions for this user.
                userAppPermissionsCacheProvider.get().remove(user.getUuid());
            }
        }

        return true;
    }

    private void addUserToGroup(final User user, final User userGroup) {
        try {
            userServiceProvider.get().addUserToGroup(user.getUuid(), userGroup.getUuid());
            authorisationEventLogProvider.get()
                    .addUserToGroup(user, userGroup.getSubjectId(), true, null);
        } catch (final RuntimeException e) {
            authorisationEventLogProvider.get()
                    .addUserToGroup(user, userGroup.getSubjectId(), false, e.getMessage());
        }
    }

    private void removeUserFromGroup(final User user, final User userGroup) {
        try {
            userServiceProvider.get().removeUserFromGroup(user.getUuid(), userGroup.getUuid());
            authorisationEventLogProvider.get()
                    .removeUserFromGroup(user, userGroup.getSubjectId(), true, null);
        } catch (final RuntimeException e) {
            authorisationEventLogProvider.get()
                    .removeUserFromGroup(user, userGroup.getSubjectId(), false, e.getMessage());
        }
    }

    private void addPermission(User user, String permission) {
        try {
            userAppPermissionServiceProvider.get().addPermission(user.getUuid(), permission);
            authorisationEventLogProvider.get()
                    .addUserToGroup(user, permission, true, null);
        } catch (final RuntimeException e) {
            authorisationEventLogProvider.get()
                    .addUserToGroup(user, permission, false, e.getMessage());
        }
    }

    private void removePermission(User user, String permission) {
        try {
            userAppPermissionServiceProvider.get().removePermission(user.getUuid(), permission);
            authorisationEventLogProvider.get()
                    .removeUserFromGroup(user, permission, true, null);
        } catch (final RuntimeException e) {
            authorisationEventLogProvider.get()
                    .removeUserFromGroup(user, permission, false, e.getMessage());
        }
    }
}
