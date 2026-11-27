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
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.processing.ErrorTriage;
import stroom.ai.impl.tools.streams.SteppingWalks;
import stroom.ai.impl.tools.streams.SteppingWalks.Walk;
import stroom.ai.impl.tools.streams.StreamSummaries;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.ToolTimeoutException;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.json.JsonUtil;

import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/// `step_records` - steps chosen records of existing streams, in place, and returns one verdict for them all, like
/// step_sample: error groups triaged, and per record the events it produced and its errors. With survey_feed's
/// locations it checks a translation against every kind of event a feed holds, without copying or processing anything.
/// A clean verdict over every shape is recorded in the build's record for the code stepped.
///
/// Stepped as the user, who needs the Stepping permission; locations in a row from one stream share a stepping
/// session on this node, ended when they are done. It takes a run slot over `/mcp`, says where it is now and then,
/// stops at once when its thread is interrupted, and stops before the session's time limit for a long call, with a
/// verdict of the records stepped so far that is not recorded.
class StepRecordsTool extends AbstractBuiltInTool {

    static final String NAME = "step_records";
    static final String PIPELINE_UUID_ARG = "pipeline_uuid";
    static final String LOCATIONS_ARG = "locations";
    /// The argument naming a saved survey_feed result whose locations are stepped.
    static final String LOCATIONS_FROM_ARG = "locations_from";
    static final String DRAFT_CODE_ARG = "draft_code";
    static final String EXPECT_NONE = "none";
    static final String EXPECT_EVENT = "event";
    /// A record's key when a source type group's pipeline passed it over.
    static final String PASSED_OVER = "passed_over";

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
                        .addStringProperty(LOCATIONS_FROM_ARG, "Instead of locations: a survey_feed result saved "
                                                               + "to the workspace (its whole_result.saved, given "
                                                               + "when it was too large to show whole), every "
                                                               + "location it lists stepped (for a source type "
                                                               + "group's pipeline, those of its source types).")
                        .addProperty(DRAFT_CODE_ARG, ObjectSchema.builder()
                                .description(PipelineStepping.DRAFT_CODE_DESCRIPTION)
                                .build())
                        .addEnumProperty(PipelineStepping.CHILD_TYPE_ARG, PipelineStepping.CHILD_TYPES,
                                PipelineStepping.CHILD_TYPE_DESCRIPTION)
                        .required(PIPELINE_UUID_ARG)
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
            final List<Location> given = locations(call);
            final boolean fromSurvey = given.isEmpty() && stringArg(call, LOCATIONS_FROM_ARG) != null;
            final List<Location> locations = fromSurvey
                    ? savedLocations(context, stringArg(call, LOCATIONS_FROM_ARG))
                    : given;
            if (locations.isEmpty()) {
                return JsonResults.refused("Give locations: the records to step, e.g. survey_feed's locations: "
                                           + "{stream, part, record}; or locations_from= a survey_feed result "
                                           + "saved to the workspace.");
            }
            final Map<String, String> draftCode = PipelineStepping.draftCode(call, DRAFT_CODE_ARG);
            return JsonResults.of(fromSurvey
                    ? stepSurveyed(call, context, pipelineUuid, locations, draftCode)
                    : stepRecords(call, context, pipelineUuid, locations, draftCode));
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

