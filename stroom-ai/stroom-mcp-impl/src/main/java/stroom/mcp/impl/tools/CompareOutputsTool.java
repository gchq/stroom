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

import stroom.ai.impl.tools.AbstractBuiltInTool;
import stroom.ai.impl.tools.SteppingWalks;
import stroom.ai.impl.tools.SteppingWalks.Walk;
import stroom.ai.impl.tools.StreamSummaries;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.workspace.ToolTimeoutException;
import stroom.ai.shared.AiToolCategory;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.json.JsonUtil;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/// `compare_outputs` - steps the same records through the current pipeline and a candidate (draft code, or another
/// pipeline such as a new version) and diffs each record's output field by field: per field path, how many records
/// gained, lost or changed a value, with examples. Ported from the Stroom MCP server's `tools/stepping.py`.
///
/// Stepped as the user, who needs the Stepping permission, on this node. It changes nothing, takes a run slot over
/// `/mcp`, says where it is now and then, stops at once when its thread is interrupted, and before the session's
/// time limit for a long call, comparing the records stepped by then.
class CompareOutputsTool extends AbstractBuiltInTool {

    static final String NAME = "compare_outputs";
    static final String PIPELINE_UUID_ARG = "pipeline_uuid";
    static final String STREAM_IDS_ARG = "stream_ids";
    static final String DRAFT_CODE_ARG = "draft_code";
    static final String OTHER_PIPELINE_UUID_ARG = "other_pipeline_uuid";
    static final String ELEMENT_ARG = "element";
    static final String MAX_RECORDS_ARG = "max_records";
    static final String EVENTS_ARG = "events";
    static final int DEFAULT_MAX_RECORDS = 50;
    static final int MAX_RECORDS = 500;

    private final PipelineStepping stepping;
    private final EventLocations eventLocations;

    @Inject
    CompareOutputsTool(final PipelineStepping stepping, final EventLocations eventLocations) {
        this.stepping = stepping;
        this.eventLocations = eventLocations;
    }

    @Override
    public ToolDefinition definition() {
        final ObjectSchema event = ObjectSchema.builder()
                .addIntegerProperty("stream_id", "The event's Events stream (StreamId).")
                .addIntegerProperty("event_id", "The event within it (EventId), from 1.")
                .required("stream_id", "event_id")
                .build();
        return ToolDefinition.builder()
                .name(NAME)
                .description(BuildPlan.onboardingLabel(NAME)
                             + "Step the same records through the current pipeline and a candidate (draft code, or "
                             + "another pipeline such as a new version) and diff each record's output field by "
                             + "field. Reports, per field path, how many records gained, lost or changed a value, "
                             + "with examples. Use it to prove a change touches only the fields it should.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(PIPELINE_UUID_ARG, PipelineStepping.PIPELINE_UUID_DESCRIPTION)
                        .addProperty(STREAM_IDS_ARG, stringArraySchema("Streams whose records to compare."))
                        .addProperty(DRAFT_CODE_ARG, ObjectSchema.builder()
                                .description(PipelineStepping.DRAFT_CODE_DESCRIPTION)
                                .build())
                        .addStringProperty(OTHER_PIPELINE_UUID_ARG, "Compare against this pipeline instead of draft "
                                                                    + "code, e.g. a v2 copy.")
                        .addStringProperty(ELEMENT_ARG, "Element whose output to compare. Defaults to the "
                                                        + "pipeline's own XSLT step.")
                        .addIntegerProperty(MAX_RECORDS_ARG, "The most records to compare (1 to " + MAX_RECORDS
                                                             + "; default " + DEFAULT_MAX_RECORDS + ").")
                        .addProperty(EVENTS_ARG, ArraySchema.builder()
                                .items(event)
                                .description("Indexed events whose records to compare, each {stream_id, event_id} "
                                             + "as find_events gives them (with stream_ids too, or instead).")
                                .build())
                        .required(PIPELINE_UUID_ARG)
                        .build())
                .build();
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.PROCESSING;
    }

    @Override
    public boolean usesRunSlot() {
        // It steps up to 500 records twice.
        return true;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .requires(AppPermission.STEPPING_PERMISSION)
                .checksDocuments(DocumentPermission.VIEW)
                .note("Stepped as the user, only through pipelines they may view, and streams of feeds they may "
                      + "view.")
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        final String pipelineUuid = stringArg(call, PIPELINE_UUID_ARG);
        if (pipelineUuid == null) {
            return JsonResults.refused("Give pipeline_uuid: the pipeline to step.");
        }
        try {
            final Request request = new Request(pipelineUuid,
                    StartOnboardingTool.streamIds(call, STREAM_IDS_ARG),
                    PipelineStepping.draftCode(call, DRAFT_CODE_ARG),
                    stringArg(call, OTHER_PIPELINE_UUID_ARG),
                    stringArg(call, ELEMENT_ARG),
                    maxRecords(call),
                    events(call));
            return JsonResults.of(compareOutputs(context, request));
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        } catch (final EventLocations.LocateException e) {
            return JsonResults.refused(e.getMessage());
        } catch (final ToolTimeoutException e) {
            return JsonResults.refused(PipelineStepping.CANCELLED);
        } catch (final RuntimeException e) {
            return JsonResults.refused(StreamSummaries.reason(e));
        }
    }

