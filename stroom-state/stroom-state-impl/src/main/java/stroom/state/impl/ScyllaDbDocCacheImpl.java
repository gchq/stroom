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

package stroom.state.impl;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.docref.DocRef;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.state.shared.ScyllaDbDoc;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Clearable;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

@Singleton
@EntityEventHandler(
        type = ScyllaDbDoc.TYPE,
        action = {EntityAction.DELETE, EntityAction.UPDATE, EntityAction.CLEAR_CACHE})
public class ScyllaDbDocCacheImpl implements ScyllaDbDocCache, Clearable, EntityEvent.Handler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ScyllaDbDocCacheImpl.class);

    private static final String CACHE_NAME = "ScyllaDB Doc Cache";

    private final ScyllaDbDocStore stateDocStore;
    private final LoadingStroomCache<DocRef, ScyllaDbDoc> cache;
    private final SecurityContext securityContext;

    @Inject
    ScyllaDbDocCacheImpl(final CacheManager cacheManager,
                         final ScyllaDbDocStore stateDocStore,
                         final SecurityContext securityContext,
                         final Provider<StateConfig> stateConfigProvider) {
        this.stateDocStore = stateDocStore;
        this.securityContext = securityContext;
        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> stateConfigProvider.get().getScyllaDbDocCache(),
                this::create);
    }

    private ScyllaDbDoc create(final DocRef docRef) {
        return securityContext.asProcessingUserResult(() -> {
            final ScyllaDbDoc loaded = stateDocStore.readDocument(docRef);
            if (loaded == null) {
                throw new NullPointerException("No ScyllaDB doc can be found for: " + docRef);
            }

            return loaded;
        });
    }

    @Override
    public ScyllaDbDoc get(final DocRef docRef) {
        Objects.requireNonNull(docRef, "Null DocRef supplied");

        if (!securityContext.hasDocumentPermission(docRef, DocumentPermission.USE)) {
            throw new PermissionException(
                    securityContext.getUserRef(),
                    LogUtil.message("You are not authorised to read {}", docRef));
        }
        return cache.get(docRef);
    }

    @Override
    public void remove(final DocRef key) {
        cache.invalidate(key);
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public void onChange(final EntityEvent event) {
        LOGGER.debug("Received event {}", event);
        final EntityAction eventAction = event.getAction();

        switch (eventAction) {
            case CLEAR_CACHE -> {
                LOGGER.debug("Clearing cache");
                clear();
            }
            case UPDATE, DELETE -> {
                NullSafe.consume(
                        event.getDocRef(),
                        docRef -> {
                            LOGGER.debug("Invalidating docRef {}", docRef);
                            cache.invalidate(docRef);
                        });
            }
            default -> LOGGER.debug("Unexpected event action {}", eventAction);
        }
    }
}
