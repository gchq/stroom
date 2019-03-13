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
import stroom.docref.DocRef;
import stroom.entity.shared.EntityEvent;
import stroom.entity.shared.EntityEventBus;
import stroom.entity.shared.EntityEventHandler;
import stroom.security.service.DocumentPermissionService;
import stroom.util.shared.Clearable;
import stroom.entity.shared.EntityAction;
import stroom.security.shared.DocumentPermissions;
import stroom.cache.api.CacheManager;
import stroom.cache.api.CacheUtil;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.concurrent.TimeUnit;

@Singleton
@EntityEventHandler(action = EntityAction.CLEAR_CACHE)
class DocumentPermissionsCache implements EntityEvent.Handler, Clearable {
    private static final int MAX_CACHE_ENTRIES = 10000;

    private final LoadingCache<String, DocumentPermissions> cache;

    @Inject
    @SuppressWarnings("unchecked")
    DocumentPermissionsCache(final CacheManager cacheManager,
                             final DocumentPermissionService documentPermissionService) {

        final CacheLoader<String, DocumentPermissions> cacheLoader =
                CacheLoader.from(documentPermissionService::getPermissionsForDocument);
        final CacheBuilder cacheBuilder = CacheBuilder.newBuilder()
                .maximumSize(MAX_CACHE_ENTRIES)
                .expireAfterAccess(30, TimeUnit.MINUTES);
        cache = cacheBuilder.build(cacheLoader);
        cacheManager.registerCache("Document Permissions Cache", cacheBuilder, cache);
    }

    DocumentPermissions get(final String key) {
        return cache.getUnchecked(key);
    }

    void remove(final String docRefUuid) {
        cache.invalidate(docRefUuid);
    }

    @Override
    public void clear() {
        CacheUtil.clear(cache);
    }

    @Override
    public void onChange(final EntityEvent event) {
        cache.invalidate(event.getDocRef());
    }
}
