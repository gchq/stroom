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
import stroom.pathways.shared.TraceHistogram;
import stroom.pathways.shared.TraceSpanPage;
import stroom.pathways.shared.TraceSpanRow;
import stroom.pathways.shared.TracesStore;
import stroom.pathways.shared.otel.trace.Span;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.dao.ShardKeyRouter;
import stroom.planb.impl.dao.trace.TraceDb;
import stroom.planb.impl.data.archive.ArchiveShardLocator;
import stroom.planb.impl.data.archive.ArchiveShardRef;
import stroom.planb.impl.data.shard.ShardManager;
import stroom.planb.impl.serde.trace.HexStringUtil;
import stroom.planb.shared.PlanBDocument;
import stroom.planb.shared.TraceSettings;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.GroupSelection;
import stroom.query.api.TimeFilter;
import stroom.query.api.TimeRange;
import stroom.query.common.v2.DateExpressionParser;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.common.v2.FieldProviderImpl;
import stroom.query.common.v2.SimpleStringExpressionParser.FieldProvider;
import stroom.query.common.v2.ValueFunctionFactoriesImpl;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.annotation.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Query helpers shared by a trace store's read operations: archive-bucket paging and cursors,
 * quick-filter parsing, trace merging across buckets, and histogram sizing and assembly.
 *
 * <p>Declares no {@link TracesStore} operation itself — {@link SharedFileTracesStore} is the only
 * subclass and implements all of them in terms of these helpers.
 */
