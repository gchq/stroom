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
import java.util.function.Function;

/// Where the pipelines feeding one index map a thing to different fields (`host` to `HostName` in one plan,
/// `host.name` in the other: [TimelineFields.Found#byPipeline]), each event's value is read from its own pipeline's
/// field (decided with the user 2026-11-25): the pipeline that produced its stream, by its `StreamId`
/// ([stroom.ai.impl.tools.streams.StreamProvenance], one batched lookup of the distinct streams read), where that is
/// one of the plans' pipelines; else the plans' field the event holds (the plans differ, so a pipeline's documents
/// hold its own field), the first in the plans' order; else none. Where the thing is a field the event itself
/// carries (its host, application, window title, object or text) the event takes the value; where it is one the
/// classification reads (its element, Action, event type, session or process id), the value goes in its read
/// fields under the primary field's name, so it is read as any other source's.
final class StreamFields {

    /// Read by the pipeline that produced the event's stream.
    static final String BY_STREAM = "the stream's pipeline";
    /// Read by the plans' field the event holds.
    static final String BY_HELD = "the field the event holds";

    private StreamFields() {
        // Static only.
    }

    /// @param events     The events read.
    /// @param sources    The sources, by their place.
    /// @param extras     The events' more fields, by event id.
    /// @param pipelineOf The pipeline that produced a stream, by its id; null where it isn't known.
    /// @return The events and their more fields with each varied thing's value its own pipeline's, and how each
    /// source's were read.
    static Resolved resolve(final List<Event> events,
                            final List<Source> sources,
                            final Map<String, Map<String, String>> extras,
                            final Function<Long, DocRef> pipelineOf) {
        if (sources.stream().allMatch(source -> source.varied().isEmpty())) {
            return new Resolved(events, extras, Map.of());
        }
        final List<Event> out = new ArrayList<>(events.size());
        final Map<String, Map<String, String>> outExtras = new LinkedHashMap<>(extras);
        final Map<Integer, Map<String, Map<String, Long>>> read = new LinkedHashMap<>();
        for (final Event event : events) {
            final Source source = event.source() >= 0 && event.source() < sources.size()
                    ? sources.get(event.source())
                    : null;
            if (source == null || source.varied().isEmpty()) {
                out.add(event);
                continue;
            }
            final Map<String, String> held = extras.getOrDefault(event.id(), Map.of());
            final DocRef pipeline = pipelineOf == null
                    ? null
                    : pipelineOf.apply(number(event.streamId()));
            final Map<String, String> values = new LinkedHashMap<>();
            final Map<String, String> moved = new LinkedHashMap<>(held);
            for (final Map.Entry<String, Varied> thing : source.varied().entrySet()) {
                final Varied varied = thing.getValue();
                String field = pipeline == null
                        ? null
                        : fieldOf(varied, pipeline);
                String how = BY_STREAM;
                if (field == null) {
                    field = varied.fields().stream().filter(held::containsKey).findFirst().orElse(null);
                    how = BY_HELD;
                }
                final String value = field == null
                        ? null
                        : held.get(field);
                values.put(thing.getKey(), value);
                if (varied.primary() != null) {
                    if (value == null) {
                        moved.remove(varied.primary());
                    } else {
                        moved.put(varied.primary(), value);
                    }
                }
                if (field != null) {
                    read.computeIfAbsent(event.source(), k -> new LinkedHashMap<>())
                            .computeIfAbsent(thing.getKey(), k -> new LinkedHashMap<>())
                            .merge(field + " (" + how + ")", 1L, Long::sum);
                }
            }
            outExtras.put(event.id(), moved);
            out.add(with(event, values));
        }
        return new Resolved(out, outExtras, read);
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


    /// The events with their own pipelines' fields.
    ///
    /// @param events The events.
    /// @param extras Their more fields, by event id.
    /// @param read   For each source whose pipelines differ, by its place: each thing's fields read (with how,
    ///               e.g. `host.name (the stream's pipeline)`) and how many events each.
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
