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

import stroom.ai.impl.AiWorkspaceConfig;
import stroom.ai.impl.tools.AbstractBuiltInTool;
import stroom.ai.impl.tools.CallProgress;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.ProcessorFilterAccess;
import stroom.ai.impl.tools.PyText;
import stroom.ai.impl.tools.StreamSummaries;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolRunContext;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.workspace.ToolTimeoutException;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.meta.shared.Meta;
import stroom.meta.shared.Status;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.shared.ProcessorFilter;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;
import stroom.util.time.StroomDuration;

import jakarta.inject.Inject;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/// `wait_for_processing` - waits until each input stream's processor task has ended (complete or failed), then
/// reports per input stream the output (Events, or Reference) and Error streams it produced: the gate before the
/// next stage. Ported from the Stroom MCP server's `tools/processing_writes.py`.
///
/// It stops waiting at once when nothing will process the streams (no filter, none selecting them, or only disabled
/// ones, for the user to enable), and before the session's time limit for a long call (its query time limit) runs
/// out, saying to call again. It says where processing is now and then while it waits, and stops when its thread is
/// interrupted (the client gave up on the call) or the chat's run is stopped. Once indexing passes, the build's
/// Elasticsearch indexing pipeline's development batch size is taken off again ([IndexingRuns#developmentBatch]).
///
/// Unlike the Stroom MCP server's, an input stream is only done once none of its outputs is still locked (being
/// written) and no task for it is still to end (seen: "finished" with a 100,000-event stream still being written, so
/// it couldn't be read, and its Error stream not yet made). The warnings and errors processing wrote beside the
/// output are given (`look_into`), triaged, for the agent to look into and fix or tell the user of (asked for by the
/// user: a warning that invalid XML characters were removed went unread); errors that must be fixed fail the gate.
///
/// Read as the user: the filters and their tasks need the Manage Processors permission and View on the pipeline,
/// and only streams of feeds the user may view are seen.
class WaitForProcessingTool extends AbstractBuiltInTool {

    static final String NAME = "wait_for_processing";
    static final String PIPELINE_UUID_ARG = "pipeline_uuid";
    static final String STREAM_IDS_ARG = "stream_ids";
    static final String TIMEOUT_SECONDS_ARG = "timeout_seconds";
    static final String EXPECT_EVENTS_ARG = "expect_events";
    static final String FILTER_ID_ARG = "filter_id";
    static final String OUTPUT_TYPE_ARG = "output_type";

    static final int DEFAULT_TIMEOUT_SECONDS = 180;
    static final int MIN_TIMEOUT_SECONDS = 5;
    static final int MAX_TIMEOUT_SECONDS = 900;
    /// What to do with the warnings and errors processing wrote beside the output.
    static final String LOOK_INTO_HINT = "Processing wrote these warnings or errors beside the output: look into "
                                         + "each (summarise_streams kind=errors gives them in full), then fix its "
                                         + "cause, or tell the user what it is and why it stays. Never pass over "
                                         + "one.";
    /// The time between looks at processing.
    static final Duration POLL_INTERVAL = Duration.ofSeconds(3);
    /// What is kept of the session's time limit for the call's last reads, after the wait.
    static final Duration MARGIN = Duration.ofSeconds(10);

    private final DocumentAccess documentAccess;
    private final ProcessorFilterAccess processorFilterAccess;
    private final ProcessingStatusTool processingStatus;
    private final StreamProcessing streamProcessing;
    private final PipelineOutputs pipelineOutputs;
    private final WriteGuard guard;
    private final BuildRecords buildRecords;
    private final BuildPlan plan;
    private final IndexingRuns indexingRuns;
    private final StreamSummaries streamSummaries;
    private final LongSupplier clockMs;
    private final Pause pause;

