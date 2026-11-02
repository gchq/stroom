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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.MergedPipeline;
import stroom.ai.impl.tools.SteppingWalks;
import stroom.ai.impl.tools.SteppingWalks.Walk;
import stroom.ai.impl.tools.StreamReader;
import stroom.data.shared.StreamTypeNames;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.Status;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.SteppingResult;

import jakarta.inject.Inject;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/// Traces what a user reported back to where it came from: the raw stream, pipeline and Events stream for
/// whatever stream they reported, and each indexed event's raw record, found by stepping its raw stream with the
/// pipeline that produced it and counting the events each record gives (a record can give none, or several).
///
/// Read as the user: streams are found only where the user may view their feeds, pipelines only where they may
/// view them, and stepping needs the Stepping permission.
class EventLocations {

    /// The most records stepped from the head of a raw stream to find one event in (also the most records a stepping
    /// call steps, [PipelineStepping#MAX_SAMPLE_RECORDS]).
    static final int MAX_RECORDS = 500;
    /// The longest one step may take.
    static final Duration STEP_TIME_LIMIT = Duration.ofMinutes(2);
    /// The most streams produced from a stream looked at.
    static final int MAX_CHILDREN = 100;

    private final StreamReader streamReader;
    private final DocumentAccess documentAccess;
    private final SteppingWalks steppingWalks;

    @Inject
    EventLocations(final StreamReader streamReader,
                   final DocumentAccess documentAccess,
                   final SteppingWalks steppingWalks) {
        this.streamReader = streamReader;
        this.documentAccess = documentAccess;
        this.steppingWalks = steppingWalks;
    }

    /// @param streamId     The stream the user reported: Events, Error or Raw Events.
    /// @param pipelineUuid The pipeline, only needed when a raw stream was processed by more than one; or null.
    /// @return The raw stream, pipeline and Events stream for it.
    /// @throws LocateException if it can't be traced, saying why.
    Resolved resolve(final long streamId, final String pipelineUuid) {
        final Meta meta = meta(streamId);
        final String kind = meta.getTypeName();
        final Map<String, Object> reported = new LinkedHashMap<>();
        reported.put("id", streamId);
        reported.put("type", kind);
        if (meta.getPipelineUuid() != null) {
            // An output (Events, Error, ...): its parent is the input.
            final Long raw = meta.getParentMetaId();
            if (raw == null) {
                throw new LocateException("Stream " + streamId + " (" + kind + ") has no parent stream to trace "
                                          + "back to");
            }
            final String pipeline = meta.getPipelineUuid();
            final Long events = StreamTypeNames.EVENTS.equals(kind)
                    ? Long.valueOf(streamId)
                    : children(raw).stream()
                            .filter(c -> StreamTypeNames.EVENTS.equals(c.getTypeName())
                                         && pipeline.equals(c.getPipelineUuid()))
                            .map(Meta::getId)
                            .findFirst()
                            .orElse(null);
            return new Resolved(reported, raw, pipeline, events);
        }
        List<Meta> outputs = children(streamId).stream()
                .filter(c -> StreamTypeNames.EVENTS.equals(c.getTypeName()))
                .toList();
        if (pipelineUuid != null) {
            outputs = outputs.stream().filter(c -> pipelineUuid.equals(c.getPipelineUuid())).toList();
        }
        final List<String> pipelines = List.copyOf(new TreeSet<>(outputs.stream()
                .map(Meta::getPipelineUuid)
                .filter(Objects::nonNull)
                .toList()));
        if (pipelines.size() > 1) {
            throw new LocateException("Stream " + streamId + " was processed by several pipelines " + pipelines
                                      + "; give pipeline_uuid");
        }
        if (pipelines.isEmpty() && pipelineUuid == null) {
            throw new LocateException("Stream " + streamId + " (" + kind + ") has no Events output; give "
                                      + "pipeline_uuid to step it");
        }
        return new Resolved(reported, streamId, pipelineUuid != null
                ? pipelineUuid
                : pipelines.getFirst(), outputs.isEmpty()
                ? null
                : outputs.getFirst().getId());
    }

