/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.planb.impl;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.docstore.api.DocumentActionHandler;
import stroom.docstore.api.DocumentNotFoundException;
import stroom.docstore.api.DocumentTypeName;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.planb.shared.PlanBDocument;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Clearable;
import stroom.util.shared.PermissionException;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Singleton
@EntityEventHandler(action = {EntityAction.DELETE, EntityAction.UPDATE, EntityAction.CLEAR_CACHE})
public class PlanBDocCacheImpl implements PlanBDocCache, Clearable, EntityEvent.Handler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlanBDocCacheImpl.class);

    private static final String CACHE_NAME = "Plan B State Doc Cache";

    private final LoadingStroomCache<String, PlanBDocument> cache;
    private final SecurityContext securityContext;
    private final DocFinder docFinder;
    private final Provider<Map<DocumentTypeName, DocumentActionHandler>> documentActionHandlersProvider;
    private final Set<String> planBDocumentTypes;

    @Inject
    PlanBDocCacheImpl(final CacheManager cacheManager,
                      final SecurityContext securityContext,
                      final Provider<PlanBConfig> stateConfigProvider,
                      final DocFinder docFinder,
                      final Provider<Map<DocumentTypeName, DocumentActionHandler>> documentActionHandlersProvider,
                      @PlanBDocumentTypes final Set<String> planBDocumentTypes) {
        this.securityContext = securityContext;
        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> stateConfigProvider.get().getStateDocCache(),
                this::create);
        this.docFinder = docFinder;
        this.documentActionHandlersProvider = documentActionHandlersProvider;
        this.planBDocumentTypes = planBDocumentTypes;
    }

    private PlanBDocument create(final String name) {
        return securityContext.asProcessingUserResult(() -> {
            final Map<DocumentTypeName, DocumentActionHandler> handlers = documentActionHandlersProvider.get();

            PlanBDocument result = null;
            for (final String type : planBDocumentTypes) {
                final List<DocRef> matches = docFinder.findByName(type, name);
                for (final DocRef docRef : matches) {
                    final DocumentActionHandler<?> handler = handlers.get(new DocumentTypeName(docRef.getType()));
                    if (handler != null) {
                        final Object loaded = handler.readDocument(docRef);
                        if (loaded instanceof final PlanBDocument planBDoc) {
                            if (result != null) {
                                throw new RuntimeException(
                                        "Unexpectedly found more than one state doc with key: " + name);
                            }
                            result = planBDoc;
                        }
                    }
                }
            }

            if (result == null) {
                throw new DocumentNotFoundException(DocRef.builder().name(name).build());
            }
            return result;
        });
    }

    @Override
    public List<PlanBDocument> getAll() {
        return securityContext.asProcessingUserResult(() -> {
            final Map<DocumentTypeName, DocumentActionHandler> handlers = documentActionHandlersProvider.get();
            final List<PlanBDocument> results = new ArrayList<>();
            for (final String type : planBDocumentTypes) {
                final DocumentActionHandler<?> handler = handlers.get(new DocumentTypeName(type));
                if (handler instanceof final ImportExportActionHandler ieHandler) {
                    for (final DocRef docRef : ieHandler.listDocuments()) {
                        try {
                            final PlanBDocument doc = cache.get(docRef.getName());
                            if (doc != null) {
                                results.add(doc);
                            }
                        } catch (final Exception e) {
                            LOGGER.error("Error loading PlanB doc '{}': {}",
                                    docRef.getName(), e.getMessage(), e);
                        }
                    }
                }
            }
            return results;
        });
    }

    @Override
    public PlanBDocument get(final String name) {
        Objects.requireNonNull(name, "Null key supplied");
        final PlanBDocument doc = cache.get(name);

        final DocRef docRef = doc.asDocRef();
        if (!securityContext.hasDocumentPermission(docRef, DocumentPermission.USE)) {
            throw new PermissionException(
                    securityContext.getUserRef(),
                    LogUtil.message("You are not authorised to read {}", docRef));
        }

        return doc;
    }

    @Override
    public void remove(final String key) {
        cache.invalidate(key);
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public void onChange(final EntityEvent event) {
        // Ignore events for doc types that are not managed by this cache.
        final DocRef eventDocRef = event.getDocRef();
        if (eventDocRef == null || !planBDocumentTypes.contains(eventDocRef.getType())) {
            return;
        }

        LOGGER.debug("Received event {}", event);

        final EntityAction eventAction = event.getAction();

        switch (eventAction) {
            case UPDATE, DELETE -> {
                // Evict only the specific document that changed.
                // Fall back to clearing all if the name is not available in the event.
                final String name = event.getDocRef().getName();
                if (name != null) {
                    LOGGER.debug("Removing cache entry for '{}'", name);
                    remove(name);
                    // Also evict the old name on a rename so stale entries don't linger.
                    final DocRef oldDocRef = event.getOldDocRef();
                    if (oldDocRef != null && !Objects.equals(oldDocRef.getName(), name)) {
                        LOGGER.debug("Removing old cache entry for '{}'", oldDocRef.getName());
                        remove(oldDocRef.getName());
                    }
                } else {
                    LOGGER.debug("Clearing cache (no name in event)");
                    clear();
                }
            }
            case CLEAR_CACHE -> {
                LOGGER.debug("Clearing cache");
                clear();
            }
            default -> LOGGER.debug("Unexpected event action {}", eventAction);
        }
    }
}