    /// The comparison, as the Stroom MCP server gave it: `records_compared`, `records_changed`, `element`,
    /// `fields_changed` (per path `added`, `removed`, `changed` and an `example`, most changed first) and
    /// `unchanged`; and `stopped_early` and `hint` when the call's time ran out first.
    ///
    /// @throws ToolTimeoutException if the call is cancelled.
    Map<String, Object> compareOutputs(final ToolCallContext context, final Request request) {
        if (request.draftCode() == null && request.otherPipelineUuid() == null) {
            throw new ToolRefusal("Give draft_code or other_pipeline_uuid to compare against");
        }
        stepping.checkCanStep();
        final SteppedPipeline base = stepping.load(request.pipelineUuid());
        final SteppedPipeline other = request.otherPipelineUuid() != null
                ? stepping.load(request.otherPipelineUuid())
                : base;
        final String element = request.element() != null
                ? request.element()
                : base.defaultOutputs().getFirst();
        if (request.streamIds().isEmpty() && request.events().isEmpty()) {
            throw new ToolRefusal("Give stream_ids, or events (from find_events), whose records to compare");
        }
        final List<Map<String, Object>> locations = request.events().isEmpty()
                ? List.of()
                : eventLocations.eventLocations(request.events());
        final String otherElement = other.types().containsKey(element)
                ? element
                : other.defaultOutputs().getFirst();
        final Map<String, String> candidate = request.otherPipelineUuid() == null
                ? request.draftCode()
                : null;
        final Compared compared;
        try {
            compared = PipelineStepping.withinTimeLimit(context, () -> step(context, request, base, other, element,
                    otherElement, candidate, locations));
        } finally {
            final List<Long> streams = new ArrayList<>(request.streamIds());
            locations.forEach(loc -> streams.add(((Number) loc.get("raw")).longValue()));
            stepping.audit(context, streams, "Compared outputs of " + base.name() + " with the " + NAME + " tool");
        }
        final Map<String, Object> result = diff(compared.before(), compared.after(), element);
        if (compared.stopped()) {
            result.put("stopped_early", true);
            result.put("hint", PipelineStepping.outOfTime(context, "after " + compared.before().size()
                                                                   + " records")
                               + " The comparison is of those records only.");
        }
        return result;
    }

    // Both sides stepped: the streams' records spread, then exactly the located events' records.
    private Compared step(final ToolCallContext context,
                          final Request request,
                          final SteppedPipeline base,
                          final SteppedPipeline other,
                          final String element,
                          final String otherElement,
                          final Map<String, String> candidate,
                          final List<Map<String, Object>> locations) {
        final Map<String, String> before = new LinkedHashMap<>();
        final Map<String, String> after = new LinkedHashMap<>();
        boolean stopped = false;
        if (!request.streamIds().isEmpty()) {
            final PipelineStepping.Outputs old = stepping.outputs(context, base, request.streamIds(), element, null,
                    request.maxRecords());
            before.putAll(old.outputs());
            stopped = old.stopped();
            if (!stopped) {
                final PipelineStepping.Outputs now = stepping.outputs(context, other, request.streamIds(),
                        otherElement, candidate, request.maxRecords());
                after.putAll(now.outputs());
                stopped = now.stopped();
            }
        }
        for (final Map<String, Object> loc : locations) {
            if (stopped || PipelineStepping.timeIsUp()) {
                stopped = true;
                break;
            }
            // Exactly the located events' records, wherever they are (asked for by the user: a fix proven on what
            // a search found).
            final long raw = ((Number) loc.get("raw")).longValue();
            final StepLocation where = new StepLocation(raw, ((Number) loc.get("part")).longValue(),
                    ((Number) loc.get("record")).longValue());
            final String key = SteppingWalks.recordKey(raw, where);
            try {
                final String old = stepOne(base, raw, where, element, null);
                final String now = stepOne(other, raw, where, otherElement, candidate);
                before.put(key, old);
                after.put(key, now);
            } catch (final ToolTimeoutException e) {
                if (Thread.currentThread().isInterrupted()) {
                    throw e;
                }
                stopped = true;
            }
        }
        if (stopped) {
            // Only the records stepped through both are compared.
            before.keySet().retainAll(after.keySet());
        }
        return new Compared(before, after, stopped);
    }

    // One record stepped, in a session of its own, and its output at the element.
    private String stepOne(final SteppedPipeline pipeline,
                           final long streamId,
                           final StepLocation where,
                           final String element,
                           final Map<String, String> code) {
        try (final Walk walk = stepping.open(pipeline, streamId, code)) {
            return SteppingWalks.output(walk.refresh(where), element);
        }
    }

