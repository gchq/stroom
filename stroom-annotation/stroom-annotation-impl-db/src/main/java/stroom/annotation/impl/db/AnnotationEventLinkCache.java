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
import stroom.docref.DocRef;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import it.unimi.dsi.fastutil.longs.LongCollection;
import it.unimi.dsi.fastutil.longs.LongCollections;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import jakarta.inject.Singleton;

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
        if (NullSafe.hasItems(annotationEventLinks)) {
            final Map<EventId, Set<CachedAnnotationIdentity>> newMap = new ConcurrentHashMap<>();
            annotationEventLinks.forEach(annotationEventLink -> {
                final CachedAnnotationIdentity cachedAnnotationIdentity = new CachedAnnotationIdentity(
                        annotationEventLink.annotationId,
                        annotationEventLink.annotationUuid);
                newMap.computeIfAbsent(annotationEventLink.eventId, ignored -> new HashSet<>())
                        .add(cachedAnnotationIdentity);
            });
            mapWrapper.set(new MapWrapper(newMap, Instant.now()));
        } else {
            clear(Instant.now());
        }
    }

    void addLink(final EventId eventId, final String annotationUuid, final long annotationId) {
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(annotationUuid);
        mapWrapper.get().annotationEventIdCache.computeIfAbsent(
                        eventId, ignored -> new HashSet<>())
                .add(new CachedAnnotationIdentity(annotationId, annotationUuid));
    }

    void removeLink(final EventId eventId, final String annotationUuid, final long annotationId) {
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

    public Set<AnnotationIdentity> getLinkedAnnotations(final EventId eventId) {
        Objects.requireNonNull(eventId);
        final Set<CachedAnnotationIdentity> values = mapWrapper.get().annotationEventIdCache.get(eventId);
        if (NullSafe.isEmptyCollection(values)) {
            return Collections.emptySet();
        } else {
            return values.stream()
                    .map(cacheValue -> new AnnotationIdentity(
                            cacheValue.uuid.toString(), cacheValue.id))
                    .collect(Collectors.toSet());
        }
    }

    public LongCollection getLinkedAnnotationIds(final EventId eventId) {
        final Set<CachedAnnotationIdentity> values = mapWrapper.get().annotationEventIdCache.get(eventId);
        if (NullSafe.isEmptyCollection(values)) {
            return LongSet.of();
        } else {
            final LongSet ids = new LongOpenHashSet(values.size());
            for (final CachedAnnotationIdentity cacheValue : values) {
                ids.add(cacheValue.id);
            }
            return LongCollections.unmodifiable(ids);
        }
    }

    public Collection<DocRef> getLinkedAnnotationDocRefs(final EventId eventId) {
        final Set<CachedAnnotationIdentity> values = mapWrapper.get().annotationEventIdCache.get(eventId);
        if (NullSafe.isEmptyCollection(values)) {
            return Collections.emptySet();
        } else {
            return values.stream()
                    .map(cacheValue -> new DocRef(DOC_REF_TYPE, cacheValue.uuid.toString()))
                    .collect(Collectors.toSet());
        }
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
                case DELETE -> delete(event);
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
            final Map<EventId, Set<CachedAnnotationIdentity>> cache = mapWrapper.get().annotationEventIdCache();
            final AnnotationIdentity annotationIdentity = annotationEventLinks.getAnnotationIdentity();
            for (final EventId eventId : annotationEventLinks.getEventIds()) {
                NullSafe.consume(cache.get(eventId), cacheValues -> {
                    final CachedAnnotationIdentity valueToAdd = new CachedAnnotationIdentity(
                            annotationIdentity.getId(),
                            annotationIdentity.getUuid());
                    cacheValues.add(valueToAdd);
                });
            }
        }
    }

    private void unlink(final EntityEvent entityEvent) {
        final AnnotationEventLinks annotationEventLinks = getAnnotationEventLinks(entityEvent);
        if (annotationEventLinks != null) {
            final Map<EventId, Set<CachedAnnotationIdentity>> cache = mapWrapper.get().annotationEventIdCache();
            final AnnotationIdentity annotationIdentity = annotationEventLinks.getAnnotationIdentity();
            for (final EventId eventId : annotationEventLinks.getEventIds()) {
                NullSafe.consume(cache.get(eventId), cacheValues -> {
                    final CachedAnnotationIdentity valueToRemove = new CachedAnnotationIdentity(
                            annotationIdentity.getId(),
                            UUID.fromString(annotationIdentity.getUuid()));
                    cacheValues.remove(valueToRemove);
                });
            }
        }
    }

    private void delete(final EntityEvent entityEvent) {
        final AnnotationIdEntityEventData annotationIdEventData = getAnnotationIdEventData(entityEvent);
        if (annotationIdEventData != null) {
            final CachedAnnotationIdentity cachedAnnotationIdentity = new CachedAnnotationIdentity(
                    annotationIdEventData.getAnnotationId(),
                    entityEvent.getDocRef().getUuid());

            final AnnotationIdentity annotationIdentity = new AnnotationIdentity(
                    entityEvent.getDocRef().getUuid(),
                    annotationIdEventData.getAnnotationId());

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
    }


    // --------------------------------------------------------------------------------


    record AnnotationEventLink(EventId eventId, String annotationUuid, long annotationId) {

        AnnotationEventLink {
            Objects.requireNonNull(eventId);
            Objects.requireNonNull(annotationUuid);
        }
    }
}
