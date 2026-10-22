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

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.tools.AbstractBuiltInTool;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.ErrorTriage;
import stroom.ai.impl.tools.MergedPipeline;
import stroom.ai.impl.tools.SteppingWalks;
import stroom.ai.impl.tools.SteppingWalks.Walk;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.StreamSummaries;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.shared.AiToolCategory;
import stroom.meta.shared.Meta;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// `locate_event` - traces a reported stream, and optionally one event in it, back to where it came from: the raw
/// stream, the part and record within it, the pipeline, and the XSLTs and text converters it runs (marking any
/// inherited from a template). With an event id, the stored event is shown next to the record's input and what
/// stepping that record produces now. Changes nothing.
///
/// Read as the user: streams are found only where the user may view their feeds, reading them needs the View Data
/// permission, and stepping needs the Stepping permission. It steps, so a call takes one of the user's run slots.
class LocateEventTool extends AbstractBuiltInTool {

    static final String NAME = "locate_event";
    static final String STREAM_ID_ARG = "stream_id";
    static final String EVENT_ID_ARG = "event_id";
    static final String PIPELINE_UUID_ARG = "pipeline_uuid";
    /// The most characters of each of the record's input, the stored event and the event stepped now (the Stroom
    /// MCP server's max_stream_chars, a quarter each).
    static final int BUDGET = 20_000 / 4;

    private final EventLocations eventLocations;
    private final StreamReader streamReader;
    private final SteppingWalks steppingWalks;
    private final DocumentAccess documentAccess;
    private final AiAuditLog auditLog;

    @Inject
    LocateEventTool(final EventLocations eventLocations,
                    final StreamReader streamReader,
                    final SteppingWalks steppingWalks,
                    final DocumentAccess documentAccess,
                    final AiAuditLog auditLog) {
        this.eventLocations = eventLocations;
        this.streamReader = streamReader;
        this.steppingWalks = steppingWalks;
        this.documentAccess = documentAccess;
        this.auditLog = auditLog;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("Trace a reported stream, and optionally one event in it, back to where it came from: "
                             + "the raw stream, the part and record within it, the pipeline, and the XSLTs and text "
                             + "converters it runs (marking any inherited from a template). With an event_id, the "
                             + "stored event is shown next to the record's input and what stepping that record "
                             + "produces now, ready for step_pipeline(stream, record, part) and a fix.")
                .parameters(ObjectSchema.builder()
                        .addIntegerProperty(STREAM_ID_ARG, "The stream the user reported: Events, Error or Raw "
                                                           + "Events.")
                        .addIntegerProperty(EVENT_ID_ARG, "The event within the Events stream (1-based, as "
                                                          + "dashboards show EventId).")
                        .addStringProperty(PIPELINE_UUID_ARG, "Only needed when a raw stream was processed by more "
                                                              + "than one pipeline.")
                        .required(STREAM_ID_ARG)
                        .build())
                .build();
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.PROCESSING;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .requires(AppPermission.VIEW_DATA_PERMISSION, AppPermission.STEPPING_PERMISSION)
                .checksDocuments(DocumentPermission.VIEW)
                .build();
    }

    @Override
    public boolean usesRunSlot() {
        // It steps a stream.
        return true;
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        final Integer streamId = intArg(call, STREAM_ID_ARG);
        if (streamId == null) {
            return ToolResult.error("Error: give stream_id, the stream the user reported.");
        }
        final Integer eventId = intArg(call, EVENT_ID_ARG);
        if (eventId != null && eventId < 1) {
            return ToolResult.error("Error: event_id counts from 1, as dashboards show EventId.");
        }
        if (!documentAccess.canViewData()) {
            return ToolResult.error("Error: you don't have permission to view data, so the stream can't be traced.");
        }
        final List<Long> viewed = new ArrayList<>();
        try {
            final Map<String, Object> result = locate(streamId, eventId, stringArg(call, PIPELINE_UUID_ARG),
                    viewed);
            return ToolResult.text(JsonUtil.writeValueAsString(result, false)).withStructuredContent(result);
        } catch (final RuntimeException e) {
            return ToolResult.error("Error: " + StreamSummaries.reason(e));
        } finally {
            auditLog.streamsViewed(context.session(), viewed, "Traced stream " + streamId + " with the " + NAME
                                                              + " tool");
        }
    }

