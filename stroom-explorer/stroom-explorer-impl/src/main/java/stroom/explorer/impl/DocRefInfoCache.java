/*
 * Copyright 2024 Crown Copyright
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

package stroom.explorer.impl;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.docstore.api.DocumentActionHandlers;
import stroom.security.api.SecurityContext;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Clearable;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Function;

@Singleton
@EntityEventHandler(action = {
        EntityAction.CREATE,
        EntityAction.UPDATE,
        EntityAction.DELETE,
        EntityAction.UPDATE_EXPLORER_NODE})
class DocRefInfoCache implements EntityEvent.Handler, Clearable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DocRefInfoCache.class);
    private static final String CACHE_NAME = "Doc Ref Info Cache";
    public static final String UNKNOWN_TYPE = "UNKNOWN";

    // Effectively docUuid => optDocRefInfo
    private final LoadingStroomCache<DocRef, Optional<DocRefInfo>> cache;
    private final SecurityContext securityContext;
    // Provider to avoid circular guice dependency issue
    private final Provider<DocumentActionHandlers> documentActionHandlersProvider;
    private final ExplorerActionHandlers explorerActionHandlers;

    private Map<String, Function<DocRef, DocRefInfo>> handlers;

    @Inject
    DocRefInfoCache(final CacheManager cacheManager,
                    final Provider<ExplorerConfig> explorerConfigProvider,
                    final SecurityContext securityContext,
                    final Provider<DocumentActionHandlers> documentActionHandlersProvider,
                    final ExplorerActionHandlers explorerActionHandlers) {
        this.securityContext = securityContext;
        this.documentActionHandlersProvider = documentActionHandlersProvider;
        this.explorerActionHandlers = explorerActionHandlers;

        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> explorerConfigProvider.get().getDocRefInfoCache(),
                this::loadDocRefInfo);
    }

    private Optional<DocRefInfo> loadDocRefInfo(final DocRef docRef) {
        LOGGER.trace("loadDocRefInfo: {}", docRef);
        DocRefInfo docRefInfo = null;

        try {
            docRefInfo = securityContext.asProcessingUserResult(() -> {
                if (UNKNOWN_TYPE.equals(docRef.getType())) {
                    return getDocRefInfoWithoutType(docRef);
                } else {
                    return getDocRefInfoWithType(docRef);
                }
            });
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
        }
        return Optional.ofNullable(docRefInfo);
    }

    private DocRefInfo getDocRefInfoWithoutType(final DocRef docRef) {
        // Throw an exception to find out what calls this code.
        if (LOGGER.isDebugEnabled()) {
            try {
                throw new RuntimeException();
            } catch (final RuntimeException e) {
                LOGGER.debug("Getting document info without type for: {}", docRef, e);
            }
        }

        // No type so need to check all handlers and return the one that has it.
        // Hopefully next time it will still be in the cache so this won't be needed
        final Map<String, Function<DocRef, DocRefInfo>> handlers = getHandlers();
        for (final Entry<String, Function<DocRef, DocRefInfo>> entry : handlers.entrySet()) {
            try {
                final String type = entry.getKey();
                final Function<DocRef, DocRefInfo> function = entry.getValue();
                final DocRef typeFixedDocRef = createTypeSpecificDocRef(type, docRef);
                final DocRefInfo docRefInfo = function.apply(typeFixedDocRef);
                if (docRefInfo != null) {
                    return docRefInfo;
                }
            } catch (final Exception e) {
                LOGGER.debug(e::getMessage, e);
            }
        }

        return null;
    }

    private Map<String, Function<DocRef, DocRefInfo>> getHandlers() {
        if (handlers != null) {
            return handlers;

        } else {
            final Map<String, Function<DocRef, DocRefInfo>> map = new HashMap<>();
            documentActionHandlersProvider.get()
                    .stream()
                    .forEach(handler -> {
                        final Function<DocRef, DocRefInfo> function = handler::info;
                        map.putIfAbsent(handler.getType(), function);
                    });
            explorerActionHandlers.stream()
                    .forEach(handler -> {
                        final Function<DocRef, DocRefInfo> function = handler::info;
                        map.putIfAbsent(handler.getType(), function);
                    });
            handlers = map;
        }
        return handlers;
    }

    private DocRef createTypeSpecificDocRef(final String type, final DocRef docRef) {
        return DocRef
                .builder()
                .type(type)
                .uuid(docRef.getUuid())
                .name(docRef.getName())
                .build();
    }

    private DocRefInfo getDocRefInfoWithType(final DocRef docRef) {
        final Map<String, Function<DocRef, DocRefInfo>> handlers = getHandlers();
        final Function<DocRef, DocRefInfo> function = handlers.get(docRef.getType());
        if (function == null) {
            throw new RuntimeException("No handler found for type: " + docRef.getType());
        }
        return function.apply(docRef);
    }

    Optional<DocRefInfo> get(final DocRef docRef) {
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
