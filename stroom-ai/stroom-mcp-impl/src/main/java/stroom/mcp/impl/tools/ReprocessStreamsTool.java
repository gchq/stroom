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
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.shared.ProcessorFilter;
import stroom.query.api.ExpressionOperator;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;

import jakarta.inject.Inject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// `reprocess_streams` - processes streams again through a pipeline under development, after changing it, at most
/// [ProcessorFilterWrites#MAX_REPROCESS_STREAMS] a call, one task at a time. Ported from the Stroom MCP server's
/// `tools/processing_writes.py`.
///
/// A new filter on the pipeline selects the streams by id, made through Stroom's processor filter service as the
/// user, as the data browser's Process action makes one with the pipeline chosen; Stroom marks the pipeline's
/// earlier outputs for those streams deleted once the new ones are written. For an events (or records) or indexing
/// pipeline the user approves it in a form and it is created enabled; any other pipeline's filter is created
/// disabled, for the user to enable.
class ReprocessStreamsTool extends AbstractBuiltInTool {

    static final String NAME = "reprocess_streams";
    static final String PIPELINE_UUID_ARG = "pipeline_uuid";
    static final String STREAM_IDS_ARG = "stream_ids";

    private final ProcessorFilterWrites filterWrites;
    private final ProcessingSources sources;
    private final ProcessingApprovals approvals;
    private final IndexingRuns indexingRuns;
    private final IndexTemplateAgreements agreements;
    private final SampleStreamTexts sampleStreamTexts;
    private final Consent consent;
    private final BuildPlan plan;

    @Inject
    ReprocessStreamsTool(final ProcessorFilterWrites filterWrites,
                         final ProcessingSources sources,
                         final ProcessingApprovals approvals,
                         final IndexingRuns indexingRuns,
                         final IndexTemplateAgreements agreements,
                         final SampleStreamTexts sampleStreamTexts,
                         final Consent consent,
                         final BuildPlan plan) {
        this.filterWrites = filterWrites;
        this.sources = sources;
        this.approvals = approvals;
        this.indexingRuns = indexingRuns;
        this.agreements = agreements;
        this.sampleStreamTexts = sampleStreamTexts;
        this.consent = consent;
        this.plan = plan;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(BuildPlan.onboardingLabel(NAME)
                             + "Process streams again through a pipeline under development, after changing it. At "
                             + "most " + ProcessorFilterWrites.MAX_REPROCESS_STREAMS + " streams per call, run one "
                             + "task at a time. Stroom marks the pipeline's earlier outputs for "
                             + "these streams deleted once the new ones are written; pass the returned filter_id to "
                             + "wait_for_processing so only the new outputs count. An events (or records) or "
                             + "indexing pipeline: the user approves it in a form, then its filter is created "
                             + "enabled and processing starts (for Elasticsearch indexing the approval also "
                             + "confirms the index template is applied on the cluster; an index the pipeline wrote "
                             + "to before gets a drop_first reply ahead of the approval, the user dropping it, "
                             + "except a build's own Lucene index, emptied once approved, its shards deleted). Any "
                             + "other pipeline (CEF output, reference data): the filter is created disabled, for "
                             + "the user to enable. Promoted pipelines are refused by the write guard: reprocessing "
                             + "production data is the user's.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(PIPELINE_UUID_ARG, "A pipeline this server built, after a change.")
                        .addProperty(STREAM_IDS_ARG, stringArraySchema("Streams it already processed, to process "
                                                                       + "again."))
                        .addStringProperty(CreateProcessorFilterTool.SOURCE_PIPELINE_UUID_ARG,
                                CreateProcessorFilterTool.SOURCE_PIPELINE_DESCRIPTION)
                        .addBooleanProperty(CreateProcessorFilterTool.INDEX_DROPPED_ARG,
                                CreateProcessorFilterTool.INDEX_DROPPED_DESCRIPTION)
                        .addBooleanProperty(CreateProcessorFilterTool.TEMPLATE_COMMITTED_BY_USER_ARG,
                                CreateProcessorFilterTool.TEMPLATE_COMMITTED_DESCRIPTION)
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
        // It creates a processor filter, which processes streams again.
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
            final Map<String, Object> result = reprocess(call, context);
            return UserActionEntries.withActions(JsonResults.of(result), result);
        } catch (final ToolRefusal e) {
            return JsonResults.refused(e);
        } catch (final PipelineUnreadableException e) {
            return JsonResults.refused(e.getMessage());
        }
    }

