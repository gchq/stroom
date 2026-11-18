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
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.FeedAccess;
import stroom.ai.impl.tools.ProcessorFilterAccess;
import stroom.ai.impl.tools.ProcessorTaskFinder;
import stroom.ai.impl.tools.StreamSummaries;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolPermissions;
import stroom.ai.impl.tools.ToolResult;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.shared.AiToolCategory;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.api.ProcessorTaskService;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskFields;
import stroom.processor.shared.QueryData;
import stroom.processor.shared.TaskStatus;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// `processing_status` - a pipeline's processor filters and their progress: enabled, priority, what they select
/// and the feeds that is (through dictionaries and stream ids too), tracker status and counts, and processing
/// tasks by status, so whether processing has finished is known.
///
/// Read as the user: the filters and their tasks need the Manage Processors permission and View on the pipeline,
/// as the Processors tab does, and only tasks of feeds the user may view are counted. Each filter's tasks are read
/// by the filter (on a busy instance, a page of every task in the system may not include them), reusing
/// processor_tasks' paged reading.
class ProcessingStatusTool extends AbstractBuiltInTool {

    static final String NAME = "processing_status";
    static final String PIPELINE_UUID_ARG = "pipeline_uuid";
    /// The most tasks of one filter read.
    static final int MAX_TASKS_PER_FILTER = 1_000;
    private static final Set<TaskStatus> ENDED = Set.of(TaskStatus.COMPLETE, TaskStatus.FAILED,
            TaskStatus.DELETED);

    private final ProcessorFilterAccess processorFilterAccess;
    private final DocumentAccess documentAccess;
    private final FeedAccess feedAccess;
    private final Provider<ProcessorTaskService> processorTaskServiceProvider;
    private final PipelineFeeds pipelineFeeds;

    @Inject
    ProcessingStatusTool(final ProcessorFilterAccess processorFilterAccess,
                         final DocumentAccess documentAccess,
                         final FeedAccess feedAccess,
                         final Provider<ProcessorTaskService> processorTaskServiceProvider,
                         final PipelineFeeds pipelineFeeds) {
        this.processorFilterAccess = processorFilterAccess;
        this.documentAccess = documentAccess;
        this.feedAccess = feedAccess;
        this.processorTaskServiceProvider = processorTaskServiceProvider;
        this.pipelineFeeds = pipelineFeeds;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("Processor filters for a pipeline and their progress: enabled, priority, what they "
                             + "select and the feeds that is (through dictionaries and stream ids too), tracker "
                             + "status and counts, and processor tasks by status. Use it to see whether processing "
                             + "has finished.")
                .parameters(ObjectSchema.builder()
                        .addStringProperty(PIPELINE_UUID_ARG, "Pipeline whose processor filters to report.")
                        .required(PIPELINE_UUID_ARG)
                        .build())
                .build();
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.PROCESSING;
    }

    @Override
    public ToolPermissions permissions() {
        return ToolPermissions.builder()
                .requires(AppPermission.MANAGE_PROCESSORS_PERMISSION)
                .checksDocuments(DocumentPermission.VIEW)
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall call, final ToolCallContext context) {
        final String pipelineUuid = stringArg(call, PIPELINE_UUID_ARG);
        if (pipelineUuid == null) {
            return JsonResults.refused("Error: give pipeline_uuid, the pipeline whose processor filters to report.");
        }
        final DocRef pipeline = documentAccess.findDocRef(pipelineUuid)
                .filter(docRef -> PipelineDoc.TYPE.equals(docRef.getType()))
                .orElse(null);
        final ProcessorFilterAccess.Filters found = processorFilterAccess.filtersOf(pipeline);
        if (found.refused() != null) {
            return JsonResults.refused("Error: Stroom refused to list this pipeline's processor filters: "
                                    + found.refused() + ".");
        }
        final Map<String, Object> result = status(pipelineUuid, found.filters());
        return JsonResults.of(result);
    }

    // Whether no filter has finished and none has a task processing: nothing is moving.
    private static boolean noProgress(final List<Map<String, Object>> filters) {
        return filters.stream().noneMatch(filter -> Boolean.TRUE.equals(filter.get("finished"))
                                                    || filter.get("tasks") instanceof final Map<?, ?> tasks
                                                       && tasks.containsKey(TaskStatus.PROCESSING.name()));
    }

