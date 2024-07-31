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

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.shared.PermissionException;
import stroom.util.shared.UserRef;

import java.util.Set;

@Singleton
class AppPermissionServiceImpl implements AppPermissionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppPermissionServiceImpl.class);

    private final AppPermissionDao appPermissionDao;
    private final EntityEventBus entityEventBus;
    private final SecurityContext securityContext;

    @Inject
    AppPermissionServiceImpl(final AppPermissionDao appPermissionDao,
                             final EntityEventBus entityEventBus,
                             final SecurityContext securityContext) {
        this.appPermissionDao = appPermissionDao;
        this.entityEventBus = entityEventBus;
        this.securityContext = securityContext;
    }

    @Override
    public Set<AppPermission> getPermissions(final UserRef userRef) {
        if (!securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)) {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to get app permissions");
        }
        return appPermissionDao.getPermissionsForUser(userRef.getUuid());
    }

    @Override
    public void addPermission(final UserRef userRef, final AppPermission permission) {
        if (!securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)) {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to add app permissions");
        }

        try {
            appPermissionDao.addPermission(userRef.getUuid(), permission);
            fireEntityChangeEvent(userRef);
        } catch (final RuntimeException e) {
            LOGGER.error("addPermission()", e);
            throw e;
        }
    }

    @Override
    public void removePermission(final UserRef userRef, final AppPermission permission) {
        if (!securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)) {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to remove app permissions");
        }

        try {
            appPermissionDao.removePermission(userRef.getUuid(), permission);
            fireEntityChangeEvent(userRef);
        } catch (final RuntimeException e) {
            LOGGER.error("removePermission()", e);
            throw e;
        }
    }

    private void fireEntityChangeEvent(final UserRef userRef) {
        EntityEvent.fire(
                entityEventBus,
                UserDocRefUtil.createDocRef(userRef),
                EntityAction.UPDATE);
    }
}
