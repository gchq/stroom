/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.docstore.impl;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.docref.DocRef;
import stroom.docstore.impl.db.jooq.tables.Doc;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Clearable;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Optional;

@Singleton
@EntityEventHandler(action = {
        EntityAction.CREATE,
        EntityAction.UPDATE,
        EntityAction.DELETE,
        EntityAction.UPDATE_EXPLORER_NODE})
class DocRefToNameCache implements EntityEvent.Handler, Clearable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DocRefToNameCache.class);
    private static final String CACHE_NAME = "Doc Ref To Name Cache";

    private final LoadingStroomCache<DocRef, Optional<String>> cache;

    @Inject
    DocRefToNameCache(final CacheManager cacheManager,
                      final Provider<DocStoreConfig> docStoreConfigProvider,
                      final Persistence persistence) {

        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> docStoreConfigProvider.get().getDocRefInfoCache(),
                docRef -> load(persistence, docRef));
    }

    private Optional<String> load(final Persistence persistence,
                                  final DocRef docRef) {
        LOGGER.trace("load: {}", docRef);
        try {
            // Persistence has no permission checks, so no asProcessingUser() needed.
            // The cache stores results for ALL users; permission filtering happens
            // at the boundary (DocRefInfoServiceImpl).
            return persistence.getName(docRef);
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
            return Optional.empty();
        }
    }

    Optional<String> getName(final DocRef docRef) {
        return cache.get(docRef);
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public void onChange(final EntityEvent event) {
        if (event != null) {
            // Need to handle all types as we are caching an optional, e.g.
            // If you do a delete then an empty is loaded into the cache,
            // then the same doc is created again, then we need the empty to
            // be evicted.
            LOGGER.debug("Invalidating entry for {}", event);
            cache.invalidate(event.getDocRef());
        }
    }
}
