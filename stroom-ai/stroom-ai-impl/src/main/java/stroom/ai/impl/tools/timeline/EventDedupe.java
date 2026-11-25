/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.impl.tools.timeline;

import stroom.ai.impl.tools.timeline.TimelineReader.Source;
import stroom.ai.impl.tools.timeline.TimelineSessions.Event;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/// The events a timeline read, each counted once (decided with the user 2026-11-25): the same event read from two
/// sources (two indexes fed from one stream), or the old and new documents of a reprocessed event in one index, are
/// one event. An event is known by the raw stream its Events stream was translated from (its stream's parent,
/// [stroom.ai.impl.tools.streams.StreamProvenance]) and its record (`EventId`), not by `(StreamId, EventId)`, as
/// reprocessing gives an event a new `StreamId`; where a stream's parent isn't known (not seen, or none), by its own
/// `StreamId`. Of the documents of one event, the newest stream's (the highest `StreamId`, the latest processing) is
/// kept, the earlier source's where they are of one stream. A source whose index has no `StreamId` and `EventId`
/// isn't deduplicated, and is said so.
final class EventDedupe {

    private EventDedupe() {
        // Static only.
    }

    /// @param events   The events read, in time order.
    /// @param sources  The sources, by their place.
    /// @param parentOf A stream's parent (the raw stream it was translated from), by its id; null where it isn't
    ///                 known.
    /// @return The events each kept once, in their order, with the duplicates left out per source.
    static Deduped dedupe(final List<Event> events,
                          final List<Source> sources,
                          final Function<Long, Long> parentOf) {
        final Map<String, Event> kept = new LinkedHashMap<>();
        final List<Event> unkeyed = new ArrayList<>();
        final Map<Integer, Integer> duplicates = new LinkedHashMap<>();
        for (final Event event : events) {
            final Long streamId = number(event.streamId());
            final Long eventId = number(event.eventId());
            if (streamId == null || eventId == null) {
                unkeyed.add(event);
                continue;
            }
            final Long parent = parentOf == null
                    ? null
                    : parentOf.apply(streamId);
            final String key = (parent == null
                    ? "s" + streamId
                    : "p" + parent) + ":" + eventId;
            final Event earlier = kept.get(key);
            if (earlier == null) {
                kept.put(key, event);
            } else if (newer(event, earlier)) {
                kept.put(key, event);
                duplicates.merge(earlier.source(), 1, Integer::sum);
            } else {
                duplicates.merge(event.source(), 1, Integer::sum);
            }
        }
        final Set<Event> keep = Collections.newSetFromMap(new IdentityHashMap<>());
        keep.addAll(kept.values());
        keep.addAll(unkeyed);
        final List<Event> out = new ArrayList<>(keep.size());
        for (final Event event : events) {
            if (keep.contains(event)) {
                out.add(event);
            }
        }
        final Set<Integer> notDeduplicated = new LinkedHashSet<>();
        for (int i = 0; i < sources.size(); i++) {
            if (!sources.get(i).hasStreamIds()) {
                notDeduplicated.add(i);
            }
        }
        return new Deduped(out, duplicates, notDeduplicated);
    }

    // True if the event is of a later stream than the one kept, which replaces it.
    private static boolean newer(final Event event, final Event kept) {
        return number(event.streamId()) > number(kept.streamId());
    }

    private static Long number(final String value) {
        try {
            return NullSafe.isBlankString(value)
                    ? null
                    : Long.parseLong(value.strip());
        } catch (final NumberFormatException e) {
            return null;
        }
    }


    // --------------------------------------------------------------------------------


    /// The events each kept once.
    ///
    /// @param events          The events kept, in their order.
    /// @param duplicates      The documents left out as another document of the same event, by the place of the
    ///                        source they were read from.
    /// @param notDeduplicated The places of the sources whose index has no `StreamId` and `EventId`, so whose events
    ///                        weren't deduplicated.
    record Deduped(List<Event> events, Map<Integer, Integer> duplicates, Set<Integer> notDeduplicated) {

        /// Copies the collections.
        Deduped {
            events = List.copyOf(events);
            duplicates = Collections.unmodifiableMap(new LinkedHashMap<>(NullSafe.map(duplicates)));
            notDeduplicated = Collections.unmodifiableSet(new LinkedHashSet<>(NullSafe.set(notDeduplicated)));
        }
    }
}
