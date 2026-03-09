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
import stroom.planb.shared.PlanBDoc;
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

import java.util.List;
import java.util.Objects;

@Singleton
@EntityEventHandler(
        type = PlanBDoc.TYPE,
        action = {EntityAction.DELETE, EntityAction.UPDATE, EntityAction.CLEAR_CACHE})
public class PlanBDocCacheImpl implements PlanBDocCache, Clearable, EntityEvent.Handler {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlanBDocCacheImpl.class);

    private static final String CACHE_NAME = "Plan B State Doc Cache";

    private final PlanBDocStore planBDocStore;
    private final LoadingStroomCache<String, PlanBDoc> cache;
    private final SecurityContext securityContext;

    @Inject
    PlanBDocCacheImpl(final CacheManager cacheManager,
                      final PlanBDocStore planBDocStore,
                      final SecurityContext securityContext,
                      final Provider<PlanBConfig> stateConfigProvider) {
        this.planBDocStore = planBDocStore;
        this.securityContext = securityContext;
        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> stateConfigProvider.get().getStateDocCache(),
                this::create);
    }

    private PlanBDoc create(final String name) {
        return securityContext.asProcessingUserResult(() -> {
            final List<DocRef> list = planBDocStore.findByName(name);
            if (list.size() > 1) {
                throw new RuntimeException("Unexpectedly found more than one state doc with key: " + name);
            }
            if (list.isEmpty()) {
                throw new NullPointerException("No state doc can be found for key: " + name);
            }

            final DocRef docRef = list.getFirst();
            final PlanBDoc loaded = planBDocStore.readDocument(docRef);
            if (loaded == null) {
                throw new NullPointerException("No state doc can be found for: " + docRef);
            }

            return loaded;
        });
    }

    @Override
    public PlanBDoc get(final String name) {
        Objects.requireNonNull(name, "Null key supplied");
        final PlanBDoc doc = cache.get(name);

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
        LOGGER.debug("Received event {}", event);
        final EntityAction eventAction = event.getAction();

        switch (eventAction) {
            case UPDATE, DELETE, CLEAR_CACHE -> {
                LOGGER.debug("Clearing cache");
                clear();
            }
            default -> LOGGER.debug("Unexpected event action {}", eventAction);
        }
    }
}
