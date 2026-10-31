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
import stroom.ai.impl.AiChatRunManager;
import stroom.ai.impl.tools.AbstractBuiltInTool;
import stroom.ai.impl.tools.CallProgress;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.MergedPipeline;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.StreamSummaries;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.workspace.ToolTimeoutException;
import stroom.ai.shared.AiToolCategory;
import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaRow;
import stroom.pipeline.shared.PipelineDoc;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/// `evaluate_pipeline` - the facts for an evaluation of an existing pipeline, the same ground every time for each
/// stage of pipeline ([PipelineShapes]): what it is made of, what its processing wrote to its newest Error streams
/// (triaged, with the errors the user accepted as benign reported as such), a step_sample verdict on its input
/// streams, the checks for its stage, and an outline of the report for the agent to fill in. The checks by stage:
/// an events (translation) pipeline's newest Events (schema validity, quality, field population) and its coverage
/// (records no rule matched); an indexing pipeline's fields against its index's ([IndexEvaluation]); a CEF output
/// pipeline's lines ([CefEvaluation]); a reference data pipeline's maps and who looks them up
/// ([ReferenceEvaluation]); any other, none. Saves nothing: the agent shows the report, and saves it with
/// write_documentation only if the user wants it kept.
///
/// Made of the tools that cover each part (step_sample, check_events, summarise_streams' summaries, review_coverage's
/// reads, draft_cef_mapping's stepping, find_reference_data's reads), each read as the user. A part the user may not
/// see (no View Data, no Stepping) says so and the rest is still given. It takes a run slot over `/mcp` and says
/// where it is now and then; stepping stops before the session's time limit for a long call, as step_sample does.
class EvaluatePipelineTool extends AbstractBuiltInTool {

    static final String NAME = "evaluate_pipeline";
    static final String PIPELINE_UUID_ARG = "pipeline_uuid";
    static final String STREAM_IDS_ARG = "stream_ids";
    static final String MAX_RECORDS_ARG = "max_records";
    static final String SCHEMA_VERSION_ARG = CheckEventsTool.SCHEMA_VERSION_ARG;
    /// The checks for an events (translation) pipeline.
    static final String EVENTS_CHECKS = "events";
    /// The checks for an indexing (or discovery index) pipeline.
    static final String INDEX_CHECKS = "index";
    /// The checks for a CEF output pipeline.
    static final String CEF_CHECKS = "cef";
    /// The checks for a reference data pipeline.
    static final String REFERENCE_CHECKS = "reference";
    /// No checks of a stage's own.
    static final String GENERAL = "general";
    /// The checks each stage of pipeline gets; any other stage is [#GENERAL].
    static final Map<String, String> CHECKS_BY_STAGE = Map.of("translation", EVENTS_CHECKS, "indexing", INDEX_CHECKS,
            "discovery", INDEX_CHECKS, "forwarding", CEF_CHECKS, "reference", REFERENCE_CHECKS);
    /// The result's sections, in order, by the checks the pipeline gets.
    static final Map<String, List<String>> SECTIONS = Map.of(
            EVENTS_CHECKS, List.of("pipeline", "processing", "stepping", "events", "coverage", "report"),
            INDEX_CHECKS, List.of("pipeline", "processing", "stepping", "index", "report"),
            CEF_CHECKS, List.of("pipeline", "processing", "stepping", "cef", "report"),
            REFERENCE_CHECKS, List.of("pipeline", "processing", "stepping", "reference", "report"),
            GENERAL, List.of("pipeline", "processing", "stepping", "checks", "report"));
    /// The newest Error streams read for the processing summary.
    static final int ERROR_STREAMS = 10;
    /// The most streams of each type counted.
    static final int COUNTED = 500;
    /// The most input streams stepped when none are given.
    static final int STEP_STREAMS = 3;
    /// The most error groups shown in a section.
    static final int GROUPS = 8;
    /// The most events checked and profiled of the newest Events stream.
    static final int EVENTS = 200;
    /// The most paths shown of the field population.
    static final int POPULATION_PATHS = 40;

    private static final String UNAVAILABLE = "unavailable";
    private static final String NO_VIEW_DATA = "You don't have permission to view data (View Data), so the "
                                               + "pipeline's streams can't be read.";
    // The stream type each kind of pipeline reads, for its input streams by default.
    private static final Map<String, String> INPUT_TYPES = Map.of("translation", StreamTypeNames.RAW_EVENTS,
            "indexing", StreamTypeNames.EVENTS, "discovery", StreamTypeNames.RAW_EVENTS, "forwarding",
            StreamTypeNames.EVENTS, "reference", StreamTypeNames.RAW_REFERENCE);

