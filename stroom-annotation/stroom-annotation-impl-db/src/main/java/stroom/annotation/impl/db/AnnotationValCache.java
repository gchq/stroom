/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.annotation.impl.db;

import stroom.annotation.impl.AnnotationConfig;
import stroom.annotation.impl.AnnotationIdEntityEventData;
import stroom.annotation.impl.AnnotationValues;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.AnnotationIdentity;
import stroom.cache.api.CacheManager;
import stroom.cache.api.StroomCache;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Clearable;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Objects;

/**
 * Cache for mapping annotation feed IDs to feed names.
 * <p>
 * This cache uses async execution to avoid thread-local connection conflicts
 * when accessed from within existing database transaction contexts.
 */
@Singleton
@EntityEventHandler(
        type = Annotation.TYPE,
        action = {EntityAction.UPDATE, EntityAction.DELETE, EntityAction.CLEAR_CACHE})
class AnnotationValCache implements Clearable, EntityEvent.Handler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnnotationValCache.class);
    private static final String CACHE_NAME = "Annotation Value Cache";

    private final StroomCache<Long, AnnotationValues> cache;

    @Inject
    AnnotationValCache(final CacheManager cacheManager,
                       final Provider<AnnotationConfig> annotationConfigProvider) {
        cache = cacheManager.create(
                CACHE_NAME,
                () -> annotationConfigProvider.get().getAnnotationValCache());
    }

    public AnnotationValues get(final AnnotationIdentity annotationIdentity) {
        return cache.get(annotationIdentity.getId(), ignored -> new AnnotationValues(annotationIdentity));
    }

    public void invalidate(final long id) {
        cache.invalidate(id);
    }

    @Override
    public void clear() {
        cache.clear();
    }

    @Override
    public void onChange(final EntityEvent event) {
        LOGGER.debug("onChange() - event: {}", event);
        if (event != null && Objects.equals(event.getDataClassName(), AnnotationIdEntityEventData.class.getName())) {
            final EntityAction action = event.getAction();
            final AnnotationIdEntityEventData entityEventData = event.getDataAsObject(
                    AnnotationIdEntityEventData.class);
            if (entityEventData != null) {
                switch (action) {
                    // TODO the event data could include the change type so that we only
                    //  need to clear one entry inside AnnotationValues rather than bin the whole lot
                    case UPDATE -> invalidate(entityEventData.getAnnotationId());
                    case DELETE -> invalidate(entityEventData.getAnnotationId());
                    case CLEAR_CACHE -> clear();
                }
            } else {
                LOGGER.debug("onChange() - Ignoring event with no entityEventData, event: {}", event);
            }
        } else {
            LOGGER.debug("onChange() - Ignoring null event or with no dataClassName, event: {}", event);
        }
    }
}
