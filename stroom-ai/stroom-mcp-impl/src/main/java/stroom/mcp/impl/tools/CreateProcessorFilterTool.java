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
import stroom.ai.impl.tools.PyText;
import stroom.ai.impl.tools.ToolArgs;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.shared.ProcessorFilter;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// `create_processor_filter` - creates a processor filter for Stroom to process streams through a pipeline in a
/// build: the sample stream ids, or a feed's streams. Ported from the Stroom MCP server's
/// `tools/processing_writes.py`.
///
/// For an events (or records) or indexing pipeline the user approves it in a form, then it is created enabled and
/// processing starts; any other pipeline's filter is created disabled, for the user to enable. The filter is made
/// through Stroom's processor filter service as the user (Manage Processors, and View on the pipeline), as the data
/// browser's Process action makes one.
class CreateProcessorFilterTool extends AbstractBuiltInTool {

    static final String NAME = "create_processor_filter";
    static final String PIPELINE_UUID_ARG = "pipeline_uuid";
    static final String STREAM_IDS_ARG = "stream_ids";
    static final String FEED_ARG = "feed";
    static final String STREAM_TYPE_ARG = "stream_type";
    static final String CREATED_AFTER_ARG = "created_after";
    static final String PRIORITY_ARG = "priority";
    static final String SOURCE_PIPELINE_UUID_ARG = "source_pipeline_uuid";
    static final String INDEX_DROPPED_ARG = "index_dropped";
    static final String TEMPLATE_COMMITTED_BY_USER_ARG = "template_committed_by_user";

    /// The source pipeline's argument, as both processing tools describe it.
    static final String SOURCE_PIPELINE_DESCRIPTION = "Indexing pipelines reading Events: the events pipeline that "
                                                      + "produced them (one this server built, or another with the "
                                                      + "user's confirmation). The filter only selects Events from "
                                                      + "exactly that pipeline.";
    /// The template-committed argument.
    static final String TEMPLATE_COMMITTED_DESCRIPTION = "Elasticsearch: true only when the user says an index "
                                                         + "template of their own is already committed to the "
                                                         + "cluster for this index, and wants to go on without "
                                                         + "agreeing one here. They confirm it in a form; it is then "
                                                         + "kept with the pipeline in place of an agreed one.";
    /// The index-dropped argument.
    static final String INDEX_DROPPED_DESCRIPTION = "From an earlier drop_first reply: true once the user says the "
                                                    + "index this pipeline wrote to before has been dropped (or its "
                                                    + "shards deleted).";

    private static final int MIN_PRIORITY = 1;
    private static final int MAX_PRIORITY = 100;

    private final ProcessorFilterWrites filterWrites;
    private final ProcessingSources sources;
    private final ProcessingApprovals approvals;
    private final IndexingRuns indexingRuns;
    private final IndexTemplateAgreements agreements;
    private final PipelineOutputs pipelineOutputs;
    private final PipelineShapes pipelineShapes;
    private final SampleStreamTexts sampleStreamTexts;
    private final BuildRecords buildRecords;
    private final Consent consent;
    private final BuildPlan plan;

