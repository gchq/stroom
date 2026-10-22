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

import stroom.ai.impl.AiChatRunManager;
import stroom.ai.impl.tools.AbstractBuiltInTool;
import stroom.ai.impl.tools.CallProgress;
import stroom.ai.impl.tools.ErrorTriage;
import stroom.ai.impl.tools.PyText;
import stroom.ai.impl.tools.SteppingWalks;
import stroom.ai.impl.tools.SteppingWalks.Walk;
import stroom.ai.impl.tools.StreamSummaries;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolDescription;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.IntegerSchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.workspace.ToolTimeoutException;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.json.JsonUtil;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/// `step_records` - steps chosen records of existing streams, in place, and returns one verdict for them all, like
/// step_sample: error groups triaged, and per record the events it produced and its errors. With survey_feed's
/// locations it checks a translation against every kind of event a feed holds, without copying or processing
/// anything. A clean verdict over every shape is recorded in the build's record for the code stepped. Ported from
/// the Stroom MCP server's `tools/stepping.py`.
///
/// Stepped as the user, who needs the Stepping permission; locations in a row from one stream share a stepping
/// session on this node, ended when they are done. It takes a run slot over `/mcp`, says where it is now and then,
/// stops at once when its thread is interrupted, and stops before the session's time limit for a long call, with a
/// verdict of the records stepped so far that is not recorded.
class StepRecordsTool extends AbstractBuiltInTool {

    static final String NAME = "step_records";
    static final String PIPELINE_UUID_ARG = "pipeline_uuid";
    static final String LOCATIONS_ARG = "locations";
    static final String DRAFT_CODE_ARG = "draft_code";
    static final String EXPECT_NONE = "none";
    static final String EXPECT_EVENT = "event";

    private final PipelineStepping stepping;
    private final int maxSampleRecords;

    @Inject
    StepRecordsTool(final PipelineStepping stepping) {
        this(stepping, PipelineStepping.MAX_SAMPLE_RECORDS);
    }

    /// As the injected constructor, with the most records stepped given, for tests.
    StepRecordsTool(final PipelineStepping stepping, final int maxSampleRecords) {
        this.stepping = stepping;
        this.maxSampleRecords = maxSampleRecords;
    }

    @Override
    public ToolDefinition definition() {
        final ObjectSchema location = ObjectSchema.builder()
                .addProperty("stream", new IntegerSchema(ToolDescription.of("The stream.")))
                .addProperty("part", new IntegerSchema(ToolDescription.of("The zero-based part (default 0).")))
                .addProperty("record", new IntegerSchema(ToolDescription.of("The zero-based record within the "
                                                                            + "part.")))
                .addStringProperty("shape", "The survey shape this record is an example of, if any.")
                .addEnumProperty("expect", List.of(EXPECT_EVENT, EXPECT_NONE), "none: a kind the user chose to "
                                                                               + "leave untranslated "
                                                                               + "(survey_feed sets it), so no "
                                                                               + "Event is right.")
                .required("stream", "record")
                .build();
        return ToolDefinition.builder()
                .name(NAME)
                .description(BuildPlan.onboardingLabel(NAME)
                             + "Step chosen records of existing streams, in place, and return one verdict for them "
                             + "all, like step_sample: error groups triaged, and per record the events it produced "
                             + "and its errors. Use it with survey_feed's locations to check a translation against "
                             + "every kind of event a feed holds, without copying or processing anything. A record "
                             + "that produces no event is flagged.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(PIPELINE_UUID_ARG, PipelineStepping.PIPELINE_UUID_DESCRIPTION)
                        .addProperty(LOCATIONS_ARG, ArraySchema.builder()
                                .items(location)
                                .description("Records to step where they are, e.g. survey_feed's locations: "
                                             + "{stream, part, record}.")
                                .build())
                        .addProperty(DRAFT_CODE_ARG, ObjectSchema.builder()
                                .description(PipelineStepping.DRAFT_CODE_DESCRIPTION)
                                .build())
                        .required(PIPELINE_UUID_ARG, LOCATIONS_ARG)
                        .build())
                .build();
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.PROCESSING;
    }

