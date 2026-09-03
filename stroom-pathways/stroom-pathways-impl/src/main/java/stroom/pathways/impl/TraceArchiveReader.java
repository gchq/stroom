/*
 * Copyright 2016-2026 Crown Copyright
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
import stroom.pathways.shared.TraceSpanPage;
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
import stroom.query.api.DateTimeSettings;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.common.v2.FieldProviderImpl;
import stroom.query.common.v2.SimpleStringExpressionParser.FieldProvider;
import stroom.query.common.v2.ValueFunctionFactoriesImpl;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Reads a trace store's archive buckets: resolving the document, locating the buckets a window covers,
 * opening one for reading, and paging a trace's span tree across the buckets it spans.
 *
 * <p>Every read of a bucket goes through {@link #readArchive}, which is also where a store that turns
 * out not to hold traces is rejected.
 */
@Singleton
class TraceArchiveReader {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TraceArchiveReader.class);

    // Quick-filter field mappers: bare terms fuzzy-match either (OR'd); 'operation:'/'traceid:' qualify;
    // 'iserror:' matches the boolean error flag (iserror:true / iserror:false).
    private static final ValueFunctionFactoriesImpl<TraceRoot> FILTER_VALUE_FUNCTIONS =
            new ValueFunctionFactoriesImpl<TraceRoot>()
                    .put(FindTraceCriteria.FIELD_DEF_OPERATION, TraceRoot::getName)
                    .put(FindTraceCriteria.FIELD_DEF_TRACE_ID, TraceRoot::getTraceId)
                    .put(FindTraceCriteria.FIELD_DEF_IS_ERROR, root -> Boolean.toString(root.isError()));
    private static final FieldProvider FILTER_FIELD_PROVIDER =
            new FieldProviderImpl(FindTraceCriteria.FIELD_DEFINITIONS);

    private final TracesDocLoader docLoader;
    private final ShardManager shardManager;
    private final ArchiveShardLocator archiveShardLocator;
    private final MergedCheckpointCache mergedCheckpointCache;
    private final ExpressionPredicateFactory expressionPredicateFactory;

    @Inject
    TraceArchiveReader(final TracesDocLoader docLoader,
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

    PlanBDocument getPlanBDoc(final DocRef docRef) {
        return docLoader.getPlanBDoc(docRef);
    }

    // Builds the quick-filter predicate for a TraceRoot from the criteria's filter string, or null when
    // the filter is blank (so the query can use its O(1)/key-only count fast paths).
    Predicate<TraceRoot> buildFilterPredicate(final String filter) {
        if (NullSafe.isBlankString(filter)) {
            return null;
        }
        return expressionPredicateFactory.create(
                filter, FILTER_FIELD_PROVIDER, FILTER_VALUE_FUNCTIONS, DateTimeSettings.builder().build());
    }

    int archiveShardIndex(final PlanBDocument doc, final String traceId) {
        if (doc == null || doc.getSharedPath() == null || doc.getShardCount() <= 0) {
            return -1;
        }
        return ShardKeyRouter.computeShardIndex(traceId, doc.getShardCount());
    }

    List<ArchiveShardRef> relevantArchiveShards(final PlanBDocument doc,
                                                final String traceId,
                                                final long fromMs,
                                                final long toMs) {
        final int shardIndex = archiveShardIndex(doc, traceId);
        if (shardIndex < 0) {
            return Collections.emptyList();
        }
        return shardsForIndex(doc, shardIndex, fromMs, toMs);
    }

    List<ArchiveShardRef> shardsForIndex(final PlanBDocument doc,
                                         final int shardIndex,
                                         final long fromMs,
                                         final long toMs) {
        return archiveShardLocator.findRelevantShards(doc, shardIndex, fromMs, toMs);
    }

    /**
     * Opens one archive bucket and hands it to {@code function}. The bucket is a cached, read-only,
     * idle-evicted local copy, and the shard holds its read lock for the whole call, so everything one
     * call reads comes from the same copy.
     */
    <R> R readArchive(final PlanBDocument doc,
                      final int shardIndex,
                      final ArchiveShardRef ref,
                      final Function<TraceDb, R> function) {
        return shardManager.getArchive(doc, shardIndex, ref, reader -> {
            if (reader instanceof final TraceDb traceDb) {
                return function.apply(traceDb);
            }
            throw new IllegalStateException("Unexpected value: " + reader);
        });
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
    TraceSpanPage archiveSpanPage(final GetSpansRequest request,
                                  final List<ArchiveShardRef> refs,
                                  final TraceDb.SpanOpenTest openTest) {
        if (refs.isEmpty()) {
            return new TraceSpanPage(List.of(), false, null, 0);
        }
        final byte[] traceIdBytes = HexStringUtil.decode(request.getTraceId());
        final PlanBDocument doc = getPlanBDoc(request.getDataSourceRef());
        final int shardIndex = archiveShardIndex(doc, request.getTraceId());
        final List<byte[]> cursorPath = SpanPaging.decodeCursor(request.getCursor());
        final String cacheKey = checkpointCacheKey(doc, shardIndex, request.getTraceId(), refs)
                + SpanPaging.groupSelectionKey(request.getGroupSelection());
        return openArchivesAndPage(doc, shardIndex, refs, 0, traceIdBytes, new ArrayList<>(),
                cursorPath, request.getOffset(), request.getLimit(), cacheKey, openTest);
    }

    // Recursively nests the readArchive callbacks (each holds the archive shard's read lock + a read txn)
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
            return SpanPaging.toSpanPage(page, true, total);
        }
        return readArchive(doc, shardIndex, refs.get(i), archiveDb -> archiveDb.read(archiveTxn -> {
            cursors.add(new TraceDb.SingleStoreChildCursor(archiveDb, archiveTxn, traceIdBytes));
            return openArchivesAndPage(doc, shardIndex, refs, i + 1, traceIdBytes, cursors,
                    cursorPath, offset, limit, cacheKey, openTest);
        }));
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

    Trace getTraceFromArchive(final ArchiveShardRef ref,
                              final int shardIndex,
                              final byte[] traceIdBytes,
                              final PlanBDocument doc) {
        try {
            return readArchive(doc, shardIndex, ref, traceDb -> traceDb.findTrace(traceIdBytes).orElse(null));
        } catch (final Exception e) {
            LOGGER.error(() -> "Error reading trace from archive shard " + ref.dateLabel() +
                    " for doc " + doc.getName() + ": " + e.getMessage(), e);
            return null;
        }
    }

    // Merges the spans of several partial Traces for the same traceId (one per contributing archive
    // bucket) into one, de-duplicating by spanId. Each source's parentSpanIdMap keying is preserved,
    // so the root resolves normally if its span is present. Returns null if there are no spans anywhere.
    static Trace mergeTraces(final String traceId, final List<Trace> sources) {
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
}
