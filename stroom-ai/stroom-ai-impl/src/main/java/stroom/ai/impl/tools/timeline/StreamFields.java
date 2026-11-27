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

import stroom.ai.impl.tools.instructions.InstructionRules;
import stroom.ai.impl.tools.timeline.TimelineReader.Source;
import stroom.ai.impl.tools.timeline.TimelineReader.Varied;
import stroom.ai.impl.tools.timeline.TimelineSessions.Event;
import stroom.docref.DocRef;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiFunction;

/// Where the pipelines feeding one index map a thing to different fields (`host` to `HostName` in one plan,
/// `host.name` in the other: [TimelineFields.Found#byPipeline]), and nothing explicit (the call, a skill, an AGENTS
/// doc's `## Fields`) names the field, each event's value is read from its own indexing pipeline's field (decided
/// with the user 2026-11-25): the pipeline that produced its stream, by its `StreamId`
/// ([stroom.ai.impl.tools.streams.StreamProvenance], one batched lookup of the distinct streams read), where that is
/// one of the plans' pipelines (one pipeline translating and indexing); else the plans' pipeline its Events stream
/// was traced to, once per stream, by its processing tasks or the pipelines' processor filters
/// ([stroom.ai.impl.tools.streams.IndexingTrace]); else the thing's value in the event's Events XML at its
/// event-logging path (never by matching values), for the events up to the XML cap; else not read, and counted so.
/// Where the thing is a field the event itself carries (its host, application, window title, object or text) the
/// event takes the value; where it is one the classification reads (its element, Action, event type, session or
/// process id), the value goes in its read fields under the primary field's name, so it is read as any other
/// source's. The user is always the index's.
final class StreamFields {

    /// Read by the pipeline that produced the event's stream.
    static final String BY_STREAM = "the stream's pipeline";
    /// Read by the pipeline the stream's processing tasks ran.
    static final String BY_TASKS = "traced by its tasks";
    /// Read by the one pipeline whose processor filters select the stream.
    static final String BY_FILTERS = "traced by the processor filters";
    /// Read from the event's Events XML at the thing's path.
    static final String BY_XML = "the Events XML";
    /// Not read: no pipeline known and the event's XML not read (past the cap, gone, or not holding it).
    static final String NOT_READ = "not read";

    private StreamFields() {
        // Static only.
    }

    /// @param events     The events read, in time order.
    /// @param sources    The sources, by their place.
    /// @param indexedBy  The pipeline that indexed an Events stream into a source's index, by the source's place and
    ///                   the stream's id; null where it isn't known.
    /// @param cap        The most events whose Events XML is read.
    /// @return The events whose Events XML is to be read for a varied thing (their source varies, they hold a
    ///         thing with a path, and their pipeline isn't known), by stream, in time order up to the cap; and how
    ///         many more there were.
    static ToRead toRead(final List<Event> events,
                         final List<Source> sources,
                         final BiFunction<Integer, Long, Indexed> indexedBy,
                         final int cap) {
        final Map<Long, List<Long>> ids = new TreeMap<>();
        int wanted = 0;
        int taken = 0;
        for (final Event event : events) {
            final Source source = sourceOf(event, sources);
            final Long streamId = number(event.streamId());
            final Long eventId = number(event.eventId());
            if (source == null || streamId == null || eventId == null
                || source.varied().values().stream().allMatch(varied -> varied.paths().isEmpty())) {
                continue;
            }
            final Indexed indexed = indexedBy == null
                    ? null
                    : indexedBy.apply(event.source(), streamId);
            if (indexed != null && source.varied().values().stream()
                    .allMatch(varied -> fieldOf(varied, indexed.pipeline()) != null)) {
                continue;
            }
            wanted++;
            if (taken < cap) {
                ids.computeIfAbsent(streamId, k -> new ArrayList<>()).add(eventId);
                taken++;
            }
        }
        return new ToRead(ids, wanted - taken);
    }