    @Override
    public boolean isReadOnly() {
        // Read-only, although a clean verdict is recorded in the build's record of the pipeline stepped, as
        // step_sample's is (see there, and check_events): the tools' own bookkeeping, kept only for pipelines in a
        // build, changing no data, document content or processing.
        return true;
    }

    @Override
    public boolean usesRunSlot() {
        // It steps up to 500 records.
        return true;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .requires(AppPermission.STEPPING_PERMISSION)
                .checksDocuments(DocumentPermission.VIEW)
                .note("Stepped as the user, only through pipelines they may view, and streams of feeds they may "
                      + "view. A clean verdict is recorded in the build record of the pipeline stepped, when it "
                      + "is in a build.")
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        final String pipelineUuid = stringArg(call, PIPELINE_UUID_ARG);
        if (pipelineUuid == null) {
            return JsonResults.refused("Give pipeline_uuid: the pipeline to step.");
        }
        try {
            final List<Location> locations = locations(call);
            if (locations.isEmpty()) {
                return JsonResults.refused("Give locations: the records to step, e.g. survey_feed's locations: "
                                           + "{stream, part, record}.");
            }
            final Map<String, String> draftCode = PipelineStepping.draftCode(call, DRAFT_CODE_ARG);
            return JsonResults.of(stepRecords(call, context, pipelineUuid, locations, draftCode));
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        } catch (final ToolTimeoutException e) {
            return JsonResults.refused(PipelineStepping.CANCELLED);
        } catch (final AiChatRunManager.WaitingForUserException e) {
            // A form waits for the user: the call is made again with the answer.
            throw e;
        } catch (final RuntimeException e) {
            return JsonResults.refused(StreamSummaries.reason(e));
        }
    }

    /// The records stepped, as the Stroom MCP server gave them: `pipeline`, `records_stepped`,
    /// `records_with_errors`, `records_not_found`, `shapes_not_clean`, `left_untranslated_as_intended`,
    /// `draft_code_used`, the triage (`verdict`, `groups_by_class`, `groups`) and `records` (the first 100), and any
    /// `hint`, `stopped_early` and `record`.
    ///
    /// @throws ToolTimeoutException if the call is cancelled.
    Map<String, Object> stepRecords(final ToolCall call,
                                    final ToolCallContext context,
                                    final String pipelineUuid,
                                    final List<Location> locations,
                                    final Map<String, String> draftCode) {
        stepping.checkCanStep();
        stepping.checkDrafts(call, context, pipelineUuid, draftCode);
        final List<Location> stepped = locations.subList(0, Math.min(locations.size(), maxSampleRecords));
        final List<Long> streams = new ArrayList<>(new TreeSet<>(locations.stream().map(Location::stream).toList()));
        stepping.refuseOlderThanFeed(streams);
        final SteppedPipeline pipeline = stepping.load(pipelineUuid);
        pipeline.refuseMistyped();
        final Walked walked;
        try {
            walked = PipelineStepping.withinTimeLimit(context, () -> walk(context, pipeline, stepped, draftCode));
        } finally {
            stepping.audit(context, streams, "Stepped records through " + pipeline.name() + " with the " + NAME
                                             + " tool");
        }
        return verdict(context, pipeline, stepped, draftCode, walked);
    }

    // Each location stepped, until the call's time is up.
    private Walked walk(final ToolCallContext context,
                        final SteppedPipeline pipeline,
                        final List<Location> locations,
                        final Map<String, String> draftCode) {
        final Walked walked = new Walked();
        final CallProgress progress = CallProgress.of(context);
        progress.say("Stepping " + locations.size() + " records through " + pipeline.name());
        int at = 0;
        // Locations in a row from one stream are stepped in one session: Stroom captures the stream once for them.
        while (at < locations.size() && !walked.cut) {
            final long streamId = locations.get(at).stream();
            int end = at;
            while (end < locations.size() && locations.get(end).stream() == streamId) {
                end++;
            }
            try (final Walk walk = stepping.open(pipeline, streamId, draftCode)) {
                for (final Location location : locations.subList(at, end)) {
                    if (PipelineStepping.timeIsUp()) {
                        walked.cut = true;
                        break;
                    }
                    final StepLocation where = new StepLocation(location.stream(), location.part(),
                            location.record());
                    record(walked, pipeline, location, SteppingWalks.recordKey(location.stream(), where),
                            walk.refresh(where));
                    progress.nowAndThen("Stepping records through " + pipeline.name() + ": "
                                        + walked.records.size() + " of " + locations.size() + " stepped");
                }
            } catch (final ToolTimeoutException e) {
                if (Thread.currentThread().isInterrupted()) {
                    throw e;
                }
                // A step outlasted the call's time.
                walked.cut = true;
            }
            at = end;
        }
        return walked;
    }

