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
import stroom.ai.impl.tools.SteppingWalks;
import stroom.ai.impl.tools.SteppingWalks.Walk;
import stroom.ai.impl.tools.StreamSummaries;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.workspace.ToolTimeoutException;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.SteppingResult;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/// `step_sample` - steps every record of the sample streams to completion and returns one verdict for the whole sample:
/// error groups triaged (blocking / review / benign) with the records they affect, plus per-record status. The
/// correctness check before processing. A clean verdict is recorded in the build's record for the code stepped, for
/// promotion.
///
/// Stepped as the user, who needs the Stepping permission, one stepping session per stream on this node, each ended
/// when its stream is done. It takes a run slot over `/mcp`, says where it is now and then, stops at once when its
/// thread is interrupted (the client gave up on the call, or the run was stopped), and stops before the session's
/// time limit for a long call, with a verdict of the records stepped so far that is not recorded.
class StepSampleTool extends AbstractBuiltInTool {

    static final String NAME = "step_sample";
    static final String PIPELINE_UUID_ARG = "pipeline_uuid";
    static final String STREAM_IDS_ARG = "stream_ids";
    static final String DRAFT_CODE_ARG = "draft_code";
    static final String MAX_RECORDS_ARG = "max_records";
    static final String RECORDS_PER_STREAM_ARG = "records_per_stream";
    /// Records stepped from the head of each sample stream unless told otherwise.
    static final int PER_STREAM = 50;

    private final PipelineStepping stepping;
    private final int perStream;
    private final int maxSampleRecords;

    @Inject
    StepSampleTool(final PipelineStepping stepping) {
        this(stepping, PER_STREAM, PipelineStepping.MAX_SAMPLE_RECORDS);
    }

