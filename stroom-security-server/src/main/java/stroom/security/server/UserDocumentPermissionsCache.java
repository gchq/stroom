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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.springframework.stereotype.Component;
import stroom.node.server.StroomPropertyService;
import stroom.security.server.event.AddPermissionEvent;
import stroom.security.server.event.ClearDocumentPermissionsEvent;
import stroom.security.server.event.ClearUserPermissionsEvent;
import stroom.security.server.event.PermissionChangeEvent;
import stroom.security.server.event.PermissionChangeEventHandler;
import stroom.security.server.event.RemovePermissionEvent;
import stroom.util.cache.CacheManager;
import stroom.util.cache.CacheUtil;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

@Component
@PermissionChangeEventHandler
public class UserDocumentPermissionsCache implements PermissionChangeEvent.Handler {
    private static final String MAXIMUM_SIZE_PROPERTY = "stroom.security.documentPermissions.maxCacheSize";

    private static final int DEFAULT_MAXIMUM_SIZE = 1000;
    private static final long TEN_MINUTES = 10 * 60 * 1000;
    private static final String CACHE_NAME = "User Document Permissions Cache";

    private final StroomPropertyService stroomPropertyService;

    private final CacheManager cacheManager;
    private final DocumentPermissionService documentPermissionService;

    private volatile Integer lastMaximumSize;
    private volatile long lastMaximumSizeCheck;
    private volatile LoadingCache<String, UserDocumentPermissions> cache;

    @Inject
    public UserDocumentPermissionsCache(final CacheManager cacheManager,
                                        final DocumentPermissionService documentPermissionService,
                                        final StroomPropertyService stroomPropertyService) {
        this.cacheManager = cacheManager;
        this.documentPermissionService = documentPermissionService;
        this.stroomPropertyService = stroomPropertyService;
    }

    UserDocumentPermissions get(final String userUuid) {
        return getCache().getUnchecked(userUuid);
    }

    void clear() {
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
        int size = DEFAULT_MAXIMUM_SIZE;
        final long now = System.currentTimeMillis();
        if (lastMaximumSize == null || lastMaximumSizeCheck + TEN_MINUTES < now) {
            size = stroomPropertyService.getIntProperty(MAXIMUM_SIZE_PROPERTY, DEFAULT_MAXIMUM_SIZE);
            lastMaximumSizeCheck = now;
        }
        return size;
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
