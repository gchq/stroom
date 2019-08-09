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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import stroom.cache.api.CacheManager;
import stroom.cache.api.CacheUtil;
import stroom.security.impl.event.AddPermissionEvent;
import stroom.security.impl.event.ClearDocumentPermissionsEvent;
import stroom.security.impl.event.ClearUserPermissionsEvent;
import stroom.security.impl.event.PermissionChangeEvent;
import stroom.security.impl.event.PermissionChangeEventHandler;
import stroom.security.impl.event.RemovePermissionEvent;
import stroom.util.shared.Clearable;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.TimeUnit;

@Singleton
@PermissionChangeEventHandler
public class UserDocumentPermissionsCache implements PermissionChangeEvent.Handler, Clearable {
    private static final String CACHE_NAME = "User Document Permissions Cache";

    private final AuthorisationConfig authorisationConfig;

    private final CacheManager cacheManager;
    private final DocumentPermissionServiceImpl documentPermissionService;

    private volatile Integer lastMaximumSize;
    private volatile LoadingCache<String, UserDocumentPermissions> cache;

    @Inject
    public UserDocumentPermissionsCache(final CacheManager cacheManager,
                                        final DocumentPermissionServiceImpl documentPermissionService,
                                        final AuthorisationConfig authorisationConfig) {
        this.cacheManager = cacheManager;
        this.documentPermissionService = documentPermissionService;
        this.authorisationConfig = authorisationConfig;
    }

    UserDocumentPermissions get(final String userUuid) {
        return getCache().getUnchecked(userUuid);
    }

    @Override
    public void clear() {
        if (cache != null) {
            CacheUtil.clear(cache);
        }
    }

    @Override
    public void onChange(final PermissionChangeEvent event) {
        if (cache != null) {
            if (event instanceof AddPermissionEvent) {
                final AddPermissionEvent addPermissionEvent = (AddPermissionEvent) event;
                final UserDocumentPermissions userDocumentPermissions = getCache().asMap().get(addPermissionEvent.getUserUuid());
                if (userDocumentPermissions != null) {
                    userDocumentPermissions.addPermission(addPermissionEvent.getDocUuid(), addPermissionEvent.getPermission());
                }

            } else if (event instanceof RemovePermissionEvent) {
                final RemovePermissionEvent removePermissionEvent = (RemovePermissionEvent) event;
                final UserDocumentPermissions userDocumentPermissions = getCache().asMap().get(removePermissionEvent.getUserUuid());
                if (userDocumentPermissions != null) {
                    userDocumentPermissions.removePermission(removePermissionEvent.getDocUuid(), removePermissionEvent.getPermission());
                }

            } else if (event instanceof ClearDocumentPermissionsEvent) {
                final ClearDocumentPermissionsEvent clearDocumentPermissionsEvent = (ClearDocumentPermissionsEvent) event;
                getCache().asMap().values().forEach(userDocumentPermissions -> {
                    if (userDocumentPermissions != null) {
                        userDocumentPermissions.clearDocumentPermissions(clearDocumentPermissionsEvent.getDocUuid());
                    }
                });

            } else if (event instanceof ClearUserPermissionsEvent) {
                final ClearUserPermissionsEvent clearUserPermissionsEvent = (ClearUserPermissionsEvent) event;
                getCache().invalidate(clearUserPermissionsEvent.getUserUuid());
            }
        }
    }

    private int getMaximumSize() {
        return authorisationConfig.getMaxDocumentPermissionCacheSize();
    }

    private LoadingCache<String, UserDocumentPermissions> getCache() {
        if (lastMaximumSize == null || lastMaximumSize != getMaximumSize()) {
            createCache();
        }
        return cache;
    }

    @SuppressWarnings("unchecked")
    private synchronized void createCache() {
        final int maximumSize = getMaximumSize();
        if (lastMaximumSize == null || lastMaximumSize != maximumSize) {
            final CacheLoader<String, UserDocumentPermissions> cacheLoader = CacheLoader.from(documentPermissionService::getPermissionsForUser);
            final CacheBuilder cacheBuilder = CacheBuilder.newBuilder()
                    .maximumSize(maximumSize)
                    .expireAfterAccess(10, TimeUnit.MINUTES);
            final LoadingCache<String, UserDocumentPermissions> cache = cacheBuilder.build(cacheLoader);
            if (lastMaximumSize == null) {
                cacheManager.registerCache(CACHE_NAME, cacheBuilder, cache);
            } else {
                cacheManager.replaceCache(CACHE_NAME, cacheBuilder, cache);
            }
            lastMaximumSize = maximumSize;
            this.cache = cache;
        }
    }
}