abstract class AbstractTracesStore implements TracesStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractTracesStore.class);

    // Quick-filter field mappers: bare terms fuzzy-match either (OR'd); 'operation:'/'traceid:' qualify;
    // 'iserror:' matches the boolean error flag (iserror:true / iserror:false).
    private static final ValueFunctionFactoriesImpl<TraceRoot> FILTER_VALUE_FUNCTIONS =
            new ValueFunctionFactoriesImpl<TraceRoot>()
                    .put(FindTraceCriteria.FIELD_DEF_OPERATION, TraceRoot::getName)
                    .put(FindTraceCriteria.FIELD_DEF_TRACE_ID, TraceRoot::getTraceId)
                    .put(FindTraceCriteria.FIELD_DEF_IS_ERROR, root -> Boolean.toString(root.isError()));
    private static final FieldProvider FILTER_FIELD_PROVIDER =
            new FieldProviderImpl(FindTraceCriteria.FIELD_DEFINITIONS);

    protected final TracesDocLoader docLoader;
    protected final ShardManager shardManager;
    protected final ArchiveShardLocator archiveShardLocator;
    protected final MergedCheckpointCache mergedCheckpointCache;
    protected final ExpressionPredicateFactory expressionPredicateFactory;

    protected AbstractTracesStore(final TracesDocLoader docLoader,
                                  final ShardManager shardManager,
                                  final ArchiveShardLocator archiveShardLocator,
                                  final MergedCheckpointCache mergedCheckpointCache,
                                  final ExpressionPredicateFactory expressionPredicateFactory) {
        this.docLoader = docLoader;
        this.shardManager = shardManager;
        this.archiveShardLocator = archiveShardLocator;
        this.mergedCheckpointCache = mergedCheckpointCache;
        this.expressionPredicateFactory = expressionPredicateFactory;
    }

    protected PlanBDocument getPlanBDoc(final DocRef docRef) {
        return docLoader.getPlanBDoc(docRef);
    }

    // Builds the quick-filter predicate for a TraceRoot from the criteria's filter string, or null when
    // the filter is blank (so the query can use its O(1)/key-only count fast paths).
    protected Predicate<TraceRoot> buildFilterPredicate(final String filter) {
        if (NullSafe.isBlankString(filter)) {
            return null;
        }
        return expressionPredicateFactory.create(
                filter, FILTER_FIELD_PROVIDER, FILTER_VALUE_FUNCTIONS, DateTimeSettings.builder().build());
    }

    // Expand/collapse: the client sends a GroupSelection only when the view actually prunes something
    // (a collapsed span or a reduced expand-level); null ⇒ fully expanded ⇒ the unfiltered walk, which
    // keeps the fast on-disk-checkpoint path. The "group key" is the span's spanId (hex).
    protected TraceDb.SpanOpenTest openTest(final GroupSelection groupSelection) {
        return groupSelection == null
                ? TraceDb.SpanOpenTest.ALL
                : (spanId, depth) -> groupSelection.isGroupOpen(HexStringUtil.encode(spanId), depth);
    }

    /**
     * Pages a trace wholly from its archive bucket(s) — the queryable copy — with no contribution from
     * the holding-area shard. Returns an empty page when the trace has no bucket yet, i.e. it arrived
     * since the last publish run.
     *
     * <p>Normally there is exactly one bucket, because a trace's spans are all archived to its root's
     * start-time bucket. Several are still merged when they occur, which covers a trace whose late
     * spans were bucketed under a synthesized orphan root.
     */
    protected TraceSpanPage archiveSpanPage(final GetSpansRequest request,
                                            final List<ArchiveShardRef> refs,
                                            final TraceDb.SpanOpenTest openTest) {
        if (refs.isEmpty()) {
            return new TraceSpanPage(List.of(), false, null, 0);
        }
        final byte[] traceIdBytes = HexStringUtil.decode(request.getTraceId());
        final PlanBDocument doc = getPlanBDoc(request.getDataSourceRef());
        final int shardIndex = archiveShardIndex(doc, request.getTraceId());
        final List<byte[]> cursorPath = decodeCursor(request.getCursor());
        final String cacheKey = checkpointCacheKey(doc, shardIndex, request.getTraceId(), refs)
                + groupSelectionKey(request.getGroupSelection());
        return openArchivesAndPage(doc, shardIndex, refs, 0, traceIdBytes, new ArrayList<>(),
                cursorPath, request.getOffset(), request.getLimit(), cacheKey, openTest);
    }

    // Recursively nests the getArchive callbacks (each holds the archive shard's read lock + a read txn)
    // to hold the shard + every relevant archive bucket open at once, then serves the page from the merged
    // pre-order DFS: by opaque cursor when one was supplied (cheap sequential next/prev), by offset via a
    // cached merged checkpoint index when a jump/last is requested (built once, O(n)), else from the root.
    private TraceSpanPage openArchivesAndPage(final PlanBDocument doc,
                                              final int shardIndex,
                                              final List<ArchiveShardRef> refs,
                                              final int i,
                                              final byte[] traceIdBytes,
                                              final List<TraceDb.ChildCursor> cursors,
                                              final List<byte[]> cursorPath,
                                              final int offset,
                                              final int limit,
                                              final String cacheKey,
                                              final TraceDb.SpanOpenTest openTest) {
        if (i >= refs.size()) {
            final TraceDb.MergedChildCursor merged = new TraceDb.MergedChildCursor(cursors);
            final TraceDb.SpanPage page;
            final Integer total;
            if (cursorPath != null) {
                // Sequential resume — cheap, no checkpoints needed.
                page = TraceDb.getSpanPage(merged, cursorPath, limit, openTest);
                final TraceDb.CheckpointIndex cached = mergedCheckpointCache.getIfPresent(cacheKey);
                total = cached != null ? cached.total() : null;
            } else if (offset > 0) {
                // Random-access jump/last — seek via the merged checkpoint index (built + cached once).
                final TraceDb.CheckpointIndex index = mergedCheckpointCache.getOrBuild(
                        cacheKey, () -> TraceDb.buildCheckpoints(merged, openTest));
                page = TraceDb.getSpanPageAtOffset(merged, index, offset, limit, openTest);
                total = index.total();
            } else {
                // First page (offset 0, no cursor) — walk from the root; don't force a build.
                page = TraceDb.getSpanPage(merged, null, limit, openTest);
                final TraceDb.CheckpointIndex cached = mergedCheckpointCache.getIfPresent(cacheKey);
                total = cached != null ? cached.total() : null;
            }
            return toSpanPage(page, true, total);
        }
        return shardManager.getArchive(doc, shardIndex, refs.get(i), archiveReader -> {
            if (!(archiveReader instanceof final TraceDb archiveDb)) {
                throw new IllegalStateException("Unexpected value: " + archiveReader);
            }
            return archiveDb.read(archiveTxn -> {
                cursors.add(new TraceDb.SingleStoreChildCursor(archiveDb, archiveTxn, traceIdBytes));
                return openArchivesAndPage(doc, shardIndex, refs, i + 1, traceIdBytes, cursors,
                        cursorPath, offset, limit, cacheKey, openTest);
            });
        });
    }

    protected TraceSpanPage toSpanPage(final TraceDb.SpanPage page,
                                       final boolean sequential,
                                       final Integer totalSpans) {
        final List<TraceSpanRow> rows = new ArrayList<>();
        boolean more = false;
        List<byte[]> next = null;
        if (page != null) {
            if (page.rows() != null) {
                for (final TraceDb.SpanRow row : page.rows()) {
                    rows.add(new TraceSpanRow(row.span(), row.depth(), row.hasChildren()));
                }
            }
            more = page.more();
            next = page.nextCursor();
        }
        // A page with more rows to come exposes a resume cursor, so next/prev stay cheap after any page,
        // including one reached by offset — archiveSpanPage passes sequential true on every path.
        final String nextCursor = (sequential && more) ? encodeCursor(next) : null;
        return new TraceSpanPage(rows, more, nextCursor, totalSpans);
    }

    // Cache key for a trace's merged checkpoint index: identifies the trace and the versions of every
    // contributing archive bucket, so the entry self-invalidates when any of them changes.
    //
    // Deliberately does NOT include the holding shard's version. Spans are read from buckets only, and
    // the shard is republished every merge cycle, so folding its version in here would discard every
    // cached checkpoint index once a minute for no reason.
    private String checkpointCacheKey(final PlanBDocument doc,
                                      final int shardIndex,
                                      final String traceId,
                                      final List<ArchiveShardRef> refs) {
        final StringBuilder sb = new StringBuilder()
                .append(doc == null ? "" : doc.getUuid()).append('_')
                .append(shardIndex).append('_')
                .append(traceId);
        for (final ArchiveShardRef ref : refs) {
            sb.append(";arch=").append(ref.dateLabel()).append('=').append(readVersion(ref.dir()));
        }
        return sb.toString();
    }

    private static String readVersion(final Path dir) {
        final Path versionFile = dir.resolve(PlanBConstants.VERSION_FILE_NAME);
        try {
            return Files.exists(versionFile) ? Files.readString(versionFile).trim() : "";
        } catch (final IOException e) {
            return "";
        }
    }

    protected int archiveShardIndex(final PlanBDocument doc, final String traceId) {
        if (doc == null || doc.getSharedPath() == null || doc.getShardCount() <= 0) {
            return -1;
        }
        return ShardKeyRouter.computeShardIndex(traceId, doc.getShardCount());
    }

    protected List<ArchiveShardRef> relevantArchiveShards(final PlanBDocument doc,
                                                          final String traceId,
                                                          final long fromMs,
                                                          final long toMs) {
        final int shardIndex = archiveShardIndex(doc, traceId);
        if (shardIndex < 0) {
            return Collections.emptyList();
        }
        return archiveShardLocator.findRelevantShards(doc, shardIndex, fromMs, toMs);
    }

    private static String encodeCursor(final List<byte[]> path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        return Base64.getEncoder().encodeToString(TraceDb.encodePath(path));
    }

    private static List<byte[]> decodeCursor(final String cursor) {
        if (cursor == null || cursor.isEmpty()) {
            return null;
        }
        return TraceDb.decodePath(Base64.getDecoder().decode(cursor));
    }

    // Deterministic cache-key suffix for a GroupSelection (empty when fully expanded / null). Sorted so it
    // is stable regardless of set iteration order.
    private static String groupSelectionKey(final GroupSelection groupSelection) {
        if (groupSelection == null) {
            return "";
        }
        final String open = groupSelection.getOpenGroups().stream().sorted().collect(Collectors.joining(","));
        final String closed = groupSelection.getClosedGroups().stream().sorted()
                .collect(Collectors.joining(","));
        return "|gs=" + groupSelection.getExpandedDepth() + ";o=" + open + ";c=" + closed;
    }

    // Merges the spans of several partial Traces for the same traceId (one per contributing archive
    // bucket) into one, de-duplicating by spanId. Each source's parentSpanIdMap keying is preserved,
    // so the root resolves normally if its span is present. Returns null if there are no spans anywhere.
    protected Trace mergeTraces(final String traceId, final List<Trace> sources) {
        final Map<String, List<Span>> merged = new HashMap<>();
        final Set<String> seenSpanIds = new HashSet<>();
        for (final Trace source : sources) {
            if (source == null || source.getParentSpanIdMap() == null) {
                continue;
            }
            for (final Map.Entry<String, List<Span>> entry : source.getParentSpanIdMap().entrySet()) {
                for (final Span span : entry.getValue()) {
                    if (seenSpanIds.add(span.getSpanId())) {
                        merged.computeIfAbsent(entry.getKey(), k -> new ArrayList<>()).add(span);
                    }
                }
            }
        }
        if (merged.isEmpty()) {
            return null;
        }
        return Trace.builder().traceId(traceId).parentSpanIdMap(merged).build();
    }

    protected Trace getTraceFromArchive(final ArchiveShardRef ref,
                                        final int shardIndex,
                                        final byte[] traceIdBytes,
                                        final PlanBDocument doc) {
        try {
            return shardManager.getArchive(doc, shardIndex, ref, reader -> {
                if (reader instanceof final TraceDb traceDb) {
                    return traceDb.findTrace(traceIdBytes).orElse(null);
                }
                throw new IllegalStateException("Unexpected value: " + reader);
            });
        } catch (final Exception e) {
            LOGGER.error(() -> "Error reading trace from archive shard " + ref.dateLabel() +
                    " for doc " + doc.getName() + ": " + e.getMessage(), e);
            return null;
        }
    }

    // Widest window a single request may cover, and the window used when a request supplies no time
    // range at all. Bounded because every archive bucket the window overlaps is copied to local disk
    // to be read, so the cost of one request grows with the width of its window.
    protected static final long DEFAULT_MAX_WINDOW_MS = 24L * 60 * 60 * 1000;

    // The doc's maxQueryTimeRange when one is set, else DEFAULT_MAX_WINDOW_MS.
    protected static long maxWindowMs(final PlanBDocument doc) {
        if (doc.getSettings() instanceof final TraceSettings ts && ts.getMaxQueryTimeRange() != null) {
            return ts.getMaxQueryTimeRange().getApproxMillis();
        }
        return DEFAULT_MAX_WINDOW_MS;
    }

    @Nullable
    protected static TimeFilter resolveTimeFilter(@Nullable final TimeRange timeRange) {
        if (timeRange == null) {
            return null;
        }
        return DateExpressionParser.getTimeFilter(timeRange, DateTimeSettings.builder().build());
    }

    // Bucket widths the histogram may use, narrowest first. The width is chosen from this ladder
    // rather than by dividing the window, because a relative range like day() to now() grows by a
    // millisecond every millisecond: a derived width would grow with it, every bucket edge would
    // creep, and traces near an edge would move between buckets on each request with no new data.
    // A ladder width only changes when the window crosses a rung, so the same data gives the same
    // histogram.
    private static final long[] BUCKET_WIDTHS_MS = {
            100L,
            500L,
            1_000L,
            5_000L,
            10_000L,
            30_000L,
            60_000L,
            5L * 60_000,
            10L * 60_000,
            15L * 60_000,
            30L * 60_000,
            60L * 60_000,
            3L * 60 * 60_000,
            6L * 60 * 60_000,
            12L * 60 * 60_000,
            24L * 60 * 60_000,
    };

    // Resolves the histogram window + equal-bucket layout, or an unavailable spec when the range is
    // unbounded or wider than maxWindowMs (so a wide/all-time range never scans).
    protected static HistogramSpec histogramSpec(final TimeRange timeRange,
                                                 final int bucketCount,
                                                 final PlanBDocument doc) {
        final long maxWindowMs = maxWindowMs(doc);
        final TimeFilter timeFilter = resolveTimeFilter(timeRange);
        // Judged on the window as asked for, before it is rounded out to whole buckets, so rounding
        // can never carry a request over the limit.
        if (timeFilter == null || timeFilter.getTo() - timeFilter.getFrom() > maxWindowMs) {
            return new HistogramSpec(false, maxWindowMs, null, 0L, 0L, 0L, 0);
        }
        final int requestedBuckets = Math.max(1, bucketCount);
        final long fromMs = timeFilter.getFrom();
        final long span = Math.max(1L, timeFilter.getTo() - fromMs);
        final long bucketWidthMs = bucketWidth(span, requestedBuckets);
        // Enough whole buckets to reach the requested end, so the axis ends on or after it. Ending it
        // early would leave the newest traces out of the histogram while the list still listed them.
        final int nBuckets = (int) Math.max(1L, (span + bucketWidthMs - 1) / bucketWidthMs);
        final long toMs = fromMs + (long) nBuckets * bucketWidthMs;
        // Layout and scan range are not the same window. The layout (origin, width, bucket count) is
        // rounded out so the axis holds still between requests; the scan stays on the window as asked
        // for, which is the one the traces list uses. Handing the rounded end to TraceDb.histogram
        // instead would count traces past the requested end - its scan bound is inclusive and
        // addToBucket folds anything beyond the last edge into the final bucket - so on a range that
        // ends in the past the last bar would gain traces the list leaves out. fromMs is not rounded,
        // so this filter already carries the bucket origin the layout is built from.
        return new HistogramSpec(true, maxWindowMs, timeFilter,
                fromMs, toMs, bucketWidthMs, nBuckets);
    }

    // Narrowest ladder width that fits the span into requestedBuckets, else the widest on offer.
    private static long bucketWidth(final long span, final int requestedBuckets) {
        final long minWidth = (span + requestedBuckets - 1) / requestedBuckets;
        for (final long width : BUCKET_WIDTHS_MS) {
            if (width >= minWidth) {
                return width;
            }
        }
        return BUCKET_WIDTHS_MS[BUCKET_WIDTHS_MS.length - 1];
    }

    protected static TraceHistogram assembleHistogram(final HistogramSpec spec, final long[] totals) {
        final List<Long> counts = new ArrayList<>(spec.nBuckets());
        for (int b = 0; b < spec.nBuckets(); b++) {
            counts.add(totals[b]);
        }
        // Already on the narrowest rung, so narrowing the range to one bucket would only come back as
        // that same single bucket. The client decides nothing here; it is told.
        final boolean drillable = spec.bucketWidthMs() > BUCKET_WIDTHS_MS[0];
        return new TraceHistogram(
                true, spec.fromMs(), spec.toMs(), spec.bucketWidthMs(), spec.maxWindowMs(),
                counts, drillable);
    }

    protected record HistogramSpec(boolean available,
                                   long maxWindowMs,
                                   TimeFilter timeFilter,
                                   long fromMs,
                                   long toMs,
                                   long bucketWidthMs,
                                   int nBuckets) {

    }
}
