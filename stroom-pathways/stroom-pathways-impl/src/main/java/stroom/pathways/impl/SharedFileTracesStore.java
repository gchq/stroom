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
import stroom.pathways.impl.TraceHistograms.HistogramSpec;
import stroom.pathways.shared.FindTraceCriteria;
import stroom.pathways.shared.FindTracesWithHistogramCriteria;
import stroom.pathways.shared.GetSpansRequest;
import stroom.pathways.shared.GetTraceOverviewRequest;
import stroom.pathways.shared.GetTraceRequest;
import stroom.pathways.shared.TraceHistogram;
import stroom.pathways.shared.TraceOverview;
import stroom.pathways.shared.TraceSpanPage;
import stroom.pathways.shared.TracesResultPage;
import stroom.pathways.shared.TracesStore;
import stroom.pathways.shared.otel.trace.Span;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.planb.impl.dao.trace.TraceDb;
import stroom.planb.impl.dao.trace.TraceRootField;
import stroom.planb.impl.dao.trace.TraceSecondaryIndex;
import stroom.planb.impl.data.archive.ArchiveShardRef;
import stroom.planb.impl.serde.trace.HexStringUtil;
import stroom.planb.shared.PlanBDocument;
import stroom.planb.shared.TraceSettings;
import stroom.query.api.TimeFilter;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

/**
 * Query implementation for a trace store held on a shared filesystem.
 *
 * <p><b>Reads archive buckets only.</b> The holding-area shards are never queried: they hold each trace's
 * root purely as an accumulator for late spans, while the bucket — labelled by the root's start time —
 * is the queryable copy. Because a trace's root therefore lives in exactly one bucket, per-bucket totals
 * sum to an exact count with no reconciliation between shard and archive.
 *
 * <p>Long scans are cancellable: each operation runs inside a {@link TaskContext} and each per-bucket task
 * is a {@link TaskContextFactory#childContextResult child context}, so terminating the query interrupts
 * the scan threads.
 */