    // What one record's step met and wrote.
    private static void record(final Walked walked,
                               final SteppedPipeline pipeline,
                               final Location location,
                               final String key,
                               final SteppingResult result) {
        final String outputElement = pipeline.outputElement();
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("record", key);
        entry.put("shape", location.shape());
        if (!PipelineStepping.isFound(result)) {
            final List<ErrorTriage.Marker> found = SteppingWalks.markers(result, key);
            walked.markers.addAll(found);
            entry.put("found", false);
            if (!found.isEmpty()) {
                entry.put("errors", found.size());
            }
            walked.records.add(entry);
            return;
        }
        List<ErrorTriage.Marker> found = new ArrayList<>(SteppingWalks.markers(result, key));
        found.addAll(SteppingVerdicts.emptyOutput(result, outputElement, key));
        final String output = SteppingWalks.output(result, outputElement);
        final int events = EventXml.countEvents(output);
        if (SteppingVerdicts.writesUnknown(output)) {
            walked.unknown.add(key);
            walked.unknownOutputs.add(output);
        }
        if (EXPECT_NONE.equals(location.expect())) {
            // Left untranslated by choice: the no-elements check doesn't apply, but an Event does.
            found = new ArrayList<>(found.stream()
                    .filter(marker -> !marker.message().startsWith(SteppingVerdicts.NO_ELEMENTS))
                    .toList());
            if (events > 0) {
                found.add(ErrorTriage.marker("WARNING", outputElement, "The record produced " + events
                                                                       + " Event(s), but its kind is left "
                                                                       + "untranslated", null).withRecord(key));
            }
        } else if (events == 0 && found.isEmpty() && !SteppingVerdicts.sendsOn(output)) {
            found.add(ErrorTriage.marker("WARNING", outputElement, "The record produced no Event", null)
                    .withRecord(key));
        }
        walked.markers.addAll(found);
        entry.put("events", events);
        entry.put("errors", found.size());
        if (EXPECT_NONE.equals(location.expect())) {
            entry.put("expect", EXPECT_NONE);
        }
        walked.records.add(entry);
    }

    // The verdict of what was stepped, recorded when every shape is clean.
    private Map<String, Object> verdict(final ToolCallContext context,
                                        final SteppedPipeline pipeline,
                                        final List<Location> locations,
                                        final Map<String, String> draftCode,
                                        final Walked walked) {
        final List<Map<String, Object>> records = walked.records;
        final Map<String, Object> summary = stepping.triage(walked.markers, pipeline, records.size());
        SteppingVerdicts.listRecords(summary, walked.markers);
        SteppingVerdicts.block(summary, stepping.unagreedUnknown(pipeline, pipeline.outputElement(),
                walked.unknown, records.size(), draftCode, SteppingVerdicts.whatUnknownHolds(walked.unknownOutputs)));
        final List<String> missing = records.stream()
                .filter(r -> Boolean.FALSE.equals(r.get("found")))
                .map(r -> String.valueOf(r.get("record")))
                .toList();
        final List<String> uncovered = List.copyOf(new TreeSet<>(records.stream()
                .filter(r -> r.get("shape") != null
                             && (errors(r) > 0 || (Integer.valueOf(0).equals(r.get("events"))
                                                   && !EXPECT_NONE.equals(r.get("expect")))))
                .map(r -> String.valueOf(r.get("shape")))
                .toList()));
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("pipeline", pipeline.name());
        result.put("records_stepped", records.size() - missing.size());
        result.put("records_with_errors", records.stream().filter(r -> errors(r) > 0).count());
        result.put("records_not_found", missing);
        result.put("shapes_not_clean", uncovered);
        result.put("left_untranslated_as_intended", records.stream()
                .filter(r -> EXPECT_NONE.equals(r.get("expect")) && errors(r) == 0)
                .count());
        result.put("draft_code_used", PipelineStepping.draftCodeUsed(draftCode));
        result.putAll(summary);
        result.put("records", records.subList(0, Math.min(100, records.size())));
        if (records.size() == missing.size()) {
            SteppingVerdicts.nothingStepped(result, walked.markers, "the records "
                                                                    + PyText.list(missing.subList(0,
                    Math.min(5, missing.size()))));
        }
        if (walked.cut) {
            // A verdict of fewer records than asked for isn't the clean step promotion relies on: not recorded.
            result.put("stopped_early", true);
            result.put("hint", PipelineStepping.outOfTime(context, "after " + records.size() + " records")
                               + " The verdict is of those records only.");
            return result;
        }
        if (uncovered.isEmpty()) {
            final DocRef ref = pipeline.ref();
            stepping.rememberClean(ref, draftCode, result, locations.stream().map(Location::stream).toList());
        }
        return result;
    }

