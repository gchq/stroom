package stroom.security.impl;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.api.exception.AuthenticationException;
import stroom.security.shared.AppPermission;
import stroom.security.shared.AppPermissionResource;
import stroom.security.shared.AppUserPermissions;
import stroom.security.shared.AppUserPermissionsReport;
import stroom.security.shared.ChangeSet;
import stroom.security.shared.ChangeUserRequest;
import stroom.security.shared.FetchAppUserPermissionsRequest;
import stroom.security.shared.HasUserRef;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Set;

@AutoLogged(OperationType.MANUALLY_LOGGED)
class AppPermissionResourceImpl implements AppPermissionResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AppPermissionResourceImpl.class);

    private final Provider<SecurityContext> securityContextProvider;
    private final Provider<UserService> userServiceProvider;
    private final Provider<AppPermissionService> appPermissionServiceProvider;
    private final Provider<UserAndPermissionsHelper> userAndPermissionsHelperProvider;
    private final Provider<AuthenticationConfig> authenticationConfigProvider;
    private final Provider<AuthorisationEventLog> authorisationEventLogProvider;
    private final Provider<UserGroupsCache> userGroupsCacheProvider;
    private final Provider<UserAppPermissionsCache> userAppPermissionsCacheProvider;

    @Inject
    AppPermissionResourceImpl(final Provider<SecurityContext> securityContextProvider,
                              final Provider<UserService> userServiceProvider,
                              final Provider<AppPermissionService> appPermissionServiceProvider,
                              final Provider<UserAndPermissionsHelper> userAndPermissionsHelperProvider,
                              final Provider<AuthenticationConfig> authenticationConfigProvider,
                              final Provider<AuthorisationEventLog> authorisationEventLogProvider,
                              final Provider<UserGroupsCache> userGroupsCacheProvider,
                              final Provider<UserAppPermissionsCache> userAppPermissionsCacheProvider) {
        this.securityContextProvider = securityContextProvider;
        this.userServiceProvider = userServiceProvider;
        this.appPermissionServiceProvider = appPermissionServiceProvider;
        this.userAndPermissionsHelperProvider = userAndPermissionsHelperProvider;
        this.authenticationConfigProvider = authenticationConfigProvider;
        this.authorisationEventLogProvider = authorisationEventLogProvider;
        this.userGroupsCacheProvider = userGroupsCacheProvider;
        this.userAppPermissionsCacheProvider = userAppPermissionsCacheProvider;
    }

    @AutoLogged(OperationType.VIEW)
    @Override
    public ResultPage<AppUserPermissions> fetchAppUserPermissions(final FetchAppUserPermissionsRequest request) {
        return appPermissionServiceProvider.get().fetchAppUserPermissions(request);
    }

    @Override
    @AutoLogged(OperationType.VIEW)
    public AppUserPermissions getEffectiveAppPermissions() {
        final SecurityContext securityContext = securityContextProvider.get();
        final UserIdentity userIdentity = securityContext.getUserIdentity();
        if (userIdentity == null) {
            LOGGER.debug("Null userIdentity");
            return null;
        } else {
            if (userIdentity instanceof HasUserRef) {
                final boolean preventLogin = authenticationConfigProvider.get().isPreventLogin();
                if (preventLogin) {
                    final boolean isAdmin = securityContext.isAdmin();
                    LOGGER.debug("Preventing login for all but admin, isAdmin: {}", isAdmin);
                    if (!securityContext.isAdmin()) {
                        throw new AuthenticationException("Stroom is down for maintenance. Please try again later.");
                    }
                }
                final AppUserPermissions userAndPermissions = new AppUserPermissions(
                        securityContext.getUserRef(),
                        userAndPermissionsHelperProvider.get().getCurrentAppPermissions());

                LOGGER.debug("Returning {}", userAndPermissions);
                return userAndPermissions;
            } else {
                LOGGER.debug(LogUtil.message("Wrong type of user, expecting: {}, got: {}",
                        HasUserRef.class.getSimpleName(),
                        userIdentity.getClass().getSimpleName()));
                return null;
            }
        }
    }

    @Override
    @AutoLogged(OperationType.VIEW)
    public AppUserPermissionsReport getAppUserPermissionsReport(final UserRef user) {
        return appPermissionServiceProvider.get().getAppUserPermissionsReport(user);
    }

    @Override
    @AutoLogged(OperationType.MANUALLY_LOGGED)
    public Boolean changeUser(final ChangeUserRequest action) {
        final UserRef user = action.getUser();
        if (user != null) {

            // Modify linked users and user groups
            final ChangeSet<UserRef> linkedUsers = action.getChangedLinkedUsers();
            if (linkedUsers != null) {
                if (linkedUsers.getAddSet() != null && !linkedUsers.getAddSet().isEmpty()) {
                    for (final UserRef add : linkedUsers.getAddSet()) {
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
                        userGroupsCacheProvider.get().remove(add);
                    }
                }

                if (linkedUsers.getRemoveSet() != null && !linkedUsers.getRemoveSet().isEmpty()) {
                    for (final UserRef remove : linkedUsers.getRemoveSet()) {
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
                        userGroupsCacheProvider.get().remove(remove);
                    }
                }

                // Clear cached user groups for this user.
                userGroupsCacheProvider.get().remove(user);
            }

            // Modify user/user group feature permissions.
            final ChangeSet<AppPermission> appPermissionChangeSet = action.getChangedAppPermissions();
            if (appPermissionChangeSet != null) {
                if (appPermissionChangeSet.getAddSet() != null && !appPermissionChangeSet.getAddSet().isEmpty()) {
                    for (final AppPermission permission : appPermissionChangeSet.getAddSet()) {
                        addPermission(user, permission);
                    }
                }

                if (appPermissionChangeSet.getRemoveSet() != null && !appPermissionChangeSet.getRemoveSet().isEmpty()) {
                    for (final AppPermission permission : appPermissionChangeSet.getRemoveSet()) {
                        removePermission(user, permission);
                    }
                }

                // Clear cached application permissions for this user.
                userAppPermissionsCacheProvider.get().remove(user);
            }
        }

        return true;
    }

    private void addUserToGroup(final UserRef user, final UserRef userGroup) {
        try {
            userServiceProvider.get().addUserToGroup(user.getUuid(), userGroup.getUuid());
            authorisationEventLogProvider.get()
                    .addPermission(user, userGroup.toDisplayString(), true, null);
        } catch (final RuntimeException e) {
            authorisationEventLogProvider.get()
                    .addPermission(user, userGroup.toDisplayString(), false, e.getMessage());
        }
    }

    private void removeUserFromGroup(final UserRef user, final UserRef userGroup) {
        try {
            userServiceProvider.get().removeUserFromGroup(user.getUuid(), userGroup.getUuid());
            authorisationEventLogProvider.get()
                    .addPermission(user, userGroup.toDisplayString(), true, null);
        } catch (final RuntimeException e) {
            authorisationEventLogProvider.get()
                    .addPermission(user, userGroup.toDisplayString(), false, e.getMessage());
        }
    }

    private void addPermission(UserRef user, AppPermission permission) {
        try {
            appPermissionServiceProvider.get().addPermission(user, permission);
            authorisationEventLogProvider.get()
                    .addPermission(user, permission.getDisplayValue(), true, null);
        } catch (final RuntimeException e) {
            authorisationEventLogProvider.get()
                    .addPermission(user, permission.getDisplayValue(), false, e.getMessage());
        }
    }

    private void removePermission(UserRef user, AppPermission permission) {
        try {
            appPermissionServiceProvider.get().removePermission(user, permission);
            authorisationEventLogProvider.get()
                    .removePermission(user, permission.getDisplayValue(), true, null);
        } catch (final RuntimeException e) {
            authorisationEventLogProvider.get()
                    .removePermission(user, permission.getDisplayValue(), false, e.getMessage());
        }
    }
}
