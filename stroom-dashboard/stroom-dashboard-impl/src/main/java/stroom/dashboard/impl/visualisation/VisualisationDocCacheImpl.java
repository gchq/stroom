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

package stroom.dashboard.impl.visualisation;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.dashboard.impl.DashboardConfig;
import stroom.docref.DocRef;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Clearable;
import stroom.util.shared.PermissionException;
import stroom.visualisation.shared.VisualisationDoc;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Objects;

@Singleton
@EntityEventHandler(type = VisualisationDoc.TYPE, action = {
        EntityAction.DELETE,
        EntityAction.UPDATE,
        EntityAction.CLEAR_CACHE})
public class VisualisationDocCacheImpl implements VisualisationDocCache, Clearable, EntityEvent.Handler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(VisualisationDocCacheImpl.class);

    private static final String CACHE_NAME = "Visualisation Doc Cache";

    private final VisualisationStore visualisationStore;
    private final LoadingStroomCache<DocRef, VisualisationDoc> cache;
    private final SecurityContext securityContext;

    @Inject
    VisualisationDocCacheImpl(final CacheManager cacheManager,
                              final VisualisationStore visualisationStore,
                              final SecurityContext securityContext,
                              final Provider<DashboardConfig> dashboardConfigProvider) {
        this.visualisationStore = visualisationStore;
        this.securityContext = securityContext;
        cache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> dashboardConfigProvider.get().getVisualisationDocCache(),
                this::create);
    }

    private VisualisationDoc create(final DocRef docRef) {
        return securityContext.asProcessingUserResult(() -> {
            LOGGER.debug("Loading docRef {}", docRef);
            final VisualisationDoc loaded = visualisationStore.readDocument(docRef);
            if (loaded == null) {
                throw new NullPointerException("No visualisationDoc can be found for: " + docRef);
            }

            return loaded;
        });
    }

    @Override
    public VisualisationDoc get(final DocRef docRef) {
        Objects.requireNonNull(docRef, "Null DocRef supplied");

        if (!securityContext.hasDocumentPermission(docRef, DocumentPermission.USE)) {
            throw new PermissionException(
                    securityContext.getUserRef(),
                    LogUtil.message("You are not authorised to read {}", docRef));
        }
        return cache.get(docRef);
    }

    @Override
    public void remove(final DocRef docRef) {
        LOGGER.debug("Invalidating docRef {}", docRef);
        cache.invalidate(docRef);
    }

    @Override
    public void clear() {
        LOGGER.debug("Clearing cache");
        cache.clear();
    }

    @Override
    public void onChange(final EntityEvent event) {
        LOGGER.debug("Received event {}", event);
        final EntityAction eventAction = event.getAction();
        final DocRef docRef = event.getDocRef();

        if (VisualisationDoc.TYPE.equals(docRef.getType())) {
            switch (eventAction) {
                case CLEAR_CACHE -> clear();
                case DELETE,
                     UPDATE -> remove(docRef);
                default -> LOGGER.warn("Unexpected event action {}", eventAction);
            }
        } else {
            LOGGER.warn("Unexpected document type {}", docRef);
        }
    }
}