    private static int errors(final Map<String, Object> record) {
        return record.get("errors") instanceof final Number number
                ? number.intValue()
                : 0;
    }

    /// @param call A call.
    /// @return Its locations, from a list of `{stream, part, record, shape, expect}` or that list as JSON text.
    /// @throws ToolRefusal if one isn't a location.
    static List<Location> locations(final ToolCall call) {
        Object value = argsMap(call).get(LOCATIONS_ARG);
        if (value instanceof final String text && !text.isBlank()) {
            try {
                value = JsonUtil.readValue(text, Object.class);
            } catch (final RuntimeException e) {
                throw new ToolRefusal("locations are records to step, each {stream, part, record}: '" + text
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
            throw new ToolRefusal("locations are records to step, each {stream, part, record}");
        }
        final List<Location> locations = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            locations.add(Location.of(list.get(i), i));
        }
        return locations;
    }


    // --------------------------------------------------------------------------------


    /// A record to step, where it is (as survey_feed gives locations).
    ///
    /// @param stream The stream.
    /// @param part   The zero-based part.
    /// @param record The zero-based record within the part.
    /// @param shape  The survey shape this record is an example of, or null.
    /// @param expect `event`, or `none`: a kind the user chose to leave untranslated (survey_feed sets it), so no
    ///               Event is right.
    record Location(long stream, long part, long record, String shape, String expect) {

        /// @param value A location as given.
        /// @param index Its place in the list, for what is said when it isn't one.
        /// @return It.
        /// @throws ToolRefusal if it isn't a location.
        static Location of(final Object value, final int index) {
            if (!(value instanceof final Map<?, ?> map)) {
                throw new ToolRefusal("locations[" + index + "] is not a location {stream, part, record}");
            }
            final Long stream = whole(map.get("stream"));
            final Long record = whole(map.get("record"));
            final Long part = map.get("part") == null
                    ? Long.valueOf(0)
                    : whole(map.get("part"));
            if (stream == null || record == null || part == null || part < 0 || record < 0) {
                throw new ToolRefusal("locations[" + index + "] needs stream and record (and part, if any) as "
                                      + "whole numbers: " + map);
            }
            final String expect = map.get("expect") == null
                    ? EXPECT_EVENT
                    : String.valueOf(map.get("expect"));
            if (!EXPECT_EVENT.equals(expect) && !EXPECT_NONE.equals(expect)) {
                throw new ToolRefusal("locations[" + index + "].expect is 'event' or 'none', not '" + expect + "'");
            }
            return new Location(stream, part, record, map.get("shape") == null
                    ? null
                    : String.valueOf(map.get("shape")), expect);
        }

        private static Long whole(final Object value) {
            if (value instanceof final Number number) {
                return number.doubleValue() == Math.rint(number.doubleValue())
                        ? Long.valueOf(number.longValue())
                        : null;
            }
            if (value instanceof final String text) {
                try {
                    return Long.parseLong(text.strip());
                } catch (final NumberFormatException e) {
                    return null;
                }
            }
            return null;
        }
    }

    // What the steps found.
    private static final class Walked {

        private final List<ErrorTriage.Marker> markers = new ArrayList<>();
        private final List<Map<String, Object>> records = new ArrayList<>();
        private final List<String> unknown = new ArrayList<>();
        private final List<String> unknownOutputs = new ArrayList<>();
        // Stopped before the call's time ran out.
        private boolean cut;
    }
}
