/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.security.api.AppPermissionService;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.AppUserPermissions;
import stroom.security.shared.AppUserPermissionsReport;
import stroom.security.shared.FetchAppUserPermissionsRequest;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserDocRefUtil;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
class AppPermissionServiceImpl implements AppPermissionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppPermissionServiceImpl.class);

    private final AppPermissionDao appPermissionDao;
    private final EntityEventBus entityEventBus;
    private final SecurityContext securityContext;
    private final UserGroupsCache userGroupsCache;

    @Inject
    AppPermissionServiceImpl(final AppPermissionDao appPermissionDao,
                             final EntityEventBus entityEventBus,
                             final SecurityContext securityContext,
                             final UserGroupsCache userGroupsCache) {
        this.appPermissionDao = appPermissionDao;
        this.entityEventBus = entityEventBus;
        this.securityContext = securityContext;
        this.userGroupsCache = userGroupsCache;
    }

    private boolean canUserChangePermission() {
        return securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION);
    }

    @Override
    public ResultPage<AppUserPermissions> fetchAppUserPermissions(final FetchAppUserPermissionsRequest request) {
        return securityContext.secureResult(() -> {
            final UserRef userRef = securityContext.getUserRef();
            Objects.requireNonNull(userRef, "Null user");

            FetchAppUserPermissionsRequest modified = request;

            // If the current user is not allowed to change permissions then only show them permissions for themselves.
            if (!canUserChangePermission()) {
                modified = new FetchAppUserPermissionsRequest
                        .Builder(request)
                        .userRef(userRef)
                        .build();
            }

            return appPermissionDao.fetchAppUserPermissions(modified);
        });
    }

    @Override
    public Set<AppPermission> getDirectAppUserPermissions(final UserRef userRef) {
        if (!securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)) {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to get app permissions");
        }
        return appPermissionDao.getPermissionsForUser(userRef.getUuid());
    }

    @Override
    public AppUserPermissionsReport getAppUserPermissionsReport(final UserRef userRef) {
        if (!securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)) {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to get app permissions");
        }
        final Map<AppPermission, List<List<UserRef>>> inheritedPermissions = new HashMap<>();
        final Set<UserRef> cyclicPrevention = new HashSet<>();
        final List<UserRef> parentPath = Collections.emptyList();
        addDeepPermissionsAndPaths(userRef, parentPath, inheritedPermissions, cyclicPrevention);

        final Set<AppPermission> explicitPermissions = appPermissionDao.getPermissionsForUser(userRef.getUuid());
        return new AppUserPermissionsReport(explicitPermissions, convertToPaths(inheritedPermissions));
    }

    private <T> Map<T, List<String>> convertToPaths(final Map<T, List<List<UserRef>>> map) {
        return map
                .entrySet()
                .stream()
                .collect(Collectors.toMap(Entry::getKey, entry -> {
                    return entry.getValue()
                            .stream()
                            .map(list -> list.stream()
                                    .map(UserRef::toDisplayString)
                                    .collect(Collectors.joining(" > ")))
                            .toList();
                }));
    }

    private void addDeepPermissionsAndPaths(final UserRef userRef,
                                            final List<UserRef> parentPath,
                                            final Map<AppPermission, List<List<UserRef>>> inheritedPermissions,
                                            final Set<UserRef> cyclicPrevention) {
        if (cyclicPrevention.add(userRef)) {
            final Set<UserRef> parentGroups = userGroupsCache.getGroups(userRef);
            if (parentGroups != null) {
                for (final UserRef group : parentGroups) {
                    final List<UserRef> path = new ArrayList<>(parentPath);
                    path.add(group);

                    final Set<AppPermission> permissions = appPermissionDao.getPermissionsForUser(group.getUuid());
                    permissions.forEach(appPermission -> {
                        inheritedPermissions.computeIfAbsent(appPermission, k -> new ArrayList<>()).add(path);
                    });

                    addDeepPermissionsAndPaths(group, path, inheritedPermissions, cyclicPrevention);
                }
            }
        }
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
