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
import stroom.entity.shared.EntityAction;
import stroom.entity.shared.EntityEvent;
import stroom.entity.shared.EntityEventBus;
import stroom.entity.shared.EntityEventHandler;
import stroom.security.shared.User;
import stroom.util.shared.Clearable;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Singleton
@EntityEventHandler(action = EntityAction.CLEAR_CACHE)
public class UserDocumentPermissionsCache implements EntityEvent.Handler, Clearable {
    private final Provider<EntityEventBus> eventBusProvider;
    private final AuthorisationConfig authorisationConfig;
    private final UserGroupsCache userGroupsCache;

    private final CacheManager cacheManager;
    private final DocumentPermissionService documentPermissionService;

    private volatile Integer lastMaximumSize;
    private volatile LoadingCache<String, UserDocumentPermissions> cache;

    @Inject
    public UserDocumentPermissionsCache(final CacheManager cacheManager,
                                        final DocumentPermissionService documentPermissionService,
                                        final Provider<EntityEventBus> eventBusProvider,
                                        final AuthorisationConfig authorisationConfig,
                                        final UserGroupsCache userGroupsCache) {
        this.cacheManager = cacheManager;
        this.documentPermissionService = documentPermissionService;
        this.eventBusProvider = eventBusProvider;
        this.authorisationConfig = authorisationConfig;
        this.userGroupsCache = userGroupsCache;
    }

    UserDocumentPermissions get(final String userUuid) {
        return getCache().getUnchecked(userUuid);
    }

    void removeAll() {
        clear();
        final EntityEventBus entityEventBus = eventBusProvider.get();
        EntityEvent.fire(entityEventBus, null, EntityAction.CLEAR_CACHE);
    }

    @Override
    public void clear() {
        if (cache != null) {
            CacheUtil.clear(cache);
        }
    }

    @Override
    public void onChange(final EntityEvent event) {
        if (cache != null) {
            clear();
//            cache.invalidate(event.getDocRef());
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
            final CacheLoader<String, UserDocumentPermissions> cacheLoader = CacheLoader.from(userUuid -> {
                final List<User> userGroups = userGroupsCache.get(userUuid);
                final Set<String> users = new HashSet<>();
                users.add(userUuid);
                users.addAll(userGroups.stream().map(User::getUuid).collect(Collectors.toSet()));
                return documentPermissionService.getPermissionsForUsers(users);
            });
            final CacheBuilder cacheBuilder = CacheBuilder.newBuilder()
                    .maximumSize(maximumSize)
                    .expireAfterAccess(10, TimeUnit.MINUTES);
            final LoadingCache<String, UserDocumentPermissions> cache = cacheBuilder.build(cacheLoader);
            if (lastMaximumSize == null) {
                cacheManager.registerCache("User Document Permissions Cache", cacheBuilder, cache);
            } else {
                cacheManager.replaceCache("User Document Permissions Cache", cacheBuilder, cache);
            }
            lastMaximumSize = maximumSize;
            this.cache = cache;
        }
    }
}
