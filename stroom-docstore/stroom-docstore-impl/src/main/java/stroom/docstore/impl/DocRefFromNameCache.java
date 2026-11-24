/*
 * Copyright 2026 Crown Copyright
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
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Clearable;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Singleton
@EntityEventHandler(action = {
        EntityAction.CREATE,
        EntityAction.UPDATE,
        EntityAction.DELETE,
        EntityAction.UPDATE_EXPLORER_NODE})
class DocRefFromNameCache implements EntityEvent.Handler, Clearable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DocRefFromNameCache.class);
    private static final String CACHE_NAME = "Doc Ref From Name Cache";

    // Cache keyed by name only — one entry per name, all types included.
    private final LoadingStroomCache<TypeAndName, List<DocRef>> cache;

    @Inject
    DocRefFromNameCache(final CacheManager cacheManager,
                        final Provider<DocStoreConfig> docStoreConfigProvider,
                        final Persistence persistence) {

        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> docStoreConfigProvider.get().getDocRefNameCache(),
                name -> load(persistence, name));
    }

    private List<DocRef> load(final Persistence persistence,
                              final TypeAndName typeAndName) {
        LOGGER.trace("load: {}", typeAndName);
        // Persistence has no permission checks, so no asProcessingUser() needed.
        // Gets ALL types for this name; type filtering happens at retrieval.
        // A failure is thrown rather than returned as an empty list, so it isn't cached as "no such document".
        final List<DocRef> result = persistence.find(typeAndName.type, typeAndName.name, false);
        return result != null
                ? result
                : Collections.emptyList();
    }

    /**
     * Get documents with the given name, filtered to a single type.
     */
    List<DocRef> get(final String type, final String name) {
        Objects.requireNonNull(type, "Null type");
        Objects.requireNonNull(name, "Null name");
        final List<DocRef> docRefs;
        try {
            docRefs = cache.get(new TypeAndName(type, name));
        } catch (final RuntimeException e) {
            // The load failed, so nothing was cached and the next lookup tries again.
            LOGGER.debug(() -> LogUtil.message("Unable to find documents of type {} named '{}': {}",
                    type, name, LogUtil.exceptionMessage(e)), e);
            return Collections.emptyList();
        }
        return docRefs.stream()
                .filter(docRef -> type.equals(docRef.getType()))
                .collect(Collectors.toList());
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public void onChange(final EntityEvent event) {
        if (event != null) {
            // Invalidate the document's name and, on a rename, its old name, so neither lookup is stale.
            // If the event doesn't say which names, clear the entire cache. This is acceptable because entity
            // events are relatively infrequent compared to reads.
            LOGGER.debug("Invalidating entries due to {}", event);
            final DocRef docRef = event.getDocRef();
            if (NullSafe.allNonNull(docRef, docRef.getType(), docRef.getName())) {
                cache.invalidate(new TypeAndName(docRef.getType(), docRef.getName()));
                final DocRef oldDocRef = event.getOldDocRef();
                if (oldDocRef != null && NullSafe.allNonNull(oldDocRef.getType(), oldDocRef.getName())) {
                    cache.invalidate(new TypeAndName(oldDocRef.getType(), oldDocRef.getName()));
                }
            } else {
                cache.clear();
            }
        }
    }


    // --------------------------------------------------------------------------------


    private record TypeAndName(String type, String name) {

    }
}