    /// Each event's raw record, found by stepping its raw stream with the pipeline that produced it and counting
    /// the events each record gives. Events of one raw stream are found in one pass. Asked for by the user: proving
    /// a fix on the events a search found, across many streams, not on whole streams.
    ///
    /// @param events The events, each its Events stream (the StreamId an index holds) and its EventId (from 1).
    /// @return Each event's `stream_id`, `event_id`, `raw`, `part` and `record`.
    /// @throws LocateException if an event can't be found, saying why.
    List<Map<String, Object>> eventLocations(final List<EventRef> events) {
        final Map<Long, List<Long>> byStream = new LinkedHashMap<>();
        for (final EventRef event : events) {
            byStream.computeIfAbsent(event.streamId(), k -> new ArrayList<>()).add(event.eventId());
        }
        final List<Map<String, Object>> out = new ArrayList<>();
        for (final Map.Entry<Long, List<Long>> entry : byStream.entrySet()) {
            final long streamId = entry.getKey();
            final Resolved found = resolve(streamId, null);
            if (!Objects.equals(found.events(), streamId)) {
                throw new LocateException("Stream " + streamId + " is not an Events stream: give each event's "
                                          + "Events stream (StreamId)");
            }
            final MergedPipeline pipeline = pipeline(found.pipeline());
            final String outputElement = pipeline.defaultOutputs().getLast();
            final List<Long> targets = new ArrayList<>(new TreeSet<>(entry.getValue()));
            final int cap = MAX_RECORDS * 4;
            long seen = 0;
            int steps = 0;
            try (final Walk walk = steppingWalks.open(pipeline.doc(), found.raw(), null, STEP_TIME_LIMIT)) {
                SteppingResult step = walk.first();
                while (step != null && step.isFoundRecord() && !targets.isEmpty() && steps < cap) {
                    steps++;
                    final int produced = EventXml.countEvents(SteppingWalks.output(step, outputElement));
                    final StepLocation location = step.getFoundLocation();
                    while (!targets.isEmpty() && seen < targets.getFirst() && targets.getFirst() <= seen + produced) {
                        final Map<String, Object> located = new LinkedHashMap<>();
                        located.put("stream_id", streamId);
                        located.put("event_id", targets.removeFirst());
                        located.put("raw", found.raw());
                        located.put("part", location.getPartIndex());
                        located.put("record", location.getRecordIndex());
                        out.add(located);
                    }
                    seen += produced;
                    if (!targets.isEmpty()) {
                        step = walk.forward(location);
                    }
                }
            }
            if (!targets.isEmpty()) {
                throw new LocateException("Event(s) " + targets + " of Events stream " + streamId + " were not "
                                          + "found in the first " + steps + " records of raw stream " + found.raw()
                                          + " (" + seen + " events produced): the pipeline may have changed "
                                          + "since it was processed");
            }
        }
        return out;
    }

    /// @param uuid A pipeline's UUID.
    /// @return It, merged with the templates it inherits from.
    /// @throws LocateException if it, or a template it inherits from, can't be read.
    MergedPipeline pipeline(final String uuid) {
        return MergedPipeline.load(documentAccess, uuid)
                .orElseThrow(() -> new LocateException("Pipeline " + uuid + " can't be read: it doesn't exist, or "
                                                       + "you don't have permission to view it or a template it "
                                                       + "inherits from"));
    }

    /// @return The stream's meta.
    /// @throws LocateException if there is no such stream.
    Meta meta(final long streamId) {
        try {
            return streamReader.meta(streamId);
        } catch (final StreamReader.StreamReadException e) {
            throw new LocateException(e.getMessage());
        }
    }

    private List<Meta> children(final long streamId) {
        return streamReader.children(streamId, MAX_CHILDREN).stream()
                .map(MetaRow::getMeta)
                .filter(meta -> !Status.DELETED.equals(meta.getStatus()))
                .toList();
    }


    // --------------------------------------------------------------------------------


    /// What a reported stream traces back to.
    ///
    /// @param reported The stream reported: `id` and `type`.
    /// @param raw      The raw stream it came from.
    /// @param pipeline The pipeline that processed it.
    /// @param events   The Events stream that pipeline wrote from it, or null.
    record Resolved(Map<String, Object> reported, long raw, String pipeline, Long events) {

    }

    /// An indexed event, as a search or a dashboard row gives it.
    ///
    /// @param streamId Its Events stream (the StreamId an index holds).
    /// @param eventId  The event within it (EventId), from 1.
    record EventRef(long streamId, long eventId) {

    }

    /// What a reported stream or event can't be traced, saying why.
    static class LocateException extends RuntimeException {

        /// @param message Why it can't be traced.
        LocateException(final String message) {
            super(message);
        }
    }
}
