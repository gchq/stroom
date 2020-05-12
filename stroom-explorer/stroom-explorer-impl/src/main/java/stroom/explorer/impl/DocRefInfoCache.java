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
import stroom.cache.api.ICache;
import stroom.docref.DocRef;
import stroom.docref.DocRefInfo;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.security.api.SecurityContext;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.shared.Clearable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

@Singleton
@EntityEventHandler(action = {EntityAction.CREATE, EntityAction.UPDATE})
class DocRefInfoCache implements EntityEvent.Handler, Clearable {
    private static final Logger LOGGER = LoggerFactory.getLogger(DocRefInfoCache.class);
    private static final String CACHE_NAME = "Doc Ref Info Cache";

    private final ICache<DocRef, Optional<DocRefInfo>> cache;

    @Inject
    DocRefInfoCache(final CacheManager cacheManager,
                    final ExplorerActionHandlers explorerActionHandlers,
                    final ExplorerConfig explorerConfig,
                    final SecurityContext securityContext) {
        cache = cacheManager.create(CACHE_NAME, explorerConfig::getDocRefInfoCache, docRef -> {
            DocRefInfo docRefInfo = null;

            try {
                docRefInfo = securityContext.asProcessingUserResult(() -> {
                    final ExplorerActionHandler handler = explorerActionHandlers.getHandler(docRef.getType());
                    if (handler == null) {
                        return null;
                    }
                    return handler.info(docRef.getUuid());
                });
            } catch (final RuntimeException e) {
                LOGGER.debug(e.getMessage(), e);
            }

            return Optional.ofNullable(docRefInfo);
        });
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
        final DocRef docRef = event.getDocRef();
        cache.invalidate(docRef);
    }
}