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
import stroom.ai.impl.tools.CallProgress;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.StreamSummaries;
import stroom.ai.impl.tools.ToolArgs;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.cef.CefDraft;
import stroom.ai.impl.tools.cef.CefEvents;
import stroom.ai.impl.tools.cef.CefPlan;
import stroom.ai.impl.tools.generation.EventRule;
import stroom.ai.impl.tools.generation.TranslationMapping;
import stroom.ai.impl.tools.generation.XmlElement;
import stroom.ai.impl.tools.generation.XmlParser;
import stroom.ai.impl.tools.generation.XsltGenerator;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.ai.impl.tools.indexing.FieldPlans;
import stroom.ai.impl.tools.indexing.PlannedField;
import stroom.ai.impl.tools.validation.EventStreams;
import stroom.ai.impl.workspace.ToolTimeoutException;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.meta.shared.MetaRow;
import stroom.pipeline.shared.PipelineDoc;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/// `review_coverage` - whole-feed coverage of an events pipeline: kinds of event the sample missed, which only
/// appear once the feed is processed. Ported from the Stroom MCP server's `tools/coverage.py`.
///
/// The generated translation logs, into each raw stream's Error stream, every record no rule matched ("No event
/// mapping matched record N (action=view | ...)", WARN) and every record a rule keeps as Unknown ("Kept as Unknown
/// by rule 'other': record N (...)", INFO), with the values its rules test. So the pipeline's Error streams name
/// which raw streams and records were missed, and of what kind, without reading the raw data. A feed may hold
/// hundreds of thousands of streams: they are read in bounded batches by Id (`continue_from` carries on), only clean
/// ones skipped where Stroom counts them, and what is kept is bounded. Nothing is processed: reprocessing is approved
/// by the user (reprocess_streams, create_processor_filter), or done by them in production.
///
/// A change to the events pipeline reaches the pipelines that read its Events (indexing, CEF output): their
/// processor filters name it or its Events feed ([ProcessorFilters#followOnPipelines]). Each is checked against the
/// Events the changed pipeline writes for the records it missed, with what to change, and, for Elasticsearch, what
/// the cluster's admin has to do before those streams are indexed again.
///
/// Read as the user. It takes a run slot, says where it is, stops at once when its thread is interrupted, and
/// stops reading Error streams before the session's time limit for a long call, saying where to carry on.
class ReviewCoverageTool extends AbstractBuiltInTool {

