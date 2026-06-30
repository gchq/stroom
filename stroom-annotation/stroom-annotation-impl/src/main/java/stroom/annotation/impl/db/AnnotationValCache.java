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
import stroom.annotation.impl.AnnotationFieldsEntityEventData;
import stroom.annotation.impl.AnnotationIdEntityEventData;
import stroom.annotation.impl.AnnotationValues;
import stroom.annotation.impl.AnnotationValues.FieldValueEntry;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.AnnotationIdentity;
import stroom.cache.api.CacheManager;
import stroom.cache.api.StroomCache;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Clearable;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NonNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

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

    /**
     * Gets an {@link AnnotationValues} from the cache, or if not present, creates a new empty one
     * and adds it to the cache.
     */
    public @NonNull AnnotationValues getOrCreate(@NonNull final AnnotationIdentity annotationIdentity) {
        Objects.requireNonNull(annotationIdentity);
        final AnnotationValues annotationValues = cache.get(
                annotationIdentity.getId(),
                ignored -> createNewAnnotationValues(annotationIdentity));

        LOGGER.trace(() -> LogUtil.message(
                "get() - annotationIdentity: {}, annotationValues.size: {}, annotationValues: {}",
                annotationIdentity, annotationValues.size(), annotationValues));
        return annotationValues;
    }

    private static @NonNull AnnotationValues createNewAnnotationValues(
            @NonNull final AnnotationIdentity annotationIdentity) {
        try {
            LOGGER.debug("createNewAnnotationValues() - annotationIdentity: {}", annotationIdentity);
            return new AnnotationValues(annotationIdentity);
        } catch (final Exception e) {
            LOGGER.error("Error creating AnnotationValues for annotationIdentity: {}", annotationIdentity);
            throw e;
        }
    }

    /**
     * Put a collection of annotation field/value entries into the cache for the provided annotationId
     *
     * @return The updated {@link AnnotationValues} object
     */
    public AnnotationValues put(final AnnotationIdentity annotationIdentity,
                                final Collection<FieldValueEntry> fieldValueEntries) {
        Objects.requireNonNull(annotationIdentity);
        LOGGER.debug("put() - annotationIdentity: {}, queryFieldToValEntries: {}",
                annotationIdentity, fieldValueEntries);
        return cache.compute(annotationIdentity.getId(), (ignored, annotationValues) -> {
            if (annotationValues == null) {
                annotationValues = createNewAnnotationValues(annotationIdentity);
            }
            annotationValues.put(fieldValueEntries);
            return annotationValues;
        });
    }

    /**
     * Mark the {@link AnnotationValues} object corresponding to the annotationIdentity as deleted.
     * This will clear all field/value entries in it.
     *
     * @return The updated {@link AnnotationValues} object
     */
    public AnnotationValues markDeleted(final AnnotationIdentity annotationIdentity) {
        // Keep it in the cache in a deleted state, so other queries don't have to hit the db
        // to discover it is deleted
        return cache.compute(annotationIdentity.getId(), (ignored, annotationValues) -> {
            if (annotationValues == null) {
                annotationValues = createNewAnnotationValues(annotationIdentity);
            }
            annotationValues.markDeleted();
            return annotationValues;
        });
    }

    private void invalidate(final long id) {
        LOGGER.debug("invalidate() - id: {}", id);
        cache.invalidate(id);
    }

    private void invalidateFields(final long annotationId, final Set<String> fieldNames) {
        LOGGER.debug("invalidateFields() - annotationId: {}, fieldNames: {}", annotationId, fieldNames);
        cache.compute(annotationId, (ignored, annotationValues) -> {
            if (annotationValues != null && NullSafe.hasItems(fieldNames)) {
                for (final String fieldName : fieldNames) {
                    annotationValues.clear(fieldName);
                }
            }
            return annotationValues;
        });
    }

    private void invalidateFields(final Set<String> fieldNames) {
        LOGGER.debug("invalidateFields() - fieldNames: {}", fieldNames);
        // Take a copy so we are protected from other threads modifying the cache entry iteration
        final Set<Long> keys = new HashSet<>(cache.keySet());
        for (final Long id : keys) {
            if (id != null) {
                invalidateFields(id, fieldNames);
            }
        }
    }

    @Override
    public void clear() {
        LOGGER.debug(() -> LogUtil.message("clear() - size: {}", cache.size()));
        cache.clear();
    }

    @Override
    public void onChange(final EntityEvent event) {
        LOGGER.debug("onChange() - event: {}", event);
        if (event != null) {
            final EntityAction action = event.getAction();
            if (action == EntityAction.CLEAR_CACHE) {
                clear();
            } else if (event.hasDataClass(AnnotationIdEntityEventData.class)) {
                handleIdOnlyEvent(event, action);
            } else if (event.hasDataClass(AnnotationFieldsEntityEventData.class)) {
                handleFieldEvent(event, action);
            } else {
                LOGGER.debug("Ignoring unexpected entityEventData type, event: {}", event);
            }
        } else {
            LOGGER.debug("onChange() - Ignoring null event");
        }
    }

    private void handleFieldEvent(final EntityEvent event, final EntityAction action) {
        final AnnotationFieldsEntityEventData fieldsEventData = event.getDataObject(
                AnnotationFieldsEntityEventData.class);

        final Long annotationId = fieldsEventData.getAnnotationId();
        // Whether it is a delete or update, we have to invalidate the fields or whole anno
        if (action == EntityAction.UPDATE || action == EntityAction.DELETE) {
            if (annotationId != null) {
                handleSingleAnnotationsFieldEvent(event, annotationId, fieldsEventData);
            } else {
                handleAllAnnotationsFieldEvent(event, fieldsEventData);
            }
        } else {
            LOGGER.error("Unexpected action, event: {}", event);
        }
    }

    private void handleSingleAnnotationsFieldEvent(final EntityEvent event,
                                                   final long annotationId,
                                                   final AnnotationFieldsEntityEventData fieldsEventData) {
        final Set<String> changedFields = fieldsEventData.getChangedFields();
        if (NullSafe.hasItems(changedFields)) {
            // We know which fields have been changed so remove only those fields
            invalidateFields(annotationId, changedFields);
        } else {
            // No field names, so no choice but to clear the whole anno
            invalidate(annotationId);
            LOGGER.warn("UPDATE event fired with an empty set of fields. " +
                        "Invalidating whole annotation. event: {}", event);
        }
    }

    private void handleAllAnnotationsFieldEvent(final EntityEvent event,
                                                final AnnotationFieldsEntityEventData fieldsEventData) {
        final Set<String> changedFields = fieldsEventData.getChangedFields();
        if (NullSafe.hasItems(changedFields)) {
            // We know which fields have been changed so remove only those fields
            invalidateFields(changedFields);
        } else {
            // No field names, or anno id, so clear the whole cache
            clear();
            LOGGER.warn("UPDATE event fired with an empty set of fields and no annotationId. " +
                        "Invalidating whole cache. event: {}", event);
        }
    }

    private void handleIdOnlyEvent(final EntityEvent event, final EntityAction action) {
        final AnnotationIdEntityEventData idEventData = event.getDataObject(
                AnnotationIdEntityEventData.class);
        if (idEventData != null) {
            switch (action) {
                // No changed field info, so have to invalidate the whole anno
                case UPDATE -> invalidate(idEventData.getAnnotationId());
                case DELETE -> markDeleted(getIdentity(event, idEventData.getAnnotationId()));
            }
        } else {
            LOGGER.debug("onChange() - Ignoring event with no entityEventData, event: {}", event);
        }
    }

    private AnnotationIdentity getIdentity(final EntityEvent event, final long id) {
        return new AnnotationIdentity(event.getDocRef().getUuid(), id);
    }
}
