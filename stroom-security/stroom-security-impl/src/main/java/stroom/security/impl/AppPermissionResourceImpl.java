package stroom.security.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.security.impl.exception.AuthenticationException;
import stroom.security.shared.AppPermissionResource;
import stroom.security.shared.ChangeSet;
import stroom.security.shared.ChangeUserRequest;
import stroom.security.shared.PermissionNames;
import stroom.security.shared.User;
import stroom.security.shared.UserAndPermissions;

import javax.inject.Inject;
import java.util.List;

class AppPermissionResourceImpl implements AppPermissionResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(AppPermissionResourceImpl.class);

    private final SecurityContext securityContext;
    private final UserService userService;
    private final UserAppPermissionService userAppPermissionService;
    private final UserAndPermissionsHelper userAndPermissionsHelper;
    private final AuthenticationConfig authenticationConfig;
    private final AuthorisationEventLog authorisationEventLog;
    private final UserGroupsCache userGroupsCache;
    private final UserAppPermissionsCache userAppPermissionsCache;

    @Inject
    AppPermissionResourceImpl(final SecurityContext securityContext,
                              final UserService userService,
                              final UserAppPermissionService userAppPermissionService,
                              final UserAndPermissionsHelper userAndPermissionsHelper,
                              final AuthenticationConfig authenticationConfig,
                              final AuthorisationEventLog authorisationEventLog,
                              final UserGroupsCache userGroupsCache,
                              final UserAppPermissionsCache userAppPermissionsCache) {
        this.securityContext = securityContext;
        this.userService = userService;
        this.userAppPermissionService = userAppPermissionService;
        this.userAndPermissionsHelper = userAndPermissionsHelper;
        this.authenticationConfig = authenticationConfig;
        this.authorisationEventLog = authorisationEventLog;
        this.userGroupsCache = userGroupsCache;
        this.userAppPermissionsCache = userAppPermissionsCache;
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

        return new UserAndPermissions(user, userAndPermissionsHelper.get(user.getUuid()));
    }

    @Override
    public UserAndPermissions fetchUserAppPermissions(final User user) {
        return new UserAndPermissions(user, userAndPermissionsHelper.get(user.getUuid()));
    }

    @Override
    public List<String> fetchAllPermissions() {
        return List.of(PermissionNames.PERMISSIONS);
    }

    @Override
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
                        userGroupsCache.remove(add.getUuid());
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
                        userGroupsCache.remove(remove.getUuid());
                    }
                }

                // Clear cached user groups for this user.
                userGroupsCache.remove(user.getUuid());
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
                userAppPermissionsCache.remove(user.getUuid());
            }
        }

        return true;
    }

    private void addUserToGroup(final User user, final User userGroup) {
        try {
            userService.addUserToGroup(user.getUuid(), userGroup.getUuid());
            authorisationEventLog.addUserToGroup(user.getName(), userGroup.getName(), true, null);
        } catch (final RuntimeException e) {
            authorisationEventLog.addUserToGroup(user.getName(), userGroup.getName(), false, e.getMessage());
        }
    }

    private void removeUserFromGroup(final User user, final User userGroup) {
        try {
            userService.removeUserFromGroup(user.getUuid(), userGroup.getUuid());
            authorisationEventLog.removeUserFromGroup(user.getName(), userGroup.getName(), true, null);
        } catch (final RuntimeException e) {
            authorisationEventLog.removeUserFromGroup(user.getName(), userGroup.getName(), false, e.getMessage());
        }
    }

    private void addPermission(User user, String permission) {
        try {
            userAppPermissionService.addPermission(user.getUuid(), permission);
            authorisationEventLog.addUserToGroup(user.getName(), permission, true, null);
        } catch (final RuntimeException e) {
            authorisationEventLog.addUserToGroup(user.getName(), permission, false, e.getMessage());
        }
    }

    private void removePermission(User user, String permission) {
        try {
            userAppPermissionService.removePermission(user.getUuid(), permission);
            authorisationEventLog.removeUserFromGroup(user.getName(), permission, true, null);
        } catch (final RuntimeException e) {
            authorisationEventLog.removeUserFromGroup(user.getName(), permission, false, e.getMessage());
        }
    }
}
