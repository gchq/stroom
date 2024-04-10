/*
 * Copyright 2017 Crown Copyright
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
        EntityAction.UPDATE_EXPLORER_NODE})
class DocRefInfoCache implements EntityEvent.Handler, Clearable {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocRefInfoCache.class);
    private static final String CACHE_NAME = "Doc Ref Info Cache";

    // Effectively docUuid => optDocRefInfo
    private final LoadingStroomCache<DocRefCacheKey, Optional<DocRefInfo>> cache;
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

    private Optional<DocRefInfo> loadDocRefInfo(final DocRefCacheKey docRefCacheKey) {
        DocRefInfo docRefInfo = null;

        try {
            docRefInfo = securityContext.asProcessingUserResult(() -> {
                final DocRef docRef = docRefCacheKey.getDocRef();
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
        final String uuid = docRef.getUuid();
        // No type so need to check all handlers and return the one that has it.
        // Hopefully next time it will still be in the cache so this won't be needed
        final Set<String> typesChecked = new HashSet<>();
        final Optional<DocRefInfo> optInfo = documentActionHandlersProvider.get()
                .stream()
                .map(handler -> {
                    typesChecked.add(handler.getType());
                    try {
                        return handler.info(uuid);
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
                                        return handler.info(uuid);
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
        final String uuid = docRef.getUuid();

        DocRefInfo docRefInfo = NullSafe.get(
                documentActionHandlersProvider.get().getHandler(type),
                handler -> handler.info(uuid));

        if (docRefInfo == null) {
            docRefInfo = NullSafe.get(
                    explorerActionHandlers.getHandler(type),
                    handler -> handler.info(uuid));
        }
        return docRefInfo;
    }

    Optional<DocRefInfo> get(final DocRef docRef) {
        return cache.get(new DocRefCacheKey(docRef));
    }

    Optional<DocRefInfo> get(final String docUuid) {
        return cache.get(new DocRefCacheKey(docUuid));
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public void onChange(final EntityEvent event) {
        if (event != null && !EntityAction.CREATE.equals(event.getAction())) {
            LOGGER.debug("Invalidating entry for {}", event);
            cache.invalidate(new DocRefCacheKey(event.getDocRef()));
        }
    }


    // --------------------------------------------------------------------------------


    /**
     * Custom cache key that holds a {@link DocRef} but only does equals/hashcode on
     * the doc's UUID. The UUID is unique without the type.
     */
    private static class DocRefCacheKey {

        private final DocRef docRef;
        private transient int hashCode = -1;

        private DocRefCacheKey(final DocRef docRef) {
            this.docRef = Objects.requireNonNull(docRef);
        }

        private DocRefCacheKey(final String uuid) {
            this.docRef = new DocRef(null, Objects.requireNonNull(uuid));
        }

        public DocRef getDocRef() {
            return docRef;
        }

        public String getType() {
            return docRef.getType();
        }

        public String getUuid() {
            return docRef.getUuid();
        }

        public String getName() {
            return docRef.getName();
        }

        public boolean hasType() {
            return docRef.getType() != null;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof final DocRefCacheKey docRefCacheKey)) {
                return false;
            }
            return Objects.equals(docRef.getUuid(), docRefCacheKey.getUuid());
        }

        @Override
        public int hashCode() {
            // In the unlikely event that the hash is actually -1 then it just means we compute every time
            if (hashCode == -1) {
                hashCode = Objects.hash(docRef.getUuid());
            }
            return hashCode;
        }
    }
}
