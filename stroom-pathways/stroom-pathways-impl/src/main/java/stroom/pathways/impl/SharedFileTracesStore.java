/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.pathways.impl;

import stroom.docref.DocRef;
import stroom.pathways.shared.FindTraceCriteria;
import stroom.pathways.shared.GetSpansRequest;
import stroom.pathways.shared.GetTraceOverviewRequest;
import stroom.pathways.shared.GetTraceRequest;
import stroom.pathways.shared.TraceHistogram;
import stroom.pathways.shared.TraceHistogramRequest;
import stroom.pathways.shared.TraceOverview;
import stroom.pathways.shared.TraceSpanPage;
import stroom.pathways.shared.TracesResultPage;
import stroom.pathways.shared.otel.trace.Span;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.planb.impl.data.archive.ArchiveShardLocator;
import stroom.planb.impl.data.archive.ArchiveShardRef;
import stroom.planb.impl.data.shard.ShardManager;
import stroom.planb.impl.db.trace.TraceDb;
import stroom.planb.impl.db.trace.TraceRootField;
import stroom.planb.impl.db.trace.TraceSecondaryIndex;
import stroom.planb.impl.serde.trace.HexStringUtil;
import stroom.planb.shared.PlanBDocument;
import stroom.planb.shared.TraceSettings;
import stroom.query.api.GroupSelection;
import stroom.query.api.TimeFilter;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserIdentity;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PageResponse;
import stroom.util.shared.time.SimpleDuration;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.ws.rs.NotFoundException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

/**
 * Query implementation for a shared-filesystem trace store ({@code SharedFileStoreShard}). Every node reads the
 * shared mount directly, fanning out across shards + overlapping archive buckets and merging. Long scans are
 * cancellable: each operation runs inside a {@link TaskContext} and each per-shard/per-archive task is a
 * {@link TaskContextFactory#childContextResult child context}, so terminating the query interrupts the scan
 * threads.
 */