    private Map<String, Object> reprocess(final ToolCall call, final ToolCallContext context) {
        final String pipelineUuid = stringArg(call, PIPELINE_UUID_ARG);
        if (pipelineUuid == null) {
            throw new ToolRefusal("Give pipeline_uuid: a pipeline this server built, after a change.");
        }
        PipelineDoc pipeline = filterWrites.managedPipeline(context.session(), pipelineUuid);
        final DocRef ref = ProcessorFilterWrites.ref(pipeline);
        final List<Long> streamIds = StartOnboardingTool.streamIds(call, STREAM_IDS_ARG);
        final int limit = ProcessorFilterWrites.MAX_REPROCESS_STREAMS;
        if (streamIds.isEmpty() || streamIds.size() > limit) {
            throw new ToolRefusal("Give 1 to " + limit + " streams per call; order more once these finish");
        }
        final Set<Long> done = Set.copyOf(filterWrites.alreadyProcessed(ref, streamIds));
        final List<Long> fresh = streamIds.stream().filter(id -> !done.contains(id)).toList();
        if (!fresh.isEmpty()) {
            throw new ToolRefusal("Stream(s) " + PyText.repr(fresh) + " have not been processed by '"
                                  + pipeline.getName() + "': use create_processor_filter");
        }
        sources.buildFeedsOnly(ref, streamIds, null);
        sampleStreamTexts.refuseOlderThanFeed(streamIds);
        final ProcessingSources.EventsSource source = sources.eventsSource(call, context, pipeline,
                stringArg(call, CreateProcessorFilterTool.SOURCE_PIPELINE_UUID_ARG), streamIds,
                ProcessorFilters.EVENTS);
        final int maxTasks = ProcessorFilterWrites.REPROCESS_MAX_TASKS;
        final Map<String, Object> details = new LinkedHashMap<>();
        details.put("pipeline", pipeline.getName());
        details.put("streams", streamIds);
        details.put("max tasks", maxTasks);
        if (source.pipeline() != null) {
            details.put("only Events from pipeline", source.pipeline().getName());
        }
        details.put("earlier outputs", "superseded: Stroom marks them deleted once the new ones are written");
        ExpressionOperator expression = ProcessorFilterWrites.anyOf(streamIds);
        if (source.pipeline() != null) {
            expression = ProcessorFilterWrites.allOf(List.of(expression,
                    ProcessorFilterWrites.pipelineTerm(source.pipeline())));
        }
        final Map<String, Object> destination = agreements.elasticDestination(pipelineUuid).orElse(null);
        if (destination != null) {
            final ProcessingApprovals.Committed committed = approvals.committed(call, context, pipeline, destination,
                    NAME, ToolArgs.flag(call,
                            CreateProcessorFilterTool.TEMPLATE_COMMITTED_BY_USER_ARG));
            details.put(ProcessingApprovals.INDEX_TEMPLATE, committed.text());
            pipeline = committed.pipeline();
        }
        // Stroom's purgeOnReprocess does not apply to a second filter on the same streams, nor does it remove a
        // Lucene index's documents (seen: an eval build's index held each event twice after its indexing XSLT was
        // fixed and the stream reprocessed), and the tools have no Elasticsearch access: the index is dropped first
        // and starts fresh.
        final DocRef lucene = indexingRuns.luceneIndex(pipelineUuid).orElse(null);
        final List<ProcessorFilter> filters = filterWrites.filters(ref);
        final IndexingRuns.FreshIndex freshIndex = indexingRuns.freshIndex(pipeline, destination, lucene, filters);
        final boolean runs = approvals.reprocessRuns(pipelineUuid);
        final boolean clear = freshIndex.empty() && runs;
        details.putAll(freshIndex.details());
        // Reprocessing an events or indexing pipeline while it is developed in a build runs once the user approves
        // it (asked for by the user); every other filter is made disabled, for the user to enable.
        if (!runs) {
            final boolean small = destination != null && indexingRuns.developmentBatch(pipelineUuid, true);
            final ProcessorFilter created = filterWrites.createFilter(ref, expression,
                    ProcessorFilterWrites.DEFAULT_PRIORITY, maxTasks, null, false);
            final Map<String, Object> result = result(created, pipeline, streamIds, maxTasks, false, small);
            result.putAll(ProcessingApprovals.forTheUser(context.links(), ref, created.getId(), details,
                    ProcessingApprovals.beforeActions(context.links(), pipeline, destination, lucene, details)));
            return plan.nextFor(context, ref, result);
        }
        if (details.containsKey(IndexingRuns.ALREADY_INDEXED)
            && !ToolArgs.flag(call, CreateProcessorFilterTool.INDEX_DROPPED_ARG)) {
            return plan.nextFor(context, ref, ProcessingApprovals.dropFirst(context.links(), pipeline, destination,
                    lucene, details, NAME));
        }
        String summary = "Create processor filter and reprocess streams\nReprocess " + streamIds.size()
                         + " stream(s) with '" + pipeline.getName() + "'.";
        if (details.containsKey(IndexingRuns.ALREADY_INDEXED)) {
            summary += " Approve only once the index is dropped, as said below.";
        }
        consent.require(call, context, Consent.Request.approval(NAME, summary, details).withDocuments(ref,
                source.pipeline()));
        if (clear) {
            indexingRuns.deleteIndexShards(lucene.getUuid());
        }
        final boolean small = destination != null && indexingRuns.developmentBatch(pipelineUuid, true);
        final ProcessorFilter created = filterWrites.createFilter(ref, expression,
                ProcessorFilterWrites.DEFAULT_PRIORITY, maxTasks, null, true);
        final Map<String, Object> result = result(created, pipeline, streamIds, maxTasks, true, small);
        result.put("hint", "wait_for_processing with filter_id=" + created.getId() + " so only this run's outputs "
                           + "count.");
        return plan.nextFor(context, ref, result);
    }

    private static Map<String, Object> result(final ProcessorFilter created,
                                              final PipelineDoc pipeline,
                                              final List<Long> streamIds,
                                              final int maxTasks,
                                              final boolean enabled,
                                              final boolean small) {
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("filter_id", created.getId());
        result.put("pipeline", pipeline.getName());
        result.put("streams", streamIds);
        result.put("max_tasks", maxTasks);
        result.put("enabled", enabled);
        if (small) {
            result.put("batch_size", IndexingRuns.BATCH_NOTE);
        }
        return result;
    }
}