    /// A survey's locations stepped: for a source type group's pipeline, only those of the group's source types
    /// (decided by the user 2026-11-23: seen in chat 189, an Entra ID group stepped 292 records of other source
    /// types, four said to produce no Event, and the agent spent ten calls finding they were passed over). Those left
    /// out are counted by source type (`locations_left_out`).
    ///
    /// @throws ToolTimeoutException if the call is cancelled.
    /// @throws ToolRefusal if the group's source types leave no location to step.
    private Map<String, Object> stepSurveyed(final ToolCall call,
                                             final ToolCallContext context,
                                             final String pipelineUuid,
                                             final List<Location> locations,
                                             final Map<String, String> draftCode) {
        final Optional<Set<String>> groupTypes = stepping.passedOver(stepping.load(pipelineUuid))
                .groupSourceTypes();
        if (groupTypes.isEmpty()) {
            return stepRecords(call, context, pipelineUuid, locations, draftCode);
        }
        final Map<String, Integer> leftOut = new TreeMap<>(ToolText::compare);
        final List<Location> own = new ArrayList<>();
        for (final Location location : locations) {
            if (location.onlyOthers(groupTypes.get())) {
                location.sourceTypes().forEach(type -> leftOut.merge(type, 1, Integer::sum));
            } else {
                own.add(location);
            }
        }
        if (own.isEmpty()) {
            throw new ToolRefusal("None of the survey's " + locations.size() + " locations is of the group's source "
                                  + "types (" + ToolText.list(List.copyOf(new TreeSet<>(groupTypes.get())))
                                  + "): they are " + leftOut.keySet() + "'s, which its pipeline passes over. "
                                  + "Survey the group's records, or give locations of them.");
        }
        final Map<String, Object> result = stepRecords(call, context, pipelineUuid, own, draftCode);
        if (!leftOut.isEmpty()) {
            final Map<String, Object> out = new LinkedHashMap<>(result);
            out.put("locations_left_out", leftOut);
            out.put("locations_left_out_note", (locations.size() - own.size()) + " of the survey's locations are "
                                               + "of other source types alone (" + String.join(", ", leftOut
                    .entrySet().stream().map(entry -> entry.getKey() + " " + entry.getValue()).toList())
                                               + "), which the group's pipeline passes over: not stepped. Only "
                                               + "the group's own are stepped and judged.");
            return out;
        }
        return result;
    }

