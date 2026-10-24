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

import stroom.ai.impl.tools.FeedAccess;
import stroom.ai.impl.tools.ProcessorTaskFinder;
import stroom.ai.impl.tools.PyText;
import stroom.ai.impl.tools.StreamReader;
import stroom.docref.DocRef;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.Status;
import stroom.processor.api.ProcessorTaskService;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskFields;
import stroom.processor.shared.QueryData;
import stroom.processor.shared.TaskStatus;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.date.DateUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/// What processing a pipeline's filters has done, or will do, with given input streams: the outputs each has, the
/// tasks that have ended or are still to, and why nothing will process one (no filter selects it, or the filters
/// that would are disabled). Ported from the helpers of the Stroom MCP server's `tools/processing_writes.py` that
/// `wait_for_processing` is made of (`_outputs`, `_tasks_ended`, `_selects`, `_will_process`, `_waiting_on`,
/// `_unselected` and `_iso`), for its write tools (`reprocess_streams`, `create_processor_filter`) to share.
///
/// Read as the user: streams only of feeds the user may view, and tasks only of those feeds (processing tasks need
/// the Manage Processors permission, which the caller has checked by listing the pipeline's filters).
class StreamProcessing {

    /// The most streams produced from one input stream read.
    static final int MAX_OUTPUTS = 100;
    /// The most task rows of one filter read.
    static final int MAX_TASKS = 1_000;
    private static final Set<TaskStatus> ENDED = Set.of(TaskStatus.COMPLETE, TaskStatus.FAILED);

    private final StreamReader streamReader;
    private final FeedAccess feedAccess;
    private final Provider<ProcessorTaskService> processorTaskServiceProvider;

    /// @param streamReader                 Finds streams, as the user.
    /// @param feedAccess                   Says which feeds' tasks the user may see.
    /// @param processorTaskServiceProvider Finds processing tasks.
    @Inject
    StreamProcessing(final StreamReader streamReader,
                     final FeedAccess feedAccess,
                     final Provider<ProcessorTaskService> processorTaskServiceProvider) {
        this.streamReader = streamReader;
        this.feedAccess = feedAccess;
        this.processorTaskServiceProvider = processorTaskServiceProvider;
    }

    /// @param rawId        An input stream.
    /// @param pipelineUuid The pipeline that processes it.
    /// @param filterId     Only the outputs of this processor filter, or null for any of the pipeline's.
    /// @return The streams the pipeline produced from it that are not deleted (Events, Error and so on).
    List<Meta> outputs(final long rawId, final String pipelineUuid, final Integer filterId) {
        return streamReader.children(rawId, MAX_OUTPUTS).stream()
                .map(MetaRow::getMeta)
                .filter(Objects::nonNull)
                .filter(meta -> Objects.equals(meta.getPipelineUuid(), pipelineUuid)
                                && meta.getStatus() != Status.DELETED
                                && (filterId == null || Objects.equals(meta.getProcessorFilterId(), filterId)))
                .toList();
    }

    /// @param filterIds Processor filters.
    /// @param streamIds Input streams.
    /// @return The streams these filters have tasks for that ended (complete or failed), and those with a task that
    /// hasn't ended yet (created, queued, being processed).
    Tasks tasks(final List<Integer> filterIds, final List<Long> streamIds) {
        final Set<Long> ended = new TreeSet<>();
        final Set<Long> open = new TreeSet<>();
        final Map<String, Boolean> feedViews = new HashMap<>();
        for (final Integer filterId : filterIds) {
            final ExpressionOperator.Builder streams = ExpressionOperator.builder().op(Op.OR);
            streamIds.forEach(id -> streams.addIdTerm(ProcessorTaskFields.META_ID, Condition.EQUALS, id));
            final ExpressionOperator expression = ExpressionOperator.builder().op(Op.AND)
                    .addIdTerm(ProcessorTaskFields.PROCESSOR_FILTER_ID, Condition.EQUALS, filterId)
                    .addOperator(streams.build())
                    .build();
            final List<ProcessorTask> tasks = ProcessorTaskFinder.find(processorTaskServiceProvider.get(),
                    expression, MAX_TASKS, Integer.MAX_VALUE,
                    task -> feedViews.computeIfAbsent(NullSafe.string(task.getFeedName()), feedAccess::canView))
                    .visible();
            tasks.stream()
                    .filter(task -> task.getMetaId() != null)
                    .forEach(task -> (ENDED.contains(task.getStatus())
                            ? ended
                            : open).add(task.getMetaId()));
        }
        return new Tasks(ended, open);
    }