    /// As the injected constructor, with the records stepped per stream by default and the most stepped in all
    /// given, for tests.
    StepSampleTool(final PipelineStepping stepping, final int perStream, final int maxSampleRecords) {
        this.stepping = stepping;
        this.perStream = perStream;
        this.maxSampleRecords = maxSampleRecords;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(BuildPlan.onboardingLabel(NAME)
                             + "Step every record of the sample streams to completion and return one verdict for "
                             + "the whole sample: error groups triaged (blocking / review / benign) with the records "
                             + "they affect, plus per-record status. This is the correctness check before "
                             + "processing; a blocking group means fix and step again. Large samples are stepped "
                             + "from the head of each stream (records_per_stream); processing covers the rest.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(PIPELINE_UUID_ARG, PipelineStepping.PIPELINE_UUID_DESCRIPTION)
                        .addProperty(STREAM_IDS_ARG, stringArraySchema("Sample streams to step through, every "
                                                                       + "record."))
                        .addProperty(DRAFT_CODE_ARG, ObjectSchema.builder()
                                .description(PipelineStepping.DRAFT_CODE_DESCRIPTION)
                                .build())
                        .addIntegerProperty(MAX_RECORDS_ARG, "Stop after this many records: at most "
                                                             + PipelineStepping.MAX_SAMPLE_RECORDS + ", and that "
                                                             + "by default.")
                        .addIntegerProperty(RECORDS_PER_STREAM_ARG, "Step at most this many records from the start "
                                                                    + "of each stream (default " + PER_STREAM
                                                                    + ", unless max_records is given: processing "
                                                                    + "then checks every record).")
                        .required(PIPELINE_UUID_ARG, STREAM_IDS_ARG)
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
        // check_events records Events that pass. The record is the tools' own bookkeeping, kept only for pipelines in a
        // build (WriteGuard's own), never for anything a person wrote or anything that runs: stepping changes no data,
        // document content or processing, and promotion, which reads the record, asks for its own approval. Taken as a
        // write, every step would need approving over /mcp, and none could be made while the user is away.
        return true;
    }

    @Override
    public boolean usesRunSlot() {
        // It steps up to 500 records, a stepping session per stream.
        return true;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .requires(AppPermission.STEPPING_PERMISSION)
                .unlockedBy(AppPermission.VIEW_DATA_PERMISSION)
                .checksDocuments(DocumentPermission.VIEW)
                .note("Stepped as the user, only through pipelines they may view, and streams of feeds they may "
                      + "view. View Data lets it tell a JSON array stepped as one record. A clean verdict is "
                      + "recorded in the build record of the pipeline stepped, when it is in a build.")
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        final String pipelineUuid = stringArg(call, PIPELINE_UUID_ARG);
        if (pipelineUuid == null) {
            return JsonResults.refused("Give pipeline_uuid: the pipeline to step.");
        }
        try {
            final List<Long> streamIds = StartOnboardingTool.streamIds(call, STREAM_IDS_ARG);
            if (streamIds.isEmpty()) {
                return JsonResults.refused("Give stream_ids: the sample streams to step through.");
            }
            final Integer maxRecords = atLeastOne(call, MAX_RECORDS_ARG);
            final Integer recordsPerStream = atLeastOne(call, RECORDS_PER_STREAM_ARG);
            final Map<String, String> draftCode = PipelineStepping.draftCode(call, DRAFT_CODE_ARG);
            return JsonResults.of(stepSample(call, context, pipelineUuid, streamIds, draftCode, maxRecords,
                    recordsPerStream));
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

    /// The sample stepped: `pipeline`, `records_stepped`, `records_with_errors`, `draft_code_used`, the triage
    /// (`verdict`, `groups_by_class`, `groups`, each group with its `records`), `first_record_output`, and any `hint`,
    /// `stopped_early`, `record` and `next`.
    ///
    /// @throws ToolTimeoutException if the call is cancelled.
    Map<String, Object> stepSample(final ToolCall call,
                                   final ToolCallContext context,
                                   final String pipelineUuid,
                                   final List<Long> streamIds,
                                   final Map<String, String> draftCode,
                                   final Integer maxRecords,
                                   final Integer recordsPerStream) {
        stepping.checkCanStep();
        stepping.checkDrafts(call, context, pipelineUuid, draftCode);
        stepping.refuseOlderThanFeed(streamIds);
        final SteppedPipeline pipeline = stepping.load(pipelineUuid);
        pipeline.refuseMistyped();
        stepping.refuseUnloadedReferences(pipeline);
        final int cap = Math.min(maxRecords == null
                ? maxSampleRecords
                : maxRecords, maxSampleRecords);
        // Large sample files (985 records each) were stepped a record per request on a remote Stroom: the head of
        // each stream says whether the translation is right, and processing then reads every record.
        final Integer perStreamLimit = recordsPerStream == null && maxRecords == null
                ? Integer.valueOf(perStream)
                : recordsPerStream;
        final Walked walked;
        try {
            walked = PipelineStepping.withinTimeLimit(context, () -> walk(context, pipeline, streamIds, draftCode,
                    cap, perStreamLimit));
        } finally {
            stepping.audit(context, streamIds, "Stepped stream(s) through " + pipeline.name() + " with the " + NAME
                                               + " tool");
        }
        return verdict(context, pipeline, streamIds, draftCode, maxRecords, perStreamLimit, cap, walked);
    }

    // Every stream walked, up to the caps, or until the call's time is up.
    private Walked walk(final ToolCallContext context,
                        final SteppedPipeline pipeline,
                        final List<Long> streamIds,
                        final Map<String, String> draftCode,
                        final int cap,
                        final Integer perStreamLimit) {
        final Walked walked = new Walked();
        final String outputElement = pipeline.outputElement();
        final CallProgress progress = CallProgress.of(context);
        int n = 0;
        for (final long streamId : streamIds) {
            n++;
            final int streamStart = walked.records.size();
            final int limit = perStreamLimit == null
                    ? cap - streamStart
                    : Math.min(perStreamLimit, cap - streamStart);
            if (limit <= 0) {
                // The cap is reached: the streams left are not stepped at all.
                break;
            }
            // Told when each stream starts, and while one is stepped only now and then, never at every record.
            progress.say("Stepping stream " + streamId + " (" + n + " of " + streamIds.size() + ") through "
                         + pipeline.name() + ": up to " + limit + " records");
            try (final Walk walk = stepping.open(pipeline, streamId, draftCode)) {
                SteppingResult result = walk.first();
                if (!PipelineStepping.isFound(result)) {
                    // An element that fails before the first record (an XSLT that doesn't compile, say) reports it
                    // here.
                    walked.markers.addAll(SteppingWalks.markers(result, String.valueOf(streamId)));
                }
                while (PipelineStepping.isFound(result) && walked.records.size() < cap
                       && (perStreamLimit == null || walked.records.size() - streamStart < perStreamLimit)) {
                    walked.perStream.merge(streamId, 1, Integer::sum);
                    final StepLocation location = result.getFoundLocation();
                    final String key = SteppingWalks.recordKey(streamId, location);
                    record(walked, pipeline, outputElement, result, key);
                    progress.nowAndThen("Stepping stream " + streamId + " (" + n + " of " + streamIds.size()
                                        + "): " + (walked.records.size() - streamStart) + " of up to " + limit
                                        + " records stepped");
                    if (PipelineStepping.timeIsUp()) {
                        walked.cut = true;
                        break;
                    }
                    result = walk.forward(location);
                }
            } catch (final ToolTimeoutException e) {
                if (Thread.currentThread().isInterrupted()) {
                    throw e;
                }
                // A step outlasted the call's time.
                walked.cut = true;
            }
            if (walked.cut) {
                break;
            }
        }
        return walked;
    }

    // What one record's step met and wrote.
    private static void record(final Walked walked,
                               final SteppedPipeline pipeline,
                               final String outputElement,
                               final SteppingResult result,
                               final String key) {
        final List<ErrorTriage.Marker> found = new ArrayList<>(SteppingWalks.markers(result, key));
        found.addAll(SteppingVerdicts.emptyOutput(result, outputElement, key));
        walked.markers.addAll(found);
        walked.records.add(new Stepped(key, found.size()));
        final String output = SteppingWalks.output(result, outputElement);
        if (SteppingVerdicts.writesEvents(output)) {
            walked.wroteEvents++;
            if (EventXml.countEvents(output) > 0) {
                walked.wroteAnEvent++;
            }
        }
        if (SteppingVerdicts.writesReferenceData(output)) {
            walked.wroteReference++;
            if (SteppingVerdicts.writesAReference(output)) {
                walked.wroteAReference++;
            }
        }
        if (SteppingVerdicts.writesUnknown(output)) {
            walked.unknown.add(key);
            walked.unknownOutputs.add(output);
        }
        if (walked.firstOutput == null) {
            walked.firstOutput = new LinkedHashMap<>();
            for (final String element : pipeline.defaultOutputs()) {
                final String text = SteppingWalks.output(result, element);
                walked.firstOutput.put(element, text.length() > PipelineStepping.MAX_STREAM_CHARS / 4
                        ? text.substring(0, PipelineStepping.MAX_STREAM_CHARS / 4)
                        : text);
            }
        }
    }

    // The verdict of what was stepped, recorded when clean.
    private Map<String, Object> verdict(final ToolCallContext context,
                                        final SteppedPipeline pipeline,
                                        final List<Long> streamIds,
                                        final Map<String, String> draftCode,
                                        final Integer maxRecords,
                                        final Integer perStreamLimit,
                                        final int cap,
                                        final Walked walked) {
        final String outputElement = pipeline.outputElement();
        final List<String> keys = walked.records.stream().map(Stepped::record).toList();
        final Map<String, Object> summary = stepping.triage(walked.markers, pipeline, walked.records.size());
        SteppingVerdicts.listRecords(summary, walked.markers);
        SteppingVerdicts.block(summary, stepping.unagreedUnknown(pipeline, outputElement, walked.unknown,
                walked.records.size(), draftCode, SteppingVerdicts.whatUnknownHolds(walked.unknownOutputs)));
        if (!keys.isEmpty() && walked.wroteEvents == keys.size() && walked.wroteAnEvent == 0) {
            SteppingVerdicts.block(summary, SteppingVerdicts.noEvents(outputElement, keys));
        }
        if (!keys.isEmpty() && walked.wroteReference == keys.size() && walked.wroteAReference == 0) {
            SteppingVerdicts.block(summary, SteppingVerdicts.noReference(outputElement, keys));
        }
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("pipeline", pipeline.name());
        result.put("records_stepped", keys.size());
        result.put("records_with_errors", walked.records.stream().filter(r -> r.errors() > 0).count());
        result.put("draft_code_used", PipelineStepping.draftCodeUsed(draftCode));
        result.putAll(summary);
        result.put("first_record_output", walked.firstOutput);
        if (keys.size() >= cap) {
            // Seen in production: max_records=1000 stepped 500 and said only that the sample had more; the agent
            // called it again the same way, three more minutes for the same 500 records.
            final String asked = maxRecords != null && maxRecords > cap
                    ? " (max_records=" + maxRecords + " is more than it steps)"
                    : "";
            result.put("hint", "Stopped at " + cap + " records, the limit for one step_sample" + asked
                               + ": calling it again steps the same " + cap + ". The rest are checked when the "
                               + "sample is processed: wait_for_processing reports any record that fails, and "
                               + "check_events reads the Events.");
        } else if (perStreamLimit != null && walked.perStream.values().stream().anyMatch(n -> n >= perStreamLimit)) {
            result.put("hint", "Stepped the first " + perStreamLimit + " records of each stream: processing then "
                               + "reads every record, and wait_for_processing reports any that fail.");
        }
        if (pipeline.jsonRootMap() && !keys.isEmpty() && walked.perStream.values().stream().allMatch(n -> n == 1)
            && stepping.streamIsArray(streamIds.getFirst())) {
            // JSON lines need the root map and step as one record too: only an array is told to change it.
            arrayAsOneRecord(summary, pipeline, walked);
            result.put("verdict", summary.get("verdict"));
        }
        if (keys.isEmpty()) {
            SteppingVerdicts.nothingStepped(result, walked.markers, "streams " + ToolText.repr(streamIds));
        }
        if (walked.cut) {
            // A verdict of fewer records than asked for isn't the clean step promotion relies on: not recorded.
            result.put("stopped_early", true);
            result.put("hint", PipelineStepping.outOfTime(context, "after " + keys.size() + " records")
                               + " The verdict is of those records only.");
            return result;
        }
        final DocRef ref = pipeline.ref();
        stepping.rememberClean(ref, draftCode, result, streamIds);
        if ("clean".equals(result.get("verdict")) && draftCode == null) {
            return stepping.withNext(context, ref, result);
        } else if ("clean".equals(result.get("verdict"))) {
            final String hint = result.get("hint") instanceof final String said
                    ? said + " "
                    : "";
            result.put("hint", hint + "Clean with draft code: save it (build_translation_xslt uuid= for a "
                               + "generated XSLT, else save_xslt uuid=) and step the saved code once more.");
        }
        return result;
    }

    // Each stream stepped as one record, from a JSON array read with the parser's root map: a review group.
    private static void arrayAsOneRecord(final Map<String, Object> summary,
                                         final SteppedPipeline pipeline,
                                         final Walked walked) {
        final Map<String, Object> example = new LinkedHashMap<>();
        example.put("message", "Each stream stepped as one record: a JSON array read with the parser's root map "
                               + "(jsonParser.addRootObject true) is a single record, however many items it "
                               + "holds, so a large one outlasts Stroom's stepping. Set it false (update_pipeline "
                               + "set_properties=[{element: <the JSON parser>, name: addRootObject, value: "
                               + "false}]) so each item is a record, change the XSLT to match /array/map, and step "
                               + "again.");
        example.put("location", null);
        final Map<String, Object> group = new LinkedHashMap<>();
        group.put("class", ErrorTriage.REVIEW);
        group.put("reason", "Each JSON array stepped as one record");
        group.put("severity", "WARNING");
        group.put("element", pipeline.jsonParser());
        group.put("own_element", true);
        group.put("count", walked.perStream.size());
        group.put("records_affected", walked.perStream.size());
        group.put("records", new TreeSet<>(walked.perStream.keySet().stream().map(String::valueOf).toList())
                .stream().limit(20).toList());
        group.put("examples", List.of(example));
        SteppingVerdicts.groups(summary).addFirst(group);
        SteppingVerdicts.byClass(summary).merge(ErrorTriage.REVIEW, 1, Integer::sum);
        if ("clean".equals(summary.get("verdict"))) {
            summary.put("verdict", ErrorTriage.REVIEW);
        }
    }

    // A whole number argument of at least 1, or null if absent.
    private Integer atLeastOne(final ToolCall call, final String name) {
        if (stringArg(call, name) == null) {
            return null;
        }
        final Integer value = intArg(call, name);
        if (value == null || value < 1) {
            throw new ToolRefusal(name + " must be a whole number of at least 1, not '" + stringArg(call, name)
                                  + "'");
        }
        return value;
    }


    // --------------------------------------------------------------------------------


    /// One record stepped.
    ///
    /// @param record Its key, e.g. `7:3`.
    /// @param errors How many errors it met.
    record Stepped(String record, int errors) {

    }

    // What the walks of the streams found.
    private static final class Walked {

        private final List<ErrorTriage.Marker> markers = new ArrayList<>();
        private final List<Stepped> records = new ArrayList<>();
        private final List<String> unknown = new ArrayList<>();
        private final List<String> unknownOutputs = new ArrayList<>();
        private final Map<Long, Integer> perStream = new LinkedHashMap<>();
        // Records whose output is event-logging Events; of those, with an Event.
        private int wroteEvents;
        private int wroteAnEvent;
        private int wroteReference;
        private int wroteAReference;
        private Map<String, Object> firstOutput;
        // Stopped before the call's time ran out.
        private boolean cut;
    }
}
