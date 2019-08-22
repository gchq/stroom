/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.security.impl;

import stroom.security.api.SecurityContext;
import stroom.security.shared.ChangeSet;
import stroom.security.shared.ChangeUserAction;
import stroom.security.shared.PermissionNames;
import stroom.security.shared.User;
import stroom.task.api.AbstractTaskHandler;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;


class ChangeUserHandler extends AbstractTaskHandler<ChangeUserAction, VoidResult> {
    private final UserService userService;
    private final UserAppPermissionService userAppPermissionService;
    private final AuthorisationEventLog authorisationEventLog;
    private final UserGroupsCache userGroupsCache;
    private final UserAppPermissionsCache userAppPermissionsCache;
    private final SecurityContext securityContext;

    @Inject
    ChangeUserHandler(final UserService userService,
                      final UserAppPermissionService userAppPermissionService,
                      final AuthorisationEventLog authorisationEventLog,
                      final UserGroupsCache userGroupsCache,
                      final UserAppPermissionsCache userAppPermissionsCache,
                      final SecurityContext securityContext) {
        this.userService = userService;
        this.userAppPermissionService = userAppPermissionService;
        this.authorisationEventLog = authorisationEventLog;
        this.userGroupsCache = userGroupsCache;
        this.userAppPermissionsCache = userAppPermissionsCache;
        this.securityContext = securityContext;
    }

    @Override
    public VoidResult exec(final ChangeUserAction action) {
        return securityContext.secureResult(PermissionNames.MANAGE_USERS_PERMISSION, () -> {
            final User userRef = action.getUser();
            if (userRef != null) {

                // Modify linked users and user groups
                final ChangeSet<User> linkedUsers = action.getChangedLinkedUsers();
                if (linkedUsers != null) {
                    if (linkedUsers.getAddSet() != null && linkedUsers.getAddSet().size() > 0) {
                        for (final User add : linkedUsers.getAddSet()) {
                            if (userRef.isGroup()) {
                                if (!add.isGroup()) {
                                    addUserToGroup(add, userRef);
                                }
                            } else {
                                if (add.isGroup()) {
                                    addUserToGroup(userRef, add);
                                }
                            }

                            // Clear cached user groups for this user.
                            userGroupsCache.remove(add);
                        }
                    }

                    if (linkedUsers.getRemoveSet() != null && linkedUsers.getRemoveSet().size() > 0) {
                        for (final User remove : linkedUsers.getRemoveSet()) {
                            if (userRef.isGroup()) {
                                if (!remove.isGroup()) {
                                    removeUserFromGroup(remove, userRef);
                                }
                            } else {
                                if (remove.isGroup()) {
                                    removeUserFromGroup(userRef, remove);
                                }
                            }

                            // Clear cached user groups for this user.
                            userGroupsCache.remove(remove);
                        }
                    }

                    // Clear cached user groups for this user.
                    userGroupsCache.remove(userRef);
                }

                // Modify user/user group feature permissions.
                final ChangeSet<String> appPermissionChangeSet = action.getChangedAppPermissions();
                if (appPermissionChangeSet != null) {
                    if (appPermissionChangeSet.getAddSet() != null && appPermissionChangeSet.getAddSet().size() > 0) {
                        for (final String permission : appPermissionChangeSet.getAddSet()) {
                            addPermission(userRef, permission);
                        }
                    }

                    if (appPermissionChangeSet.getRemoveSet() != null && appPermissionChangeSet.getRemoveSet().size() > 0) {
                        for (final String permission : appPermissionChangeSet.getRemoveSet()) {
                            removePermission(userRef, permission);
                        }
                    }

                    // Clear cached application permissions for this user.
                    userAppPermissionsCache.remove(userRef);
                }
            }

            return VoidResult.INSTANCE;
        });
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

    private void addPermission(User userRef, String permission) {
        try {
            userAppPermissionService.addPermission(userRef.getUuid(), permission);
            authorisationEventLog.addUserToGroup(userRef.getName(), permission, true, null);
        } catch (final RuntimeException e) {
            authorisationEventLog.addUserToGroup(userRef.getName(), permission, false, e.getMessage());
        }
    }

    private void removePermission(User userRef, String permission) {
        try {
            userAppPermissionService.removePermission(userRef.getUuid(), permission);
            authorisationEventLog.removeUserFromGroup(userRef.getName(), permission, true, null);
        } catch (final RuntimeException e) {
            authorisationEventLog.removeUserFromGroup(userRef.getName(), permission, false, e.getMessage());
        }
    }
}