    @Inject
    WaitForProcessingTool(final DocumentAccess documentAccess,
                          final ProcessorFilterAccess processorFilterAccess,
                          final ProcessingStatusTool processingStatus,
                          final StreamProcessing streamProcessing,
                          final PipelineOutputs pipelineOutputs,
                          final WriteGuard guard,
                          final BuildRecords buildRecords,
                          final BuildPlan plan,
                          final IndexingRuns indexingRuns,
                          final StreamSummaries streamSummaries) {
        this(documentAccess, processorFilterAccess, processingStatus, streamProcessing, pipelineOutputs, guard,
                buildRecords, plan, indexingRuns, streamSummaries, System::currentTimeMillis,
                duration -> Thread.sleep(duration.toMillis()));
    }

    /// As the injected constructor, with the clock and the pause between looks given, for tests.
    WaitForProcessingTool(final DocumentAccess documentAccess,
                          final ProcessorFilterAccess processorFilterAccess,
                          final ProcessingStatusTool processingStatus,
                          final StreamProcessing streamProcessing,
                          final PipelineOutputs pipelineOutputs,
                          final WriteGuard guard,
                          final BuildRecords buildRecords,
                          final BuildPlan plan,
                          final IndexingRuns indexingRuns,
                          final StreamSummaries streamSummaries,
                          final LongSupplier clockMs,
                          final Pause pause) {
        this.documentAccess = documentAccess;
        this.processorFilterAccess = processorFilterAccess;
        this.processingStatus = processingStatus;
        this.streamProcessing = streamProcessing;
        this.pipelineOutputs = pipelineOutputs;
        this.guard = guard;
        this.buildRecords = buildRecords;
        this.plan = plan;
        this.indexingRuns = indexingRuns;
        this.streamSummaries = streamSummaries;
        this.clockMs = clockMs;
        this.pause = pause;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(BuildPlan.onboardingLabel(NAME)
                             + "Wait until each input stream's processor task has ended (complete or failed), then "
                             + "report per input stream the output (Events, or Reference) and Error streams it "
                             + "produced. The gate before the next stage: exactly one output stream per input "
                             + "stream, or none for a pipeline that writes none (indexing). A missing or duplicate "
                             + "one is flagged with the likely cause.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(PIPELINE_UUID_ARG, "The pipeline that is processing.")
                        .addProperty(STREAM_IDS_ARG, stringArraySchema("Input streams to wait for."))
                        .addIntegerProperty(TIMEOUT_SECONDS_ARG, "The longest to wait, in seconds ("
                                                                 + MIN_TIMEOUT_SECONDS + " to " + MAX_TIMEOUT_SECONDS
                                                                 + "; default " + DEFAULT_TIMEOUT_SECONDS + ").")
                        .addBooleanProperty(EXPECT_EVENTS_ARG, "Whether each input gives an output stream. Left "
                                                               + "out: from the pipeline: a translation pipeline's "
                                                               + "StreamAppender writes one per input; an indexing "
                                                               + "pipeline, or one with no StreamAppender (sending "
                                                               + "to Kafka, say), writes only an Error stream when "
                                                               + "something went wrong.")
                        .addIntegerProperty(FILTER_ID_ARG, "Only count outputs from this processor filter, e.g. the "
                                                           + "one reprocess_streams made.")
                        .addStringProperty(OUTPUT_TYPE_ARG, "The stream type expected per input (Events, Reference, "
                                                            + "Records, ...). Left out: what the pipeline writes, "
                                                            + "from its stream appender.")
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
        // Read-only, though once indexing passes it takes the development batch size off the build's own
        // Elasticsearch indexing pipeline (the Stroom MCP server's said readOnlyHint false for that): it only undoes
        // what create_processor_filter and reprocess_streams set, on a pipeline tagged as the tools' own in a build,
        // never on a document a person wrote. As a write, every wait would need approving over MCP, and none could
        // be made while the user is away.
        return true;
    }

    @Override
    public boolean usesRunSlot() {
        // It waits for minutes at a time.
        return true;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .requires(AppPermission.MANAGE_PROCESSORS_PERMISSION)
                .checksDocuments(DocumentPermission.VIEW)
                .note("Streams are read only where the user may view their feeds.")
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        final String pipelineUuid = stringArg(call, PIPELINE_UUID_ARG);
        if (pipelineUuid == null) {
            return JsonResults.refused("Give pipeline_uuid: the pipeline that is processing.");
        }
        try {
            final List<Long> streamIds = StartOnboardingTool.streamIds(call, STREAM_IDS_ARG);
            if (streamIds.isEmpty()) {
                return JsonResults.refused("Give stream_ids: the input streams to wait for.");
            }
            final Integer timeout = intArg(call, TIMEOUT_SECONDS_ARG);
            final Request request = new Request(pipelineUuid, streamIds,
                    Math.clamp(Objects.requireNonNullElse(timeout, DEFAULT_TIMEOUT_SECONDS), MIN_TIMEOUT_SECONDS,
                            MAX_TIMEOUT_SECONDS),
                    booleanArg(call, EXPECT_EVENTS_ARG), intArg(call, FILTER_ID_ARG),
                    stringArg(call, OUTPUT_TYPE_ARG));
            final Map<String, Object> result = waitFor(context, request);
            return UserActionEntries.withActions(JsonResults.of(result), result);
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        } catch (final ToolTimeoutException e) {
            // Interrupted: the client gave up on the call, or the run was stopped.
            return JsonResults.refused("Stopped waiting: the call was cancelled. Call wait_for_processing again to "
                                       + "go on waiting.");
        }
    }

    // Waits, then reports.
    private Map<String, Object> waitFor(final ToolCallContext context, final Request request) {
        final DocRef pipeline = documentAccess.findDocRef(request.pipelineUuid())
                .filter(docRef -> PipelineDoc.TYPE.equals(docRef.getType()))
                .orElse(null);
        final DocRef ref = Objects.requireNonNullElseGet(pipeline,
                () -> new DocRef(PipelineDoc.TYPE, request.pipelineUuid(), request.pipelineUuid()));
        final Deadline deadline = deadline(context, request.timeoutSeconds());
        // A pipeline that writes no output stream has none to wait for, whatever was asked (seen: an Events
        // pipeline that writes only on error, and an indexing pipeline, each waited out two timeouts with every task
        // complete).
        final boolean expected = !Boolean.FALSE.equals(request.expectEvents())
                                 && pipelineOutputs.writesOutput(request.pipelineUuid());
        final String outputType = expected && request.outputType() == null
                ? pipelineOutputs.outputStreamType(request.pipelineUuid())
                : request.outputType();
        final CallProgress progress = CallProgress.of(context);
        boolean checked = false;
        Map<String, Object> status;
        Map<Long, List<Meta>> outputs;
        List<Long> pending;
        while (true) {
            final List<ProcessorFilter> filters = filters(pipeline);
            status = processingStatus.status(request.pipelineUuid(), filters);
            if (filters.isEmpty()) {
                // Nothing was set to process: waiting would only run out the timeout, as if tasks were running.
                return plan.nextFor(context, ref, noFilter(request));
            }
            // Tasks first, outputs after: a task ends once its output is written, so an ended task's output is read.
            final StreamProcessing.Tasks tasks = streamProcessing.tasks(request.filterId() != null
                    ? List.of(request.filterId())
                    : filters.stream().map(ProcessorFilter::getId).toList(), request.streamIds());
            outputs = new LinkedHashMap<>();
            for (final Long raw : request.streamIds()) {
                outputs.put(raw, streamProcessing.outputs(raw, request.pipelineUuid(), request.filterId()));
            }
            if (!checked) {
                checked = true;
                final List<Meta> noOutput = streamProcessing.metas(outputs.entrySet().stream()
                        .filter(entry -> entry.getValue().isEmpty())
                        .map(Map.Entry::getKey)
                        .toList());
                final StreamProcessing.Waiting waiting = StreamProcessing.waitingOn(filters, noOutput);
                if (!waiting.what().isEmpty()) {
                    return plan.nextFor(context, ref, waitingForTheUser(context, request, ref, waiting));
                }
                final List<String> unselected = StreamProcessing.unselected(filters, noOutput);
                if (!unselected.isEmpty()) {
                    return plan.nextFor(context, ref, nothingWillProcess(request, unselected));
                }
            }
            // A stream is done once its own task has ended (seen in e2e: a promoted indexing pipeline's earlier
            // tasks were all complete before its filter made one for the new stream, and the gate passed with
            // nothing indexed), or once it has its output, Stroom deleting old tasks; but never while one of its
            // outputs is still being written, or a task for it is still to end.
            final Map<Long, List<Meta>> found = outputs;
            pending = request.streamIds().stream()
                    .filter(raw -> !done(found.get(raw), tasks, raw, expected))
                    .toList();
            if (pending.isEmpty() || clockMs.getAsLong() > deadline.atMs()) {
                break;
            }
            // Where processing is, now and then while waiting (seen: three minutes of silence until the timeout).
            progress.nowAndThen(whereProcessingIs(request, status, outputs, pending, expected, deadline));
            pauseOrStop(context);
        }
        final Map<String, Object> result = report(request, status, outputs, pending, expected, outputType, ref,
                deadline);
        return plan.nextFor(context, ref, result);
    }

    // What the wait found, per input stream, with the gate.
    private Map<String, Object> report(final Request request,
                                       final Map<String, Object> status,
                                       final Map<Long, List<Meta>> outputs,
                                       final List<Long> pending,
                                       final boolean expected,
                                       final String outputType,
                                       final DocRef ref,
                                       final Deadline deadline) {
        final boolean finished = pending.isEmpty();
        final List<Map<String, Object>> perStream = new ArrayList<>();
        final List<String> problems = new ArrayList<>();
        final List<Map<String, Object>> lookInto = new ArrayList<>();
        outputs.forEach((raw, metas) -> {
            final List<Long> events = expected
                    ? metas.stream().filter(meta -> Objects.equals(meta.getTypeName(), outputType))
                            .map(Meta::getId).toList()
                    : List.of();
            final List<Long> errors = metas.stream().filter(meta -> "Error".equals(meta.getTypeName()))
                    .map(Meta::getId).toList();
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("input", raw);
            entry.put("events", events);
            entry.put("errors", errors);
            if (expected && !PipelineOutputs.EVENTS.equals(outputType)) {
                entry.put("output_type", outputType);
            }
            perStream.add(entry);
            final boolean errorsOnly = !expected || pending.contains(raw);
            problems.addAll(problems(raw, events, errors, errorsOnly, outputType));
            if (!errorsOnly && !events.isEmpty() && !errors.isEmpty()) {
                lookInto(raw, errors, lookInto, problems);
            }
        });
        // Only while the pipeline is in a build, where its code changes: a promoted one keeps no record of its steps.
        if (guard.tags(ref).contains(WriteGuard.MANAGED) && !steppedClean(ref)) {
            // Seen: a filter made after a clean step kept processing once the XSLT was replaced by one that never
            // stepped clean, and the agent went on to document and index its output.
            problems.add("The pipeline's current code has not stepped clean: its output may not be what was "
                         + "checked. step_sample over the sample streams until the verdict is clean, then "
                         + "reprocess_streams");
        }
        // Every task of every filter ended, and none for these streams: waiting again would only run out another
        // timeout.
        final boolean idle = filtersOf(status).stream().allMatch(filter -> Boolean.TRUE.equals(filter.get("finished")));
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("pipeline", request.pipelineUuid());
        result.put("finished", finished);
        result.put("streams", perStream);
        if (!expected) {
            result.put("output", "none expected: the pipeline writes no output stream (indexing, or no "
                                 + "StreamAppender), so only Error streams are reported");
        }
        result.put("gate", problems.isEmpty() && finished
                ? "pass"
                : "fail");
        result.put("problems", problems);
        if (!lookInto.isEmpty()) {
            result.put("look_into", lookInto);
            result.put("look_into_hint", LOOK_INTO_HINT);
        }
        result.put("hint", hint(finished, idle, pending, deadline));
        if ("pass".equals(result.get("gate")) && restoredBatch(request.pipelineUuid())) {
            // Indexing completed without errors: the small development batch was only to see Elasticsearch's
            // responses.
            result.put("batch_size", "restored to the template's default, now that indexing completed without "
                                     + "errors");
        }
        return result;
    }

    // Whether the build's Elasticsearch indexing pipeline's development batch size was taken off again.
    private boolean restoredBatch(final String pipelineUuid) {
        try {
            return indexingRuns.developmentBatch(pipelineUuid, false);
        } catch (final RuntimeException e) {
            // Housekeeping: never a reason for the wait to fail.
            return false;
        }
    }

    // Whether an input stream is done: its task ended, or its output is there (Stroom deleting old tasks), and none
    // of its outputs is still being written and no task for it is still to end.
    private static boolean done(final List<Meta> outputs,
                                final StreamProcessing.Tasks tasks,
                                final long raw,
                                final boolean expected) {
        if (tasks.open().contains(raw) || outputs.stream().anyMatch(meta -> meta.getStatus() == Status.LOCKED)) {
            return false;
        }
        return tasks.ended().contains(raw) || (expected && !outputs.isEmpty());
    }

    // What processing wrote to the Error streams beside an input's output (warnings, errors), triaged: each group
    // the build hasn't accepted, to look into. Errors that must be fixed are a problem too.
    @SuppressWarnings("unchecked")
    private void lookInto(final long raw,
                          final List<Long> errors,
                          final List<Map<String, Object>> lookInto,
                          final List<String> problems) {
        for (final long errorStream : errors) {
            final Map<String, Object> summary;
            try {
                summary = streamSummaries.summariseErrors(errorStream);
            } catch (final RuntimeException e) {
                final Map<String, Object> unread = new LinkedHashMap<>();
                unread.put("input", raw);
                unread.put("error_stream", errorStream);
                unread.put("unread", StreamSummaries.reason(e));
                lookInto.add(unread);
                continue;
            }
            for (final Map<?, ?> group : NullSafe.list((List<Map<?, ?>>) summary.get("groups"))) {
                if (Boolean.TRUE.equals(group.get("accepted"))) {
                    continue;
                }
                final Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("input", raw);
                entry.put("error_stream", errorStream);
                entry.put("severity", group.get("severity"));
                entry.put("element", group.get("element"));
                entry.put("count", group.get("count"));
                entry.put("class", group.get("class"));
                entry.put("message", firstMessage(group));
                lookInto.add(entry);
                if ("blocking".equals(group.get("class"))) {
                    problems.add("Stream " + raw + "'s processing wrote " + group.get("severity") + "s that must be "
                                 + "fixed (Error stream " + errorStream + ", " + group.get("element") + "): "
                                 + firstMessage(group) + ". summarise_streams (kind=errors) " + raw);
                }
            }
        }
    }

    private static String firstMessage(final Map<?, ?> group) {
        return group.get("examples") instanceof final List<?> examples && !examples.isEmpty()
               && examples.getFirst() instanceof final Map<?, ?> example
                ? String.valueOf(example.get("message"))
                : "";
    }

    // The problems with one input stream's outputs.
    private static List<String> problems(final long raw,
                                         final List<Long> events,
                                         final List<Long> errors,
                                         final boolean errorsOnly,
                                         final String outputType) {
        if (errorsOnly) {
            return errors.isEmpty()
                    ? List.of()
                    : List.of("Stream " + raw + " produced Error stream(s) " + PyText.repr(errors)
                              + ": summarise_streams (kind=errors) " + raw);
        }
        if (events.isEmpty()) {
            return List.of("Stream " + raw + " produced no " + outputType + " stream, and its task has ended, so "
                           + "none is coming: summarise_streams (kind=errors) " + raw + " (a failed task, a fatal "
                           + "error, or every record filtered out)");
        }
        if (events.size() > 1) {
            return List.of("Stream " + raw + " has " + events.size() + " " + outputType + " streams: it was "
                           + "processed more than once. After reprocess_streams, pass its filter_id so only the new "
                           + "output counts; otherwise ask the user which to keep");
        }
        return List.of();
    }

    private static String hint(final boolean finished,
                               final boolean idle,
                               final List<Long> pending,
                               final Deadline deadline) {
        if (finished) {
            return null;
        }
        if (idle) {
            return "Every task of the pipeline's filters has ended, but none for stream(s) "
                   + PyText.repr(pending) + ": waiting again won't change that. processing_status shows "
                   + "what each filter's tracker matched.";
        }
        if (deadline.bySessionLimit() != null) {
            return "Stopped waiting, with tasks still running, before this session's time limit for a call ("
                   + deadline.bySessionLimit().toSeconds() + " s) ran out. Call again: each call has its own "
                   + "time, so there is nothing for the user to do.";
        }
        return "Tasks were still running at the timeout; call again.";
    }

    private Map<String, Object> noFilter(final Request request) {
        final Map<String, Object> result = notStarted(request, "fail");
        result.put("problems", List.of("This pipeline has no processor filter, so nothing is processing and there "
                                       + "is nothing to wait for."));
        result.put("hint", "create_processor_filter first. If it was refused, do what its message says; don't "
                           + "wait.");
        return result;
    }

    private Map<String, Object> waitingForTheUser(final ToolCallContext context,
                                                  final Request request,
                                                  final DocRef pipeline,
                                                  final StreamProcessing.Waiting waiting) {
        final String disabled = waiting.what();
        final Map<String, Object> result = notStarted(request, "waiting");
        result.put("waiting_for", "the user to enable " + disabled);
        result.put("pipeline_link", context.links().documentTarget(pipeline));
        final List<Map<String, Object>> actions = new ArrayList<>();
        waiting.disabled().forEach(id -> actions.add(UserActionEntries.enableFilter(context.links(), id, pipeline,
                "")));
        if (!waiting.stopped().isEmpty()) {
            actions.add(UserActionEntries.openDocument(context.links(), pipeline, "enable its processor (the row "
                                                                                  + "above its filters) on the "
                                                                                  + "Processors tab"));
        }
        UserActionEntries.withEntries(result, actions);
        // Created disabled (asked for by the user): nothing runs until the user enables it, so waiting would only
        // run out the timeout.
        result.put("hint", "Processing hasn't started: " + disabled + " disabled, and enabling is the user's. Ask "
                           + "them to enable it on the pipeline's Processors tab (pipeline_link) and to tell you "
                           + "when they have; then wait_for_processing again.");
        return result;
    }

    private static Map<String, Object> nothingWillProcess(final Request request, final List<String> problems) {
        final Map<String, Object> result = notStarted(request, "fail");
        result.put("problems", problems);
        result.put("hint", "Nothing will process these streams, so there is nothing to wait for: "
                           + "create_processor_filter with stream_ids= them, then wait.");
        return result;
    }

    private static Map<String, Object> notStarted(final Request request, final String gate) {
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("pipeline", request.pipelineUuid());
        result.put("finished", false);
        result.put("streams", List.of());
        result.put("gate", gate);
        return result;
    }

    private String whereProcessingIs(final Request request,
                                            final Map<String, Object> status,
                                            final Map<Long, List<Meta>> outputs,
                                            final List<Long> pending,
                                            final boolean expected,
                                            final Deadline deadline) {
        final Map<String, Integer> tasks = new TreeMap<>();
        for (final Map<String, Object> filter : filtersOf(status)) {
            if (filter.get("tasks") instanceof final Map<?, ?> counts) {
                counts.forEach((name, count) -> tasks.merge(String.valueOf(name), ((Number) count).intValue(),
                        Integer::sum));
            }
        }
        final String taskText = tasks.entrySet().stream()
                .map(entry -> entry.getValue() + " " + entry.getKey().toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(", "));
        final long withOutput = outputs.values().stream().filter(metas -> !metas.isEmpty()).count();
        final long left = Math.max(0, (deadline.atMs() - clockMs.getAsLong()) / 1000);
        return "Waiting for processing: " + (request.streamIds().size() - pending.size()) + " of "
               + request.streamIds().size() + " streams processed"
               + (expected
                ? ", " + withOutput + " with output"
                : "")
               + "; tasks: " + (taskText.isEmpty()
                ? "none yet"
                : taskText) + "; " + left + " s left";
    }

    // The pipeline's filters, as the Processors tab lists them.
    private List<ProcessorFilter> filters(final DocRef pipeline) {
        final ProcessorFilterAccess.Filters found = processorFilterAccess.filtersOf(pipeline);
        if (found.refused() != null) {
            throw new ToolRefusal("Stroom refused to list this pipeline's processor filters: " + found.refused()
                                  + ".");
        }
        return found.filters();
    }

    private boolean steppedClean(final DocRef ref) {
        try {
            return buildRecords.steppedClean(ref);
        } catch (final RuntimeException e) {
            // Code that can't be read can't be known to have stepped clean.
            return false;
        }
    }

    // Until when to wait: the timeout asked for, or sooner, before the session's time limit for a long call (its
    // query time limit) runs out.
    private Deadline deadline(final ToolCallContext context, final int timeoutSeconds) {
        final long now = clockMs.getAsLong();
        final Duration timeout = Duration.ofSeconds(timeoutSeconds);
        final Duration limit = NullSafe.get(context.limits().workspace(), AiWorkspaceConfig::getQueryTimeLimit,
                StroomDuration::getDuration);
        if (limit != null && limit.minus(MARGIN).compareTo(timeout) < 0) {
            final Duration waited = limit.minus(MARGIN).compareTo(Duration.ofSeconds(MIN_TIMEOUT_SECONDS)) < 0
                    ? Duration.ofSeconds(MIN_TIMEOUT_SECONDS)
                    : limit.minus(MARGIN);
            return new Deadline(now + waited.toMillis(), limit);
        }
        return new Deadline(now + timeout.toMillis(), null);
    }

    // Pauses before the next look, stopping if the call has been cancelled.
    private void pauseOrStop(final ToolCallContext context) {
        final boolean stopped = context.runContext()
                .map(ToolRunContext::getCancelled)
                .map(BooleanSupplier::getAsBoolean)
                .orElse(false);
        if (stopped || Thread.currentThread().isInterrupted()) {
            throw new ToolTimeoutException();
        }
        try {
            pause.pause(POLL_INTERVAL);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ToolTimeoutException();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> filtersOf(final Map<String, Object> status) {
        return status.get("filters") instanceof final List<?> list
                ? (List<Map<String, Object>>) list
                : List.of();
    }

    // The value of a true or false argument, or null if it was absent or neither.
    private static Boolean booleanArg(final ToolCall call, final String name) {
        final Object value = argsMap(call).get(name);
        if (value instanceof final Boolean bool) {
            return bool;
        }
        if (value != null && ("true".equalsIgnoreCase(String.valueOf(value).strip())
                              || "false".equalsIgnoreCase(String.valueOf(value).strip()))) {
            return Boolean.parseBoolean(String.valueOf(value).strip());
        }
        return null;
    }


    // --------------------------------------------------------------------------------


    /// The pause between looks at processing.
    @FunctionalInterface
    interface Pause {

        /// @param duration How long to pause.
        /// @throws InterruptedException if the thread is interrupted while it pauses.
        void pause(Duration duration) throws InterruptedException;
    }

    // What was asked for.
    private record Request(String pipelineUuid,
                           List<Long> streamIds,
                           int timeoutSeconds,
                           Boolean expectEvents,
                           Integer filterId,
                           String outputType) {

    }

    // Until when to wait, and the session's time limit when that is what it comes from.
    private record Deadline(long atMs, Duration bySessionLimit) {

    }
}