        /// @return The status of the filters.
    Map<String, Object> status(final String pipelineUuid, final List<ProcessorFilter> filters) {
        final Map<Integer, Map<String, Integer>> byFilter = new HashMap<>();
        String tasksUnreadable = null;
        final Map<String, Boolean> feedViews = new HashMap<>();
        for (final ProcessorFilter filter : filters) {
            // Each filter's own tasks: on a busy instance a page of every task in the system may not include them.
            final ExpressionOperator expression = ExpressionOperator.builder().op(Op.AND)
                    .addIdTerm(ProcessorTaskFields.PROCESSOR_FILTER_ID, Condition.EQUALS, filter.getId())
                    .build();
            final Map<String, Integer> counts = new LinkedHashMap<>();
            try {
                final List<ProcessorTask> tasks = ProcessorTaskFinder.find(processorTaskServiceProvider.get(),
                        expression, MAX_TASKS_PER_FILTER, Integer.MAX_VALUE,
                        task -> feedViews.computeIfAbsent(NullSafe.string(task.getFeedName()),
                                feedAccess::canView)).visible();
                tasks.forEach(task -> counts.merge(NullSafe.getOrElse(task.getStatus(), TaskStatus::name, "null"),
                        1, Integer::sum));
            } catch (final RuntimeException e) {
                // The filters are still worth having without their tasks.
                tasksUnreadable = StreamSummaries.reason(e);
            }
            byFilter.put(filter.getId(), counts);
        }
        final List<Map<String, Object>> result = new ArrayList<>();
        for (final ProcessorFilter filter : filters) {
            final ProcessorFilterTracker tracker = filter.getProcessorFilterTracker();
            final Map<String, Integer> counts = byFilter.getOrDefault(filter.getId(), Map.of());
            final int outstanding = counts.entrySet().stream()
                    .filter(entry -> ENDED.stream().noneMatch(status -> status.name().equals(entry.getKey())))
                    .mapToInt(Map.Entry::getValue)
                    .sum();
            final Map<String, Object> trackerMap = new LinkedHashMap<>();
            trackerMap.put("status", NullSafe.get(tracker, ProcessorFilterTracker::getStatus, Enum::name));
            trackerMap.put("streams_matched", NullSafe.get(tracker, ProcessorFilterTracker::getMetaCount));
            trackerMap.put("events", NullSafe.get(tracker, ProcessorFilterTracker::getEventCount));
            trackerMap.put("message", NullSafe.get(tracker, ProcessorFilterTracker::getMessage));
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", filter.getId());
            entry.put("enabled", filter.isEnabled());
            entry.put("priority", filter.getPriority());
            entry.put("selects", PipelineFeeds.terms(NullSafe.get(filter.getQueryData(), QueryData::getExpression)));
            entry.put("tracker", trackerMap);
            entry.put("tasks", counts);
            entry.put("finished", !counts.isEmpty() && outstanding == 0);
            result.add(entry);
        }
        final Map<String, Object> read = pipelineFeeds.pipelineFeeds(pipelineUuid, 0, filters);
        final List<?> described = read.get("filters") instanceof final List<?> list
                ? list
                : List.of();
        for (int i = 0; i < Math.min(result.size(), described.size()); i++) {
            final Map<?, ?> filterRead = (Map<?, ?>) described.get(i);
            result.get(i).put("feeds", filterRead.get("feeds"));
            for (final String key : List.of("feeds_from_dictionaries", "output_of")) {
                if (filterRead.containsKey(key)) {
                    result.get(i).put(key, filterRead.get(key));
                }
            }
        }
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("pipeline_uuid", pipelineUuid);
        out.put("filters", result);
        out.put("feeds", read.getOrDefault("feeds", List.of()));
        if (tasksUnreadable != null) {
            out.put("tasks_unreadable", tasksUnreadable);
        }
        out.put("hint", result.isEmpty()
                ? "No processor filters for this pipeline."
                : noProgress(result)
                        ? "No filter has finished or has a task processing. If that doesn't change, "
                          + "explain_processing pipeline_uuid=" + pipelineUuid + " says why (the jobs and nodes, "
                          + "each filter's tracker and profile, where its tasks wait, and the filters ahead of it)."
                        : null);
        return out;
    }
}
