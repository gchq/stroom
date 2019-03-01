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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.security.dao.AppPermissionDao;
import stroom.security.shared.PermissionNames;
import stroom.security.shared.UserAppPermissions;
import stroom.security.shared.UserRef;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Set;

@Singleton
class UserAppPermissionServiceImpl implements UserAppPermissionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserAppPermissionServiceImpl.class);

    private final Security security;
    private static final Set<String> ALL_PERMISSIONS = Set.of(PermissionNames.PERMISSIONS);
    private final AppPermissionDao appPermissionDao;

    @Inject
    public UserAppPermissionServiceImpl(final AppPermissionDao appPermissionDao,
                                        final Security security) {
        this.appPermissionDao = appPermissionDao;
        this.security = security;
    }

    @Override
    public UserAppPermissions getPermissionsForUser(final UserRef userRef) {
        return security.secureResult(() -> {
            final Set<String> userPermissions = appPermissionDao.getPermissionsForUser(userRef.getUuid());
            return new UserAppPermissions(userRef, ALL_PERMISSIONS, userPermissions);
        });
    }

    @Override
    public void addPermission(final UserRef userRef, final String permission) {
        security.secure(PermissionNames.MANAGE_USERS_PERMISSION,
                () -> appPermissionDao.addPermission(userRef.getUuid(), permission));
    }

    @Override
    public void removePermission(final UserRef userRef, final String permission) {
        security.secure(PermissionNames.MANAGE_USERS_PERMISSION,
                () -> appPermissionDao.removePermission(userRef.getUuid(), permission));
    }
}
