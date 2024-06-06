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
 *
 */

package stroom.state.impl;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.docref.DocRef;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.state.shared.StateDoc;
import stroom.util.NullSafe;
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

import java.util.Objects;

@Singleton
@EntityEventHandler(
        type = StateDoc.DOCUMENT_TYPE,
        action = {EntityAction.DELETE, EntityAction.UPDATE, EntityAction.CLEAR_CACHE})
public class StateDocCacheImpl implements StateDocCache, Clearable, EntityEvent.Handler {

    private static final Logger LOGGER = LoggerFactory.getLogger(StateDocCacheImpl.class);

    private static final String CACHE_NAME = "State Doc Cache";

    private final StateDocStore stateDocStore;
    private final LoadingStroomCache<DocRef, StateDoc> cache;
    private final SecurityContext securityContext;

    @Inject
    StateDocCacheImpl(final CacheManager cacheManager,
                      final StateDocStore stateDocStore,
                      final SecurityContext securityContext,
                      final Provider<StateConfig> stateConfigProvider) {
        this.stateDocStore = stateDocStore;
        this.securityContext = securityContext;
        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> stateConfigProvider.get().getStateDocCache(),
                this::create);
    }

    private StateDoc create(final DocRef docRef) {
        return securityContext.asProcessingUserResult(() -> {
            final StateDoc loaded = stateDocStore.readDocument(docRef);
            if (loaded == null) {
                throw new NullPointerException("No state doc can be found for: " + docRef);
            }

            return loaded;
        });
    }

    @Override
    public StateDoc get(final DocRef docRef) {
        Objects.requireNonNull(docRef, "Null DocRef supplied");

        if (!securityContext.hasDocumentPermission(docRef, DocumentPermissionNames.USE)) {
            throw new PermissionException(
                    securityContext.getUserIdentityForAudit(),
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
