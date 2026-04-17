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
import java.util.HashSet;
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
    private static final String DOC_REF_TYPE = Annotation.TYPE;

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
            final Map<EventId, Set<CachedAnnotationIdentity>> newMap = new ConcurrentHashMap<>();
            annotationEventLinks.forEach(annotationEventLink -> {
                final CachedAnnotationIdentity cachedAnnotationIdentity = new CachedAnnotationIdentity(
                        annotationEventLink.annotationId,
                        annotationEventLink.annotationUuid);
                newMap.computeIfAbsent(annotationEventLink.eventId, ignored -> new HashSet<>())
                        .add(cachedAnnotationIdentity);
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
        mapWrapper.get().annotationEventIdCache.computeIfAbsent(
                        eventId, ignored -> new HashSet<>())
                .add(new CachedAnnotationIdentity(annotationId, annotationUuid));
    }

    void removeLink(final EventId eventId, final String annotationUuid, final long annotationId) {
        LOGGER.debug("removeLink() - eventId: {}, annotationUuid: {}, annotationId: {}",
                eventId, annotationUuid, annotationId);
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(annotationUuid);
        mapWrapper.get().annotationEventIdCache.compute(
                eventId, (ignored, cachedAnnotationIdentities) -> {
                    if (cachedAnnotationIdentities != null) {
                        cachedAnnotationIdentities.remove(
                                new CachedAnnotationIdentity(annotationId, annotationUuid));
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
        final Set<CachedAnnotationIdentity> values = mapWrapper.get().annotationEventIdCache.get(eventId);
        final Set<AnnotationIdentity> result;
        if (NullSafe.isEmptyCollection(values)) {
            result = Collections.emptySet();
        } else {
            result = values.stream()
                    .map(CachedAnnotationIdentity::getAnnotationIdentity)
                    .collect(Collectors.toUnmodifiableSet());
        }

        LOGGER.debug(() -> LogUtil.message(
                "getLinkedAnnotations() - Returning {} annotationIdentities for eventId: {}",
                result.size(), eventId));

        return result;
    }

    public boolean hasAnnotationLinks(final EventId eventId) {
        Objects.requireNonNull(eventId);
        final Set<CachedAnnotationIdentity> values = mapWrapper.get().annotationEventIdCache.get(eventId);
        return NullSafe.hasItems(values);
    }

    public void invalidate(final EventId eventId) {
        LOGGER.debug("invalidate() - eventId: {}", eventId);
        Objects.requireNonNull(eventId);
        mapWrapper.get().annotationEventIdCache.remove(eventId);
    }

    @Override
    public void onChange(final EntityEvent event) {
        LOGGER.debug("onChange() - event: {}", event);
        if (event != null && Objects.equals(event.getDataClassName(), AnnotationIdEntityEventData.class.getName())) {
            final EntityAction action = event.getAction();
            switch (action) {
                // TODO the event data could include the change type so that we only
                //  need to clear one entry inside AnnotationValues rather than bin the whole lot
                case LINK -> link(event);
                case UNLINK -> unlink(event);
                case DELETE -> deleteAnnotation(event);
                case CLEAR_CACHE -> clear(Instant.now());
            }
        } else {
            LOGGER.debug("onChange() - Ignoring null event or with no dataClassName, event: {}", event);
        }
    }

    private AnnotationEventLinks getAnnotationEventLinks(final EntityEvent entityEvent) {
        if (AnnotationEventLinks.class.getName().equals(entityEvent.getDataClassName())) {
            return entityEvent.getDataAsObject(AnnotationEventLinks.class);
        } else {
            LOGGER.error("getAnnotationEventLinks() - Unexpected dataClassName for event: {}, expecting: {}",
                    entityEvent.getDataClassName(), AnnotationEventLinks.class.getName());
            return null;
        }
    }

    private AnnotationIdEntityEventData getAnnotationIdEventData(final EntityEvent entityEvent) {
        if (AnnotationIdEntityEventData.class.getName().equals(entityEvent.getDataClassName())) {
            return entityEvent.getDataAsObject(AnnotationIdEntityEventData.class);
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
            final CachedAnnotationIdentity cachedAnnotationIdentity = new CachedAnnotationIdentity(
                    annotationIdEventData.getAnnotationId(),
                    entityEvent.getDocRef().getUuid());

            final Map<EventId, Set<CachedAnnotationIdentity>> cache = mapWrapper.get().annotationEventIdCache();
            cache.values().forEach(cacheValues ->
                    cacheValues.remove(cachedAnnotationIdentity));
        }
    }


    // --------------------------------------------------------------------------------


    private record MapWrapper(Map<EventId, Set<CachedAnnotationIdentity>> annotationEventIdCache,
                              Instant lastEventIdLoad) {

    }


    // --------------------------------------------------------------------------------

    /**
     * Use {@link UUID} rather than {@link String} to use less memory
     */
    private record CachedAnnotationIdentity(long id,
                                            UUID uuid) {

        private CachedAnnotationIdentity(final long id, final UUID uuid) {
            this.id = id;
            this.uuid = uuid;
        }

        private CachedAnnotationIdentity(final long id, final String uuid) {
            this(id, UUID.fromString(Objects.requireNonNull(uuid)));
        }

        private CachedAnnotationIdentity(final AnnotationIdentity annotationIdentity) {
            this(Objects.requireNonNull(annotationIdentity).getId(),
                    UUID.fromString(annotationIdentity.getUuid()));
        }

        private String getUuidAsString() {
            return uuid.toString();
        }

        private AnnotationIdentity getAnnotationIdentity() {
            return new AnnotationIdentity(uuid.toString(), id);
        }
    }


    // --------------------------------------------------------------------------------


    record AnnotationEventLink(EventId eventId, String annotationUuid, long annotationId) {

        AnnotationEventLink {
            Objects.requireNonNull(eventId);
            Objects.requireNonNull(annotationUuid);
        }
    }
}
