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

package stroom.ai.impl.tools.streams;

import stroom.ai.impl.tools.processing.ProcessorTasksTool;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorTaskService;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorTask;
import stroom.processor.shared.ProcessorTaskFields;
import stroom.processor.shared.QueryData;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.security.api.SecurityContext;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/// Which of an index's feeding (indexing) pipelines indexed each Events stream (decided with the user 2026-11-25),
/// for a timeline reading a thing the pipelines' plans put in different fields. Each distinct stream is traced once:
/// by its processing tasks where they still exist (exact: the task ran that pipeline on it), else by the candidate
/// pipelines' processor filters matched against its meta (feed, type, producing pipeline), as Stroom matches a
/// filter, by a stream search with the filter's expression.
///
/// The candidates are the caller's: only pipelines feeding the index whose plans the analyst can view. Tasks and
/// filters need the Manage Processors permission, so they are read as the processing user, as Stroom's own
/// `ProcessorFilterDependencies` reads filters; that read only computes the link from a stream to a candidate: the
/// result names nothing else (no filter, feed or other pipeline), so nothing the analyst can't view is given. A
/// stream two candidates match is left untraced (ambiguous), for the caller to read otherwise.
public class IndexingTrace {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexingTrace.class);

    /// The most streams asked about in one search.
    static final int BATCH = 500;
    /// The most task rows read for one batch of streams.
    static final int MAX_TASK_ROWS = 5_000;

    private final SecurityContext securityContext;
    private final Provider<ProcessorTaskService> processorTaskServiceProvider;
    private final Provider<ProcessorFilterService> processorFilterServiceProvider;
    private final Provider<MetaService> metaServiceProvider;

    /// @param securityContext                Runs the reads as the processing user.
    /// @param processorTaskServiceProvider   Finds the streams' processing tasks.
    /// @param processorFilterServiceProvider Finds the candidate pipelines' processor filters.
    /// @param metaServiceProvider            Matches a filter's expression against the streams' meta.
    @Inject
    public IndexingTrace(final SecurityContext securityContext,
                         final Provider<ProcessorTaskService> processorTaskServiceProvider,
                         final Provider<ProcessorFilterService> processorFilterServiceProvider,
                         final Provider<MetaService> metaServiceProvider) {
        this.securityContext = securityContext;
        this.processorTaskServiceProvider = processorTaskServiceProvider;
        this.processorFilterServiceProvider = processorFilterServiceProvider;
        this.metaServiceProvider = metaServiceProvider;
    }

    /// Traces each stream to the candidate pipeline that indexed it.
    ///
    /// @param streamIds  The Events streams, e.g. the distinct `StreamId`s of events read; duplicates and nulls
    ///                   are ignored.
    /// @param candidates The pipelines feeding the index whose plans the analyst can view.
    /// @return Each stream traced, by its tasks or by the filters, and those matched by two or more candidates.
    public Traced trace(final Collection<Long> streamIds, final Collection<DocRef> candidates) {
        final Set<Long> wanted = new LinkedHashSet<>();
        NullSafe.collection(streamIds).stream().filter(Objects::nonNull).forEach(wanted::add);
        final Map<String, DocRef> byUuid = new LinkedHashMap<>();
        NullSafe.collection(candidates).stream()
                .filter(candidate -> candidate != null && candidate.getUuid() != null)
                .forEach(candidate -> byUuid.putIfAbsent(candidate.getUuid(), candidate));
        if (wanted.isEmpty() || byUuid.isEmpty()) {
            return Traced.NONE;
        }
        try {
            return securityContext.asProcessingUserResult(() -> traceAsProcessingUser(wanted, byUuid));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("The streams' indexing pipelines weren't traced: {}",
                    e.getMessage()), e);
            return Traced.NONE;
        }
    }

    private Traced traceAsProcessingUser(final Set<Long> wanted, final Map<String, DocRef> byUuid) {
        final Map<Long, Set<String>> fromTasks = new LinkedHashMap<>();
        for (final List<Long> batch : batches(wanted)) {
            tasks(batch, byUuid.keySet(), fromTasks);
        }
        final Map<Long, DocRef> byTasks = new LinkedHashMap<>();
        final Set<Long> ambiguous = new LinkedHashSet<>();
        final Set<Long> left = new LinkedHashSet<>();
        for (final Long stream : wanted) {
            final Set<String> pipelines = fromTasks.getOrDefault(stream, Set.of());
            if (pipelines.size() == 1) {
                byTasks.put(stream, byUuid.get(pipelines.iterator().next()));
            } else if (pipelines.size() > 1) {
                ambiguous.add(stream);
            } else {
                left.add(stream);
            }
        }
        final Map<Long, Set<String>> fromFilters = new LinkedHashMap<>();
        if (!left.isEmpty()) {
            for (final DocRef candidate : byUuid.values()) {
                for (final ProcessorFilter filter : filters(candidate)) {
                    for (final List<Long> batch : batches(left)) {
                        for (final Long stream : matching(filter, batch)) {
                            fromFilters.computeIfAbsent(stream, k -> new LinkedHashSet<>()).add(candidate.getUuid());
                        }
                    }
                }
            }
        }
        final Map<Long, DocRef> byFilters = new LinkedHashMap<>();
        for (final Long stream : left) {
            final Set<String> pipelines = fromFilters.getOrDefault(stream, Set.of());
            if (pipelines.size() == 1) {
                byFilters.put(stream, byUuid.get(pipelines.iterator().next()));
            } else if (pipelines.size() > 1) {
                ambiguous.add(stream);
            }
        }
        return new Traced(byTasks, byFilters, ambiguous);
    }

    // The candidate pipelines each of the streams' tasks ran.
    private void tasks(final List<Long> batch,
                       final Set<String> candidates,
                       final Map<Long, Set<String>> out) {
        final ExpressionOperator expression = ExpressionOperator.builder().op(Op.AND)
                .addTerm(ProcessorTaskFields.META_ID.getFldName(), Condition.IN, ProcessorTasksTool.ids(batch))
                .build();
        int read = 0;
        while (read < MAX_TASK_ROWS) {
            final ExpressionCriteria criteria = new ExpressionCriteria(expression);
            criteria.setPageRequest(new PageRequest(read, BATCH));
            final List<ProcessorTask> page = NullSafe.list(NullSafe.get(processorTaskServiceProvider.get()
                    .find(criteria), ResultPage::getValues));
            for (final ProcessorTask task : page) {
                final String pipeline = NullSafe.get(task, ProcessorTask::getProcessorFilter,
                        ProcessorFilter::getPipelineUuid);
                if (task.getMetaId() != null && pipeline != null && candidates.contains(pipeline)) {
                    out.computeIfAbsent(task.getMetaId(), k -> new LinkedHashSet<>()).add(pipeline);
                }
            }
            read += page.size();
            if (page.size() < BATCH) {
                break;
            }
        }
    }

    // The pipeline's processor filters, not deleted.
    private List<ProcessorFilter> filters(final DocRef pipeline) {
        return NullSafe.list(NullSafe.get(processorFilterServiceProvider.get().find(pipeline),
                        ResultPage::getValues)).stream()
                .filter(filter -> filter != null && !filter.isDeleted()
                                  && pipeline.getUuid().equals(filter.getPipelineUuid()))
                .toList();
    }

    // The streams of the batch the filter selects: a stream search with its expression and created-time bounds.
    private List<Long> matching(final ProcessorFilter filter, final List<Long> batch) {
        final ExpressionOperator.Builder builder = ExpressionOperator.builder().op(Op.AND)
                .addTerm(MetaFields.ID.getFldName(), Condition.IN, ProcessorTasksTool.ids(batch));
        final ExpressionOperator selection = NullSafe.get(filter.getQueryData(), QueryData::getExpression);
        if (selection != null) {
            builder.addOperator(selection);
        }
        NullSafe.consume(filter.getMinMetaCreateTimeMs(), ms -> builder.addDateTerm(MetaFields.CREATE_TIME,
                Condition.GREATER_THAN_OR_EQUAL_TO, DateUtil.createNormalDateTimeString(ms)));
        NullSafe.consume(filter.getMaxMetaCreateTimeMs(), ms -> builder.addDateTerm(MetaFields.CREATE_TIME,
                Condition.LESS_THAN_OR_EQUAL_TO, DateUtil.createNormalDateTimeString(ms)));
        final FindMetaCriteria criteria = new FindMetaCriteria(builder.build());
        criteria.setPageRequest(new PageRequest(0, batch.size()));
        return NullSafe.list(NullSafe.get(metaServiceProvider.get().find(criteria), ResultPage::getValues))
                .stream()
                .filter(Objects::nonNull)
                .map(Meta::getId)
                .toList();
    }

    private static List<List<Long>> batches(final Collection<Long> ids) {
        final List<List<Long>> out = new ArrayList<>();
        List<Long> batch = new ArrayList<>();
        for (final Long id : ids) {
            batch.add(id);
            if (batch.size() == BATCH) {
                out.add(batch);
                batch = new ArrayList<>();
            }
        }
        if (!batch.isEmpty()) {
            out.add(batch);
        }
        return out;
    }


    // --------------------------------------------------------------------------------


    /// The streams traced to the candidate pipeline that indexed them.
    ///
    /// @param byTasks   By a processing task that ran the pipeline on the stream (exact).
    /// @param byFilters By the one candidate whose processor filters select the stream.
    /// @param ambiguous Those two or more candidates' tasks or filters select: not traced.
    public record Traced(Map<Long, DocRef> byTasks, Map<Long, DocRef> byFilters, Set<Long> ambiguous) {

        /// Nothing traced.
        public static final Traced NONE = new Traced(Map.of(), Map.of(), Set.of());

        /// Copies the collections, in order.
        public Traced {
            byTasks = Collections.unmodifiableMap(new LinkedHashMap<>(NullSafe.map(byTasks)));
            byFilters = Collections.unmodifiableMap(new LinkedHashMap<>(NullSafe.map(byFilters)));
            ambiguous = Collections.unmodifiableSet(new LinkedHashSet<>(NullSafe.set(ambiguous)));
        }
    }
}
