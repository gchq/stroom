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

package stroom.ai.impl.tools;

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.shared.AiToolCategory;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.json.JsonUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/// `explain_processing`: why processing isn't happening (or is slow) for a pipeline, a feed or a stream, or how
/// the cluster's processing stands and in what order its filters get work. It traces the chain from the cluster's
/// nodes and jobs through each processor filter's tracker, profile, tasks and priority ([ProcessingExplainer]),
/// reading state only.
///
/// Read as the user. It needs the Manage Processors permission, as the Processors tab does; job, node and claiming
/// state need Manage Jobs, Manage Nodes and View System Info, and a check the user can't see is reported as not
/// checked rather than left out. Filters are only those of pipelines the user can view, and streams only those of
/// feeds they can view.
class ExplainProcessingTool extends AbstractBuiltInTool {

    static final String NAME = "explain_processing";
    static final String PIPELINE_UUID_ARG = "pipeline_uuid";
    static final String FEED_ARG = "feed";
    static final String STREAM_ID_ARG = "stream_id";

    static final String NEEDS_MANAGE_PROCESSORS = "You need the Manage Processors permission to see processing, as "
                                                  + "the Processors tab does.";

    private final Provider<StroomProcessingFacts> factsProvider;
    private final ProcessorFilterAccess processorFilterAccess;
    private final DocumentAccess documentAccess;
    private final AiAuditLog auditLog;

    @Inject
    ExplainProcessingTool(final Provider<StroomProcessingFacts> factsProvider,
                          final ProcessorFilterAccess processorFilterAccess,
                          final DocumentAccess documentAccess,
                          final AiAuditLog auditLog) {
        this.factsProvider = factsProvider;
        this.processorFilterAccess = processorFilterAccess;
        this.documentAccess = documentAccess;
        this.auditLog = auditLog;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .requires(AppPermission.MANAGE_PROCESSORS_PERMISSION)
                .unlockedBy(AppPermission.MANAGE_JOBS_PERMISSION, AppPermission.MANAGE_NODES_PERMISSION,
                        AppPermission.VIEW_SYSTEM_INFO_PERMISSION)
                .checksDocuments(DocumentPermission.VIEW)
                .build();
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.PROCESSING;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("Explains why processing isn't happening, or is slow, and in what order filters get "
                             + "work: e.g. 'why is this pipeline (or feed, or stream) not processing?', 'why is "
                             + "this filter starved?', 'is processing healthy?'. Give one of pipeline_uuid, feed or "
                             + "stream_id, or none for the whole cluster. It traces the chain: the nodes (enabled, "
                             + "answering, the master), the processing settings, the jobs processing needs "
                             + "(Processor Task Creator, Data Processor, the reaper and heartbeat: enabled, on which "
                             + "nodes, task limits, last and next runs, asked of every node), each processor "
                             + "filter (enabled, its tracker, stream create time window, feed dependencies, "
                             + "polling), its processor profile (node group, periods, threads each node gets now), "
                             + "its tasks and where they wait (locked streams, limits), and its priority among the "
                             + "filters ahead of it. Each link is ok, blocking, slowing or not checked (the user "
                             + "lacks the permission to see it); 'answer' and 'first_blocker' say what stops it, "
                             + "and each finding's 'fix' what would help. Read-only: it changes nothing. It doesn't "
                             + "explain why a task that ran failed: Stroom keeps no reason for that.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(PIPELINE_UUID_ARG, "A pipeline (or analytic rule) whose processing to "
                                                              + "explain.")
                        .addStringProperty(FEED_ARG, "A feed's name: its newest stream's processing is explained.")
                        .addIntegerProperty(STREAM_ID_ARG, "A stream (meta) id whose processing to explain.")
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall request, final ToolCallContext context) {
        if (!processorFilterAccess.canManageProcessors()) {
            return ToolResult.text(NEEDS_MANAGE_PROCESSORS);
        }
        final String pipelineUuid = stringArg(request, PIPELINE_UUID_ARG);
        final String feed = stringArg(request, FEED_ARG);
        final Long streamId;
        try {
            streamId = streamId(request);
        } catch (final NumberFormatException e) {
            return ToolResult.error("stream_id must be a stream's id, a number.");
        }
        final long given = List.of(Optional.ofNullable(pipelineUuid), Optional.ofNullable(feed),
                        Optional.ofNullable(streamId)).stream()
                .filter(Optional::isPresent)
                .count();
        if (given > 1) {
            return ToolResult.error("Give one of pipeline_uuid, feed or stream_id (or none, for the whole cluster).");
        }
        final ProcessingExplainer explainer = new ProcessingExplainer(factsProvider.get());
        final Map<String, Object> result;
        if (pipelineUuid != null) {
            final Optional<DocRef> pipeline = documentAccess.findDocRef(pipelineUuid)
                    .filter(docRef -> PipelineDoc.TYPE.equals(docRef.getType())
                                      || AnalyticRuleDoc.TYPE.equals(docRef.getType()));
            if (pipeline.isEmpty()) {
                return ToolResult.error("No pipeline or analytic rule " + pipelineUuid + " that you can view.");
            }
            result = explainer.explainPipeline(pipeline.get());
            auditLog.documentsSearched(context.session(), "processing of " + pipeline.get().getName(),
                    List.of(pipeline.get()), Map.of(), "Processing explained by the " + NAME + " tool");
        } else if (feed != null) {
            result = explainer.explainFeed(feed);
            auditLog.streamsSearched(context.session(), "processing of feed " + feed, streamsOf(result), Map.of(),
                    "Processing explained by the " + NAME + " tool");
        } else if (streamId != null) {
            result = explainer.explainStream(streamId);
            auditLog.streamsSearched(context.session(), "processing of stream " + streamId, streamsOf(result),
                    Map.of(), "Processing explained by the " + NAME + " tool");
        } else {
            result = explainer.explainCluster();
            auditLog.documentsSearched(context.session(), "processing across the cluster", List.of(), Map.of(),
                    "Processing explained by the " + NAME + " tool");
        }
        return ToolResult.text(JsonUtil.writeValueAsString(result, true));
    }

    private static Long streamId(final ToolCall request) {
        final Object value = argsMap(request).get(STREAM_ID_ARG);
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return Long.parseLong(String.valueOf(value).strip());
    }

    // The stream the answer was about, if it found one, for the audit log.
    private static List<Long> streamsOf(final Map<String, Object> result) {
        if (result.get("stream") instanceof final Map<?, ?> stream && stream.get("id") instanceof final Number id) {
            return List.of(id.longValue());
        }
        return List.of();
    }
}