@Singleton
class SharedFileTracesStore extends AbstractTracesStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SharedFileTracesStore.class);

    // Exact distinct-trace counting only runs when the window overlaps at most this many archive buckets
    // (~one archival-granularity period straddles two buckets); a wider window keeps the approximate "?".
    private static final int MAX_EXACT_COUNT_BUCKETS = 2;

    private final SecurityContext securityContext;
    private final Executor executor;
    private final TaskContextFactory taskContextFactory;

    @Inject
    SharedFileTracesStore(final TracesDocLoader docLoader,
                          final ShardManager shardManager,
                          final ArchiveShardLocator archiveShardLocator,
                          final MergedCheckpointCache mergedCheckpointCache,
                          final ExpressionPredicateFactory expressionPredicateFactory,
                          final SecurityContext securityContext,
                          final ExecutorProvider executorProvider,
                          final TaskContextFactory taskContextFactory) {
        super(docLoader, shardManager, archiveShardLocator, mergedCheckpointCache, expressionPredicateFactory);
        this.securityContext = securityContext;
        this.executor = executorProvider.get();
        this.taskContextFactory = taskContextFactory;
    }

    @Override
    public Trace getTrace(final GetTraceRequest request) {
        return taskContextFactory.contextResult(
                "Trace query: get trace",
                taskContext -> doGetTrace(request)).get();
    }

    // Reads the trace from its archive bucket(s). The holding-area shard is never consulted: it holds
    // each trace's root purely as an accumulator for late spans, and the bucket is the queryable copy.
    // A trace that arrived since the last archival run therefore has no bucket yet and is not found.
    private Trace doGetTrace(final GetTraceRequest request) {
        final byte[] traceIdBytes = HexStringUtil.decode(request.getTraceId());
        final PlanBDocument doc = getPlanBDoc(request.getDataSourceRef());
        final long fromMs = request.getStartTimeMs() != null ? request.getStartTimeMs() : Long.MIN_VALUE;
        final long toMs = request.getStartTimeMs() != null ? request.getStartTimeMs() : Long.MAX_VALUE;
        final int shardIndex = archiveShardIndex(doc, request.getTraceId());

        // Normally one bucket; still unioned so data left split by the older insert-time bucketing reads
        // whole.
        final List<Trace> sources = new ArrayList<>();
        for (final ArchiveShardRef ref : relevantArchiveShards(doc, request.getTraceId(), fromMs, toMs)) {
            final Trace archived = getTraceFromArchive(ref, shardIndex, traceIdBytes, doc);
            if (archived != null) {
                sources.add(archived);
            }
        }

        final Trace merged = mergeTraces(request.getTraceId(), sources);
        if (merged != null) {
            return merged;
        }
        throw new NotFoundException("No spans found for trace " + request.getTraceId());
    }

    @Override
    public TraceSpanPage getSpans(final GetSpansRequest request) {
        return taskContextFactory.contextResult(
                "Trace query: get spans",
                taskContext -> doGetSpans(request)).get();
    }

    // Pages the span tree from the trace's archive bucket(s) only — see doGetTrace for why the
    // holding-area shard is not consulted.
    private TraceSpanPage doGetSpans(final GetSpansRequest request) {
        final TraceDb.SpanOpenTest openTest = openTest(request.getGroupSelection());
        final PlanBDocument doc = getPlanBDoc(request.getDataSourceRef());
        final long fromMs = request.getStartTimeMs() != null ? request.getStartTimeMs() : Long.MIN_VALUE;
        final long toMs = request.getStartTimeMs() != null ? request.getStartTimeMs() : Long.MAX_VALUE;
        final List<ArchiveShardRef> refs = relevantArchiveShards(doc, request.getTraceId(), fromMs, toMs);
        return archiveSpanPage(request, refs, openTest);
    }

    @Override
    public TraceOverview getTraceOverview(final GetTraceOverviewRequest request) {
        return taskContextFactory.contextResult(
                "Trace query: get trace overview",
                taskContext -> doGetTraceOverview(request)).get();
    }

    // Reads the downsampled overview from the trace's archive bucket(s) only, deduped by spanId with
    // first-write-wins — see doGetTrace for why the holding-area shard is not consulted.
    private TraceOverview doGetTraceOverview(final GetTraceOverviewRequest request) {
        final byte[] traceIdBytes = HexStringUtil.decode(request.getTraceId());
        final PlanBDocument doc = getPlanBDoc(request.getDataSourceRef());
        final int shardIndex = archiveShardIndex(doc, request.getTraceId());
        final Map<String, Span> bySpanId = new LinkedHashMap<>();

        for (final ArchiveShardRef ref : relevantArchiveShards(
                doc, request.getTraceId(), request.getFromMs(), request.getToMs())) {
            final List<Span> archived = shardManager.getArchive(doc, shardIndex, ref, reader -> {
                if (reader instanceof final TraceDb traceDb) {
                    return traceDb.getOverviewSpans(
                            traceIdBytes, request.getFromMs(), request.getToMs(), request.getMaxBars());
                }
                throw new IllegalStateException("Unexpected value: " + reader);
            });
            if (archived != null) {
                archived.forEach(s -> bySpanId.putIfAbsent(s.getSpanId(), s));
            }
        }
        return new TraceOverview(new ArrayList<>(bySpanId.values()));
    }

    @Override
    public TracesResultPage findTraces(final FindTraceCriteria criteria) {
        return taskContextFactory.contextResult(
                "Trace query: find traces",
                taskContext -> doFindTraces(criteria)).get();
    }

    private TracesResultPage doFindTraces(final FindTraceCriteria criteria) {
        final DocRef docRef = criteria.getDataSourceRef();
        final PlanBDocument doc = getPlanBDoc(docRef);
        if (doc == null) {
            LOGGER.warn(() -> "No Plan B doc found for '" + docRef.getName() + "'");
            throw new RuntimeException("No Plan B doc found for '" + docRef.getName() + "'");
        }

        final Predicate<TraceRoot> filterPredicate = buildFilterPredicate(criteria.getFilter());
        final UserIdentity userIdentity = securityContext.getUserIdentity();
        final TaskContext parentContext = taskContextFactory.current();
        final List<CompletableFuture<TracesResultPage>> futures = new ArrayList<>();

        // Bound per-shard results to (offset + length): each shard sorts by the same
        // secondary index and returns at most this many rows, so the global merge of
        // N shards has at most N×(offset+length) rows to sort — far fewer than all rows.
        final int callerOffset = criteria.getPageRequest() != null
                ? criteria.getPageRequest().getOffset() : 0;
        final int callerLength = criteria.getPageRequest() != null
                ? criteria.getPageRequest().getLength() : Integer.MAX_VALUE;
        // Guard against integer overflow when offset + length > MAX_VALUE.
        final int shardPageSize = callerLength == Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : callerOffset + callerLength;

        final FindTraceCriteria shardCriteria = new FindTraceCriteria(
                new PageRequest(0, shardPageSize),
                criteria.getSortList(),
                criteria.getDataSourceRef(),
                criteria.getFilter(),
                criteria.getPathway(),
                criteria.getTemporalOrderingTolerance(),
                criteria.getTimeRange());

        // Resolve the time filter once for archive fan-out.
        final TimeFilter timeFilter = resolveTimeFilter(criteria.getTimeRange());
        validateTimeRangeLimit(doc, timeFilter);

        for (int i = 0; i < doc.getShardCount(); i++) {
            final int shardIndex = i;
            futures.add(CompletableFuture.supplyAsync(
                    taskContextFactory.childContextResult(parentContext,
                            "Query trace shard " + shardIndex, ctx -> {
                                try {
                                    return securityContext.asUserResult(userIdentity, () ->
                                            shardManager.get(doc.getName(), shardIndex, reader -> {
                                                if (reader instanceof final TraceDb traceDb) {
                                                    return traceDb.findTraces(
                                                            shardCriteria, filterPredicate);
                                                }
                                                throw new IllegalStateException("Unexpected value: " + reader);
                                            }));
                                } catch (final Exception e) {
                                    LOGGER.error("Error querying shard " + shardIndex
                                            + " for doc " + doc.getName(), e);
                                    return null;
                                }
                            }), executor));
        }

        // --- Archive shard fan-out ---
        // Distinct archive bucket time-labels overlapping the window, used to decide whether an exact
        // distinct-trace count is cheap enough (see MAX_EXACT_COUNT_BUCKETS).
        final Set<String> archiveLabels = new HashSet<>();
        if (timeFilter != null) {
            for (int i = 0; i < doc.getShardCount(); i++) {
                final int shardIndex = i;
                final List<ArchiveShardRef> archiveRefs = archiveShardLocator.findRelevantShards(
                        doc, shardIndex,
                        timeFilter.getFrom(), timeFilter.getTo());
                for (final ArchiveShardRef ref : archiveRefs) {
                    archiveLabels.add(ref.dateLabel());
                    futures.add(CompletableFuture.supplyAsync(
                            taskContextFactory.childContextResult(parentContext,
                                    "Query trace archive " + ref.dateLabel(), ctx -> {
                                        try {
                                            return securityContext.asUserResult(userIdentity,
                                                    () -> queryArchive(ref, shardIndex,
                                                            shardCriteria, doc, filterPredicate));
                                        } catch (final Exception e) {
                                            LOGGER.error("Error querying archive shard " + ref.dateLabel() +
                                                    " for doc " + doc.getName(), e);
                                            return null;
                                        }
                                    }), executor));
                }
            }
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        final TracesResultPage merged = mergeAndPaginate(futures, criteria);

        // The summed/deduped total above is only approximate when a split trace is double-counted
        // across shard + archive. When the window overlaps few enough archive buckets, replace it with
        // an exact distinct-trace count (union of traceIds across the stores).
        if (timeFilter != null
                && NullSafe.isBlankString(criteria.getFilter())
                && !archiveLabels.isEmpty()
                && archiveLabels.size() <= MAX_EXACT_COUNT_BUCKETS) {
            // With a quick filter active the exact distinct-count path (windowTraceIds) is key-only and
            // can't apply the filter, so fall back to the summed filter-aware per-shard totals instead.
            final long exactTotal = countDistinctTraces(doc, timeFilter, userIdentity);
            final PageResponse pr = merged.getPageResponse();
            return new TracesResultPage(merged.getValues(),
                    new PageResponse(pr.getOffset(), pr.getLength(), exactTotal, true));
        }
        return merged;
    }

    // Exact count of distinct traces with a start time in the window, unioning traceIds across every
    // shard + relevant archive bucket (key-only START_TIME scans). Dedupes split traces (archived real
    // root + orphan) that the per-store summed total double-counts.
    private long countDistinctTraces(final PlanBDocument doc,
                                     final TimeFilter timeFilter,
                                     final UserIdentity userIdentity) {
        final TaskContext parentContext = taskContextFactory.current();
        final List<CompletableFuture<Set<String>>> futures = new ArrayList<>();
        for (int i = 0; i < doc.getShardCount(); i++) {
            final int shardIndex = i;
            futures.add(CompletableFuture.supplyAsync(
                    taskContextFactory.childContextResult(parentContext,
                            "Count trace shard " + shardIndex, ctx ->
                            securityContext.asUserResult(userIdentity, () ->
                                    shardManager.get(doc.getName(), shardIndex, reader -> {
                                        if (reader instanceof final TraceDb traceDb) {
                                            return traceDb.windowTraceIds(timeFilter);
                                        }
                                        throw new IllegalStateException("Unexpected value: " + reader);
                                    }))), executor));
            for (final ArchiveShardRef ref : archiveShardLocator.findRelevantShards(
                    doc, shardIndex, timeFilter.getFrom(), timeFilter.getTo())) {
                futures.add(CompletableFuture.supplyAsync(
                        taskContextFactory.childContextResult(parentContext,
                                "Count trace archive " + ref.dateLabel(), ctx ->
                                securityContext.asUserResult(userIdentity, () ->
                                        shardManager.getArchive(doc, shardIndex, ref, reader -> {
                                            if (reader instanceof final TraceDb traceDb) {
                                                return traceDb.windowTraceIds(timeFilter);
                                            }
                                            throw new IllegalStateException("Unexpected value: " + reader);
                                        }))), executor));
            }
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        final Set<String> distinct = new HashSet<>();
        for (final CompletableFuture<Set<String>> future : futures) {
            try {
                final Set<String> ids = future.get();
                if (ids != null) {
                    distinct.addAll(ids);
                }
            } catch (final Exception e) {
                LOGGER.error("Failed to collect window traceIds for doc " + doc.getName(), e);
            }
        }
        return distinct.size();
    }

    /**
     * Collects results from completed futures (per-shard or per-node), merges all
     * {@link TraceRoot} values, sorts by {@code startTime} descending (most recent first)
     * with {@code traceId} as a stable tiebreaker, then applies the caller's page request.
     */
    private TracesResultPage mergeAndPaginate(
            final List<CompletableFuture<TracesResultPage>> futures,
            final FindTraceCriteria criteria) {

        final List<TraceRoot> allTraceRoots = new ArrayList<>();
        long total = 0;
        boolean exact = true;

        for (final CompletableFuture<TracesResultPage> future : futures) {
            try {
                final TracesResultPage page = future.get();
                if (page != null) {
                    if (page.getValues() != null) {
                        allTraceRoots.addAll(page.getValues());
                    }
                    if (page.getPageResponse() != null) {
                        total += page.getPageResponse().getTotal();
                        if (!page.getPageResponse().isExact()) {
                            exact = false;
                        }
                    }
                }
            } catch (final Exception e) {
                LOGGER.error("Failed to retrieve query result page from future", e);
            }
        }

        // Dedupe by traceId across shard + archive stores. A trace whose root has aged out to an
        // archive appears twice — as the archived real-root row AND as the synthesized orphan
        // row. Keep one row per traceId, preferring the real root (see preferred(...)). Done on the
        // full collected set (not post-pagination) because the two rows carry different start times
        // and so sort to different, non-adjacent positions.
        final Map<String, TraceRoot> byTraceId = new LinkedHashMap<>();
        for (final TraceRoot root : allTraceRoots) {
            byTraceId.merge(root.getTraceId(), root, SharedFileTracesStore::preferred);
        }
        final List<TraceRoot> deduped = new ArrayList<>(byTraceId.values());

        // Correct the summed total for duplicates removed within the fetched window. Collisions
        // beyond the window can't be observed, so mark the count approximate when any were removed.
        final int duplicatesRemoved = allTraceRoots.size() - deduped.size();
        if (duplicatesRemoved > 0) {
            total = Math.max(deduped.size(), total - duplicatesRemoved);
            exact = false;
        }

        // Sort the deduped set using the same comparator as the per-shard secondary index.
        deduped.sort(buildMergeComparator(criteria));

        // Apply the caller's offset/length over the full deduped set.
        final int offset = criteria.getPageRequest() != null
                ? criteria.getPageRequest().getOffset()
                : 0;
        final int length = criteria.getPageRequest() != null
                ? criteria.getPageRequest().getLength()
                : Integer.MAX_VALUE;

        final List<TraceRoot> paginatedList;
        if (offset >= deduped.size()) {
            paginatedList = Collections.emptyList();
        } else {
            paginatedList = deduped.subList(offset, Math.min(offset + length, deduped.size()));
        }

        final PageResponse pageResponse = new PageResponse(
                (long) offset,
                paginatedList.size(),
                total,
                exact
        );
        return new TracesResultPage(paginatedList, pageResponse);
    }

    // De-dup precedence for a traceId seen in >1 store (archived real-root row + orphan row):
    // keep the real root over an orphan, else the most recently active. Order-independent, so safe
    // as a Map#merge remapping function.
    //
    // A split trace's archived spans (in the real-root row) and its trailing spans (in the orphan
    // row) are disjoint sets, so the true Total Spans is their SUM. Keep the real root's identity but add
    // the orphan's span count, otherwise the trailing spans would be dropped from the reported total.
    private static TraceRoot preferred(final TraceRoot a, final TraceRoot b) {
        if (a.isOrphan() != b.isOrphan()) {
            final TraceRoot real = a.isOrphan() ? b : a;
            final TraceRoot orphan = a.isOrphan() ? a : b;
            final int combined = real.getTotalSpans() + orphan.getTotalSpans();
            return combined == real.getTotalSpans()
                    ? real
                    : real.copy().totalSpans(combined).build();
        }
        return a.getLastActivityMs() >= b.getLastActivityMs() ? a : b;
    }

    /**
     * Builds a {@link Comparator} for the inter-shard merge that matches the sort order
     * used by the per-shard secondary LMDB indexes.
     *
     * <p>The default (no sort criteria) is {@code Trace Start} descending — newest first.
     */
    private static Comparator<TraceRoot> buildMergeComparator(final FindTraceCriteria criteria) {
        final CriteriaFieldSort firstSort =
                NullSafe.get(criteria.getSortList(),
                        sorts -> sorts.isEmpty() ? null : sorts.getFirst());
        final String field = firstSort != null
                ? firstSort.getId()
                : TraceRootField.TRACE_START;
        final boolean desc = firstSort == null || firstSort.isDesc();

        // Match the per-shard secondary-index order. TRACE_ID is served by the primary
        // trace-roots DBI (not a secondary index) so it is handled explicitly; every
        // other field (and the default) comes from the single TraceSecondaryIndex source.
        final Comparator<TraceRoot> base;
        if (TraceRootField.TRACE_ID.equals(field)) {
            base = Comparator.comparing(TraceRoot::getTraceId,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        } else {
            final TraceSecondaryIndex index = TraceSecondaryIndex.forField(field);
            base = (index != null ? index : TraceSecondaryIndex.START_TIME).comparator();
        }

        final Comparator<TraceRoot> ordered = desc ? base.reversed() : base;
        // Stable tiebreaker: ascending traceId.
        return ordered.thenComparing(TraceRoot::getTraceId,
                Comparator.nullsLast(Comparator.naturalOrder()));
    }

    @Override
    public TraceHistogram getTraceHistogram(final TraceHistogramRequest request) {
        return taskContextFactory.contextResult(
                "Trace query: get trace histogram",
                taskContext -> doGetTraceHistogram(request)).get();
    }

    // Counts traces per equal time-bucket over the requested window, fanning out across shards +
    // overlapping archive buckets and summing per-bucket counts.
    private TraceHistogram doGetTraceHistogram(final TraceHistogramRequest request) {
        final DocRef docRef = request.getDataSourceRef();
        final PlanBDocument doc = getPlanBDoc(docRef);
        if (doc == null) {
            LOGGER.warn(() -> "No Plan B doc found for '" + docRef.getName() + "'");
            throw new RuntimeException("No Plan B doc found for '" + docRef.getName() + "'");
        }

        final HistogramSpec spec = histogramSpec(request, doc);
        if (!spec.available()) {
            return TraceHistogram.unavailable(spec.maxWindowMs());
        }
        final long fromMs = spec.fromMs();
        final long toMs = spec.toMs();
        final TimeFilter timeFilter = spec.timeFilter();
        final long bucketWidthMs = spec.bucketWidthMs();
        final int nBuckets = spec.nBuckets();
        final Predicate<TraceRoot> filterPredicate = buildFilterPredicate(request.getFilter());

        final UserIdentity userIdentity = securityContext.getUserIdentity();
        final TaskContext parentContext = taskContextFactory.current();
        final List<CompletableFuture<long[]>> futures = new ArrayList<>();
        for (int i = 0; i < doc.getShardCount(); i++) {
            final int shardIndex = i;
            futures.add(CompletableFuture.supplyAsync(
                    taskContextFactory.childContextResult(parentContext,
                            "Histogram trace shard " + shardIndex, ctx -> {
                                try {
                                    return securityContext.asUserResult(userIdentity, () ->
                                            shardManager.get(doc.getName(), shardIndex, reader -> {
                                                if (reader instanceof final TraceDb traceDb) {
                                                    return traceDb.histogram(timeFilter,
                                                            bucketWidthMs, nBuckets, filterPredicate);
                                                }
                                                throw new IllegalStateException("Unexpected value: " + reader);
                                            }));
                                } catch (final Exception e) {
                                    LOGGER.error("Error histogramming shard " + shardIndex
                                            + " for doc " + doc.getName(), e);
                                    return null;
                                }
                            }), executor));

            for (final ArchiveShardRef ref : archiveShardLocator.findRelevantShards(
                    doc, shardIndex, fromMs, toMs)) {
                futures.add(CompletableFuture.supplyAsync(
                        taskContextFactory.childContextResult(parentContext,
                                "Histogram trace archive " + ref.dateLabel(), ctx -> {
                                    try {
                                        return securityContext.asUserResult(userIdentity, () ->
                                                shardManager.getArchive(doc, shardIndex, ref, reader -> {
                                                    if (reader instanceof final TraceDb traceDb) {
                                                        return traceDb.histogram(timeFilter,
                                                                bucketWidthMs, nBuckets, filterPredicate);
                                                    }
                                                    throw new IllegalStateException("Unexpected value: " + reader);
                                                }));
                                    } catch (final Exception e) {
                                        LOGGER.error("Error histogramming archive shard " + ref.dateLabel()
                                                + " for doc " + doc.getName(), e);
                                        return null;
                                    }
                                }), executor));
            }
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        final long[] totals = new long[nBuckets];
        for (final CompletableFuture<long[]> future : futures) {
            try {
                final long[] counts = future.get();
                if (counts != null) {
                    for (int b = 0; b < nBuckets && b < counts.length; b++) {
                        totals[b] += counts[b];
                    }
                }
            } catch (final Exception e) {
                LOGGER.error("Failed to collect histogram counts for doc " + doc.getName(), e);
            }
        }
        return assembleHistogram(spec, totals);
    }

    /**
     * Runs {@code findTraces()} against an archive bucket via a cached, read-only, idle-evicted local
     * copy ({@link ShardManager#getArchive}) rather than copying the whole bucket to a temp dir per call.
     */
    private TracesResultPage queryArchive(final ArchiveShardRef ref,
                                          final int shardIndex,
                                          final FindTraceCriteria criteria,
                                          final PlanBDocument doc,
                                          final Predicate<TraceRoot> filterPredicate) {
        try {
            return shardManager.getArchive(doc, shardIndex, ref, reader -> {
                if (reader instanceof final TraceDb traceDb) {
                    return traceDb.findTraces(criteria, filterPredicate);
                }
                throw new IllegalStateException("Unexpected value: " + reader);
            });
        } catch (final Exception e) {
            LOGGER.error(() -> "Error querying archive shard " + ref.dateLabel() +
                    " for doc " + doc.getName() + ": " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Validates that the resolved time filter does not exceed the configured
     * {@code maxQueryTimeRange} on the document's settings.
     */
    private static void validateTimeRangeLimit(final PlanBDocument doc,
                                               final TimeFilter timeFilter) {
        if (timeFilter == null) {
            return;
        }
        final SimpleDuration maxQueryTimeRange;
        if (doc.getSettings() instanceof final TraceSettings ts) {
            maxQueryTimeRange = ts.getMaxQueryTimeRange();
        } else {
            maxQueryTimeRange = null;
        }
        if (maxQueryTimeRange == null) {
            return;
        }
        final long windowMs = timeFilter.getTo() - timeFilter.getFrom();
        final long limitMs = toMillis(maxQueryTimeRange);
        if (windowMs > limitMs) {
            throw new IllegalArgumentException(
                    "Query time range (" + windowMs / 1000 + "s) exceeds the configured " +
                    "maximum of " + maxQueryTimeRange + " for this data source. " +
                    "Please narrow the time range.");
        }
    }

    private static long toMillis(final SimpleDuration duration) {
        final long time = duration.getTime();
        return switch (duration.getTimeUnit()) {
            case NANOSECONDS  -> time / 1_000_000;
            case MILLISECONDS -> time;
            case SECONDS      -> time * 1_000;
            case MINUTES      -> time * 60 * 1_000;
            case HOURS        -> time * 60 * 60 * 1_000;
            case DAYS         -> time * 24 * 60 * 60 * 1_000;
            case WEEKS        -> time * 7 * 24 * 60 * 60 * 1_000;
            case MONTHS       -> time * 30L * 24 * 60 * 60 * 1_000;
            case YEARS        -> time * 365L * 24 * 60 * 60 * 1_000;
        };
    }
}
