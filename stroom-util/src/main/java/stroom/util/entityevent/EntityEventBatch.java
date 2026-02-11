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

package stroom.util.entityevent;


import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jspecify.annotations.NullMarked;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class EntityEventBatch {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(EntityEventBatch.class);

    private static final EntityEventBatch EMPTY = new EntityEventBatch(
            Collections.emptyList(), true);

    @JsonProperty
    private final List<EntityEvent> entityEvents;
    @JsonProperty
    private final boolean homogeneousBatch;

    @JsonIgnore
    private transient volatile Map<EntityEventKey, EntityEventBatch> eventsByKeyMap = null;

    /**
     * @param entityEvents     The events in the batch.
     * @param homogeneousBatch Set to true if all {@link EntityEvent}s in entityEvents share the same
     *                         {@link EntityEventKey}. Set to false if not known.
     */
    @JsonCreator
    public EntityEventBatch(@JsonProperty("entityEvents") final List<EntityEvent> entityEvents,
                            @JsonProperty("homogeneousBatch") final boolean homogeneousBatch) {
        // No point firing identical events, so remove any dups
        this.entityEvents = distinctEvents(entityEvents);
        if (homogeneousBatch) {
            enforceHomogeneous(this.entityEvents);
        }
        this.homogeneousBatch = homogeneousBatch;
    }

    private List<EntityEvent> distinctEvents(final List<EntityEvent> events) {
        final List<EntityEvent> deDupedEvents = NullSafe.stream(events)
                .distinct()
                .toList();
        LOGGER.debug(() -> LogUtil.message("distinctEvents() - events: {}, deDupedEvents: {}",
                NullSafe.size(events), deDupedEvents.size()));
        return deDupedEvents;
    }

    public static EntityEventBatch singleton(final EntityEvent entityEvent) {
        Objects.requireNonNull(entityEvent);
        return new EntityEventBatch(Collections.singletonList(entityEvent), true);
    }

    public static EntityEventBatch empty() {
        return EMPTY;
    }

    private void enforceHomogeneous(final List<EntityEvent> entityEvents) {
        // Implicitly homogeneous if size <= 1
        if (NullSafe.size(entityEvents) > 1) {
            final List<EntityEventKey> distinctKeys = entityEvents.stream()
                    .map(EntityEventKey::new)
                    .distinct()
                    .toList();
            if (distinctKeys.size() > 1) {
                throw new RuntimeException("Multiple event keys in a homogeneous batch: " + distinctKeys);
            }
        }
    }

    public List<EntityEvent> getEntityEvents() {
        return entityEvents;
    }

    /**
     * @return True if all items in the batch share the same {@link EntityEventKey}.
     */
    @SuppressWarnings("unused") // For json ser
    public boolean isHomogeneousBatch() {
        return homogeneousBatch;
    }

    public int size() {
        return entityEvents.size();
    }

    @JsonIgnore
    public boolean isEmpty() {
        return entityEvents.isEmpty();
    }

    public boolean hasItems() {
        return !entityEvents.isEmpty();
    }

    @NullMarked
    public void forEach(final Consumer<? super EntityEvent> action) {
        Objects.requireNonNull(action);
        getEntityEvents().forEach(action);
    }


    @JsonIgnore
    public EntityEvent getFirst() {
        return entityEvents.getFirst();
    }

    @Override
    public String toString() {
        return "EntityEventBatch{" +
               "entityEvents.size=" + entityEvents.size() +
               ", homogeneousBatch=" + homogeneousBatch +
               '}';
    }

    public Map<EntityEventKey, EntityEventBatch> groupedByKey() {
        if (eventsByKeyMap == null) {
            if (homogeneousBatch) {
                final EntityEventKey entityEventKey = new EntityEventKey(entityEvents.getFirst());
                eventsByKeyMap = Collections.singletonMap(entityEventKey, this);
            } else {
                eventsByKeyMap = groupByKey(entityEvents);
            }
        }
        return eventsByKeyMap;
    }

    public static Map<EntityEventKey, EntityEventBatch> groupByKey(final List<EntityEvent> entityEvents) {
        if (NullSafe.isEmptyCollection(entityEvents)) {
            return Collections.emptyMap();
        } else if (entityEvents.size() == 1) {
            final EntityEventKey entityEventKey = new EntityEventKey(entityEvents.getFirst());
            return Collections.singletonMap(entityEventKey, EntityEventBatch.singleton(entityEvents.getFirst()));
        } else {
            final Map<EntityEventKey, List<EntityEvent>> batches = entityEvents.stream()
                    .collect(Collectors.groupingBy(EntityEventKey::new));

            final Map<EntityEventKey, EntityEventBatch> map = batches.entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            Entry::getKey,
                            entry -> new EntityEventBatch(
                                    entry.getValue(), true)));
            return Collections.unmodifiableMap(map);
        }
    }
}