    /// @param before   Each record's output from the current pipeline, by record key.
    /// @param after    Each record's output from the candidate, by record key.
    /// @param element  The element compared.
    /// @return The field-by-field diff, as the Stroom MCP server gave it.
    static Map<String, Object> diff(final Map<String, String> before,
                                    final Map<String, String> after,
                                    final String element) {
        final Map<String, Map<String, Object>> byPath = new LinkedHashMap<>();
        int changedRecords = 0;
        for (final Map.Entry<String, String> record : before.entrySet()) {
            final Map<String, List<String>> old = EventXml.fieldValues(record.getValue());
            final Map<String, List<String>> now = EventXml.fieldValues(after.getOrDefault(record.getKey(), ""));
            final Set<String> paths = new TreeSet<>(old.keySet());
            paths.addAll(now.keySet());
            boolean recordChanged = false;
            for (final String path : paths) {
                if (Objects.equals(old.get(path), now.get(path))) {
                    continue;
                }
                final String kind = !old.containsKey(path)
                        ? "added"
                        : !now.containsKey(path)
                                ? "removed"
                                : "changed";
                final Map<String, Object> entry = byPath.computeIfAbsent(path, CompareOutputsTool::pathEntry);
                entry.merge(kind, 1, (a, b) -> (Integer) a + (Integer) b);
                if (entry.get("example") == null) {
                    final Map<String, Object> example = new LinkedHashMap<>();
                    example.put("record", record.getKey());
                    example.put("before", old.get(path));
                    example.put("after", now.get(path));
                    entry.put("example", example);
                }
                recordChanged = true;
            }
            if (recordChanged) {
                changedRecords++;
            }
        }
        final List<Map<String, Object>> fields = new ArrayList<>(byPath.values());
        fields.sort(Comparator.comparingInt(entry -> -((Integer) entry.get("added") + (Integer) entry.get("removed")
                                                       + (Integer) entry.get("changed"))));
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("records_compared", before.size());
        result.put("records_changed", changedRecords);
        result.put("element", element);
        result.put("fields_changed", fields);
        result.put("unchanged", byPath.isEmpty());
        return result;
    }

    private static Map<String, Object> pathEntry(final String path) {
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("path", path);
        entry.put("added", 0);
        entry.put("removed", 0);
        entry.put("changed", 0);
        entry.put("example", null);
        return entry;
    }

    // max_records, 1 to 500 (50 by default).
    private int maxRecords(final ToolCall call) {
        if (stringArg(call, MAX_RECORDS_ARG) == null) {
            return DEFAULT_MAX_RECORDS;
        }
        final Integer value = intArg(call, MAX_RECORDS_ARG);
        if (value == null || value < 1 || value > MAX_RECORDS) {
            throw new ToolRefusal(MAX_RECORDS_ARG + " must be a whole number from 1 to " + MAX_RECORDS + ", not '"
                                  + stringArg(call, MAX_RECORDS_ARG) + "'");
        }
        return value;
    }

    /// @param call A call.
    /// @return Its events, from a list of `{stream_id, event_id}` or that list as JSON text.
    /// @throws ToolRefusal if one isn't an event.
    static List<EventLocations.EventRef> events(final ToolCall call) {
        Object value = argsMap(call).get(EVENTS_ARG);
        if (value instanceof final String text && !text.isBlank()) {
            try {
                value = JsonUtil.readValue(text, Object.class);
            } catch (final RuntimeException e) {
                throw new ToolRefusal("events are indexed events, each {stream_id, event_id}: '" + text
                                      + "' is not a list of them");
            }
        }
        if (value instanceof final Map<?, ?> one) {
            value = List.of(one);
        }
        if (value == null || (value instanceof final String text && text.isBlank())) {
            return List.of();
        }
        if (!(value instanceof final List<?> list)) {
            throw new ToolRefusal("events are indexed events, each {stream_id, event_id}");
        }
        final List<EventLocations.EventRef> events = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof final Map<?, ?> map)
                || !(map.get("stream_id") instanceof final Number streamId)
                || !(map.get("event_id") instanceof final Number eventId)) {
                throw new ToolRefusal("events[" + i + "] is not an event {stream_id, event_id} of whole numbers");
            }
            events.add(new EventLocations.EventRef(streamId.longValue(), eventId.longValue()));
        }
        return events;
    }


    // --------------------------------------------------------------------------------


    /// A comparison asked for.
    ///
    /// @param pipelineUuid      The current pipeline.
    /// @param streamIds         Streams whose records to compare.
    /// @param draftCode         Draft code to compare against, by element, or null.
    /// @param otherPipelineUuid Another pipeline to compare against, or null.
    /// @param element           The element whose output to compare, or null for the pipeline's own XSLT step.
    /// @param maxRecords        The most records stepped from the streams.
    /// @param events            Indexed events whose records to compare.
    record Request(String pipelineUuid,
                   List<Long> streamIds,
                   Map<String, String> draftCode,
                   String otherPipelineUuid,
                   String element,
                   int maxRecords,
                   List<EventLocations.EventRef> events) {

    }

    // Both sides' outputs, by record key.
    private record Compared(Map<String, String> before, Map<String, String> after, boolean stopped) {

    }
}
