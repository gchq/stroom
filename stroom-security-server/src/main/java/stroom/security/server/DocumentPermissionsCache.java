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
import stroom.security.shared.DocumentPermissions;
import stroom.util.cache.CacheManager;
import stroom.util.cache.CacheUtil;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.concurrent.TimeUnit;

@Component
@EntityEventHandler(action = EntityAction.CLEAR_CACHE)
public class DocumentPermissionsCache implements EntityEvent.Handler {
    public static final String MAXIMUM_SIZE_PROPERTY = "stroom.security.documentPermissions.maxCacheSize";

    private static final int DEFAULT_MAXIMUM_SIZE = 100000;

    private final Provider<EntityEventBus> eventBusProvider;
    private final StroomPropertyService stroomPropertyService;

    private final CacheManager cacheManager;
    private final DocumentPermissionService documentPermissionService;

    private volatile Integer lastMaximumSize;
    private volatile LoadingCache<DocRef, DocumentPermissions> cache;

    @Inject
    public DocumentPermissionsCache(final CacheManager cacheManager,
                                    final DocumentPermissionService documentPermissionService,
                                    final Provider<EntityEventBus> eventBusProvider,
                                    final StroomPropertyService stroomPropertyService) {
        this.cacheManager = cacheManager;
        this.documentPermissionService = documentPermissionService;
        this.eventBusProvider = eventBusProvider;
        this.stroomPropertyService = stroomPropertyService;
    }

    DocumentPermissions get(final DocRef key) {
        return getCache().getUnchecked(key);
    }

    void remove(final DocRef docRef) {
        if (cache != null) {
            cache.invalidate(docRef);
        }
        final EntityEventBus entityEventBus = eventBusProvider.get();
        EntityEvent.fire(entityEventBus, docRef, EntityAction.CLEAR_CACHE);
    }

    void clear() {
        if (cache != null) {
            CacheUtil.clear(cache);
        }
    }

    @Override
    public void onChange(final EntityEvent event) {
        if (cache != null) {
            cache.invalidate(event.getDocRef());
        }
    }

    private int getMaximumSize() {
        return stroomPropertyService.getIntProperty(MAXIMUM_SIZE_PROPERTY, DEFAULT_MAXIMUM_SIZE);
    }

    private LoadingCache<DocRef, DocumentPermissions> getCache() {
        if (lastMaximumSize == null || lastMaximumSize != getMaximumSize()) {
            createCache();
        }
        return cache;
    }

    @SuppressWarnings("unchecked")
    private synchronized void createCache() {
        final int maximumSize = getMaximumSize();
        if (lastMaximumSize == null || lastMaximumSize != maximumSize) {
            final CacheLoader<DocRef, DocumentPermissions> cacheLoader = CacheLoader.from(documentPermissionService::getPermissionsForDocument);
            final CacheBuilder cacheBuilder = CacheBuilder.newBuilder()
                    .maximumSize(maximumSize)
                    .expireAfterAccess(30, TimeUnit.MINUTES);
            final LoadingCache<DocRef, DocumentPermissions> cache = cacheBuilder.build(cacheLoader);
            if (lastMaximumSize == null) {
                cacheManager.registerCache("Document Permissions Cache", cacheBuilder, cache);
            } else {
                cacheManager.replaceCache("Document Permissions Cache", cacheBuilder, cache);
            }
            lastMaximumSize = maximumSize;
            this.cache = cache;
        }
    }
}