    /// @param events    The events read.
    /// @param sources   The sources, by their place.
    /// @param extras    The events' more fields, by event id.
    /// @param indexedBy The pipeline that indexed an Events stream into a source's index, by the source's place and
    ///                  the stream's id, and how it was known; null where it isn't known.
    /// @param xml       The events' Events XML read for their varied things, by `streamId:eventId`.
    /// @return The events and their more fields with each varied thing's value its own pipeline's (or its XML's),
    /// and how each source's were read.
    static Resolved resolve(final List<Event> events,
                            final List<Source> sources,
                            final Map<String, Map<String, String>> extras,
                            final BiFunction<Integer, Long, Indexed> indexedBy,
                            final Map<String, String> xml) {
        if (sources.stream().allMatch(source -> source.varied().isEmpty())) {
            return new Resolved(events, extras, Map.of());
        }
        final List<Event> out = new ArrayList<>(events.size());
        final Map<String, Map<String, String>> outExtras = new LinkedHashMap<>(extras);
        final Map<Integer, Map<String, Map<String, Long>>> read = new LinkedHashMap<>();
        for (final Event event : events) {
            final Source source = sourceOf(event, sources);
            if (source == null || source.varied().isEmpty()) {
                out.add(event);
                continue;
            }
            final Map<String, String> held = extras.getOrDefault(event.id(), Map.of());
            final Indexed indexed = indexedBy == null
                    ? null
                    : indexedBy.apply(event.source(), number(event.streamId()));
            final String text = NullSafe.get(xml, map -> map.get(event.streamId() + ":" + event.eventId()));
            final Map<String, String> values = new LinkedHashMap<>();
            final Map<String, String> moved = new LinkedHashMap<>(held);
            for (final Map.Entry<String, Varied> thing : source.varied().entrySet()) {
                final Varied varied = thing.getValue();
                final String field = indexed == null
                        ? null
                        : fieldOf(varied, indexed.pipeline());
                final String value;
                final String how;
                if (field != null) {
                    value = held.get(field);
                    how = field + " (" + indexed.how() + ")";
                } else if (text != null && !varied.paths().isEmpty()) {
                    value = EventXmlPaths.value(text, varied.paths()).orElse(null);
                    how = value == null
                            ? NOT_READ
                            : String.join(" or ", varied.paths()) + " (" + BY_XML + ")";
                } else {
                    value = null;
                    how = NOT_READ;
                }
                values.put(thing.getKey(), value);
                if (varied.primary() != null) {
                    if (value == null) {
                        moved.remove(varied.primary());
                    } else {
                        moved.put(varied.primary(), value);
                    }
                }
                read.computeIfAbsent(event.source(), k -> new LinkedHashMap<>())
                        .computeIfAbsent(thing.getKey(), k -> new LinkedHashMap<>())
                        .merge(how, 1L, Long::sum);
            }
            outExtras.put(event.id(), moved);
            out.add(with(event, values));
        }
        return new Resolved(out, outExtras, read);
    }

    private static Source sourceOf(final Event event, final List<Source> sources) {
        return event.source() >= 0 && event.source() < sources.size()
                ? sources.get(event.source())
                : null;
    }

    // The plan's field of the pipeline, by its name (as the plans name them) or its uuid.
    private static String fieldOf(final Varied varied, final DocRef pipeline) {
        for (final Map.Entry<String, String> entry : varied.byPipeline().entrySet()) {
            if (entry.getKey().equals(pipeline.getName()) || entry.getKey().equals(pipeline.getUuid())) {
                return entry.getValue();
            }
        }
        return null;
    }

    // The event with the values of the things it carries itself, those not varied kept.
    private static Event with(final Event event, final Map<String, String> values) {
        return new Event(event.time(), event.source(), event.id(), event.role(), event.kind(),
                pick(values, InstructionRules.HOST_FIELD, event.host()),
                pick(values, InstructionRules.APPLICATION_FIELD, event.process()),
                pick(values, InstructionRules.TITLE_FIELD, event.title()), event.window(),
                pick(values, InstructionRules.OBJECT_FIELD, event.object()),
                pick(values, InstructionRules.TEXT_FIELD, event.text()), event.streamId(), event.eventId());
    }

    private static String pick(final Map<String, String> values, final String thing, final String kept) {
        return values.containsKey(thing)
                ? values.get(thing)
                : kept;
    }

    private static Long number(final String value) {
        try {
            return value == null
                    ? null
                    : Long.parseLong(value.strip());
        } catch (final NumberFormatException e) {
            return null;
        }
    }


    // --------------------------------------------------------------------------------


    /// The pipeline that indexed a stream, and how it was known.
    ///
    /// @param pipeline The pipeline.
    /// @param how      How: [#BY_STREAM], [#BY_TASKS] or [#BY_FILTERS].
    record Indexed(DocRef pipeline, String how) {

    }


    // --------------------------------------------------------------------------------


    /// The events whose Events XML is read for their varied things.
    ///
    /// @param ids    Their event ids, by stream.
    /// @param notRead How many more there were, past the cap.
    record ToRead(Map<Long, List<Long>> ids, int notRead) {

        /// Copies the map.
        ToRead {
            ids = Collections.unmodifiableMap(new TreeMap<>(NullSafe.map(ids)));
        }

        /// @return How many events are to be read.
        int size() {
            return ids.values().stream().mapToInt(List::size).sum();
        }
    }


    // --------------------------------------------------------------------------------


    /// The events with their own pipelines' fields.
    ///
    /// @param events The events.
    /// @param extras Their more fields, by event id.
    /// @param read   For each source whose pipelines differ, by its place: each thing's fields read (with how,
    ///               e.g. `host.name (traced by its tasks)`, `EventSource/Device/HostName (the Events XML)` or `not
    ///               read`) and how many events each.
    record Resolved(List<Event> events,
                    Map<String, Map<String, String>> extras,
                    Map<Integer, Map<String, Map<String, Long>>> read) {

        /// Copies the collections.
        Resolved {
            events = List.copyOf(events);
            extras = Collections.unmodifiableMap(new LinkedHashMap<>(NullSafe.map(extras)));
            read = Collections.unmodifiableMap(new LinkedHashMap<>(NullSafe.map(read)));
        }
    }
}
