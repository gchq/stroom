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

package stroom.security;

import stroom.logging.AuthorisationEventLog;
import stroom.security.Secured;
import stroom.security.shared.ChangeSet;
import stroom.security.shared.ChangeUserAction;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.UserRef;
import stroom.task.AbstractTaskHandler;
import stroom.task.TaskHandlerBean;
import stroom.util.shared.VoidResult;

import javax.inject.Inject;

@TaskHandlerBean(task = ChangeUserAction.class)
@Secured(FindUserCriteria.MANAGE_USERS_PERMISSION)
class ChangeUserHandler extends AbstractTaskHandler<ChangeUserAction, VoidResult> {
    private final UserService userService;
    private final UserAppPermissionService userAppPermissionService;
    private final AuthorisationEventLog authorisationEventLog;
    private final UserGroupsCache userGroupsCache;
    private final UserAppPermissionsCache userAppPermissionsCache;

    @Inject
    ChangeUserHandler(final UserService userService,
                      final UserAppPermissionService userAppPermissionService,
                      final AuthorisationEventLog authorisationEventLog,
                      final UserGroupsCache userGroupsCache,
                      final UserAppPermissionsCache userAppPermissionsCache) {
        this.userService = userService;
        this.userAppPermissionService = userAppPermissionService;
        this.authorisationEventLog = authorisationEventLog;
        this.userGroupsCache = userGroupsCache;
        this.userAppPermissionsCache = userAppPermissionsCache;
    }

    @Override
    public VoidResult exec(final ChangeUserAction action) {
        final UserRef userRef = action.getUserRef();
        if (userRef != null) {

            // Modify linked users and user groups
            final ChangeSet<UserRef> linkedUsers = action.getChangedLinkedUsers();
            if (linkedUsers != null) {
                if (linkedUsers.getAddSet() != null && linkedUsers.getAddSet().size() > 0) {
                    for (final UserRef add : linkedUsers.getAddSet()) {
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
                    for (final UserRef remove : linkedUsers.getRemoveSet()) {
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
    }

    private void addUserToGroup(final UserRef user, final UserRef userGroup) {
        try {
            userService.addUserToGroup(user, userGroup);
            authorisationEventLog.addUserToGroup(user.getName(), userGroup.getName(), true, null);
        } catch (final RuntimeException e) {
            authorisationEventLog.addUserToGroup(user.getName(), userGroup.getName(), false, e.getMessage());
        }
    }

    private void removeUserFromGroup(final UserRef user, final UserRef userGroup) {
        try {
            userService.removeUserFromGroup(user, userGroup);
            authorisationEventLog.removeUserFromGroup(user.getName(), userGroup.getName(), true, null);
        } catch (final RuntimeException e) {
            authorisationEventLog.removeUserFromGroup(user.getName(), userGroup.getName(), false, e.getMessage());
        }
    }

    private void addPermission(UserRef userRef, String permission) {
        try {
            userAppPermissionService.addPermission(userRef, permission);
            authorisationEventLog.addUserToGroup(userRef.getName(), permission, true, null);
        } catch (final RuntimeException e) {
            authorisationEventLog.addUserToGroup(userRef.getName(), permission, false, e.getMessage());
        }
    }

    private void removePermission(UserRef userRef, String permission) {
        try {
            userAppPermissionService.removePermission(userRef, permission);
            authorisationEventLog.removeUserFromGroup(userRef.getName(), permission, true, null);
        } catch (final RuntimeException e) {
            authorisationEventLog.removeUserFromGroup(userRef.getName(), permission, false, e.getMessage());
        }
    }
}
