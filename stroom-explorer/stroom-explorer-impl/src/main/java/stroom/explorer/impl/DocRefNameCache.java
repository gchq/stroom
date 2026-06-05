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

package stroom.explorer.impl;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerActionHandler;
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

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Singleton
@EntityEventHandler(action = {
        EntityAction.CREATE,
        EntityAction.UPDATE,
        EntityAction.DELETE,
        EntityAction.UPDATE_EXPLORER_NODE})
class DocRefNameCache implements EntityEvent.Handler, Clearable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DocRefNameCache.class);
    private static final String CACHE_NAME = "Doc Ref Name Cache";

    private final LoadingStroomCache<TypeAndName, List<DocRef>> cache;
    private final SecurityContext securityContext;
    private final ExplorerActionHandlers explorerActionHandlers;

    @Inject
    DocRefNameCache(final CacheManager cacheManager,
                    final Provider<ExplorerConfig> explorerConfigProvider,
                    final SecurityContext securityContext,
                    final ExplorerActionHandlers explorerActionHandlers) {
        this.securityContext = securityContext;
        this.explorerActionHandlers = explorerActionHandlers;

        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> explorerConfigProvider.get().getDocRefNameCache(),
                this::loadDocRefsByName);
    }

    private List<DocRef> loadDocRefsByName(final TypeAndName typeAndName) {
        return securityContext.asProcessingUserResult(() -> {
            LOGGER.trace("loadDocRefsByName: {}", typeAndName);
            try {
                final ExplorerActionHandler handler = explorerActionHandlers.getHandler(typeAndName.type());
                if (handler != null) {
                    final List<DocRef> result = handler.findByName(typeAndName.name(), false);
                    return result != null
                            ? result
                            : Collections.emptyList();
                }
            } catch (final RuntimeException e) {
                LOGGER.debug(e::getMessage, e);
            }
            return Collections.emptyList();
        });
    }

    List<DocRef> get(final String type, final String name) {
        Objects.requireNonNull(type, "Null type");
        Objects.requireNonNull(name, "Null name");
        return cache.get(new TypeAndName(type, name));
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public void onChange(final EntityEvent event) {
        if (event != null) {
            // We don't know the old name on rename/delete, so clear the entire cache.
            // This is acceptable because entity events are relatively infrequent compared to reads.
            LOGGER.debug("Clearing all entries due to {}", event);
            cache.clear();
        }
    }

    record TypeAndName(String type, String name) {

    }
}