    /// @return Where the stream (and event) came from, as the Stroom MCP server gave it.
    Map<String, Object> locate(final long streamId,
                               final Integer eventId,
                               final String pipelineUuid,
                               final List<Long> viewed) {
        final EventLocations.Resolved found = eventLocations.resolve(streamId, pipelineUuid);
        final Meta rawMeta = eventLocations.meta(found.raw());
        viewed.add(found.raw());
        final StreamReader.Fetched first = streamReader.fetch(found.raw(), 0, null);
        final MergedPipeline pipeline = eventLocations.pipeline(found.pipeline());
        final Map<String, Object> pipelineMap = new LinkedHashMap<>();
        pipelineMap.put("uuid", found.pipeline());
        pipelineMap.put("name", pipeline.doc().getName());
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("reported", found.reported());
        result.put("raw_stream", found.raw());
        result.put("feed", rawMeta.getFeedName());
        result.put("raw_parts", first.totalItems());
        result.put("pipeline", pipelineMap);
        result.put("events_stream", found.events());
        result.put("translation_docs", pipeline.translationDocMaps());
        if (eventId == null) {
            result.put("hint", "Give event_id to pin down the record, or find the event type in the Events stream "
                               + "(summarise_streams (kind=events)) and step_pipeline the raw stream's records.");
            return result;
        }
        if (found.events() == null) {
            throw new EventLocations.LocateException("Raw stream " + found.raw() + " has no Events stream from "
                                                     + "this pipeline to find event " + eventId + " in");
        }

        viewed.add(found.events());
        final StreamReader.Fetched stored = streamReader.fetch(found.events(), eventId - 1, null);
        if (stored.totalItems() != null && eventId > stored.totalItems()) {
            throw new EventLocations.LocateException("Events stream " + found.events() + " has "
                                                     + stored.totalItems() + " events; there is no event "
                                                     + eventId);
        }
        final String storedXml = StreamReader.rootClosed(NullSafe.string(stored.data()));

        // Events records run on across the raw stream's parts, so count output events record by record.
        final String outputElement = pipeline.defaultOutputs().getLast();
        long seen = 0;
        int steps = 0;
        try (final Walk walk = steppingWalks.open(pipeline.doc(), found.raw(), null,
                EventLocations.STEP_TIME_LIMIT)) {
            SteppingResult step = walk.first();
            while (step != null && step.isFoundRecord() && steps < EventLocations.MAX_RECORDS) {
                steps++;
                final String output = SteppingWalks.output(step, outputElement);
                final int produced = EventXml.countEvents(output);
                if (seen + produced >= eventId) {
                    located(result, step, pipeline, outputElement, output, storedXml, eventId, (int) seen,
                            produced);
                    return result;
                }
                seen += produced;
                step = walk.forward(step.getFoundLocation());
            }
        }
        throw new EventLocations.LocateException("Event " + eventId + " was not found in the first " + steps
                                                 + " records of raw stream " + found.raw() + " (" + seen
                                                 + " events produced); the pipeline may have changed since it "
                                                 + "was processed");
    }

    private static void located(final Map<String, Object> result,
                                final SteppingResult step,
                                final MergedPipeline pipeline,
                                final String outputElement,
                                final String output,
                                final String storedXml,
                                final int eventId,
                                final int seen,
                                final int produced) {
        final StepLocation location = step.getFoundLocation();
        final String now = EventXml.nthEvent(output, eventId - seen);
        final String parser = pipeline.parser();
        final String input = Objects.requireNonNullElse(
                NullSafe.nonBlankStringElse(parser == null
                        ? null
                        : SteppingWalks.input(step, parser), SteppingWalks.input(step, outputElement)), "");
        final Map<String, Object> where = new LinkedHashMap<>();
        where.put("part", location.getPartIndex());
        where.put("record", location.getRecordIndex());
        where.put("event_within_record", eventId - seen);
        where.put("events_from_record", produced);
        result.put("event_id", eventId);
        result.put("location", where);
        result.put("record_input", cut(input));
        result.put("stored_event", cut(storedXml));
        result.put("stepped_event_now", cut(now != null
                ? now
                : output));
        final Boolean same = now == null
                ? null
                : EventXml.fieldValues(now).equals(EventXml.fieldValues(storedXml));
        result.put("same_as_stored", same);
        result.put("errors_now", SteppingWalks.markers(step, String.valueOf(location.getRecordIndex())).stream()
                .map(LocateEventTool::markerMap)
                .toList());
        result.put("hint", "Step it with step_pipeline(stream_id=raw_stream, record=location.record, "
                           + "part=location.part); try a fix with draft_code, then summarise_fix.");
        if (Boolean.FALSE.equals(same)) {
            result.put("note", "Stepping now gives a different event than the one stored: the translation or "
                               + "reference data changed since it was processed.");
        }
    }

    /// @return A marker as the Stroom MCP server gave one: `severity`, `element`, `message`, `location`, `record`.
    static Map<String, Object> markerMap(final ErrorTriage.Marker marker) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("severity", marker.severity());
        map.put("element", marker.element());
        map.put("message", marker.message());
        map.put("location", marker.location());
        map.put("record", marker.record());
        return map;
    }

    private static String cut(final String text) {
        return text.length() > BUDGET
                ? text.substring(0, BUDGET)
                : text;
    }
}
