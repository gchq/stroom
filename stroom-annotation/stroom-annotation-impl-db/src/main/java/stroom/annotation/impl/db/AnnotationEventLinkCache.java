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

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
    private static final Duration MAX_CACHE_AGE = Duration.ofMinutes(10);
    public static final long MIN_EPOCH_MS = 0L;

    private final AtomicReference<MapWrapper> atomicRef;

    public AnnotationEventLinkCache() {
        this.atomicRef = new AtomicReference<>(createMapWrapper());
    }

    public boolean isExpired() {
        return atomicRef.get().isExpired();
    }

//    public Instant getLastLoadTime() {
//        final Instant lastEventIdLoad = Objects.requireNonNullElse(atomicRef.get().lastEventIdLoad(), Instant.MIN);
//        LOGGER.trace("getLastLoadTime: {}", lastEventIdLoad);
//        return lastEventIdLoad;
//    }

    private void clear(final Instant time) {
        LOGGER.debug(() -> LogUtil.message("clear() - size: {}", size()));
        this.atomicRef.set(createMapWrapper(time));
    }

    private MapWrapper createMapWrapper() {
        return createMapWrapper(Instant.EPOCH);
    }

    private MapWrapper createMapWrapper(final Instant time) {
        return new MapWrapper(new ConcurrentHashMap<>(), time);
    }

    private int size() {
        return NullSafe.size(getCurrentMap());
    }

    private long getLinkCount() {
        return NullSafe.get(getCurrentMap(), map -> map.values().stream()
                .mapToLong(Collection::size)
                .sum());
    }

    private Map<EventId, Set<CacheValue>> getCurrentMap() {
        return atomicRef.get().annotationEventIdCache;
    }

    void reload(final Collection<AnnotationEventLink> annotationEventLinks) {
        LOGGER.debug(() -> LogUtil.message(
                "reload() - annotationEventLinks.size: {}, current entries: {}, current link count: {}",
                annotationEventLinks.size(), size(), getLinkCount()));
        final Instant now = Instant.now();

        if (NullSafe.hasItems(annotationEventLinks)) {
            // Get the count of distinct eventIds so we can size our newMap appropriately
            final int eventIdCount = Math.toIntExact(annotationEventLinks.stream()
                    .map(AnnotationEventLink::eventId)
                    .distinct()
                    .count());
            // If annotations are linked to multiple events, then only store one instance of the anno
            // to reduce mem.
            final Map<CacheValue, CacheValue> tempInternerMap = new HashMap<>(annotationEventLinks.size());

            final List<LinkHolder> linkHolders = annotationEventLinks.stream()
                    .map(annotationEventLink -> {
                        final CacheValue cacheValue = new CacheValue(
                                annotationEventLink.annotationId,
                                annotationEventLink.annotationUuid);
                        CacheValue internedCacheValue = tempInternerMap.putIfAbsent(cacheValue, cacheValue);
                        if (internedCacheValue == null) {
                            internedCacheValue = cacheValue;
                        }
                        return new LinkHolder(annotationEventLink.eventId, internedCacheValue);
                    })
                    .toList();

            LOGGER.debug(() -> LogUtil.message(
                    "reload() - annotationEventLinks.size: {}, eventIdCount: {}, tempInternerMap.size: {}",
                    annotationEventLinks.size(), eventIdCount, tempInternerMap.size()));

            // Now build our new map
            final Map<EventId, Set<CacheValue>> newMap = new ConcurrentHashMap<>(eventIdCount);
            linkHolders.forEach(linkHolder ->
                    newMap.computeIfAbsent(linkHolder.eventId, ignored -> ConcurrentHashMap.newKeySet())
                            .add(linkHolder.cacheValue));
            atomicRef.set(new MapWrapper(newMap, now));
            LOGGER.debug(() -> LogUtil.message(
                    "reload() - Swapped mapWrapper, entry count: {}, link count: {}, now: {}",
                    newMap.size(), annotationEventLinks.size(), now));
        } else {
            clear(Instant.now());
        }
    }

    void addLink(final EventId eventId, final String annotationUuid, final long annotationId) {
        LOGGER.debug(() -> LogUtil.message(
                "addLink() - eventId: {}, annotationUuid: {}, annotationId: {}, current link count: {}",
                eventId, annotationUuid, annotationId, getLinkCount()));
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(annotationUuid);
        // Use compute rather than computeIfAbsent so we are sure we are the only thread working on this eventId
        getCurrentMap().compute(
                eventId,
                (ignored, cachedAnnotationIdentities) -> {
                    final Set<CacheValue> set = Objects.requireNonNullElseGet(
                            cachedAnnotationIdentities, ConcurrentHashMap::newKeySet);
                    set.add(new CacheValue(annotationId, annotationUuid));
                    return set;
                });
    }

    void removeLink(final EventId eventId, final String annotationUuid, final long annotationId) {
        LOGGER.debug(() -> LogUtil.message(
                "removeLink() - eventId: {}, annotationUuid: {}, annotationId: {}, current link count: {}",
                eventId, annotationUuid, annotationId, getLinkCount()));
        Objects.requireNonNull(eventId);
        Objects.requireNonNull(annotationUuid);
        getCurrentMap().compute(
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
        final Set<CacheValue> values = getCurrentMap().get(eventId);
        final Set<AnnotationIdentity> result;
        if (NullSafe.isEmptyCollection(values)) {
            result = Collections.emptySet();
        } else {
            result = values.stream()
                    .map(CacheValue::getAnnotationIdentity)
                    .collect(Collectors.toUnmodifiableSet());
        }
        LOGGER.trace(() -> LogUtil.message(
                "getLinkedAnnotations() - eventId: {}, annotationIdentities: {}",
                eventId,
                LogUtil.getSample(result, 10, annoId -> String.valueOf(annoId.getId()))));
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
                LOGGER.debug("onChange() - Ignoring unexpected dataClassName, event: {}", event);
            }
        } else {
            LOGGER.debug("onChange() - Ignoring null event");
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
            final Map<EventId, Set<CacheValue>> cache = atomicRef.get().annotationEventIdCache();
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
    private static final class MapWrapper {

        private final Map<EventId, Set<CacheValue>> annotationEventIdCache;
        private final Instant lastEventIdLoad;
        private final long nextLoadEpochMs;

        private MapWrapper(final Map<EventId, Set<CacheValue>> annotationEventIdCache,
                           final Instant lastEventIdLoad) {
            Objects.requireNonNull(annotationEventIdCache);
            Objects.requireNonNull(lastEventIdLoad);
            this.annotationEventIdCache = annotationEventIdCache;
            this.lastEventIdLoad = lastEventIdLoad;
            this.nextLoadEpochMs = lastEventIdLoad.toEpochMilli() + MAX_CACHE_AGE.toMillis();
        }

        public Map<EventId, Set<CacheValue>> annotationEventIdCache() {
            return annotationEventIdCache;
        }

        public Instant getLastEventIdLoad() {
            return lastEventIdLoad;
        }

        public long getNextLoadEpochMs() {
            return nextLoadEpochMs;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > nextLoadEpochMs;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || obj.getClass() != this.getClass()) {
                return false;
            }
            var that = (MapWrapper) obj;
            return Objects.equals(this.annotationEventIdCache, that.annotationEventIdCache) &&
                   this.lastEventIdLoad == that.lastEventIdLoad;
        }

        @Override
        public int hashCode() {
            return Objects.hash(annotationEventIdCache, lastEventIdLoad);
        }

        @Override
        public String toString() {
            return "MapWrapper[" +
                   "annotationEventIdCache=" + annotationEventIdCache + ", " +
                   "lastEventIdLoad=" + lastEventIdLoad + ']';
        }

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


    // --------------------------------------------------------------------------------


    record LinkHolder(EventId eventId, CacheValue cacheValue) {

    }
}