    /// @param streamIds Streams.
    /// @return The meta of those the user may view, by id.
    List<Meta> metas(final List<Long> streamIds) {
        final List<Meta> metas = new ArrayList<>();
        for (final Long streamId : streamIds) {
            streamReader.metaRow(streamId).map(MetaRow::getMeta).ifPresent(metas::add);
        }
        metas.sort(Comparator.comparingLong(Meta::getId));
        return metas;
    }

    /// The filters that would process these streams but are disabled, and those whose processor is disabled:
    /// both empty when something enabled will process them.
    ///
    /// @param filters A pipeline's processor filters.
    /// @param metas   Streams with no output yet.
    /// @return The filters waiting on the user.
    static Waiting waitingOn(final List<ProcessorFilter> filters, final List<Meta> metas) {
        final Set<Integer> waiting = new TreeSet<>();
        for (final Meta meta : metas) {
            final List<ProcessorFilter> selecting = filters.stream()
                    .filter(filter -> selectsInTime(filter, meta))
                    .toList();
            if (!selecting.isEmpty() && selecting.stream().noneMatch(filter -> filter.isEnabled()
                                                                               && processorEnabled(filter))) {
                selecting.forEach(filter -> waiting.add(filter.getId()));
            }
        }
        final List<Integer> stopped = new ArrayList<>();
        final List<Integer> disabled = new ArrayList<>();
        for (final Integer id : waiting) {
            final ProcessorFilter filter = filters.stream()
                    .filter(candidate -> Objects.equals(candidate.getId(), id))
                    .findFirst()
                    .orElseThrow();
            if (!processorEnabled(filter)) {
                stopped.add(id);
            }
            if (!filter.isEnabled()) {
                disabled.add(id);
            }
        }
        return new Waiting(disabled, stopped);
    }

    /// Why no enabled filter of the pipeline will process these streams.
    ///
    /// Seen (Gemma, VS Code): a feed-wide filter created after its sample arrived; the agent waited out three
    /// timeouts of "tasks still running" for a filter whose tracker had matched nothing.
    ///
    /// @param filters A pipeline's processor filters.
    /// @param metas   Streams with no output yet.
    /// @return A problem for each stream no enabled filter will process; empty when one will (or might).
    static List<String> unselected(final List<ProcessorFilter> filters, final List<Meta> metas) {
        final List<String> problems = new ArrayList<>();
        for (final Meta meta : metas) {
            final long created = meta.getCreateMs();
            List<String> reasons = new ArrayList<>();
            for (final ProcessorFilter filter : filters) {
                final ExpressionOperator expression = expressionOf(filter);
                final Long earliest = filter.getMinMetaCreateTimeMs();
                final Long latest = filter.getMaxMetaCreateTimeMs();
                final List<String> terms = PipelineFeeds.terms(expression);
                final String selects = terms.isEmpty()
                        ? "everything"
                        : String.join(", ", terms);
                if (!selects(expression, meta)) {
                    reasons.add("filter " + filter.getId() + " selects " + selects);
                } else if (created != 0 && isSet(earliest) && created < earliest) {
                    reasons.add("filter " + filter.getId() + " selects only streams created after " + iso(earliest));
                } else if (created != 0 && isSet(latest) && created > latest) {
                    reasons.add("filter " + filter.getId() + " selects only streams created before " + iso(latest));
                } else if (!filter.isEnabled()) {
                    reasons.add("filter " + filter.getId() + " is disabled");
                } else {
                    reasons = List.of();
                    break;
                }
            }
            if (!reasons.isEmpty()) {
                problems.add("No processor filter of this pipeline will process stream " + meta.getId() + " ("
                             + meta.getFeedName() + ", " + meta.getTypeName() + ", created " + (created != 0
                        ? iso(created)
                        : "at an unknown time") + "): " + String.join("; ", reasons));
            }
        }
        return problems;
    }

