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
import stroom.docstore.api.DocumentNotFoundException;
import stroom.security.api.SecurityContext;
import stroom.util.NullSafe;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.shared.Clearable;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Singleton
@EntityEventHandler(action = {
        EntityAction.CREATE,
        EntityAction.UPDATE,
        EntityAction.DELETE,
        EntityAction.UPDATE_EXPLORER_NODE})
class DocRefInfoCache implements EntityEvent.Handler, Clearable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocRefInfoCache.class);
    private static final String CACHE_NAME = "Doc Ref Info Cache";

    // Effectively docUuid => optDocRefInfo
    private final LoadingStroomCache<DocRef, Optional<DocRefInfo>> cache;
    private final SecurityContext securityContext;
    // Provider to avoid circular guice dependency issue
    private final Provider<DocumentActionHandlers> documentActionHandlersProvider;
    private final ExplorerActionHandlers explorerActionHandlers;


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
        DocRefInfo docRefInfo = null;

        try {
            docRefInfo = securityContext.asProcessingUserResult(() -> {
                if (docRef.getType() != null) {
                    return getDocRefInfoWithType(docRef);
                } else {
                    return getDocRefInfoWithoutType(docRef);
                }
            });
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
        }
        return Optional.ofNullable(docRefInfo);
    }

    private DocRefInfo getDocRefInfoWithoutType(final DocRef docRef) {
        // No type so need to check all handlers and return the one that has it.
        // Hopefully next time it will still be in the cache so this won't be needed
        final Set<String> typesChecked = new HashSet<>();
        final Optional<DocRefInfo> optInfo = documentActionHandlersProvider.get()
                .stream()
                .map(handler -> {
                    typesChecked.add(handler.getType());
                    try {
                        return handler.info(docRef);
                    } catch (DocumentNotFoundException e) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .findAny();

        // Folder is not a DocumentActionHandler so check in ExplorerActionHandlers
        return optInfo
                .or(() ->
                        explorerActionHandlers.stream()
                                .filter(handler ->
                                        !typesChecked.contains(handler.getDocumentType().getType()))
                                .map(handler -> {
                                    try {
                                        return handler.info(docRef);
                                    } catch (DocumentNotFoundException e) {
                                        return null;
                                    }
                                })
                                .filter(Objects::nonNull)
                                .findAny())
                .orElse(null);
    }

    private DocRefInfo getDocRefInfoWithType(final DocRef docRef) {
        final String type = docRef.getType();
        DocRefInfo docRefInfo = NullSafe.get(
                documentActionHandlersProvider.get().getHandler(type),
                handler -> handler.info(docRef));

        if (docRefInfo == null) {
            docRefInfo = NullSafe.get(
                    explorerActionHandlers.getHandler(type),
                    handler -> handler.info(docRef));
        }
        return docRefInfo;
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
        if (event != null && !EntityAction.CREATE.equals(event.getAction())) {
            LOGGER.debug("Invalidating entry for {}", event);
            cache.invalidate(event.getDocRef());
        }
    }
}
