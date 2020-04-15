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

import stroom.cache.api.CacheManager;
import stroom.cache.api.ICache;
import stroom.security.impl.event.AddPermissionEvent;
import stroom.security.impl.event.ClearDocumentPermissionsEvent;
import stroom.security.impl.event.ClearUserPermissionsEvent;
import stroom.security.impl.event.PermissionChangeEvent;
import stroom.security.impl.event.PermissionChangeEventHandler;
import stroom.security.impl.event.RemovePermissionEvent;
import stroom.util.shared.Clearable;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@PermissionChangeEventHandler
public class UserDocumentPermissionsCache implements PermissionChangeEvent.Handler, Clearable {
    private static final String CACHE_NAME = "User Document Permissions Cache";

    private final ICache<String, UserDocumentPermissions> cache;

    @Inject
    public UserDocumentPermissionsCache(final CacheManager cacheManager,
                                        final DocumentPermissionDao documentPermissionDao,
                                        final AuthorisationConfig authorisationConfig) {
        cache = cacheManager.create(CACHE_NAME, authorisationConfig::getUserDocumentPermissionsCache, documentPermissionDao::getPermissionsForUser);
    }

    UserDocumentPermissions get(final String userUuid) {
        return cache.get(userUuid);
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public void onChange(final PermissionChangeEvent event) {
        if (cache != null) {
            if (event instanceof AddPermissionEvent) {
                final AddPermissionEvent addPermissionEvent = (AddPermissionEvent) event;
                cache.getOptional(addPermissionEvent.getUserUuid()).ifPresent(userDocumentPermissions ->
                        userDocumentPermissions.addPermission(addPermissionEvent.getDocumentUuid(), addPermissionEvent.getPermission()));

            } else if (event instanceof RemovePermissionEvent) {
                final RemovePermissionEvent removePermissionEvent = (RemovePermissionEvent) event;
                cache.getOptional(removePermissionEvent.getUserUuid()).ifPresent(userDocumentPermissions ->
                        userDocumentPermissions.removePermission(removePermissionEvent.getDocumentUuid(), removePermissionEvent.getPermission()));

            } else if (event instanceof ClearDocumentPermissionsEvent) {
                final ClearDocumentPermissionsEvent clearDocumentPermissionsEvent = (ClearDocumentPermissionsEvent) event;
                cache.values().forEach(userDocumentPermissions -> {
                    if (userDocumentPermissions != null) {
                        userDocumentPermissions.clearDocumentPermissions(clearDocumentPermissionsEvent.getDocumentUuid());
                    }
                });

            } else if (event instanceof ClearUserPermissionsEvent) {
                final ClearUserPermissionsEvent clearUserPermissionsEvent = (ClearUserPermissionsEvent) event;
                cache.invalidate(clearUserPermissionsEvent.getUserUuid());
            }
        }
    }
}