    @Inject
    CreateProcessorFilterTool(final ProcessorFilterWrites filterWrites,
                              final ProcessingSources sources,
                              final ProcessingApprovals approvals,
                              final IndexingRuns indexingRuns,
                              final IndexTemplateAgreements agreements,
                              final PipelineOutputs pipelineOutputs,
                              final PipelineShapes pipelineShapes,
                              final SampleStreamTexts sampleStreamTexts,
                              final BuildRecords buildRecords,
                              final Consent consent,
                              final BuildPlan plan) {
        this.filterWrites = filterWrites;
        this.sources = sources;
        this.approvals = approvals;
        this.indexingRuns = indexingRuns;
        this.agreements = agreements;
        this.pipelineOutputs = pipelineOutputs;
        this.pipelineShapes = pipelineShapes;
        this.sampleStreamTexts = sampleStreamTexts;
        this.buildRecords = buildRecords;
        this.consent = consent;
        this.plan = plan;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(BuildPlan.onboardingLabel(NAME)
                             + "Create a processor filter for Stroom to process streams through the pipeline. Scope "
                             + "it to the sample stream ids, or to a feed's streams: for an events (or records) or "
                             + "indexing pipeline the user approves it in a form, then it is created enabled and "
                             + "processing starts. Any other pipeline (CEF output, reference data) is created "
                             + "disabled, for the user to enable in Stroom (the pipeline's Processors tab). A feed "
                             + "scope needs a created_after bound and is limited to the configured task count. "
                             + "Streams the pipeline already processed are refused: use reprocess_streams for those. "
                             + "An indexing pipeline whose earlier Events have since been superseded (the "
                             + "translation processed their raw streams again) still has their documents in its "
                             + "index: a drop_first reply comes before the approval, with what the cluster admin "
                             + "drops (a build's own Lucene index holding only those is emptied, its shards "
                             + "deleted, with no such step). An indexing pipeline reading Events needs "
                             + "source_pipeline_uuid, and its filter only selects Events from exactly that events "
                             + "pipeline. For an Elasticsearch indexing pipeline, the index template for its "
                             + "destination index must have been agreed with the user (propose_index_template, "
                             + "check_index_template); the user enables the filter only once it is committed to the "
                             + "cluster, so the index is created with it. A translation pipeline processes the "
                             + "build's own feeds, or the existing feeds the build works from (start_build feeds=): "
                             + "those streams are processed where they are, and their Events go to a test feed the "
                             + "server makes in the build (output_feed in the reply; promotion leaves it in the "
                             + "workspace). Sample filters run one task at a time. A disabled filter: give the user "
                             + "the reply's pipeline_link and what enabling does; once they say it is enabled, "
                             + "wait_for_processing. An enabled one: wait_for_processing with its filter_id.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(PIPELINE_UUID_ARG, "A pipeline this server created.")
                        .addProperty(STREAM_IDS_ARG, stringArraySchema("Process exactly these streams (the "
                                                                       + "sample). The default and safest "
                                                                       + "scope."))
                        .addStringProperty(FEED_ARG, "Or process a whole feed's streams of stream_type.")
                        .addStringProperty(STREAM_TYPE_ARG, "Stream type to process with a feed scope. Left out: "
                                                            + "Events for a pipeline that indexes Events, else the "
                                                            + "feed's own (Raw Events, Raw Reference, ...).")
                        .addStringProperty(CREATED_AFTER_ARG, "With a feed scope: only streams created after this "
                                                              + "ISO time. Required for a feed scope. Raised to the "
                                                              + "feed's creation time if earlier; refused if it "
                                                              + "selects none of the feed's streams still to "
                                                              + "process. To process the sample, give stream_ids "
                                                              + "instead.")
                        .addIntegerProperty(PRIORITY_ARG, "The filter's priority (" + MIN_PRIORITY + " to "
                                                          + MAX_PRIORITY + "; default "
                                                          + ProcessorFilterWrites.DEFAULT_PRIORITY + ").")
                        .addStringProperty(SOURCE_PIPELINE_UUID_ARG, SOURCE_PIPELINE_DESCRIPTION)
                        .addBooleanProperty(INDEX_DROPPED_ARG, INDEX_DROPPED_DESCRIPTION)
                        .addBooleanProperty(TEMPLATE_COMMITTED_BY_USER_ARG, TEMPLATE_COMMITTED_DESCRIPTION)
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
        // It creates a processor filter, which processes streams.
        return false;
    }

    @Override
    public boolean confirmsItself() {
        // The user approves a filter that runs in a form; any other is made disabled, for them to enable.
        return true;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .requires(AppPermission.MANAGE_PROCESSORS_PERMISSION)
                .unlockedBy(AppPermission.VIEW_DATA_PERMISSION)
                .checksDocuments(DocumentPermission.VIEW, DocumentPermission.EDIT)
                .note("Only for a pipeline in a build. Emptying a build's own Lucene index needs the Manage Index "
                      + "Shards permission. Reading why Elasticsearch refused earlier runs (their Error streams) "
                      + "needs the View Data permission: without it, the index is taken to hold what they sent.")
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        try {
            final Map<String, Object> result = create(call, context);
            return UserActionEntries.withActions(JsonResults.of(result), result);
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        } catch (final PipelineUnreadableException e) {
            return JsonResults.refused(e.getMessage());
        }
    }

    private Map<String, Object> create(final ToolCall call, final ToolCallContext context) {
        final String pipelineUuid = stringArg(call, PIPELINE_UUID_ARG);
        if (pipelineUuid == null) {
            throw new ToolRefusal("Give pipeline_uuid: a pipeline this server created.");
        }
        PipelineDoc pipeline = filterWrites.managedPipeline(context.session(), pipelineUuid);
        final DocRef ref = ProcessorFilterWrites.ref(pipeline);
        final List<Long> streamIds = StartOnboardingTool.streamIds(call, STREAM_IDS_ARG);
        final String feed = stringArg(call, FEED_ARG);
        if (streamIds.isEmpty() == (feed == null)) {
            throw new ToolRefusal("Give either stream_ids (the sample) or feed, not both");
        }
        final int priority = priority(call);
        if (!steppedClean(ref)) {
            throw new ToolRefusal("Pipeline '" + pipeline.getName() + "' has no clean step of its current code "
                                  + "recorded: step_sample (or step_records) over the sample streams until the "
                                  + "verdict is clean, then process. A change to its XSLT or converter since the "
                                  + "last clean step needs stepping again.");
        }
        final Optional<OutputFeeds.OutputFeed> output = sources.buildFeedsOnly(ref, streamIds, feed);
        if (!streamIds.isEmpty()) {
            sampleStreamTexts.refuseOlderThanFeed(streamIds);
        }
        final String createdAfter = stringArg(call, CREATED_AFTER_ARG);
        if (feed != null && createdAfter == null) {
            throw new ToolRefusal("A feed-wide filter needs created_after, so it does not reprocess the feed's "
                                  + "history");
        }
        String streamType = stringArg(call, STREAM_TYPE_ARG);
        if (feed != null && streamType == null) {
            streamType = indexesEvents(pipelineUuid)
                    ? ProcessorFilters.EVENTS
                    : filterWrites.feedStreamType(feed);
        }
        final ProcessingSources.EventsSource source = sources.eventsSource(call, context, pipeline,
                stringArg(call, SOURCE_PIPELINE_UUID_ARG), streamIds, streamType);
        final Scope scope = streamIds.isEmpty()
                ? feedScope(ref, feed, streamType, createdAfter, source.pipeline())
                : sampleScope(pipeline, streamIds);
        ExpressionOperator expression = scope.expression();
        String said = scope.said();
        if (source.pipeline() != null) {
            expression = ProcessorFilterWrites.allOf(List.of(expression,
                    ProcessorFilterWrites.pipelineTerm(source.pipeline())));
            said += ", only Events from pipeline '" + source.pipeline().getName() + "'";
        }
        final List<ProcessorFilter> filters = filterWrites.filters(ref);
        if (feed != null) {
            ProcessorFilterWrites.refuseDuplicate(filters, expression, scope.minMs());
        }
        final Map<String, Object> details = new LinkedHashMap<>();
        details.put("pipeline", pipeline.getName());
        details.put("scope", said);
        details.put("priority", priority);
        details.put("max tasks", scope.maxTasks() != 0
                ? scope.maxTasks()
                : "unlimited");
        output.ifPresent(feedOut -> details.put("events go to", "test feed " + feedOut.name() + " in the build (for "
                                                                + "another name, decline and have the agent "
                                                                + "create_feed test_feed=true with it)"));
        if (pipelineOutputs.sendsToKafka(pipelineUuid)) {
            // Stepping sends nothing; processing does: the destination (ArcSight, say) receives these records.
            details.put("sends", "each processed Event as a Kafka record, through the pipeline's KafkaConfig: the "
                                 + "destination (ArcSight, say) receives them");
        }
        final Map<String, Object> destination = agreements.elasticDestination(pipelineUuid).orElse(null);
        if (destination != null) {
            final ProcessingApprovals.Committed committed = approvals.committed(call, context, pipeline, destination,
                    NAME, ToolArgs.flag(call, TEMPLATE_COMMITTED_BY_USER_ARG));
            details.put(ProcessingApprovals.INDEX_TEMPLATE, committed.text());
            pipeline = committed.pipeline();
        }
        // A sample indexed into an index this pipeline already wrote to: the index starts fresh, dropped first
        // (asked for by the user). Not a feed's filter beside an earlier one (a backfill, then new data): those add
        // to the index.
        final DocRef lucene = indexingRuns.luceneIndex(pipelineUuid).orElse(null);
        boolean clear = false;
        if (!streamIds.isEmpty() && (destination != null || lucene != null) && !filters.isEmpty()) {
            final IndexingRuns.FreshIndex fresh = indexingRuns.freshIndex(pipeline, destination, lucene, filters);
            details.putAll(fresh.details());
            clear = fresh.empty();
        }
        final Map<String, Object> common = new LinkedHashMap<>();
        common.put("pipeline", pipeline.getName());
        common.put("scope", said);
        if (source.pipeline() != null) {
            common.put("events_from_pipeline", source.pipeline().getName());
        }
        output.ifPresent(feedOut -> {
            common.put("output_feed", feedOut.name());
            common.put("output_note", ProcessingApprovals.outputNote(feedOut));
        });
        final Run run = new Run(pipeline, ref, expression, priority, scope, destination, lucene, details, common,
                clear, streamIds);
        if (approvals.reprocessRuns(pipelineUuid)) {
            return approved(call, context, run);
        }
        // A pipeline that sends elsewhere (CEF to Kafka) or loads reference data: disabled (asked for by the user),
        // so there is no approval form: enabling it in Stroom is the user's approval.
        final boolean small = destination != null && indexingRuns.developmentBatch(pipelineUuid, true);
        final ProcessorFilter created = filterWrites.createFilter(ref, expression, priority, scope.maxTasks(),
                scope.minMs(), false);
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("filter_id", created.getId());
        result.putAll(common);
        if (small) {
            result.put("batch_size", IndexingRuns.BATCH_NOTE);
        }
        result.put("enabled", false);
        result.putAll(ProcessingApprovals.forTheUser(context.links(), ref, created.getId(), details,
                ProcessingApprovals.beforeActions(context.links(), pipeline, destination, lucene, details)));
        return plan.nextFor(context, ref, result);
    }

    // The build's sample, or a feed's streams, through an events or indexing pipeline: processed once the user
    // approves it (asked for by the user: filters made disabled were left for them to enable in Stroom, and in a
    // build the agent asked them to, between its steps). Every such pipeline is in a build (managedPipeline).
    private Map<String, Object> approved(final ToolCall call, final ToolCallContext context, final Run run) {
        final Map<String, Object> details = run.details();
        if (details.containsKey(IndexingRuns.ALREADY_INDEXED) && !ToolArgs.flag(call, INDEX_DROPPED_ARG)) {
            return plan.nextFor(context, run.ref(), ProcessingApprovals.dropFirst(context.links(), run.pipeline(),
                    run.destination(), run.lucene(), details, NAME));
        }
        String summary = run.streamIds().isEmpty()
                ? "Create processor filter and process the feed\nProcess " + run.common().get("scope") + " with '"
                  + run.pipeline().getName() + "'."
                : "Create processor filter and process the sample\nProcess " + run.streamIds().size() + " sample "
                  + "stream(s) with '" + run.pipeline().getName() + "'.";
        if (details.containsKey(IndexingRuns.ALREADY_INDEXED)) {
            summary += " Approve only once the index is dropped, as said below.";
        }
        if (details.containsKey(ProcessingApprovals.INDEX_TEMPLATE)) {
            summary += " Approve only once the agreed index template is committed to the cluster.";
        }
        consent.require(call, context, Consent.Request.approval(NAME, summary, details));
        if (run.clear()) {
            indexingRuns.deleteIndexShards(run.lucene().getUuid());
        }
        final boolean small = run.destination() != null
                              && indexingRuns.developmentBatch(run.ref().getUuid(), true);
        final ProcessorFilter created = filterWrites.createFilter(run.ref(), run.expression(), run.priority(),
                run.scope().maxTasks(), run.scope().minMs(), true);
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("filter_id", created.getId());
        result.putAll(run.common());
        if (small) {
            result.put("batch_size", IndexingRuns.BATCH_NOTE);
        }
        result.put("enabled", true);
        result.put("hint", "Processing has started: wait_for_processing with filter_id=" + created.getId() + ".");
        return plan.nextFor(context, run.ref(), result);
    }

    // Exactly the sample's streams, unless the pipeline already processed any.
    private Scope sampleScope(final PipelineDoc pipeline, final List<Long> streamIds) {
        final List<Long> done = filterWrites.alreadyProcessed(ProcessorFilterWrites.ref(pipeline), streamIds);
        if (!done.isEmpty()) {
            throw new ToolRefusal("Pipeline '" + pipeline.getName() + "' has already processed stream(s) "
                                  + PyText.repr(done) + ": use reprocess_streams (at most "
                                  + ProcessorFilterWrites.MAX_REPROCESS_STREAMS + " per call)");
        }
        return new Scope(ProcessorFilterWrites.anyOf(streamIds), "streams " + PyText.repr(streamIds),
                ProcessorFilterWrites.SAMPLE_MAX_TASKS, null);
    }

    // A feed's streams of the type, created after the bound.
    private Scope feedScope(final DocRef pipeline,
                            final String feed,
                            final String streamType,
                            final String createdAfter,
                            final DocRef source) {
        final long given = ProcessorFilterWrites.isoMs(createdAfter);
        final ProcessorFilterWrites.FeedBounds bounds = filterWrites.feedBounds(pipeline, feed, streamType, given);
        // With the source pipeline named (below), its Events are those meant, whatever feed they are in: no feed
        // condition (asked for by the user).
        final List<ExpressionItem> children = new ArrayList<>();
        if (source == null) {
            children.add(filterWrites.feedTerm(feed));
        }
        children.add(ProcessorFilterWrites.term(MetaFields.FIELD_TYPE, streamType));
        final String said = source != null
                ? streamType + " streams created after " + bounds.said()
                : "feed " + feed + " (" + streamType + ") created after " + bounds.said();
        return new Scope(ProcessorFilterWrites.allOf(children), said, ProcessorFilterWrites.MAX_FEED_FILTER_TASKS,
                bounds.minMs());
    }

    // Whether a feed scope's default stream type is Events: the pipeline indexes them.
    private boolean indexesEvents(final String pipelineUuid) {
        try {
            return "indexing".equals(pipelineShapes.shape(pipelineUuid).stage());
        } catch (final RuntimeException e) {
            return pipelineOutputs.isIndexing(pipelineUuid);
        }
    }

    private boolean steppedClean(final DocRef ref) {
        try {
            return buildRecords.steppedClean(ref);
        } catch (final RuntimeException e) {
            // Code that can't be read can't be known to have stepped clean.
            return false;
        }
    }

    private int priority(final ToolCall call) {
        if (rawArg(call, PRIORITY_ARG) == null) {
            return ProcessorFilterWrites.DEFAULT_PRIORITY;
        }
        final Integer given = intArg(call, PRIORITY_ARG);
        if (given == null || given < MIN_PRIORITY || given > MAX_PRIORITY) {
            throw new ToolRefusal("priority is a whole number from " + MIN_PRIORITY + " to " + MAX_PRIORITY);
        }
        return given;
    }


    // --------------------------------------------------------------------------------


    // What a filter selects, how the approval says it, its task limit and lower bound.
    private record Scope(ExpressionOperator expression, String said, int maxTasks, Long minMs) {

    }

    // Everything the run of a filter needs once the gates before it are passed.
    private record Run(PipelineDoc pipeline,
                       DocRef ref,
                       ExpressionOperator expression,
                       int priority,
                       Scope scope,
                       Map<String, Object> destination,
                       DocRef lucene,
                       Map<String, Object> details,
                       Map<String, Object> common,
                       boolean clear,
                       List<Long> streamIds) {

    }
}
