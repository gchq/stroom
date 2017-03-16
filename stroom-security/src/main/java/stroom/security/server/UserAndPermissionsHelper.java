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

package stroom.security.server;

import org.springframework.stereotype.Component;
import stroom.security.shared.User;
import stroom.security.shared.UserAndPermissions;
import stroom.security.shared.UserAppPermissions;
import stroom.security.shared.UserRef;

import javax.inject.Inject;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class UserAndPermissionsHelper {
    private final UserGroupsCache userGroupsCache;
    private final UserAppPermissionsCache userAppPermissionsCache;

    @Inject
    public UserAndPermissionsHelper(final UserGroupsCache userGroupsCache, final UserAppPermissionsCache userAppPermissionsCache) {
        this.userGroupsCache = userGroupsCache;
        this.userAppPermissionsCache = userAppPermissionsCache;
    }

    public UserAndPermissions get(final User user) {
        final Set<String> appPermissionSet = new HashSet<>();
        final UserRef userRef = UserRef.create(user);

        // Add app permissions set explicitly for this user first.
        addPermissions(appPermissionSet, userRef);

        // Get user groups for this user.
        final List<UserRef> userGroups = userGroupsCache.get(userRef);

        // Add app permissions set on groups this user belongs to.
        if (userGroups != null) {
            for (final UserRef userGroup : userGroups) {
                addPermissions(appPermissionSet, userGroup);
            }
        }

        return new UserAndPermissions(user, appPermissionSet);
    }

    private void addPermissions(final Set<String> appPermissionSet, final UserRef userRef) {
        final UserAppPermissions userAppPermissions = userAppPermissionsCache.get(userRef);
        if (userAppPermissions != null && userAppPermissions.getUserPermissons() != null) {
            appPermissionSet.addAll(userAppPermissions.getUserPermissons());
        }
    }
}