@Singleton
class SharedFileTracesStore implements TracesStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SharedFileTracesStore.class);

    private final TraceArchiveReader archiveReader;
    private final SecurityContext securityContext;
    private final Executor executor;
    private final TaskContextFactory taskContextFactory;

    @Inject
    SharedFileTracesStore(final TraceArchiveReader archiveReader,
                          final SecurityContext securityContext,
                          final ExecutorProvider executorProvider,
                          final TaskContextFactory taskContextFactory) {
        this.archiveReader = archiveReader;
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
    // A trace that arrived since the last publish run therefore has no bucket yet and is not found.
    private Trace doGetTrace(final GetTraceRequest request) {
        final byte[] traceIdBytes = HexStringUtil.decode(request.getTraceId());
        final PlanBDocument doc = archiveReader.getPlanBDoc(request.getDataSourceRef());
        final long fromMs = request.getStartTimeMs() != null ? request.getStartTimeMs() : Long.MIN_VALUE;
        final long toMs = request.getStartTimeMs() != null ? request.getStartTimeMs() : Long.MAX_VALUE;
        final int shardIndex = archiveReader.archiveShardIndex(doc, request.getTraceId());

        // Normally one bucket; still unioned so a trace whose spans are split across buckets reads whole.
        final List<Trace> sources = new ArrayList<>();
        for (final ArchiveShardRef ref : archiveReader.relevantArchiveShards(doc, request.getTraceId(), fromMs, toMs)) {
            final Trace archived = archiveReader.getTraceFromArchive(ref, shardIndex, traceIdBytes, doc);
            if (archived != null) {
                sources.add(archived);
            }
        }

        final Trace merged = TraceArchiveReader.mergeTraces(request.getTraceId(), sources);
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
        final TraceDb.SpanOpenTest openTest = SpanPaging.openTest(request.getGroupSelection());
        final PlanBDocument doc = archiveReader.getPlanBDoc(request.getDataSourceRef());
        final long fromMs = request.getStartTimeMs() != null ? request.getStartTimeMs() : Long.MIN_VALUE;
        final long toMs = request.getStartTimeMs() != null ? request.getStartTimeMs() : Long.MAX_VALUE;
        final List<ArchiveShardRef> refs = archiveReader.relevantArchiveShards(doc, request.getTraceId(), fromMs, toMs);
        return archiveReader.archiveSpanPage(request, refs, openTest);
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
        final PlanBDocument doc = archiveReader.getPlanBDoc(request.getDataSourceRef());
        final int shardIndex = archiveReader.archiveShardIndex(doc, request.getTraceId());
        final Map<String, Span> bySpanId = new LinkedHashMap<>();

        for (final ArchiveShardRef ref : archiveReader.relevantArchiveShards(
                doc, request.getTraceId(), request.getFromMs(), request.getToMs())) {
            final List<Span> archived = archiveReader.readArchive(doc, shardIndex, ref, traceDb ->
                    traceDb.getOverviewSpans(
                            traceIdBytes, request.getFromMs(), request.getToMs(), request.getMaxBars()));
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
                taskContext -> doFindTraces(criteria, 0)).get();
    }

    @Override
    public TracesResultPage findTracesWithHistogram(final FindTracesWithHistogramCriteria criteria) {
        return taskContextFactory.contextResult(
                "Trace query: find traces with histogram",
                taskContext -> doFindTraces(criteria.getCriteria(), criteria.getBucketCount())).get();
    }

    // A bucketCount above zero adds the histogram, counted from the same bucket copy as the page.
    private TracesResultPage doFindTraces(final FindTraceCriteria criteria, final int bucketCount) {
        final DocRef docRef = criteria.getDataSourceRef();
        final PlanBDocument doc = archiveReader.getPlanBDoc(docRef);
        if (doc == null) {
            LOGGER.warn(() -> "No Plan B doc found for '" + docRef.getName() + "'");
            throw new RuntimeException("No Plan B doc found for '" + docRef.getName() + "'");
        }

        final Predicate<TraceRoot> filterPredicate = archiveReader.buildFilterPredicate(criteria.getFilter());
        final UserIdentity userIdentity = securityContext.getUserIdentity();
        final TaskContext parentContext = taskContextFactory.current();
        final List<CompletableFuture<BucketResult>> futures = new ArrayList<>();

        // Bound per-bucket results to (offset + length): each bucket sorts by the same
        // secondary index and returns at most this many rows, so the global merge of
        // N buckets has at most N×(offset+length) rows to sort — far fewer than all rows.
        final int callerOffset = criteria.getPageRequest() != null
                ? criteria.getPageRequest().getOffset() : 0;
        final int callerLength = criteria.getPageRequest() != null
                ? criteria.getPageRequest().getLength() : Integer.MAX_VALUE;
        // Guard against integer overflow when offset + length > MAX_VALUE.
        final int storePageSize = callerLength == Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : callerOffset + callerLength;

        final FindTraceCriteria storeCriteria = new FindTraceCriteria(
                new PageRequest(0, storePageSize),
                criteria.getSortList(),
                criteria.getDataSourceRef(),
                criteria.getFilter(),
                criteria.getPathway(),
                criteria.getTemporalOrderingTolerance(),
                criteria.getTimeRange());

        final TimeFilter timeFilter = TraceHistograms.resolveTimeFilter(criteria.getTimeRange());
        validateTimeRangeLimit(doc, timeFilter);

        // Null when no histogram was asked for, or when the window is unbounded or too wide to count.
        // Where a histogram IS available its window is the criteria's own resolved time filter, so the
        // page and the counts always describe the same span of time.
        final HistogramSpec histogramSpec = bucketCount > 0
                ? TraceHistograms.histogramSpec(criteria.getTimeRange(), bucketCount, doc)
                : null;
        final HistogramSpec countableSpec = histogramSpec != null && histogramSpec.available()
                ? histogramSpec
                : null;

        // Fan out over archive buckets only — the holding-area shards are never queried.
        //
        // An absent time range is bounded rather than treated as all-time. Every ref returned here is
        // opened for reading, which copies that bucket's whole data.mdb down to local disk and holds it
        // until idle eviction — so an unbounded fan-out would pull the entire archive history onto the
        // node and saturate the shared mount. validateTimeRangeLimit cannot catch this because it
        // returns early for a null filter.
        final long toMs = timeFilter != null ? timeFilter.getTo() : System.currentTimeMillis();
        final long fromMs = timeFilter != null
                ? timeFilter.getFrom()
                : toMs - TraceHistograms.maxWindowMs(doc);
        if (timeFilter == null) {
            LOGGER.debug(() -> "No time range for '" + doc.getName() + "', defaulting to the last "
                    + TraceHistograms.maxWindowMs(doc) + "ms rather than scanning every archive bucket");
        }
        for (int i = 0; i < doc.getShardCount(); i++) {
            final int shardIndex = i;
            for (final ArchiveShardRef ref : archiveReader.shardsForIndex(
                    doc, shardIndex, fromMs, toMs)) {
                futures.add(CompletableFuture.supplyAsync(
                        taskContextFactory.childContextResult(parentContext,
                                "Query trace archive " + ref.dateLabel(), ctx -> {
                                    try {
                                        return securityContext.asUserResult(userIdentity,
                                                () -> queryArchive(ref, shardIndex,
                                                        storeCriteria, doc, filterPredicate,
                                                        countableSpec));
                                    } catch (final Exception e) {
                                        LOGGER.error("Error querying archive shard " + ref.dateLabel() +
                                                " for doc " + doc.getName(), e);
                                        return null;
                                    }
                                }), executor));
            }
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        // A trace's root lives in exactly one bucket, so summing per-bucket totals is exact and needs
        // no distinct-count repair pass.
        return mergeAndPaginate(futures, criteria, histogramSpec, countableSpec);
    }

    // The page and the histogram counts read from one archive bucket. Both are taken inside a single
    // readArchive call, which holds the bucket open for the whole callback, so neither half can be read
    // from a copy the other did not see. counts is null when no histogram was asked for.
    private record BucketResult(TracesResultPage page, long[] counts) {

    }

    /**
     * Collects results from the completed per-bucket futures, merges all {@link TraceRoot} values, sorts
     * them with {@link #buildMergeComparator} — the criteria's own sort column and direction, defaulting
     * to {@code startTime} descending, always with {@code traceId} as a stable tiebreaker — then applies
     * the caller's page request.
     */
    private TracesResultPage mergeAndPaginate(
            final List<CompletableFuture<BucketResult>> futures,
            final FindTraceCriteria criteria,
            final HistogramSpec histogramSpec,
            final HistogramSpec countableSpec) {

        final List<TraceRoot> allTraceRoots = new ArrayList<>();
        final long[] totals = countableSpec == null
                ? null
                : new long[countableSpec.nBuckets()];
        long total = 0;
        boolean exact = true;

        for (final CompletableFuture<BucketResult> future : futures) {
            try {
                final BucketResult result = future.get();
                final TracesResultPage page = result == null ? null : result.page();
                if (result != null && result.counts() != null && totals != null) {
                    for (int b = 0; b < totals.length && b < result.counts().length; b++) {
                        totals[b] += result.counts()[b];
                    }
                }
                if (page == null) {
                    // queryArchive logged the cause and returned null. Buckets are now the only source, so
                    // an unread one means traces are missing from the page — the total cannot be exact.
                    exact = false;
                    continue;
                }
                if (page.getValues() != null) {
                    allTraceRoots.addAll(page.getValues());
                }
                if (page.getPageResponse() != null) {
                    total += page.getPageResponse().getTotal();
                    if (!page.getPageResponse().isExact()) {
                        exact = false;
                    }
                }
            } catch (final Exception e) {
                LOGGER.error("Failed to retrieve query result page from future", e);
                exact = false;
            }
        }

        // Dedupe by traceId across buckets. A trace's whole root goes to one bucket, so this normally
        // finds nothing — but a trace can still show up twice when its spans are split across buckets,
        // as the real-root row in the root's start-time bucket AND a synthesized orphan row in whichever
        // bucket its stray spans landed. Keep one row per traceId, preferring the real root (see
        // preferred(...)). Done on the full collected set (not post-pagination) because the two rows
        // carry different start times and so sort to non-adjacent positions.
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
        // A histogram was asked for but the window was unbounded or too wide to count, so say so
        // rather than returning counts of zero.
        final TraceHistogram histogram;
        if (countableSpec != null) {
            histogram = TraceHistograms.assembleHistogram(countableSpec, totals);
        } else if (histogramSpec != null) {
            histogram = TraceHistogram.unavailable(histogramSpec.maxWindowMs());
        } else {
            histogram = null;
        }
        return new TracesResultPage(paginatedList, pageResponse, histogram);
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

    /**
     * Runs {@code findTraces()}, and the histogram count when {@code countableSpec} is non-null, against
     * one archive bucket. Both run inside a single
     * {@link TraceArchiveReader#readArchive} call so they read the same copy of the bucket.
     */
    private BucketResult queryArchive(final ArchiveShardRef ref,
                                      final int shardIndex,
                                      final FindTraceCriteria criteria,
                                      final PlanBDocument doc,
                                      final Predicate<TraceRoot> filterPredicate,
                                      final HistogramSpec countableSpec) {
        try {
            return archiveReader.readArchive(doc, shardIndex, ref, traceDb -> {
                final TracesResultPage page = traceDb.findTraces(criteria, filterPredicate);
                final long[] counts = countableSpec == null
                        ? null
                        : traceDb.histogram(countableSpec.timeFilter(),
                                countableSpec.bucketWidthMs(),
                                countableSpec.nBuckets(),
                                filterPredicate);
                return new BucketResult(page, counts);
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
        final long limitMs = maxQueryTimeRange.getApproxMillis();
        if (windowMs > limitMs) {
            throw new IllegalArgumentException(
                    "Query time range (" + windowMs / 1000 + "s) exceeds the configured " +
                    "maximum of " + maxQueryTimeRange + " for this data source. " +
                    "Please narrow the time range.");
        }
    }
}