    private final DocumentAccess documentAccess;
    private final PipelineShapes pipelineShapes;
    private final CoverageReads coverageReads;
    private final StreamSummaries streamSummaries;
    private final StepSampleTool stepSample;
    private final CheckEventsTool checkEvents;
    private final IndexEvaluation indexEvaluation;
    private final CefEvaluation cefEvaluation;
    private final ReferenceEvaluation referenceEvaluation;
    private final AiAuditLog auditLog;

    @Inject
    EvaluatePipelineTool(final DocumentAccess documentAccess,
                         final PipelineShapes pipelineShapes,
                         final CoverageReads coverageReads,
                         final StreamSummaries streamSummaries,
                         final StepSampleTool stepSample,
                         final CheckEventsTool checkEvents,
                         final IndexEvaluation indexEvaluation,
                         final CefEvaluation cefEvaluation,
                         final ReferenceEvaluation referenceEvaluation,
                         final AiAuditLog auditLog) {
        this.documentAccess = documentAccess;
        this.pipelineShapes = pipelineShapes;
        this.coverageReads = coverageReads;
        this.streamSummaries = streamSummaries;
        this.stepSample = stepSample;
        this.checkEvents = checkEvents;
        this.indexEvaluation = indexEvaluation;
        this.cefEvaluation = cefEvaluation;
        this.referenceEvaluation = referenceEvaluation;
        this.auditLog = auditLog;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("Evaluate an existing pipeline: the facts for a consistent evaluation, the same "
                             + "sections every time for its stage, in order: pipeline (what it is made of, its "
                             + "stage), processing (its newest Error streams' errors triaged as blocking / review / "
                             + "benign), stepping (step_sample's verdict on its input streams), then its stage's "
                             + "checks: an events pipeline's events (schema validity, quality and field population "
                             + "of its newest Events) and coverage (records no rule matched); an indexing "
                             + "pipeline's index (fields written against the index's fields, type and analyzer "
                             + "mismatches); a CEF pipeline's cef (its lines reviewed against ArcSight's "
                             + "dictionary); a reference data pipeline's reference (its maps and who looks them up); "
                             + "any other's checks (none apply); and report (the outline to fill in). Saves "
                             + "nothing: show the user the report; save it with write_documentation "
                             + "evaluation=true only if they want it kept.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(PIPELINE_UUID_ARG, "UUID of the pipeline to evaluate.")
                        .addProperty(STREAM_IDS_ARG, stringArraySchema("Input streams to step (raw streams for an "
                                                                       + "events pipeline, Events streams for an "
                                                                       + "indexing or CEF one). By default the "
                                                                       + "inputs of its newest output, else the "
                                                                       + "newest streams of the feeds its processor "
                                                                       + "filters read, up to " + STEP_STREAMS
                                                                       + "."))
                        .addIntegerProperty(MAX_RECORDS_ARG, "Step at most this many records: at most "
                                                             + PipelineStepping.MAX_SAMPLE_RECORDS + " (by "
                                                             + "default the first " + StepSampleTool.PER_STREAM
                                                             + " of each stream).")
                        .addStringProperty(SCHEMA_VERSION_ARG, "An events pipeline: the event-logging version to "
                                                               + "validate its Events against, one this Stroom "
                                                               + "holds. Defaults to the version the Events "
                                                               + "declare.")
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
        // Read-only: it saves nothing. Like step_sample, whose verdict it gives, a clean verdict may be recorded in
        // the build's record of the pipeline stepped: the tools' own bookkeeping, changing no data, document content
        // or processing.
        return true;
    }

    @Override
    public boolean usesRunSlot() {
        // It reads up to ten Error streams and an Events stream, and steps up to 500 records.
        return true;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .unlockedBy(AppPermission.VIEW_DATA_PERMISSION, AppPermission.STEPPING_PERMISSION,
                        AppPermission.MANAGE_PROCESSORS_PERMISSION)
                .checksDocuments(DocumentPermission.VIEW)
                .note("Read as the user, only through pipelines and index docs they may view, and streams of feeds "
                      + "they may view. Without View Data, the sections that read streams say they can't; without "
                      + "Stepping, the stepping section (and what is read from stepping) says it can't step; "
                      + "without Manage Processors, the feeds its processor filters read are not known: the rest "
                      + "is still given. A clean step is recorded in the build record of the pipeline stepped, "
                      + "when it is in a build.")
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        final String pipelineUuid = stringArg(call, PIPELINE_UUID_ARG);
        if (pipelineUuid == null) {
            return JsonResults.refused("Give pipeline_uuid: the pipeline to evaluate.");
        }
        try {
            final List<Long> streamIds = StartOnboardingTool.streamIds(call, STREAM_IDS_ARG);
            final Integer maxRecords = maxRecords(call);
            return JsonResults.of(evaluate(call, context, pipelineUuid, streamIds, maxRecords,
                    stringArg(call, SCHEMA_VERSION_ARG)));
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        } catch (final PipelineUnreadableException e) {
            return JsonResults.refused("Pipeline " + pipelineUuid + " can't be loaded: " + e.getMessage());
        } catch (final ToolTimeoutException e) {
            return JsonResults.refused(PipelineStepping.CANCELLED);
        } catch (final AiChatRunManager.WaitingForUserException e) {
            // A form waits for the user: the call is made again with the answer.
            throw e;
        } catch (final RuntimeException e) {
            return JsonResults.refused(StreamSummaries.reason(e));
        }
    }

    /// The evaluation's facts: the sections [#SECTIONS] gives for the pipeline's checks, always all of them and in
    /// that order. A section that can't be given says why (`status` and `reason`).
    ///
    /// @param call          The call.
    /// @param context       The call's context.
    /// @param pipelineUuid  The pipeline to evaluate.
    /// @param streamIds     Input streams to step, or none for its inputs by default.
    /// @param maxRecords    The most records to step, or null for step_sample's default.
    /// @param schemaVersion The event-logging version to validate against, or null.
    /// @return The sections.
    /// @throws ToolRefusal                 if the pipeline can't be read.
    /// @throws PipelineUnreadableException if Stroom can't load it.
    /// @throws ToolTimeoutException        if the call is cancelled.
    Map<String, Object> evaluate(final ToolCall call,
                                 final ToolCallContext context,
                                 final String pipelineUuid,
                                 final List<Long> streamIds,
                                 final Integer maxRecords,
                                 final String schemaVersion) {
        final MergedPipeline merged = MergedPipeline.load(documentAccess, pipelineUuid)
                .orElseThrow(() -> new ToolRefusal("Pipeline " + pipelineUuid + " can't be read: it doesn't "
                                                   + "exist, or you don't have permission to view it or a "
                                                   + "template it inherits from"));
        final PipelineShapes.Shape shape = pipelineShapes.shape(pipelineUuid);
        final String checks = checks(shape.stage());
        final String name = NullSafe.get(merged.doc(), PipelineDoc::getName);
        final DocRef ref = new DocRef(PipelineDoc.TYPE, pipelineUuid, name);
        final CallProgress progress = CallProgress.of(context);
        progress.say("Evaluating " + name + ": reading its newest Error streams");
        Written written = null;
        String unreadable = NO_VIEW_DATA;
        if (documentAccess.canViewData()) {
            try {
                written = written(ref, pipelineUuid, shape.stage());
            } catch (final ToolRefusal | StreamReader.StreamReadException e) {
                unreadable = "The pipeline's streams can't be read: " + e.getMessage();
            }
        }
        final Map<String, Object> processing = processing(context, written, unreadable, checks);

        final List<Long> toStep = streamIds.isEmpty()
                ? NullSafe.getOrElse(written, Written::inputStreams, List.of())
                : streamIds;
        progress.say("Evaluating " + name + ": stepping " + toStep.size() + " input stream(s)");
        final StepSampleTool.Sampled sampled = toStep.isEmpty()
                ? null
                : stepped(call, context, pipelineUuid, toStep, maxRecords, INDEX_CHECKS.equals(checks)
                                                                          || REFERENCE_CHECKS.equals(checks));
        final Map<String, Object> stepping = stepping(streamIds, toStep, NullSafe.get(sampled,
                StepSampleTool.Sampled::result), written == null
                ? unreadable
                : null);
        final List<String> outputs = sampled == null || sampled.outputs() == null
                || !"stepped".equals(stepping.get("status"))
                ? null
                : sampled.outputs();

        progress.say("Evaluating " + name + ": the checks for its stage (" + shape.stage() + ")");
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("pipeline", pipeline(merged, pipelineUuid, name, shape));
        result.put("processing", processing);
        result.put("stepping", stepping);
        switch (checks) {
            case EVENTS_CHECKS -> {
                result.put("events", events(context, written, unreadable, NullSafe.get(sampled,
                        StepSampleTool.Sampled::result), schemaVersion));
                result.put("coverage", coverage(written, unreadable, pipelineUuid));
            }
            case INDEX_CHECKS -> result.put("index", indexEvaluation.index(merged, merged.doc(), outputs,
                    String.valueOf(stepping.get("reason"))));
            case CEF_CHECKS -> result.put("cef", cefEvaluation.cef(context, pipelineUuid, toStep, shape.backend()));
            case REFERENCE_CHECKS -> result.put("reference", referenceEvaluation.reference(merged, pipelineUuid,
                    outputs));
            default -> result.put("checks", general(shape.stage()));
        }
        result.put("report", report(pipelineUuid, name, checks));
        return result;
    }

    /// @param stage A pipeline's stage, as [PipelineShapes] reads it.
    /// @return The checks it gets: [#EVENTS_CHECKS], [#INDEX_CHECKS], [#CEF_CHECKS], [#REFERENCE_CHECKS] or
    /// [#GENERAL].
    static String checks(final String stage) {
        return CHECKS_BY_STAGE.getOrDefault(NullSafe.string(stage), GENERAL);
    }

    // --------------------------------------------------------------------------------
    // The sections.
    // --------------------------------------------------------------------------------

    /// @return What the pipeline is made of: its name, UUID, stage, template chain, parser and code.
    private static Map<String, Object> pipeline(final MergedPipeline merged,
                                                final String uuid,
                                                final String name,
                                                final PipelineShapes.Shape shape) {
        final Map<String, Object> pipeline = new LinkedHashMap<>();
        pipeline.put("name", name);
        pipeline.put("uuid", uuid);
        pipeline.put("stage", shape.stage());
        if (shape.backend() != null) {
            pipeline.put("backend", shape.backend());
        }
        final String description = NullSafe.get(merged.doc(), PipelineDoc::getDescription);
        if (NullSafe.isNonBlankString(description)) {
            pipeline.put("description", description);
        }
        final List<DocRef> chain = merged.templateChain();
        pipeline.put("templates", chain.subList(0, Math.max(0, chain.size() - 1)).stream()
                .map(MergedPipeline::docRef)
                .toList());
        pipeline.put("parser", merged.parser());
        pipeline.put("translation_docs", merged.translationDocMaps());
        pipeline.put("element_chain", merged.chain());
        return pipeline;
    }

    // What its processing wrote: the streams counted, and the newest Error streams' markers triaged.
    private Map<String, Object> processing(final ToolCallContext context,
                                           final Written written,
                                           final String unreadable,
                                           final String checks) {
        if (written == null) {
            return unavailable(unreadable);
        }
        final Map<String, Object> processing = new LinkedHashMap<>();
        if (written.neverProcessed() && EVENTS_CHECKS.equals(checks)) {
            processing.put("status", "never_processed");
            processing.put("reason", "The pipeline has written no Events or Error streams: it has never processed "
                                     + "anything, or what it wrote has been deleted.");
            return processing;
        }
        if (written.errors().isEmpty() && written.events().isEmpty()) {
            processing.put("status", "no_error_streams");
            processing.put("reason", "No Error streams: it has processed nothing, or processed without errors or "
                                     + "warnings (it writes no Events stream to tell which).");
            return processing;
        }
        processing.put("status", "processed");
        processing.put("events_streams", counted(written.events().size()));
        processing.put("error_streams", counted(written.errors().size()));
        final List<Meta> newest = written.errors().stream()
                .limit(ERROR_STREAMS)
                .map(MetaRow::getMeta)
                .toList();
        processing.put("error_streams_read", newest.stream().map(Meta::getId).toList());
        processing.putAll(written.errorSummary());
        auditLog.streamsViewed(context.session(), newest.stream().map(Meta::getId).toList(),
                "Summarised the errors of " + newest.size() + " stream(s) with the " + NAME + " tool");
        return processing;
    }

    // The streams the pipeline wrote, newest first, its newest Error streams summarised, and its input streams.
    private Written written(final DocRef ref, final String pipelineUuid, final String stage) {
        final List<MetaRow> errors = coverageReads.newest(ref, StreamTypeNames.ERROR, COUNTED);
        final List<MetaRow> events = coverageReads.newest(ref, StreamTypeNames.EVENTS, COUNTED);
        final Map<String, Object> summary = new LinkedHashMap<>();
        final List<Map<String, Object>> allGroups = new ArrayList<>();
        if (!errors.isEmpty()) {
            final Map<String, Object> triaged = streamSummaries.summariseErrorStreams(errors.stream()
                    .limit(ERROR_STREAMS)
                    .map(MetaRow::getMeta)
                    .toList());
            final List<Map<String, Object>> groups = SteppingVerdicts.groups(triaged);
            allGroups.addAll(NullSafe.list(groups));
            summary.put("verdict", triaged.get("verdict"));
            summary.put("groups_by_class", triaged.get("groups_by_class"));
            summary.put("markers_by_severity", bySeverity(allGroups));
            summary.put("groups", compact(allGroups, false));
            if (allGroups.size() > GROUPS) {
                summary.put("groups_not_shown", allGroups.size() - GROUPS);
            }
            if (triaged.get("unread") != null) {
                summary.put("unread", triaged.get("unread"));
                summary.put("hint", triaged.get("hint"));
            }
        } else {
            summary.put("verdict", "clean");
            summary.put("note", "No Error streams: processing met no errors or warnings.");
        }
        final Set<Long> inputs = new LinkedHashSet<>();
        for (final MetaRow row : events.isEmpty()
                ? errors
                : events) {
            final Long parent = row.getMeta().getParentMetaId();
            if (parent != null && inputs.size() < STEP_STREAMS) {
                inputs.add(parent);
            }
        }
        if (inputs.isEmpty() && INPUT_TYPES.containsKey(stage)) {
            inputs.addAll(feedInputs(pipelineUuid, INPUT_TYPES.get(stage)));
        }
        return new Written(errors, events, summary, allGroups, List.copyOf(inputs));
    }

    // The newest streams of the type it reads on the feeds its processor filters read, up to STEP_STREAMS.
    private List<Long> feedInputs(final String pipelineUuid, final String type) {
        final List<Long> inputs = new ArrayList<>();
        for (final String feed : referenceEvaluation.feeds(pipelineUuid)) {
            for (final long id : coverageReads.newestOnFeed(feed, type, STEP_STREAMS)) {
                if (inputs.size() < STEP_STREAMS) {
                    inputs.add(id);
                }
            }
        }
        return inputs;
    }

    /// @return step_sample's verdict on the streams (with each record's output, if kept), or a refusal's reason
    /// as a section.
    private StepSampleTool.Sampled stepped(final ToolCall call,
                                           final ToolCallContext context,
                                           final String pipelineUuid,
                                           final List<Long> streamIds,
                                           final Integer maxRecords,
                                           final boolean keepOutputs) {
        try {
            return stepSample.sampled(call, context, pipelineUuid, streamIds, null, maxRecords, null, keepOutputs);
        } catch (final ToolRefusal | StreamReader.StreamReadException e) {
            // No Stepping permission, or streams it can't step: the section says why, the rest is still given.
            final Map<String, Object> refused = unavailable(e.getMessage());
            refused.put("refused", true);
            return new StepSampleTool.Sampled(refused, null);
        }
    }

    // The stepping section: the verdict, records stepped and problem groups.
    //
    // unreadable: why the pipeline's streams couldn't be found, or null if they were.
    private static Map<String, Object> stepping(final List<Long> given,
                                                final List<Long> stepped,
                                                final Map<String, Object> verdict,
                                                final String unreadable) {
        final Map<String, Object> stepping = new LinkedHashMap<>();
        if (verdict == null) {
            stepping.put("status", "not_stepped");
            stepping.put("reason", (unreadable == null
                    ? "No input streams to step: the pipeline has written nothing to find them by, and no processor "
                      + "filter of its names a feed with any."
                    : unreadable) + " Give stream_ids (the input streams it reads) to step.");
            return stepping;
        }
        if (Boolean.TRUE.equals(verdict.get("refused"))) {
            stepping.put("status", UNAVAILABLE);
            stepping.put("reason", verdict.get("reason"));
            stepping.put("streams", stepped);
            return stepping;
        }
        final List<Map<String, Object>> groups = NullSafe.list(SteppingVerdicts.groups(verdict));
        stepping.put("status", "stepped");
        stepping.put("verdict", verdict.get("verdict"));
        stepping.put("streams", stepped);
        stepping.put("streams_from", given.isEmpty()
                ? "the pipeline's newest inputs"
                : "stream_ids");
        stepping.put("records_stepped", verdict.get("records_stepped"));
        stepping.put("records_with_errors", verdict.get("records_with_errors"));
        stepping.put("groups_by_class", verdict.get("groups_by_class"));
        stepping.put("groups", compact(groups, true));
        if (groups.size() > GROUPS) {
            stepping.put("groups_not_shown", groups.size() - GROUPS);
        }
        if (verdict.get("stopped_early") != null) {
            stepping.put("stopped_early", verdict.get("stopped_early"));
        }
        if (verdict.get("hint") != null) {
            stepping.put("hint", verdict.get("hint"));
        }
        return stepping;
    }

    // The events section: the newest Events stream checked and profiled, or what stepping wrote checked.
    private Map<String, Object> events(final ToolCallContext context,
                                       final Written written,
                                       final String unreadable,
                                       final Map<String, Object> stepped,
                                       final String schemaVersion) {
        if (written == null) {
            return unavailable(unreadable);
        }
        final Map<String, Object> events = new LinkedHashMap<>();
        if (written.events().isEmpty()) {
            final String output = steppedEvents(stepped);
            if (output == null) {
                events.put("status", "not_checked");
                events.put("reason", "The pipeline has written no Events stream, and stepping gave no Events to "
                                     + "check.");
                return events;
            }
            events.put("status", "checked");
            events.put("source", "the first record stepped (the pipeline has written no Events stream)");
            try {
                events.putAll(checked(checkEvents.check(output, schemaVersion)));
            } catch (final ToolRefusal e) {
                events.put("status", "not_checked");
                events.put("reason", e.getMessage());
            }
            events.put("field_population", "Not profiled: that needs an Events stream. Process the pipeline's "
                                           + "streams, then evaluate it again.");
            return events;
        }
        final long newest = written.events().getFirst().getMeta().getId();
        events.put("status", "checked");
        events.put("source", "Events stream " + newest + ", the newest the pipeline wrote");
        events.put("stream_id", newest);
        try {
            events.putAll(checked(checkEvents.checkRead(List.of(newest), schemaVersion, EVENTS)));
            events.putAll(population(streamSummaries.summariseEvents(List.of(newest), EVENTS)));
        } catch (final ToolRefusal | StreamReader.StreamReadException e) {
            events.put("status", "not_checked");
            events.put("reason", e.getMessage());
        } finally {
            auditLog.streamsViewed(context.session(), List.of(newest), "Checked the events of a stream with the "
                                                                       + NAME + " tool");
        }
        return events;
    }

    // The coverage section: records the Error streams log as matching no rule, or kept as Unknown.
    private static Map<String, Object> coverage(final Written written,
                                                final String unreadable,
                                                final String pipelineUuid) {
        if (written == null) {
            return unavailable(unreadable);
        }
        final Map<String, Object> coverage = new LinkedHashMap<>();
        if (written.errors().isEmpty()) {
            coverage.put("status", written.neverProcessed()
                    ? "not_checked"
                    : "none_logged");
            coverage.put("reason", written.neverProcessed()
                    ? "Nothing processed yet: no Error streams to read."
                    : "No Error streams: no record was logged as matching no rule or kept as Unknown.");
            return coverage;
        }
        int unmatched = 0;
        int unknown = 0;
        final Set<String> rules = new TreeSet<>();
        for (final Map<String, Object> group : written.groups()) {
            final List<?> examples = group.get("examples") instanceof final List<?> list
                    ? list
                    : List.of();
            CoverageReads.Logged first = null;
            for (final Object example : examples) {
                if (example instanceof final Map<?, ?> map) {
                    final CoverageReads.Logged logged = CoverageReads.parseLogged(String.valueOf(map.get("message")));
                    if (logged != null) {
                        first = first == null
                                ? logged
                                : first;
                        if (logged.rule() != null) {
                            rules.add(logged.rule());
                        }
                    }
                }
            }
            if (first != null) {
                final int count = group.get("count") instanceof final Number number
                        ? number.intValue()
                        : 0;
                if (first.rule() == null) {
                    unmatched += count;
                } else {
                    unknown += count;
                }
            }
        }
        coverage.put("status", unmatched + unknown == 0
                ? "none_logged"
                : "records_missed");
        coverage.put("error_streams_read", Math.min(ERROR_STREAMS, written.errors().size()));
        coverage.put("records_no_rule_matched", unmatched);
        coverage.put("records_kept_as_unknown", unknown);
        if (!rules.isEmpty()) {
            coverage.put("rules_keeping_unknown", List.copyOf(rules));
        }
        coverage.put("next", unmatched + unknown == 0
                ? "Nothing logged as missed in these Error streams. An XSLT that logs no missed records (written by "
                  + "hand) can still miss some: review_coverage pipeline_uuid=" + pipelineUuid + " samples its "
                  + "Events for Unknown ones."
                : "Some records match no rule of the translation: review_coverage pipeline_uuid=" + pipelineUuid
                  + " finds every kind across the feed, with examples. Suggest it in the report; don't run it "
                  + "unless the user asks.");
        return coverage;
    }

    // The section of a pipeline no checks of a stage's own apply to.
    private static Map<String, Object> general(final String stage) {
        final Map<String, Object> section = new LinkedHashMap<>();
        section.put("status", "not_applicable");
        section.put("reason", "No checks specific to a pipeline of stage '" + stage + "' apply: its processing and "
                              + "stepping above are the evaluation.");
        return section;
    }

    // The report's outline, and what to do with it.
    private static Map<String, Object> report(final String pipelineUuid, final String name, final String checks) {
        final Map<String, String> headings = outline(checks);
        final StringBuilder outline = new StringBuilder("# " + name + " - Evaluation\n");
        headings.keySet().forEach(heading -> outline.append("\n").append(heading).append("\n"));
        final Map<String, Object> report = new LinkedHashMap<>();
        report.put("outline", outline.toString());
        report.put("sections", headings);
        report.put("next", "Write the report from this outline, every heading filled in from the sections above "
                           + "(say when a section couldn't be given, and why), and show it to the user. Only if "
                           + "they want it kept, save it: write_documentation build=<a build (start_build)> "
                           + "pipeline_uuid=" + pipelineUuid + " evaluation=true markdown=<the report>.");
        return report;
    }

    // --------------------------------------------------------------------------------
    // Helpers.
    // --------------------------------------------------------------------------------

    /// @param groups Triaged error groups.
    /// @param records True to keep a few of each group's records.
    /// @return Up to [#GROUPS] of them, short: class, reason, where, how often and an example.
    static List<Map<String, Object>> compact(final List<Map<String, Object>> groups, final boolean records) {
        return groups.stream()
                .limit(GROUPS)
                .map(group -> {
                    final Map<String, Object> brief = new LinkedHashMap<>();
                    brief.put("class", group.get("class"));
                    brief.put("reason", group.get("reason"));
                    brief.put("severity", group.get("severity"));
                    brief.put("element", group.get("element"));
                    brief.put("count", group.get("count"));
                    if (group.get("records_affected") != null) {
                        brief.put("records_affected", group.get("records_affected"));
                    }
                    if (Boolean.TRUE.equals(group.get("accepted"))) {
                        brief.put("accepted", true);
                    }
                    brief.put("example", example(group));
                    if (records && group.get("records") instanceof final List<?> list && !list.isEmpty()) {
                        brief.put("records", list.subList(0, Math.min(5, list.size())));
                    }
                    return brief;
                })
                .toList();
    }

    /// @param groups Triaged error groups.
    /// @return The markers they hold, counted by severity.
    static Map<String, Integer> bySeverity(final List<Map<String, Object>> groups) {
        final Map<String, Integer> counts = new TreeMap<>();
        for (final Map<String, Object> group : groups) {
            if (group.get("count") instanceof final Number number) {
                counts.merge(String.valueOf(group.get("severity")), number.intValue(), Integer::sum);
            }
        }
        return counts;
    }

    /// @param check A check_events result.
    /// @return It short: `ok`, the schema's validity and first errors, and the quality rules failed with an
    /// example each.
    static Map<String, Object> checked(final Map<String, Object> check) {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", check.get("ok"));
        if (check.get("schema") instanceof final Map<?, ?> schema) {
            final Map<String, Object> shown = new LinkedHashMap<>();
            shown.put("valid", schema.get("valid"));
            shown.put("schema", schema.get("schema"));
            shown.put("error_count", schema.get("error_count"));
            if (schema.get("errors") instanceof final List<?> errors && !errors.isEmpty()) {
                shown.put("errors", errors.subList(0, Math.min(5, errors.size())));
            }
            out.put("schema", shown);
        }
        if (check.get("quality") instanceof final Map<?, ?> quality) {
            final Map<String, Object> shown = new LinkedHashMap<>();
            shown.put("ok", quality.get("ok"));
            if (quality.get("events_checked") != null) {
                shown.put("events_checked", quality.get("events_checked"));
            }
            if (quality.get("rules") instanceof final Map<?, ?> rules && !rules.isEmpty()) {
                final Map<String, Object> failed = new LinkedHashMap<>();
                rules.forEach((rule, entry) -> {
                    final Map<String, Object> brief = new LinkedHashMap<>();
                    if (entry instanceof final Map<?, ?> map) {
                        brief.put("events_failing", map.get("events_failing"));
                        if (map.get("examples") instanceof final List<?> examples && !examples.isEmpty()) {
                            brief.put("example", examples.getFirst());
                        }
                    }
                    failed.put(String.valueOf(rule), brief);
                });
                shown.put("rules", failed);
            }
            if (quality.get("notes") != null) {
                shown.put("notes", quality.get("notes"));
            }
            if (quality.get("note") != null) {
                shown.put("note", quality.get("note"));
            }
            out.put("quality", shown);
        }
        if (check.get("read") != null) {
            out.put("read", check.get("read"));
        }
        return out;
    }

    /// @param summary A summary of Events streams ([StreamSummaries#summariseEvents]).
    /// @return It short: how many events were profiled, their types and TypeIds, and how often each path is
    /// filled in (as a share of the events), least filled first, up to [#POPULATION_PATHS] paths.
    static Map<String, Object> population(final Map<String, Object> summary) {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("events_profiled", summary.get("events"));
        out.put("event_types", summary.get("event_types"));
        if (summary.get("type_ids") instanceof final Map<?, ?> typeIds) {
            final Map<Object, Object> shown = new LinkedHashMap<>();
            typeIds.entrySet().stream().limit(10).forEach(e -> shown.put(e.getKey(), e.getValue()));
            out.put("type_ids", shown);
        }
        final Map<String, Object> population = new LinkedHashMap<>();
        if (summary.get("path_population") instanceof final Map<?, ?> paths) {
            final List<Map.Entry<String, Double>> sorted = new ArrayList<>();
            paths.forEach((path, share) -> sorted.add(Map.entry(String.valueOf(path), share instanceof
                    final Number number
                    ? number.doubleValue()
                    : 0.0)));
            sorted.sort(Map.Entry.<String, Double>comparingByValue().thenComparing(Map.Entry.comparingByKey()));
            sorted.stream().limit(POPULATION_PATHS).forEach(e -> population.put(e.getKey(), e.getValue()));
            out.put("field_population", population);
            if (sorted.size() > POPULATION_PATHS) {
                out.put("field_population_note", (sorted.size() - POPULATION_PATHS) + " more paths, each in "
                                                  + "at least " + sorted.get(POPULATION_PATHS).getValue()
                                                  + "% of the events, not shown (summarise_streams kind=events "
                                                  + "gives them all).");
            }
        } else {
            out.put("field_population", population);
        }
        return out;
    }

    // The Events that the first record stepped wrote, or null.
    private static String steppedEvents(final Map<String, Object> stepped) {
        if (stepped == null || !(stepped.get("first_record_output") instanceof final Map<?, ?> outputs)) {
            return null;
        }
        return outputs.values().stream()
                .map(String::valueOf)
                .filter(text -> text.contains("<Events"))
                .reduce((first, last) -> last)
                .orElse(null);
    }

    private static String example(final Map<String, Object> group) {
        if (group.get("examples") instanceof final List<?> examples && !examples.isEmpty()
            && examples.getFirst() instanceof final Map<?, ?> example && example.get("message") != null) {
            final String message = String.valueOf(example.get("message"));
            return message.length() > 300
                    ? message.substring(0, 300)
                    : message;
        }
        return null;
    }

    // A count of streams, or "500+" when there are at least as many as are counted.
    private static Object counted(final int count) {
        return count >= COUNTED
                ? COUNTED + "+"
                : count;
    }

    private static Map<String, Object> unavailable(final String reason) {
        final Map<String, Object> section = new LinkedHashMap<>();
        section.put("status", UNAVAILABLE);
        section.put("reason", reason);
        return section;
    }

    // max_records: a whole number of at least 1, at most step_sample's, or null if not given.
    private Integer maxRecords(final ToolCall call) {
        if (stringArg(call, MAX_RECORDS_ARG) == null) {
            return null;
        }
        final Integer value = intArg(call, MAX_RECORDS_ARG);
        if (value == null || value < 1) {
            throw new ToolRefusal(MAX_RECORDS_ARG + " must be a whole number of at least 1, not '"
                                  + stringArg(call, MAX_RECORDS_ARG) + "'");
        }
        return Math.min(value, PipelineStepping.MAX_SAMPLE_RECORDS);
    }

    /// @param checks The checks the pipeline gets.
    /// @return The report's headings, in order, each with what fills it.
    static Map<String, String> outline(final String checks) {
        final Map<String, String> outline = new LinkedHashMap<>();
        outline.put("## Summary", "Overall: is the pipeline sound, and the most important finding.");
        outline.put("## Pipeline", "pipeline: its stage, template, parser and code.");
        outline.put("## Processing errors", "processing: streams written, errors and warnings by class, the most "
                                            + "common with an example; accepted errors as benign.");
        outline.put("## Stepping", "stepping: the verdict, records stepped and problem groups.");
        switch (checks) {
            case EVENTS_CHECKS -> {
                outline.put("## Event validity", "events: schema validity, quality rules failed, and the fields "
                                                 + "rarely filled in.");
                outline.put("## Coverage", "coverage: records no rule matched or kept as Unknown, and "
                                           + "review_coverage if any.");
            }
            case INDEX_CHECKS -> outline.put("## Index fields", "index: the index written to, fields written that "
                                                                + "it lacks, its fields nothing writes, type and "
                                                                + "analyzer mismatches, and the index template.");
            case CEF_CHECKS -> outline.put("## CEF output", "cef: header fields still TODO, problems in its lines, "
                                                            + "Event values not sent, and the mapping its lines "
                                                            + "imply.");
            case REFERENCE_CHECKS -> outline.put("## Reference maps", "reference: each map's key and value, its "
                                                                      + "feeds, and the pipelines that look it "
                                                                      + "up.");
            default -> {
                // No checks of its stage's own: the general sections are the evaluation.
            }
        }
        outline.put("## Recommendations", "What to fix first, blocking before review; benign errors need nothing.");
        return Collections.unmodifiableMap(outline);
    }


    // --------------------------------------------------------------------------------


    /// What the pipeline wrote.
    ///
    /// @param errors       Its Error streams, newest first (up to [#COUNTED]).
    /// @param events       Its Events streams, newest first (up to [#COUNTED]).
    /// @param errorSummary The newest Error streams' markers, triaged and short.
    /// @param groups       Every group of that triage.
    /// @param inputStreams Its input streams to step by default, up to [#STEP_STREAMS].
    private record Written(List<MetaRow> errors,
                           List<MetaRow> events,
                           Map<String, Object> errorSummary,
                           List<Map<String, Object>> groups,
                           List<Long> inputStreams) {

        private boolean neverProcessed() {
            return errors.isEmpty() && events.isEmpty();
        }
    }
}
