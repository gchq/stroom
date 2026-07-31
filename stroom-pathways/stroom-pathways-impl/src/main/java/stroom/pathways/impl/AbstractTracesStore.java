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
import stroom.pathways.shared.TraceSpanPage;
import stroom.pathways.shared.TraceSpanRow;
import stroom.pathways.shared.TracesStore;
import stroom.pathways.shared.otel.trace.Span;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.planb.impl.PlanBConstants;
import stroom.planb.impl.data.archive.ArchiveShardLocator;
import stroom.planb.impl.data.archive.ArchiveShardRef;
import stroom.planb.impl.data.shard.ShardManager;
import stroom.planb.impl.db.ShardKeyRouter;
import stroom.planb.impl.db.trace.TraceDb;
import stroom.planb.impl.serde.trace.HexStringUtil;
import stroom.planb.shared.ArchivalGranularity;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Shared machinery for the node-local trace query implementations ({@link RestTracesStore} and
 * {@link SharedFileTracesStore}). Holds the single-trace reads (get trace / spans / overview) — which are
 * identical for both storage layouts because the archive-merge step self-selects on {@code sharedPath} — plus
 * the helpers both fan-out implementations need. The two operations that genuinely differ between the layouts,
 * {@code findTraces} and {@code getTraceHistogram}, are left abstract.
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

    // Reads the trace from the shard only, returning empty when it is not present there. A present
    // root means the trace was not archived (archival removes the root with the trace).
    protected Optional<Trace> readTrace(final GetTraceRequest request) {
        final byte[] traceIdBytes = HexStringUtil.decode(request.getTraceId());
        return shardManager.get(request.getDataSourceRef().getName(), request.getTraceId(), reader -> {
            if (reader instanceof final TraceDb traceDb) {
                return traceDb.findTrace(traceIdBytes);
            }
            throw new IllegalStateException("Unexpected value: " + reader);
        });
    }

    protected boolean hasRoot(final String docName, final String traceId, final byte[] traceIdBytes) {
        return Boolean.TRUE.equals(shardManager.get(docName, traceId, reader -> {
            if (reader instanceof final TraceDb traceDb) {
                return traceDb.rootSpan(traceIdBytes).isPresent();
            }
            throw new IllegalStateException("Unexpected value: " + reader);
        }));
    }

    // Expand/collapse: the client sends a GroupSelection only when the view actually prunes something
    // (a collapsed span or a reduced expand-level); null ⇒ fully expanded ⇒ the unfiltered walk, which
    // keeps the fast on-disk-checkpoint path. The "group key" is the span's spanId (hex).
    protected TraceDb.SpanOpenTest openTest(final GroupSelection groupSelection) {
        return groupSelection == null
                ? TraceDb.SpanOpenTest.ALL
                : (spanId, depth) -> groupSelection.isGroupOpen(HexStringUtil.encode(spanId), depth);
    }

    // Pages a rooted trace wholly from the shard: fully expanded via the on-disk checkpoints
    // (exact TraceRoot total, no total sent); pruned via a filtered in-memory checkpoint index (cached by
    // trace + shard version + selection) whose total reflects the collapsed view.
    protected TraceSpanPage rootedSpanPage(final GetSpansRequest request,
                                               final GroupSelection groupSelection,
                                               final TraceDb.SpanOpenTest openTest) {
        final byte[] traceIdBytes = HexStringUtil.decode(request.getTraceId());
        final String docName = request.getDataSourceRef().getName();

        if (groupSelection == null) {
            final TraceDb.SpanPage page = shardManager.get(docName, request.getTraceId(), reader -> {
                if (reader instanceof final TraceDb traceDb) {
                    return traceDb.getSpanPageAtOffset(
                            traceIdBytes, request.getOffset(), request.getLimit());
                }
                throw new IllegalStateException("Unexpected value: " + reader);
            });
            return toSpanPage(page, false, null);
        }

        final PlanBDocument doc = getPlanBDoc(request.getDataSourceRef());
        final int shardIndex = archiveShardIndex(doc, request.getTraceId());
        final String cacheKey = checkpointCacheKey(doc, shardIndex, request.getTraceId(), List.of())
                + groupSelectionKey(groupSelection);
        return shardManager.get(docName, request.getTraceId(), reader -> {
            if (!(reader instanceof final TraceDb traceDb)) {
                throw new IllegalStateException("Unexpected value: " + reader);
            }
            return traceDb.read(txn -> {
                final TraceDb.ChildCursor cursor =
                        new TraceDb.SingleStoreChildCursor(traceDb, txn, traceIdBytes);
                final TraceDb.CheckpointIndex index = mergedCheckpointCache.getOrBuild(
                        cacheKey, () -> TraceDb.buildCheckpoints(cursor, openTest));
                final TraceDb.SpanPage page = TraceDb.getSpanPageAtOffset(
                        cursor, index, request.getOffset(), request.getLimit(), openTest);
                return toSpanPage(page, false, index.total());
            });
        });
    }

    // Pages a trace whose root is not in the shard by merging the shard with the supplied archive
    // bucket(s) as one pre-order tree with a sequential cursor. Passing empty refs serves the shard alone,
    // which is the degenerate case for a non-shared store.
    protected TraceSpanPage mergedSpanPage(final GetSpansRequest request,
                                           final List<ArchiveShardRef> refs,
                                           final TraceDb.SpanOpenTest openTest) {
        final byte[] traceIdBytes = HexStringUtil.decode(request.getTraceId());
        final String docName = request.getDataSourceRef().getName();
        final PlanBDocument doc = getPlanBDoc(request.getDataSourceRef());
        final int shardIndex = archiveShardIndex(doc, request.getTraceId());
        final List<byte[]> cursorPath = decodeCursor(request.getCursor());
        final String cacheKey = checkpointCacheKey(doc, shardIndex, request.getTraceId(), refs)
                + groupSelectionKey(request.getGroupSelection());

        return shardManager.get(docName, request.getTraceId(), reader -> {
            if (!(reader instanceof final TraceDb db)) {
                throw new IllegalStateException("Unexpected value: " + reader);
            }
            return db.read(txn -> {
                final List<TraceDb.ChildCursor> cursors = new ArrayList<>();
                cursors.add(new TraceDb.SingleStoreChildCursor(db, txn, traceIdBytes));
                return openArchivesAndPage(doc, shardIndex, refs, 0, traceIdBytes, cursors,
                        cursorPath, request.getOffset(), request.getLimit(), cacheKey, openTest);
            });
        });
    }

    // Reads the downsampled overview spans (one streaming pass, bounded memory) from the shard only,
    // keyed by spanId with first-write-wins. Extents are supplied by the caller from the already-known
    // TraceRoot, so the axis is whole before any span is loaded.
    protected Map<String, Span> readOverview(final GetTraceOverviewRequest request) {
        final byte[] traceIdBytes = HexStringUtil.decode(request.getTraceId());
        final String docName = request.getDataSourceRef().getName();

        final Map<String, Span> bySpanId = new LinkedHashMap<>();
        final List<Span> spans = shardManager.get(docName, request.getTraceId(), reader -> {
            if (reader instanceof final TraceDb traceDb) {
                return traceDb.getOverviewSpans(
                        traceIdBytes, request.getFromMs(), request.getToMs(), request.getMaxBars());
            }
            throw new IllegalStateException("Unexpected value: " + reader);
        });
        if (spans != null) {
            spans.forEach(s -> bySpanId.putIfAbsent(s.getSpanId(), s));
        }
        return bySpanId;
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
        // The merged (split) path always exposes a resume cursor so next/prev stay cheap after any page;
        // the offset path leaves it null (the client uses offsets there).
        final String nextCursor = (sequential && more) ? encodeCursor(next) : null;
        return new TraceSpanPage(rows, more, nextCursor, totalSpans);
    }

    // Cache key for a split trace's merged checkpoint index: identifies the trace and the versions of
    // every contributing store (shard + archive buckets), so the entry self-invalidates when any of
    // them changes.
    private String checkpointCacheKey(final PlanBDocument doc,
                                      final int shardIndex,
                                      final String traceId,
                                      final List<ArchiveShardRef> refs) {
        final StringBuilder sb = new StringBuilder()
                .append(doc == null ? "" : doc.getUuid()).append('_')
                .append(shardIndex).append('_')
                .append(traceId).append("|shard=")
                .append(shardVersion(doc, shardIndex));
        for (final ArchiveShardRef ref : refs) {
            sb.append(";arch=").append(ref.dateLabel()).append('=').append(readVersion(ref.dir()));
        }
        return sb.toString();
    }

    private String shardVersion(final PlanBDocument doc, final int shardIndex) {
        if (doc == null || doc.getSharedPath() == null || shardIndex < 0) {
            return "";
        }
        return readVersion(Path.of(doc.getSharedPath())
                .resolve(PlanBConstants.SHARDS_DIR_NAME)
                .resolve(doc.getUuid())
                .resolve(String.format("%04d", shardIndex)));
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

    // Merges the spans of several partial Traces for the same traceId (shard fragment + archive
    // buckets) into one, de-duplicating by spanId. Each source's parentSpanIdMap keying is preserved,
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

    // Widest window the histogram will serve: one archival-granularity bucket (so at most 1-2 archive
    // buckets are ever touched). Defaults to DAY when archival is not configured.
    protected static long maxHistogramWindowMs(final PlanBDocument doc) {
        ArchivalGranularity granularity = ArchivalGranularity.DAY;
        if (doc.getSettings() instanceof final TraceSettings ts
                && ts.getSharedFileStore() != null
                && ts.getSharedFileStore().getArchival() != null) {
            granularity = ts.getSharedFileStore().getArchival().getGranularity();
        }
        return switch (granularity) {
            case HOUR -> 60L * 60 * 1000;
            case DAY -> 24L * 60 * 60 * 1000;
            case WEEK -> 7L * 24 * 60 * 60 * 1000;
        };
    }

    @Nullable
    protected static TimeFilter resolveTimeFilter(@Nullable final TimeRange timeRange) {
        if (timeRange == null) {
            return null;
        }
        return DateExpressionParser.getTimeFilter(timeRange, DateTimeSettings.builder().build());
    }

    // Resolves the histogram window + equal-bucket layout for a request, or an unavailable spec when the
    // range is unbounded or wider than one archival-granularity bucket (so a wide/all-time range never scans).
    protected HistogramSpec histogramSpec(final TraceHistogramRequest request, final PlanBDocument doc) {
        final long maxWindowMs = maxHistogramWindowMs(doc);
        final TimeFilter timeFilter = resolveTimeFilter(request.getTimeRange());
        if (timeFilter == null || timeFilter.getTo() - timeFilter.getFrom() > maxWindowMs) {
            return new HistogramSpec(false, maxWindowMs, null, 0L, 0L, 0L, 0);
        }
        final long fromMs = timeFilter.getFrom();
        final long toMs = timeFilter.getTo();
        final int requestedBuckets = Math.max(1, request.getBucketCount());
        final long span = Math.max(1L, toMs - fromMs);
        final long bucketWidthMs = Math.max(1L, (span + requestedBuckets - 1) / requestedBuckets);
        final int nBuckets = (int) Math.min(
                (long) requestedBuckets, (span + bucketWidthMs - 1) / bucketWidthMs);
        return new HistogramSpec(true, maxWindowMs, timeFilter, fromMs, toMs, bucketWidthMs, nBuckets);
    }

    protected TraceHistogram assembleHistogram(final HistogramSpec spec, final long[] totals) {
        final List<Long> counts = new ArrayList<>(spec.nBuckets());
        for (int b = 0; b < spec.nBuckets(); b++) {
            counts.add(totals[b]);
        }
        return new TraceHistogram(
                true, spec.fromMs(), spec.toMs(), spec.bucketWidthMs(), spec.maxWindowMs(), counts);
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
