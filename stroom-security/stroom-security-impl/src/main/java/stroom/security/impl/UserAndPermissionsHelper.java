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

import javax.inject.Inject;
import java.util.HashSet;
import java.util.Set;

class UserAndPermissionsHelper {
    private final UserGroupsCache userGroupsCache;
    private final UserAppPermissionsCache userAppPermissionsCache;

    @Inject
    UserAndPermissionsHelper(final UserGroupsCache userGroupsCache,
                             final UserAppPermissionsCache userAppPermissionsCache) {
        this.userGroupsCache = userGroupsCache;
        this.userAppPermissionsCache = userAppPermissionsCache;
    }

    public Set<String> get(final String userUuid) {
        final Set<String> appPermissionSet = new HashSet<>();

        // Add app permissions set explicitly for this user first.
        addPermissions(appPermissionSet, userUuid);

        // Get user groups for this user.
        final Set<String> userGroupUuids = userGroupsCache.get(userUuid);

        // Add app permissions set on groups this user belongs to.
        if (userGroupUuids != null) {
            for (final String userGroupUuid : userGroupUuids) {
                addPermissions(appPermissionSet, userGroupUuid);
            }
        }

        return appPermissionSet;
    }

    private void addPermissions(final Set<String> appPermissionSet, final String userGroupUuid) {
        final Set<String> userAppPermissions = userAppPermissionsCache.get(userGroupUuid);
        if (userAppPermissions != null) {
            appPermissionSet.addAll(userAppPermissions);
        }
    }
}
