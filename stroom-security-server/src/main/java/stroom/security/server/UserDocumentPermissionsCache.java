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
import stroom.entity.server.event.EntityEvent;
import stroom.entity.server.event.EntityEventBus;
import stroom.entity.server.event.EntityEventHandler;
import stroom.entity.shared.EntityAction;
import stroom.node.server.StroomPropertyService;
import stroom.query.api.v2.DocRef;
import stroom.security.shared.UserRef;
import stroom.util.cache.CacheManager;
import stroom.util.cache.CacheUtil;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@EntityEventHandler(action = EntityAction.CLEAR_CACHE)
public class UserDocumentPermissionsCache implements EntityEvent.Handler {
    private static final String MAXIMUM_SIZE_PROPERTY = "stroom.security.documentPermissions.maxCacheSize";

    private static final int DEFAULT_MAXIMUM_SIZE = 100000;

    private final Provider<EntityEventBus> eventBusProvider;
    private final StroomPropertyService stroomPropertyService;
    private final UserGroupsCache userGroupsCache;

    private final CacheManager cacheManager;
    private final DocumentPermissionService documentPermissionService;

    private volatile Integer lastMaximumSize;
    private volatile LoadingCache<UserRef, UserDocumentPermissions> cache;

    @Inject
    public UserDocumentPermissionsCache(final CacheManager cacheManager,
                                        final DocumentPermissionService documentPermissionService,
                                        final Provider<EntityEventBus> eventBusProvider,
                                        final StroomPropertyService stroomPropertyService,
                                        final UserGroupsCache userGroupsCache) {
        this.cacheManager = cacheManager;
        this.documentPermissionService = documentPermissionService;
        this.eventBusProvider = eventBusProvider;
        this.stroomPropertyService = stroomPropertyService;
        this.userGroupsCache = userGroupsCache;
    }

    UserDocumentPermissions get(final UserRef userRef) {
        return getCache().getUnchecked(userRef);
    }

    void removeAll() {
        clear();
        final EntityEventBus entityEventBus = eventBusProvider.get();
        EntityEvent.fire(entityEventBus, null, EntityAction.CLEAR_CACHE);
    }

    void clear() {
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
        return stroomPropertyService.getIntProperty(MAXIMUM_SIZE_PROPERTY, DEFAULT_MAXIMUM_SIZE);
    }

    private LoadingCache<UserRef, UserDocumentPermissions> getCache() {
        if (lastMaximumSize == null || lastMaximumSize != getMaximumSize()) {
            createCache();
        }
        return cache;
    }

    @SuppressWarnings("unchecked")
    private synchronized void createCache() {
        final int maximumSize = getMaximumSize();
        if (lastMaximumSize == null || lastMaximumSize != maximumSize) {
            final CacheLoader<UserRef, UserDocumentPermissions> cacheLoader = CacheLoader.from(userRef -> {
                final List<UserRef> userGroups = userGroupsCache.get(userRef);
                final Set<String> users = new HashSet<>();
                users.add(userRef.getUuid());
                users.addAll(userGroups.stream().map(DocRef::getUuid).collect(Collectors.toSet()));
                return documentPermissionService.getPermissionsForUsers(users);
            });
            final CacheBuilder cacheBuilder = CacheBuilder.newBuilder()
                    .maximumSize(maximumSize)
                    .expireAfterAccess(10, TimeUnit.MINUTES);
            final LoadingCache<UserRef, UserDocumentPermissions> cache = cacheBuilder.build(cacheLoader);
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