    /// Whether a filter's expression selects the stream, for the terms the tools write; any other term counts as
    /// selecting it, so a stream is only ever reported unselected when it plainly is.
    ///
    /// @param expression A processor filter's expression, or null.
    /// @param meta       A stream.
    /// @return True if it selects (or may select) the stream.
    static boolean selects(final ExpressionItem expression, final Meta meta) {
        if (expression == null) {
            return true;
        }
        if (expression instanceof final ExpressionOperator operator) {
            final List<Boolean> results = NullSafe.list(operator.getChildren()).stream()
                    .filter(ExpressionItem::enabled)
                    .map(child -> selects(child, meta))
                    .toList();
            final Op op = Objects.requireNonNullElse(operator.getOp(), Op.AND);
            if (op == Op.NOT || results.isEmpty()) {
                return true;
            }
            return op == Op.OR
                    ? results.contains(true)
                    : !results.contains(false);
        }
        if (!(expression instanceof final ExpressionTerm term)) {
            return true;
        }
        final String field = term.getField();
        final Condition condition = term.getCondition();
        final String value = String.valueOf(term.getValue());
        if (condition == Condition.EQUALS && "Id".equals(field)) {
            return String.valueOf(meta.getId()).equals(value);
        }
        if (condition == Condition.EQUALS && "Feed".equals(field)) {
            // Stroom matches feed names whatever their case.
            return lower(meta.getFeedName()).equals(lower(value));
        }
        if (condition == Condition.IS_DOC_REF && "Feed".equals(field)) {
            return lower(meta.getFeedName()).equals(lower(NullSafe.get(term.getDocRef(), DocRef::getName)));
        }
        if (condition == Condition.EQUALS && "Type".equals(field)) {
            return Objects.equals(meta.getTypeName(), value);
        }
        if (condition == Condition.IS_DOC_REF && "Pipeline".equals(field)) {
            return Objects.equals(meta.getPipelineUuid(), NullSafe.get(term.getDocRef(), DocRef::getUuid));
        }
        return true;
    }

    /// @param filter A processor filter.
    /// @param meta   A stream.
    /// @return Whether the filter, enabled, selects the stream, its creation time included.
    static boolean willProcess(final ProcessorFilter filter, final Meta meta) {
        return filter.isEnabled() && selectsInTime(filter, meta);
    }

    /// @param ms A time, in milliseconds since the epoch.
    /// @return It as Stroom writes a time, e.g. `2026-10-09T22:06:40.000Z`.
    static String iso(final long ms) {
        return DateUtil.createNormalDateTimeString(ms);
    }

    // Whether the filter selects the stream, its creation time included, enabled or not.
    private static boolean selectsInTime(final ProcessorFilter filter, final Meta meta) {
        final long created = meta.getCreateMs();
        final Long earliest = filter.getMinMetaCreateTimeMs();
        final Long latest = filter.getMaxMetaCreateTimeMs();
        return selects(expressionOf(filter), meta)
               && !(created != 0 && isSet(earliest) && created < earliest)
               && !(created != 0 && isSet(latest) && created > latest);
    }

    // A processor missing from the filter counts as enabled.
    private static boolean processorEnabled(final ProcessorFilter filter) {
        return Optional.ofNullable(filter.getProcessor()).map(Processor::isEnabled).orElse(true);
    }

    private static ExpressionOperator expressionOf(final ProcessorFilter filter) {
        return NullSafe.get(filter.getQueryData(), QueryData::getExpression);
    }

    // As Python reads a time limit: zero or none is no limit.
    private static boolean isSet(final Long ms) {
        return ms != null && ms != 0;
    }

    private static String lower(final String text) {
        return NullSafe.string(text).toLowerCase(Locale.ROOT);
    }


    // --------------------------------------------------------------------------------


    /// The filters that would process some streams, waiting for the user to enable them.
    ///
    /// @param disabled The filters that are disabled.
    /// @param stopped  The filters whose processor is disabled.
    record Waiting(List<Integer> disabled, List<Integer> stopped) {

        /// @return What the user is to enable, e.g. `filter(s) [775]`, or "" when nothing is waiting.
        String what() {
            final List<String> parts = new ArrayList<>();
            if (!disabled.isEmpty()) {
                parts.add("filter(s) " + PyText.repr(disabled));
            }
            if (!stopped.isEmpty()) {
                parts.add("the processor of filter(s) " + PyText.repr(stopped) + " (the pipeline's "
                          + "processor row, above its filters)");
            }
            return String.join(" and ", parts);
        }
    }

    /// The tasks of some filters for some input streams.
    ///
    /// @param ended The streams with a task that ended (complete or failed).
    /// @param open  The streams with a task that hasn't ended yet.
    record Tasks(Set<Long> ended, Set<Long> open) {

    }
}
