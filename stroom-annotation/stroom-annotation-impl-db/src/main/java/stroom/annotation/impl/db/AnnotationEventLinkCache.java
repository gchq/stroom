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


import stroom.annotation.impl.AnnotationEventLinks;
import stroom.annotation.impl.AnnotationIdEntityEventData;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.AnnotationIdentity;
import stroom.annotation.shared.EventId;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Singleton;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Singleton
@EntityEventHandler(
        type = Annotation.TYPE,
        action = {EntityAction.LINK, EntityAction.UNLINK, EntityAction.DELETE, EntityAction.CLEAR_CACHE})
public class AnnotationEventLinkCache implements EntityEvent.Handler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnnotationEventLinkCache.class);

    private final AtomicReference<MapWrapper> mapWrapper;

    public AnnotationEventLinkCache() {
        this.mapWrapper = new AtomicReference<>();
        clear(Instant.MIN);
    }

    public Instant getLastLoadTime() {
        return mapWrapper.get().lastEventIdLoad();
    }

    private void clear(final Instant time) {
        this.mapWrapper.set(new MapWrapper(new ConcurrentHashMap<>(), time));
    }

    void reload(final Collection<AnnotationEventLink> annotationEventLinks) {
        LOGGER.debug(() -> LogUtil.message("reload() - annotationEventLinks.size", annotationEventLinks.size()));
        if (NullSafe.hasItems(annotationEventLinks)) {
            final Map<EventId, Set<CacheValue>> newMap = new ConcurrentHashMap<>();
            annotationEventLinks.forEach(annotationEventLink -> {
                final CacheValue cacheValue = new CacheValue(
                        annotationEventLink.annotationId,
                        annotationEventLink.annotationUuid);
                newMap.computeIfAbsent(annotationEventLink.eventId, ignored -> ConcurrentHashMap.newKeySet())
                        .add(cacheValue);
            });
            final Instant now = Instant.now();
            mapWrapper.set(new MapWrapper(newMap, now));
            LOGGER.debug(() -> LogUtil.message("reload() - Swapped mapWrapper, entry count: {}, now: {}",
                    newMap.size(), now));
        } else {
            clear(Instant.now());
        }
    }

    void addLink(final EventId eventId, final String annotationUuid, final long annotationId) {
        LOGGER.debug("addLink() - eventId: {}, annotationUuid: {}, annotationId: {}",
                eventId, annotationUuid, annotationId);
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(annotationUuid);
        // Use compute rather than computeIfAbsent so we are sure we are the only thread working on this eventId
        mapWrapper.get().annotationEventIdCache.compute(
                eventId,
                (ignored, cachedAnnotationIdentities) -> {
                    final Set<CacheValue> set = Objects.requireNonNullElseGet(
                            cachedAnnotationIdentities, ConcurrentHashMap::newKeySet);
                    set.add(new CacheValue(annotationId, annotationUuid));
                    return set;
                });
    }

    void removeLink(final EventId eventId, final String annotationUuid, final long annotationId) {
        LOGGER.debug("removeLink() - eventId: {}, annotationUuid: {}, annotationId: {}",
                eventId, annotationUuid, annotationId);
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(annotationUuid);
        mapWrapper.get().annotationEventIdCache.compute(
                eventId, (ignored, cachedAnnotationIdentities) -> {
                    if (cachedAnnotationIdentities != null) {
                        cachedAnnotationIdentities.remove(new CacheValue(annotationId, annotationUuid));
                        if (cachedAnnotationIdentities.isEmpty()) {
                            return null;
                        } else {
                            return cachedAnnotationIdentities;
                        }
                    } else {
                        return null;
                    }
                });
    }

    public @NonNull Set<AnnotationIdentity> getLinkedAnnotations(@NonNull final EventId eventId) {
        Objects.requireNonNull(eventId);
        final Set<CacheValue> values = mapWrapper.get().annotationEventIdCache.get(eventId);
        final Set<AnnotationIdentity> result;
        if (NullSafe.isEmptyCollection(values)) {
            result = Collections.emptySet();
        } else {
            result = values.stream()
                    .map(CacheValue::getAnnotationIdentity)
                    .collect(Collectors.toUnmodifiableSet());
        }

        LOGGER.debug(() -> LogUtil.message(
                "getLinkedAnnotations() - Returning {} annotationIdentities for eventId: {}",
                result.size(), eventId));

        return result;
    }

    @Override
    public void onChange(final EntityEvent event) {
        LOGGER.debug("onChange() - event: {}", event);
        if (event != null) {
            // Depending on what has fired the event, it may have different data classes
            if (event.hasDataClass(AnnotationEventLinks.class)
                || event.hasDataClass(AnnotationIdEntityEventData.class)) {
                final EntityAction action = event.getAction();
                switch (action) {
                    // TODO For link/unlink we probably ought to hit the db to get the full list of
                    //  events that are linked to the anno. Then we can make an idempotent change to
                    //  the cache.
                    case LINK -> link(event);
                    case UNLINK -> unlink(event);
                    case DELETE -> deleteAnnotation(event);
                    case CLEAR_CACHE -> clear(Instant.now());
                }
            } else {
                LOGGER.debug("onChange() - Ignoring null event or with unexpected dataClassName, event: {}", event);
            }
        } else {
            LOGGER.debug("onChange() - Ignoring null event or with no dataClassName, event: {}", event);
        }
    }

    private AnnotationEventLinks getAnnotationEventLinks(final EntityEvent entityEvent) {
        if (AnnotationEventLinks.class.getName().equals(entityEvent.getDataClassName())) {
            return entityEvent.getDataObject(AnnotationEventLinks.class);
        } else {
            LOGGER.error("getAnnotationEventLinks() - Unexpected dataClassName for event: {}, expecting: {}",
                    entityEvent.getDataClassName(), AnnotationEventLinks.class.getName());
            return null;
        }
    }

    private AnnotationIdEntityEventData getAnnotationIdEventData(final EntityEvent entityEvent) {
        if (AnnotationIdEntityEventData.class.getName().equals(entityEvent.getDataClassName())) {
            return entityEvent.getDataObject(AnnotationIdEntityEventData.class);
        } else {
            LOGGER.error("getAnnotationIdEventData() - Unexpected dataClassName for event: {}, expecting: {}",
                    entityEvent.getDataClassName(), AnnotationEventLinks.class.getName());
            return null;
        }
    }

    private void link(final EntityEvent entityEvent) {
        final AnnotationEventLinks annotationEventLinks = getAnnotationEventLinks(entityEvent);
        if (annotationEventLinks != null) {
            for (final EventId eventId : annotationEventLinks.getEventIds()) {
                addLink(eventId, entityEvent.getDocRef().getUuid(), annotationEventLinks.getAnnotationId());
            }
        }
    }

    private void unlink(final EntityEvent entityEvent) {
        final AnnotationEventLinks annotationEventLinks = getAnnotationEventLinks(entityEvent);
        if (annotationEventLinks != null) {
            for (final EventId eventId : annotationEventLinks.getEventIds()) {
                removeLink(eventId, entityEvent.getDocRef().getUuid(), annotationEventLinks.getAnnotationId());
            }
        }
    }

    private void deleteAnnotation(final EntityEvent entityEvent) {
        final AnnotationIdEntityEventData annotationIdEventData = getAnnotationIdEventData(entityEvent);
        if (annotationIdEventData != null) {
            final CacheValue cacheValue = new CacheValue(
                    annotationIdEventData.getAnnotationId(),
                    entityEvent.getDocRef().getUuid());

            // It is possible that a reload may happen while we are iterating, in which case
            // all our changes will be against the redundant map, however, the reload should
            // have swapped in a fresh snapshot
            final Map<EventId, Set<CacheValue>> cache = mapWrapper.get().annotationEventIdCache();
            cache.keySet().forEach(eventId ->
                    cache.compute(eventId, (ignored, cacheValues) -> {
                        if (cacheValues != null) {
                            cacheValues.remove(cacheValue);
                            if (cacheValues.isEmpty()) {
                                return null;
                            } else {
                                return cacheValues;
                            }
                        } else {
                            return null;
                        }
                    }));
        }
    }


    // --------------------------------------------------------------------------------


    /**
     * Allows us to swap out the map and the lastEventIdLoad time as an atomic operation.
     */
    private record MapWrapper(Map<EventId, Set<CacheValue>> annotationEventIdCache,
                              Instant lastEventIdLoad) {

    }


    // --------------------------------------------------------------------------------

    /**
     * Use {@link UUID} rather than {@link String} to use less memory
     */
    private record CacheValue(long annotationId,
                              UUID annotationUuid) {

        @SuppressWarnings("RedundantRecordConstructor")
        private CacheValue(final long annotationId, final UUID annotationUuid) {
            this.annotationId = annotationId;
            this.annotationUuid = annotationUuid;
        }

        private CacheValue(final long annotationId, final String annotationUuid) {
            this(annotationId, UUID.fromString(Objects.requireNonNull(annotationUuid)));
        }

        private AnnotationIdentity getAnnotationIdentity() {
            return new AnnotationIdentity(annotationUuid.toString(), annotationId);
        }
    }


    // --------------------------------------------------------------------------------


    /**
     * Defines a link between an annotation and an event
     */
    record AnnotationEventLink(EventId eventId, String annotationUuid, long annotationId) {

        AnnotationEventLink {
            Objects.requireNonNull(eventId);
            Objects.requireNonNull(annotationUuid);
        }
    }
}