    static final String NAME = "review_coverage";
    static final String PIPELINE_UUID_ARG = "pipeline_uuid";
    static final String CONTINUE_FROM_ARG = "continue_from";
    static final String MAX_STREAMS_ARG = "max_streams";
    static final String EVENTS_STREAM_IDS_ARG = "events_stream_ids";
    static final String FOLLOW_ON_ARG = "follow_on";
    static final String CHANGED_PIPELINE_UUID_ARG = "changed_pipeline_uuid";
    static final String RAW_STREAM_IDS_ARG = "raw_stream_ids";
    static final int DEFAULT_MAX_STREAMS = 500;
    static final int MAX_STREAMS = 5_000;
    /// Affected streams named one by one; more are reprocessed by criteria on the feed.
    static final int EXACT_LIMIT = 200;
    /// The most records stepped for the Events a follow-on pipeline is checked against.
    static final int FOLLOW_ON_RECORDS = 200;
    static final String CANCELLED = "Reading was stopped: the call was cancelled.";
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneOffset.UTC);

    private final DocumentAccess documentAccess;
    private final KeptMappings keptMappings;
    private final CoverageReads reads;
    private final ProcessorFilters processorFilters;
    private final PipelineShapes pipelineShapes;
    private final IndexTemplateAgreements indexTemplateAgreements;
    private final PipelineStepping stepping;
    private final EventStreams eventStreams;
    private final WriteGuard guard;

    @Inject
    ReviewCoverageTool(final DocumentAccess documentAccess,
                       final KeptMappings keptMappings,
                       final CoverageReads reads,
                       final ProcessorFilters processorFilters,
                       final PipelineShapes pipelineShapes,
                       final IndexTemplateAgreements indexTemplateAgreements,
                       final PipelineStepping stepping,
                       final EventStreams eventStreams,
                       final WriteGuard guard) {
        this.documentAccess = documentAccess;
        this.keptMappings = keptMappings;
        this.reads = reads;
        this.processorFilters = processorFilters;
        this.pipelineShapes = pipelineShapes;
        this.indexTemplateAgreements = indexTemplateAgreements;
        this.stepping = stepping;
        this.eventStreams = eventStreams;
        this.guard = guard;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(BuildPlan.onboardingLabel(NAME)
                             + "Whole-feed coverage of an events pipeline: kinds of event the sample missed, which "
                             + "only appear once the feed is processed. The generated translation logs, into each "
                             + "raw stream's Error stream, every record no rule matched and every record a rule "
                             + "keeps as Unknown, with the values its rules test; this reads them, a bounded batch "
                             + "of Error streams a call (continue_from carries on through a large feed), groups "
                             + "them by kind with examples, and says so for the user. The affected raw streams "
                             + "alone need processing again: named when few, by criteria on the feed when many; "
                             + "the user approves any reprocessing. Then reviews the pipelines that read its Events "
                             + "(indexing, CEF output) against the Events the pipeline writes for those records: "
                             + "what each lacks, what to change, its documentation, and for Elasticsearch what the "
                             + "cluster's admin must do before the streams are indexed again. Reads only.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(PIPELINE_UUID_ARG, "The events pipeline (Raw Events to Events), once it "
                                                              + "has processed its feed, or much of it.")
                        .addIntegerProperty(CONTINUE_FROM_ARG, "From an earlier call's progress.continue_from: read "
                                                               + "the Error streams after it. A large feed is read "
                                                               + "over several calls.")
                        .addIntegerProperty(MAX_STREAMS_ARG, "Error streams to read in this call (1 to "
                                                             + MAX_STREAMS + "; default " + DEFAULT_MAX_STREAMS
                                                             + ").")
                        .addProperty(EVENTS_STREAM_IDS_ARG, stringArraySchema("Events streams the changed pipeline "
                                                                              + "wrote (e.g. after "
                                                                              + "reprocess_streams in a build), to "
                                                                              + "check the follow-on pipelines "
                                                                              + "against; left out, the Events it "
                                                                              + "writes for some missed records are "
                                                                              + "stepped."))
                        .addBooleanProperty(FOLLOW_ON_ARG, "Also review the pipelines that read its Events "
                                                           + "(default true).")
                        .addStringProperty(CHANGED_PIPELINE_UUID_ARG, "The copy with the change (a working copy or "
                                                                      + "new version in a build), which can't "
                                                                      + "process production streams: the follow-on "
                                                                      + "pipelines are checked against the Events "
                                                                      + "it gives, stepped over raw_stream_ids.")
                        .addProperty(RAW_STREAM_IDS_ARG, stringArraySchema("With changed_pipeline_uuid: raw "
                                                                           + "streams to step it over. Left out: "
                                                                           + "the affected ones found, else the "
                                                                           + "feed's newest."))
                        .required(PIPELINE_UUID_ARG)
                        .build())
                .build();
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.CONTENT;
    }

    @Override
    public boolean usesRunSlot() {
        // It reads up to 5,000 Error streams, and steps records.
        return true;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .requires(AppPermission.VIEW_DATA_PERMISSION)
                .unlockedBy(AppPermission.STEPPING_PERMISSION)
                .checksDocuments(DocumentPermission.VIEW)
                .note("The pipeline's Error and Events streams are read, and records stepped (the Stepping "
                      + "permission), as the user: only streams of feeds they may view.")
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        final String pipelineUuid = stringArg(call, PIPELINE_UUID_ARG);
        if (pipelineUuid == null) {
            return JsonResults.refused("Give pipeline_uuid: the events pipeline whose coverage to review.");
        }
        try {
            final Request request = new Request(pipelineUuid,
                    continueFrom(call),
                    maxStreams(call),
                    StartOnboardingTool.streamIds(call, EVENTS_STREAM_IDS_ARG),
                    !argsMap(call).containsKey(FOLLOW_ON_ARG) || ToolArgs.flag(call, FOLLOW_ON_ARG),
                    stringArg(call, CHANGED_PIPELINE_UUID_ARG),
                    StartOnboardingTool.streamIds(call, RAW_STREAM_IDS_ARG));
            return JsonResults.of(PipelineStepping.withinTimeLimit(context, () -> review(context, request)));
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        } catch (final StreamReader.StreamReadException | PipelineUnreadableException e) {
            return JsonResults.refused(e.getMessage());
        } catch (final ToolTimeoutException e) {
            return JsonResults.refused(CANCELLED);
        } catch (final RuntimeException e) {
            return JsonResults.refused(StreamSummaries.reason(e));
        }
    }

    /// The review, as the Stroom MCP server's `review_coverage` gives it: `pipeline`, `progress`, `missed` (the
    /// kinds found, most records first, with examples), `affected_raw_streams` (and `affected_raw_stream_ids`),
    /// `tell_user`, `next`, and with `follow_on`, `follow_on` (each pipeline reviewed) and `follow_on_events`.
    ///
    /// @param context The call's context.
    /// @param request What is asked.
    /// @return The review.
    /// @throws ToolRefusal if the pipeline can't be read, or has processed nothing yet.
    Map<String, Object> review(final ToolCallContext context, final Request request) {
        final CallProgress progress = CallProgress.of(context);
        final PipelineDoc pipelineDoc = documentAccess.read(new DocRef(PipelineDoc.TYPE, request.pipelineUuid()),
                        PipelineDoc.class)
                .orElseThrow(() -> new ToolRefusal("Pipeline " + request.pipelineUuid() + " can't be read: it "
                                                   + "doesn't exist, or you don't have permission to view it"));
        final DocRef pipeline = new DocRef(PipelineDoc.TYPE, request.pipelineUuid(),
                NullSafe.nonBlankStringElse(pipelineDoc.getName(), request.pipelineUuid()));
        final Optional<KeptMappings.KeptXslt> kept = keptMappings.keptXslt(request.pipelineUuid());
        final TranslationMapping mapping = kept.filter(k -> XsltSaves.TRANSLATION.equals(k.kept().kind()))
                .map(k -> TranslationMapping.parse(k.kept().payload().get("mapping")))
                .orElse(null);
        final long after = Objects.requireNonNullElse(request.continueFrom(), 0L);
        // As the Stroom MCP server read it: 0 is no earlier call.
        final boolean continued = after != 0;
        progress.say("Reading the Error streams of " + pipeline.getName());
        final CoverageReads.ErrorBatch batch = reads.errorBatch(pipeline, after, request.maxStreams());
        final List<MetaRow> anyEvents = reads.anyEvents(pipeline);
        if (batch.errors().isEmpty() && anyEvents.isEmpty() && !continued) {
            throw new ToolRefusal("Pipeline '" + pipelineDoc.getName() + "' has processed nothing yet: its Error "
                                  + "and Events streams are what this reads. create_processor_filter on its feed "
                                  + "first.");
        }
        final CoverageReads.LoggedRecords logged = reads.logged(progress, batch.errors());
        // Stopped before the call's time ran out: the next call carries on after the last Error stream read.
        final boolean stopped = logged.read() < batch.errors().size();
        final List<CoverageReads.ErrorStream> errors = batch.errors().subList(0, logged.read());
        final Long more = stopped
                ? Long.valueOf(errors.isEmpty()
                ? after
                : errors.getLast().id())
                : batch.more();

        final Map<List<Object>, Map<String, Object>> byKey = new LinkedHashMap<>();
        final Map<Long, Long> raws = new LinkedHashMap<>();
        for (final CoverageReads.Logged entry : logged.records()) {
            raws.putIfAbsent(entry.raw(), entry.created());
            final List<List<String>> pairs = new ArrayList<>();
            new TreeMap<>(entry.values()).forEach((name, value) -> pairs.add(List.of(name, value)));
            final List<Object> key = List.of(Objects.toString(entry.rule(), "\u0000"), pairs);
            final Map<String, Object> group = byKey.computeIfAbsent(key, k -> newGroup(entry));
            group.put("records", (int) group.get("records") + 1);
            @SuppressWarnings("unchecked") final List<Long> rawStreams = (List<Long>) group.get("raw_streams");
            if (!rawStreams.contains(entry.raw()) && rawStreams.size() < 50) {
                rawStreams.add(entry.raw());
            }
            @SuppressWarnings("unchecked") final List<Map<String, Object>> examples =
                    (List<Map<String, Object>>) group.get("examples");
            if (examples.size() < 2) {
                final Map<String, Object> example = new LinkedHashMap<>();
                example.put("raw_stream", entry.raw());
                // As step_records takes it, from 0: the log's record numbers are from 1 (seen in e2e: the record
                // after the one missed was given).
                example.put("record", entry.record() - 1);
                examples.add(example);
            }
        }
        final List<Map<String, Object>> groups = new ArrayList<>(byKey.values());
        groups.sort(Comparator.comparingInt(g -> -(int) g.get("records")));
        if (mapping != null) {
            // A few raw records for examples; the grouping itself needs none.
            for (final Map<String, Object> group : groups.subList(0, Math.min(10, groups.size()))) {
                @SuppressWarnings("unchecked") final List<Map<String, Object>> examples =
                        (List<Map<String, Object>>) group.get("examples");
                for (final Map<String, Object> example : examples.subList(0, Math.min(1, examples.size()))) {
                    example.put("holds", reads.example(mapping, (long) example.get("raw_stream"),
                            (int) example.get("record")));
                }
            }
        }
        final List<String> keptUnknown = mapping == null
                ? List.of()
                : NullSafe.list(mapping.events()).stream()
                        .filter(rule -> NullSafe.isNonEmptyString(rule.allowUnknown()))
                        .map(EventRule::name)
                        .toList();
        final boolean logsUnknown = kept.isPresent() && NullSafe.string(kept.get().xslt().getData())
                .contains(XsltGenerator.KEPT_UNKNOWN.split("'")[0]);
        final Set<Long> sampled = new TreeSet<>();
        if ((!keptUnknown.isEmpty() || mapping == null) && !logsUnknown && !continued) {
            sampled.addAll(sampledUnknown(pipeline, request.maxStreams(), groups));
        }
        final Set<Long> affectedIds = new TreeSet<>(raws.keySet());
        affectedIds.addAll(sampled);
        final List<Long> affectedList = List.copyOf(affectedIds);
        final List<Long> created = raws.values().stream().filter(Objects::nonNull).toList();
        final Map<String, Object> affected = new LinkedHashMap<>();
        affected.put("count", affectedList.size());
        affected.put("first_id", affectedList.isEmpty()
                ? null
                : affectedList.getFirst());
        affected.put("last_id", affectedList.isEmpty()
                ? null
                : affectedList.getLast());
        affected.put("from", created.isEmpty()
                ? null
                : iso(created.stream().min(Long::compare).get()));
        affected.put("to", created.isEmpty()
                ? null
                : iso(created.stream().max(Long::compare).get()));

        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("pipeline", pipelineDoc.getName());
        final Map<String, Object> progressed = new LinkedHashMap<>();
        progressed.put("error_streams_read", errors.size());
        progressed.put("after", after);
        progressed.put("continue_from", more);
        progressed.put("done", more == null);
        if (more != null) {
            progressed.put("note", "More Error streams to read: call again with continue_from, and add this call's "
                                   + "findings to the earlier ones'.");
        }
        if (stopped) {
            progressed.put("stopped_early", PipelineStepping.outOfTime(context, "after " + errors.size()
                                                                                + " Error streams"));
        }
        result.put("progress", progressed);
        result.put("missed", groups);
        result.put("affected_raw_streams", affected);
        if (!affectedList.isEmpty()) {
            result.put("affected_raw_stream_ids", affectedList.subList(0, Math.min(EXACT_LIMIT,
                    affectedList.size())));
        }
        tellUser(result, groups, affected, affectedList, keptUnknown, more, continued,
                errors.size(), pipeline);
        if (request.followOn()) {
            followOn(context, request, pipeline, kept, groups, affected, affectedList, anyEvents, more, result);
        }
        return result;
    }

    // Unknown events sampled from the pipeline's newest Events streams, added to the groups; the raw streams they
    // came from.
    private Set<Long> sampledUnknown(final DocRef pipeline,
                                     final int maxStreams,
                                     final List<Map<String, Object>> groups) {
        final CoverageReads.SampledUnknown found = reads.sampledUnknown(pipeline, Math.min(maxStreams, 200), 200);
        mostCommon(found.kinds(), 20).forEach(kind -> {
            final Map<String, Object> group = new LinkedHashMap<>();
            group.put("found_as", "kept as Unknown (its newest Events sampled: its XSLT logs no Unknown records; "
                                  + "save it again from its mapping and they are found exactly)");
            final Map<String, Object> shownKind = new LinkedHashMap<>();
            shownKind.put("TypeId", kind.getKey().typeId());
            shownKind.put("Data", kind.getKey().data());
            group.put("kind", shownKind);
            group.put("events", kind.getValue());
            group.put("example", found.examples().get(kind.getKey()));
            groups.add(group);
        });
        return found.raws();
    }

    private void tellUser(final Map<String, Object> result,
                          final List<Map<String, Object>> groups,
                          final Map<String, Object> affected,
                          final List<Long> affectedIds,
                          final List<String> keptUnknown,
                          final Long more,
                          final boolean continued,
                          final int read,
                          final DocRef pipeline) {
        final String soFar = more == null && !continued
                ? ""
                : " (in the " + read + " Error streams this call read" + (continued
                        ? ", after earlier calls"
                        : "") + (more != null
                        ? "; there are more"
                        : "") + ")";
        if (groups.isEmpty()) {
            result.put("tell_user", "No untranslated events" + soFar + ": every record was translated by a rule"
                                    + (keptUnknown.isEmpty()
                    ? ""
                    : " and none was kept as Unknown") + ".");
            return;
        }
        final int total = groups.stream()
                .mapToInt(g -> (int) g.getOrDefault("records", 0) + (int) g.getOrDefault("events", 0))
                .sum();
        result.put("tell_user", "Found untranslated events" + soFar + ": " + total + " record(s) of "
                                + groups.size() + " kind(s) the sample didn't have, in " + affectedIds.size()
                                + " raw stream(s). The mapping needs a rule for each; then only those streams need "
                                + "processing again, once the user approves.");
        final boolean inBuild = tags(pipeline).contains(WriteGuard.MANAGED);
        final boolean many = affectedIds.size() > EXACT_LIMIT || more != null;
        final String reprocess;
        if (inBuild && !many) {
            reprocess = "reprocess_streams with the affected raw streams, at most 10 a call: each call asks the "
                        + "user's approval";
        } else if (inBuild) {
            reprocess = "Too many affected streams to reprocess one by one in a build: the user processes them "
                        + "again in production once the change is promoted";
        } else if (!many) {
            reprocess = "Processing the affected raw streams again is the user's, once promoted: exactly these "
                        + "streams (affected_raw_stream_ids; Stroom's data browser: select them, Reprocess, or a "
                        + "processor filter on their Ids)";
        } else {
            reprocess = "Processing the affected streams again is the user's, once promoted. With this many, by "
                        + "criteria rather than one by one: a processor filter on the feed's Raw Events created "
                        + "from " + affected.get("from") + " to " + affected.get("to") + " (Ids "
                        + affected.get("first_id") + " to " + affected.get("last_id") + ", over every batch read), "
                        + "so every stream in that window is processed again, affected or not (Stroom supersedes "
                        + "their Events streams). Every follow-on pipeline then takes that window again.";
        }
        result.put("next", List.of(
                "Show the user the kinds found (missed) and their examples; step_records on an example to see it.",
                "Add a rule for each with build_translation_xslt changes={events: [...]} and uuid=<its XSLT>"
                + (inBuild
                        ? ""
                        : " (the pipeline is in production: copy_pipeline working_copy=true into a build first, and "
                          + "change the copy)"),
                "write_documentation with stream_ids = the sample streams plus some affected raw streams, so the new "
                + "kinds are documented to the field; change = what was found and added (one version row when the "
                + "build is promoted)",
                reprocess));
    }

    // The pipelines that read its Events, each reviewed against the Events the changed pipeline writes.
    private void followOn(final ToolCallContext context,
                          final Request request,
                          final DocRef pipeline,
                          final Optional<KeptMappings.KeptXslt> kept,
                          final List<Map<String, Object>> groups,
                          final Map<String, Object> affected,
                          final List<Long> affectedIds,
                          final List<MetaRow> anyEvents,
                          final Long more,
                          final Map<String, Object> result) {
        final List<XmlElement> events = followOnEvents(context, request, pipeline, kept, groups, affectedIds,
                anyEvents);
        final Set<String> feeds = new LinkedHashSet<>();
        anyEvents.forEach(row -> {
            if (NullSafe.isNonBlankString(row.getMeta().getFeedName())) {
                feeds.add(row.getMeta().getFeedName());
            }
        });
        final List<Long> oldEvents = !affectedIds.isEmpty() && affectedIds.size() <= EXACT_LIMIT && more == null
                ? reads.oldEvents(pipeline, affectedIds)
                : affectedIds.isEmpty()
                        ? List.of()
                        : null;
        CallProgress.of(context).say("Reviewing the pipelines that read the Events of " + pipeline.getName());
        final List<Map<String, Object>> reviewed = new ArrayList<>();
        for (final Map<String, Object> entry : processorFilters.followOnPipelines(pipeline.getUuid(), feeds)) {
            try {
                reviewed.add(reviewFollowOn(entry, events, affected, oldEvents));
            } catch (final RuntimeException e) {
                // One unreadable pipeline must not hide the others.
                final Map<String, Object> failed = new LinkedHashMap<>(entry);
                failed.put("error", head(StreamSummaries.reason(e), 200));
                reviewed.add(failed);
            }
        }
        result.put("follow_on", reviewed);
        result.put("follow_on_events", events.isEmpty()
                ? "none to check against: give events_stream_ids once the changed pipeline has written some"
                : events.size() + " Events " + (!request.eventsStreamIds().isEmpty()
                        ? "given"
                        : request.changedPipelineUuid() != null
                                ? "the changed copy gives, stepped"
                                : "the pipeline writes now for some affected records"));
    }

    // The Events the follow-on pipelines are checked against: given, the changed copy's stepped, or the pipeline's
    // own for some affected records.
    private List<XmlElement> followOnEvents(final ToolCallContext context,
                                            final Request request,
                                            final DocRef pipeline,
                                            final Optional<KeptMappings.KeptXslt> kept,
                                            final List<Map<String, Object>> groups,
                                            final List<Long> affectedIds,
                                            final List<MetaRow> anyEvents) {
        final List<XmlElement> events = new ArrayList<>();
        if (!request.eventsStreamIds().isEmpty()) {
            events.addAll(CefEvents.eventsOf(eventStreams.read(request.eventsStreamIds(),
                    PipelineStepping.MAX_SAMPLE_RECORDS).eventsXml()));
        } else if (request.changedPipelineUuid() != null) {
            // Its Events, stepped: a copy in a build can't process production streams (seen: the prompts asked for
            // "Events with the change", which only a step can give).
            stepping.checkCanStep();
            final SteppedPipeline loaded = stepping.load(request.changedPipelineUuid());
            final Optional<KeptMappings.KeptXslt> changed = keptMappings.keptXslt(request.changedPipelineUuid());
            List<Long> streams = request.rawStreamIds();
            if (streams.isEmpty()) {
                streams = exampleStreams(groups);
            }
            if (streams.isEmpty()) {
                streams = reads.newestRaw(anyEvents);
            }
            final String element = changed.or(() -> kept)
                    .map(KeptMappings.KeptXslt::element)
                    .orElseGet(() -> loaded.defaultOutputs().getFirst());
            events.addAll(stepped(context, loaded, streams, element));
        } else if (!affectedIds.isEmpty() && kept.isPresent()) {
            stepping.checkCanStep();
            final SteppedPipeline loaded = stepping.load(pipeline.getUuid());
            final List<Long> stepped = exampleStreams(groups);
            events.addAll(stepped(context, loaded, stepped.isEmpty()
                    ? affectedIds.subList(0, Math.min(3, affectedIds.size()))
                    : stepped, kept.get().element()));
        }
        return events;
    }

    private List<XmlElement> stepped(final ToolCallContext context,
                                     final SteppedPipeline loaded,
                                     final List<Long> streams,
                                     final String element) {
        final List<XmlElement> events = new ArrayList<>();
        try {
            for (final String output : stepping.outputs(context, loaded, streams, element, null, FOLLOW_ON_RECORDS)
                    .outputs().values()) {
                try {
                    events.addAll(CefEvents.eventsOf(output));
                } catch (final XmlParser.XmlParseException e) {
                    // Not Events: nothing to check against from this record.
                }
            }
        } finally {
            stepping.audit(context, streams, "Stepped stream(s) through " + loaded.name() + " with the " + NAME
                                             + " tool");
        }
        return events;
    }

    // The first example's raw stream of each group that has one, sorted, at most 5.
    private static List<Long> exampleStreams(final List<Map<String, Object>> groups) {
        final Set<Long> streams = new TreeSet<>();
        for (final Map<String, Object> group : groups) {
            if (group.get("examples") instanceof final List<?> examples && !examples.isEmpty()) {
                streams.add((Long) ((Map<?, ?>) examples.getFirst()).get("raw_stream"));
            }
        }
        return streams.stream().limit(5).toList();
    }

    /// `_review_follow_on`: a pipeline that reads the Events, checked against them.
    ///
    /// @param entry     The pipeline (`uuid`, `name`, `filters`).
    /// @param events    The Events the changed pipeline writes.
    /// @param affected  The affected raw streams (`from`, `to`).
    /// @param oldEvents The Events streams the affected raw streams wrote before (in the index), or null when too
    ///                  many to name.
    /// @return It with its `stage`, `backend`, `kind` and what it lacks, what to do, and for Elasticsearch what the
    /// cluster's admin does first.
    Map<String, Object> reviewFollowOn(final Map<String, Object> entry,
                                       final List<XmlElement> events,
                                       final Map<String, Object> affected,
                                       final List<Long> oldEvents) {
        final String uuid = (String) entry.get("uuid");
        final PipelineShapes.Shape shape = pipelineShapes.shape(uuid);
        final Optional<KeptMapping.Kept> kept = keptMappings.keptMapping(uuid);
        final Map<String, Object> out = new LinkedHashMap<>(entry);
        out.put("stage", shape.stage());
        out.put("backend", shape.backend());
        if (kept.isPresent() && XsltSaves.CEF.equals(kept.get().kind())) {
            final CefPlan plan = CefPlan.fromJson(kept.get().payload());
            final CefDraft.Extended extended = events.isEmpty()
                    ? new CefDraft.Extended(List.of(), List.of())
                    : CefDraft.extend(plan, events);
            out.put("kind", "cef");
            out.put("missing", extended.notes());
            out.put("changes", extended.added());
            out.put("to_do", extended.added().isEmpty()
                    ? "nothing to change for these Events"
                    : "draft_cef_mapping uuid=<its XSLT> overrides=<changes> stream_ids=<the new Events>, then "
                      + "step_sample, draft_cef_mapping pipeline_uuid=... to review, write_documentation");
            out.put("reprocessing", "Reprocessing the raw streams writes new Events streams, which this pipeline "
                                    + "sends again whole: ArcSight receives the events those streams had already "
                                    + "sent a second time (dedupe there, or accept it).");
        } else if (kept.isPresent() && XsltSaves.INDEX.equals(kept.get().kind())) {
            final FieldPlan plan = FieldPlan.fromJson(kept.get().payload());
            final List<String> gaps = events.isEmpty()
                    ? List.of()
                    : indexGaps(plan, events);
            out.put("kind", "index");
            out.put("not_indexed", gaps);
            out.put("to_do", gaps.isEmpty()
                    ? "nothing to change for these Events"
                    : "draft_index_mapping with these as extra_fields (the index's agreed template as the example), "
                      + "save_xslt index_plan=... uuid=<its XSLT>, step_sample, write_documentation");
            indexTemplateAgreements.elasticDestination(uuid).ifPresent(destination ->
                    out.put("elasticsearch", elasticsearch(plan, destination, gaps, affected, oldEvents)));
        } else {
            out.put("kind", "indexing".equals(shape.stage()) || "forwarding".equals(shape.stage())
                    ? "hand-written"
                    : shape.stage());
            out.put("to_do", "Its XSLT keeps no plan: step it over the new Events (step_sample) and check what it "
                             + "writes for the new kinds (draft_cef_mapping pipeline_uuid=... for a CEF pipeline)");
        }
        out.put("documentation", "write_documentation for it again once changed (the change is recorded when the "
                                 + "build is promoted)");
        return out;
    }

    private static Map<String, Object> elasticsearch(final FieldPlan plan,
                                                     final Map<String, Object> destination,
                                                     final List<String> gaps,
                                                     final Map<String, Object> affected,
                                                     final List<Long> oldEvents) {
        final Object index = destination.get("index name");
        final String field = NullSafe.list(plan.fields()).stream()
                .filter(f -> NullSafe.string(f.source()).contains("StreamId")
                             || NullSafe.string(f.source()).contains("stream-id"))
                .map(PlannedField::name)
                .findFirst()
                .orElse("StreamId");
        final String delete;
        if (oldEvents != null) {
            delete = oldEvents.isEmpty()
                    ? null
                    : "POST " + index + "/_delete_by_query {\"query\": {\"terms\": {\"" + field + "\": " + oldEvents
                      + "}}}";
        } else {
            // Too many to name: the streams are reprocessed by a time window, and so is what the index holds.
            delete = "POST " + index + "/_delete_by_query with a query for the documents of the feed's Events "
                     + "streams created from " + affected.get("from") + " to " + affected.get("to") + " (the "
                     + "window the raw streams are reprocessed in): their " + field + " values, or the event time "
                     + "field if the index has no feed field";
        }
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("index", index);
        out.put("cluster", destination.get("cluster"));
        out.put("template", gaps.isEmpty()
                ? "The index template needs no change for these Events."
                : "New fields need the index template changed first: check_index_template with them, which the "
                  + "user confirms, and the cluster's admin commits.");
        out.put("admin", delete == null
                ? "No Events streams of the affected raw streams were indexed yet."
                : "Before the affected streams are processed again, the cluster's admin deletes what their old "
                  + "Events streams put in the index, or those events are indexed twice: " + delete);
        return out;
    }

    /// `_index_gaps`: the Event paths no field of an index plan takes.
    ///
    /// @param plan   The index plan.
    /// @param events Events.
    /// @return The 25 most common, each `path (n of N events)`.
    static List<String> indexGaps(final FieldPlan plan, final List<XmlElement> events) {
        final List<String> sources = NullSafe.list(plan.leafFields()).stream().map(PlannedField::source).toList();
        final Map<String, Integer> counts = new LinkedHashMap<>();
        for (final XmlElement event : events) {
            for (final String path : CefEvents.eventValues(event).keySet()) {
                if (sources.stream().noneMatch(s -> FieldPlans.sourceMatches(stripLeadingSlashes(s), path))) {
                    counts.merge(path, 1, Integer::sum);
                }
            }
        }
        return mostCommon(counts, 25).stream()
                .map(e -> e.getKey() + " (" + e.getValue() + " of " + events.size() + " events)")
                .toList();
    }

    // Python's Counter.most_common: the most first, ties in the order first counted.
    private static <K> List<Map.Entry<K, Integer>> mostCommon(final Map<K, Integer> counts, final int n) {
        return counts.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<K, Integer> e) -> -e.getValue()))
                .limit(n)
                .toList();
    }

    private Set<String> tags(final DocRef pipeline) {
        try {
            return guard.tags(pipeline);
        } catch (final RuntimeException e) {
            return Set.of();
        }
    }

    private static Map<String, Object> newGroup(final CoverageReads.Logged entry) {
        final Map<String, Object> group = new LinkedHashMap<>();
        group.put("found_as", entry.rule() != null
                ? "kept as Unknown by rule '" + entry.rule() + "'"
                : "no rule matched");
        group.put("kind", entry.values().isEmpty()
                ? Map.of("values", "not logged (an XSLT saved before they were): see the examples")
                : new LinkedHashMap<>(entry.values()));
        group.put("records", 0);
        group.put("raw_streams", new ArrayList<Long>());
        group.put("examples", new ArrayList<Map<String, Object>>());
        return group;
    }

    private static String iso(final long ms) {
        return ms == 0
                ? null
                : ISO.format(Instant.ofEpochMilli(ms));
    }

    private static String stripLeadingSlashes(final String source) {
        int i = 0;
        while (i < source.length() && source.charAt(i) == '/') {
            i++;
        }
        return source.substring(i);
    }

    private static String head(final String text, final int n) {
        return text.length() <= n
                ? text
                : text.substring(0, n);
    }

    private Long continueFrom(final ToolCall call) {
        final String given = stringArg(call, CONTINUE_FROM_ARG);
        if (given == null) {
            return null;
        }
        try {
            return (long) Double.parseDouble(given);
        } catch (final NumberFormatException e) {
            throw new ToolRefusal("continue_from is an Error stream Id (a number), from an earlier call's "
                                  + "progress.continue_from");
        }
    }

    private int maxStreams(final ToolCall call) {
        if (stringArg(call, MAX_STREAMS_ARG) == null) {
            return DEFAULT_MAX_STREAMS;
        }
        final Integer given = intArg(call, MAX_STREAMS_ARG);
        if (given == null || given < 1 || given > MAX_STREAMS) {
            throw new ToolRefusal("max_streams must be a whole number from 1 to " + MAX_STREAMS);
        }
        return given;
    }


    // --------------------------------------------------------------------------------


    /// What a call asks.
    ///
    /// @param pipelineUuid        The events pipeline.
    /// @param continueFrom        The Error stream Id to read after, or null.
    /// @param maxStreams          The most Error streams to read.
    /// @param eventsStreamIds     Events streams the changed pipeline wrote.
    /// @param followOn            True to review the pipelines that read its Events.
    /// @param changedPipelineUuid The copy with the change, or null.
    /// @param rawStreamIds        Raw streams to step the copy over.
    record Request(String pipelineUuid,
                   Long continueFrom,
                   int maxStreams,
                   List<Long> eventsStreamIds,
                   boolean followOn,
                   String changedPipelineUuid,
                   List<Long> rawStreamIds) {

    }
}