    /// The records stepped: `pipeline`, `records_stepped`, `records_with_errors`, `records_not_found`,
    /// `shapes_not_clean`, `left_untranslated_as_intended`, `draft_code_used`, the triage (`verdict`,
    /// `groups_by_class`, `groups`) and `records` (the first 100), and any `hint`, `stopped_early` and `record`. A
    /// source type group's pipeline's records passed over are no errors: `records_passed_over`,
    /// `passed_over_by_source_type`, `passed_over_note` and `shapes_passed_over` say them. The call's `child_type`
    /// ([PipelineStepping#childType]) steps each part's child stream (its Context) in place of its data.
    ///
    /// @throws ToolTimeoutException if the call is cancelled.
    /// @throws ToolRefusal if the call names a child stream that can't be stepped.
    Map<String, Object> stepRecords(final ToolCall call,
                                    final ToolCallContext context,
                                    final String pipelineUuid,
                                    final List<Location> locations,
                                    final Map<String, String> draftCode) {
        stepping.checkCanStep();
        final String childType = PipelineStepping.childType(call);
        stepping.checkDrafts(call, context, pipelineUuid, draftCode);
        final List<Location> stepped = locations.subList(0, Math.min(locations.size(), maxSampleRecords));
        final List<Long> streams = new ArrayList<>(new TreeSet<>(locations.stream().map(Location::stream).toList()));
        final SteppedPipeline pipeline = stepping.load(pipelineUuid);
        pipeline.refuseMistyped();
        final Walked walked;
        try {
            walked = PipelineStepping.withinTimeLimit(context, () -> walk(context, pipeline, stepped, draftCode,
                    childType));
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
                        final Map<String, String> draftCode,
                        final String childType) {
        final Walked walked = new Walked(stepping.unvalidatedOutput(pipeline), stepping.passedOver(pipeline));
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
            try (final Walk walk = stepping.open(pipeline, streamId, draftCode, childType)) {
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
            // Said in records_not_found, with why; judged only if no record was found at all (an XSLT that doesn't
            // compile). Seen in testing: a record past a stream's end turned a clean verdict into review, on
            // stepping's own complaint that it had nothing to replay.
            final List<ErrorTriage.Marker> found = SteppingWalks.markers(result, key);
            walked.missingMarkers.addAll(found);
            entry.put("found", false);
            if (!found.isEmpty()) {
                entry.put("why", found.getFirst().message());
            }
            walked.records.add(entry);
            return;
        }
        // A source type group's pipeline passes over the records of the feed's other source types: counted apart,
        // never errors, and no Event is right for them.
        final PassedOverRecords.Sifted sifted = walked.passedOver.sift(SteppingWalks.markers(result, key), result,
                new StepLocation(location.stream(), location.part(), location.record()));
        List<ErrorTriage.Marker> found = new ArrayList<>(sifted.markers());
        found.addAll(SteppingVerdicts.emptyOutput(result, outputElement, key));
        final String output = SteppingWalks.output(result, outputElement);
        found.addAll(walked.unvalidated.markers(output, key));
        found.addAll(walked.unvalidated.eventMarkers(result, key));
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
        } else if (events == 0 && found.isEmpty() && !SteppingVerdicts.sendsOn(output) && !sifted.passedOver()
                   && !SteppingVerdicts.writesAReference(output)) {
            // A reference loader's record (a stream's Context stepped through a context loader, say) writes a
            // reference, not an Event.
            found.add(ErrorTriage.marker("WARNING", outputElement, "The record produced no Event", null)
                    .withRecord(key));
        }
        walked.markers.addAll(found);
        entry.put("events", events);
        entry.put("errors", found.size());
        if (sifted.passedOver()) {
            entry.put(PASSED_OVER, true);
            entry.put("passed_over_source_type", sifted.sourceType());
        }
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
        final boolean noneFound = records.stream().allMatch(r -> Boolean.FALSE.equals(r.get("found")));
        if (noneFound) {
            walked.markers.addAll(walked.missingMarkers);
        }
        final Map<String, Object> summary = stepping.triage(walked.markers, pipeline, records.size());
        SteppingVerdicts.listRecords(summary, walked.markers);
        stepping.addPassedOver(summary, walked.passedOver, pipeline);
        SteppingVerdicts.block(summary, stepping.unagreedUnknown(pipeline, pipeline.outputElement(),
                walked.unknown, records.size(), draftCode, SteppingVerdicts.whatUnknownHolds(walked.unknownOutputs)));
        final List<String> missing = records.stream()
                .filter(r -> Boolean.FALSE.equals(r.get("found")))
                .map(r -> String.valueOf(r.get("record")))
                .toList();
        final List<String> uncovered = List.copyOf(new TreeSet<>(records.stream()
                .filter(r -> r.get("shape") != null
                             && (errors(r) > 0 || (Integer.valueOf(0).equals(r.get("events"))
                                                   && !EXPECT_NONE.equals(r.get("expect"))
                                                   && !passedOver(r))))
                .map(r -> String.valueOf(r.get("shape")))
                .toList()));
        // A shape whose example the group's pipeline passed over is another source type's: not this pipeline's to
        // cover, but said, in case the agent took it for one of the group's.
        final List<String> shapesPassedOver = List.copyOf(new TreeSet<>(records.stream()
                .filter(r -> r.get("shape") != null && passedOver(r))
                .map(r -> String.valueOf(r.get("shape")))
                .toList()));
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("pipeline", pipeline.name());
        result.put("records_stepped", records.size() - missing.size());
        result.put("records_with_errors", records.stream().filter(r -> errors(r) > 0).count());
        walked.passedOver.addTo(result);
        result.put("records_not_found", missing);
        result.put("shapes_not_clean", uncovered);
        if (!shapesPassedOver.isEmpty()) {
            result.put("shapes_passed_over", shapesPassedOver);
        }
        result.put("left_untranslated_as_intended", records.stream()
                .filter(r -> EXPECT_NONE.equals(r.get("expect")) && errors(r) == 0)
                .count());
        result.put("draft_code_used", PipelineStepping.draftCodeUsed(draftCode));
        result.putAll(summary);
        walked.unvalidated.eventsSaid().ifPresent(said -> result.put("events_schema_check", said));
        result.put("records", records.subList(0, Math.min(100, records.size())));
        if (records.size() == missing.size()) {
            SteppingVerdicts.nothingStepped(result, walked.markers, "the records "
                                                                    + ToolText.list(missing.subList(0,
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

    private static boolean passedOver(final Map<String, Object> record) {
        return Boolean.TRUE.equals(record.get(PASSED_OVER));
    }

    private static int errors(final Map<String, Object> record) {
        return record.get("errors") instanceof final Number number
                ? number.intValue()
                : 0;
    }

    /// @param context The call's context.
    /// @param path    A survey_feed result saved to the session's workspace ([stroom.ai.impl.tools.LargeResults]).
    /// @return The locations it lists.
    /// @throws ToolRefusal if there is no such file, or it lists no locations.
    static List<Location> savedLocations(final ToolCallContext context, final String path) {
        final SessionWorkspace workspace = context.workspace().orElseThrow(() -> new ToolRefusal(
                "locations_from reads a file in this session's workspace, which it has none of: give locations"));
        final Object saved;
        try {
            saved = JsonUtil.readValue(Files.readString(workspace.localFile(WorkspacePath.parse(path)),
                    StandardCharsets.UTF_8), Object.class);
        } catch (final IOException | RuntimeException e) {
            throw new ToolRefusal("locations_from='" + path + "' is not a saved survey_feed result that can be read "
                                  + "(" + e.getMessage() + "): give the whole_result.saved path of a survey_feed "
                                  + "result, or locations");
        }
        final List<?> listed = saved instanceof final Map<?, ?> result && result.get(LOCATIONS_ARG) instanceof
                final List<?> list
                ? list
                : List.of();
        if (listed.isEmpty()) {
            throw new ToolRefusal("The saved result at '" + path + "' lists no locations: give the whole_result.saved "
                                  + "path of a survey_feed result, or locations");
        }
        final List<Location> locations = new ArrayList<>();
        for (int i = 0; i < listed.size(); i++) {
            locations.add(Location.of(listed.get(i), i));
        }
        return locations;
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
    /// @param expect      `event`, or `none`: a kind the user chose to leave untranslated (survey_feed sets it), so
    ///                    no Event is right.
    /// @param sourceTypes The source types of an aggregated feed the survey found the record's shape in (its
    ///                    `source_types`); none where not said.
    record Location(long stream, long part, long record, String shape, String expect, List<String> sourceTypes) {

        /// Fills in an empty list for null.
        Location {
            sourceTypes = List.copyOf(Objects.requireNonNullElse(sourceTypes, List.of()));
        }

        /// A location no source type is said of.
        ///
        /// @param stream The stream.
        /// @param part   The zero-based part.
        /// @param record The zero-based record within the part.
        /// @param shape  The survey shape this record is an example of, or null.
        /// @param expect `event`, or `none`.
        Location(final long stream, final long part, final long record, final String shape, final String expect) {
            this(stream, part, record, shape, expect, List.of());
        }

        /// @param groupTypes A group's source types.
        /// @return True if the survey said the record's shape is of other source types alone.
        boolean onlyOthers(final Set<String> groupTypes) {
            return !sourceTypes.isEmpty() && sourceTypes.stream().noneMatch(groupTypes::contains);
        }

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
            final List<String> sourceTypes = map.get("source_types") instanceof final List<?> types
                    ? types.stream().filter(Objects::nonNull).map(String::valueOf).toList()
                    : List.of();
            return new Location(stream, part, record, map.get("shape") == null
                    ? null
                    : String.valueOf(map.get("shape")), expect, sourceTypes);
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
        // What stepping said of the records it didn't find.
        private final List<ErrorTriage.Marker> missingMarkers = new ArrayList<>();
        private final List<Map<String, Object>> records = new ArrayList<>();
        private final List<String> unknown = new ArrayList<>();
        private final List<String> unknownOutputs = new ArrayList<>();
        // Checks each record's output where no schema filter in the pipeline does.
        private final UnvalidatedOutput unvalidated;
        // The records a source type group's pipeline passed over.
        private final PassedOverRecords passedOver;
        // Stopped before the call's time ran out.
        private boolean cut;

        private Walked(final UnvalidatedOutput unvalidated, final PassedOverRecords passedOver) {
            this.unvalidated = unvalidated;
            this.passedOver = passedOver;
        }
    }
}
