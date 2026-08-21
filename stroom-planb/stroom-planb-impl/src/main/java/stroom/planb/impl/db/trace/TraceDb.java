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

package stroom.planb.impl.db.trace;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.entity.shared.ExpressionCriteria;
import stroom.lmdb.stream.LmdbEntry;
import stroom.lmdb.stream.LmdbIterable;
import stroom.lmdb.stream.LmdbKeyRange;
import stroom.lmdb.stream.LmdbStream;
import stroom.lmdb2.KV;
import stroom.pathways.shared.FindTraceCriteria;
import stroom.pathways.shared.GetTraceRequest;
import stroom.pathways.shared.TracesResultPage;
import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.otel.trace.Span;
import stroom.pathways.shared.otel.trace.SpanStatus;
import stroom.pathways.shared.otel.trace.StatusCode;
import stroom.pathways.shared.otel.trace.Trace;
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.planb.impl.data.archive.BucketGranularityUtil;
import stroom.planb.impl.data.value.SpanKV;
import stroom.planb.impl.db.AbstractDb;
import stroom.planb.impl.db.Count;
import stroom.planb.impl.db.HashClashCommitRunnable;
import stroom.planb.impl.db.LmdbWriter;
import stroom.planb.impl.db.PlanBEnv;
import stroom.planb.impl.db.PlanBSearchHelper;
import stroom.planb.impl.db.PlanBSearchHelper.Context;
import stroom.planb.impl.db.PlanBSearchHelper.Converter;
import stroom.planb.impl.db.PlanBSearchHelper.LazyKV;
import stroom.planb.impl.db.PlanBSearchHelper.ValuesExtractor;
import stroom.planb.impl.db.SchemaInfo;
import stroom.planb.impl.db.UsedLookupsRecorder;
import stroom.planb.impl.serde.KeySerde;
import stroom.planb.impl.serde.Serde;
import stroom.planb.impl.serde.trace.HexStringUtil;
import stroom.planb.impl.serde.trace.LookupSerde;
import stroom.planb.impl.serde.trace.LookupSerdeImpl;
import stroom.planb.impl.serde.trace.SpanKey;
import stroom.planb.impl.serde.trace.SpanKeySerde;
import stroom.planb.impl.serde.trace.SpanValue;
import stroom.planb.impl.serde.trace.SpanValueSerde;
import stroom.planb.shared.BucketGranularity;
import stroom.planb.shared.PlanBDocument;
import stroom.planb.shared.StateKeySchema;
import stroom.planb.shared.StateValueSchema;
import stroom.planb.shared.TraceSettings;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.TimeFilter;
import stroom.query.common.v2.DateExpressionParser;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.Values;
import stroom.query.language.functions.ValuesConsumer;
import stroom.util.io.FileUtil;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageResponse;

import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.LmdbNativeException;
import org.lmdbjava.PutFlags;
import org.lmdbjava.Txn;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Stores OpenTelemetry spans, one LMDB environment per shard.
 *
 * <p>Spans are the only thing written. Everything a query needs about a <em>trace</em> is derived and
 * kept alongside them, because a trace arrives a span at a time, in any order, and re-deriving it from
 * the spans would cost a scan per arrival:
 * <ul>
 *   <li>the span DBI itself, keyed {@code traceId ∥ parentSpanId ∥ startTime ∥ spanId} so a node's
 *       children are a prefix scan in start-time order;</li>
 *   <li>{@code trace-stats} — running counts per trace, folded in by {@link #recordNewSpan};</li>
 *   <li>{@code trace-roots} — the {@link TraceRoot} each list row is built from, derived from the stats
 *       by {@link #buildRootFromStats};</li>
 *   <li>one DBI per {@link TraceSecondaryIndex}, so a sorted page is a range scan rather than a
 *       full scan and sort;</li>
 *   <li>{@code trace-roots-merge-time}, which starts the grace-period clock the pathways processor
 *       waits out.</li>
 * </ul>
 *
 * <p>Spans land in a holding-area shard, and every merge cycle {@link #publish} moves them into the
 * archive bucket named after their root's start time. Queries read the buckets, never the holding area.
 */
public class TraceDb extends AbstractDb<SpanKey, SpanValue> {

    private static final int CURRENT_SCHEMA_VERSION = 1;
    /**
     * The lookup named-DBs copied verbatim into every archive bucket so the UID / hash integers
     * embedded in archived span values stay decodable.
     *
     * <p>The {@code trace-roots*} DBIs are deliberately absent: a staged delta carries spans only and
     * the bucket derives its own roots. Copying them would put every root into every bucket, so one
     * root would appear in several archives and no longer line up with the start-time bucket label
     * {@link stroom.planb.impl.data.archive.ArchiveShardLocator} selects on.
     */
    private static final List<String> LOOKUP_DBI_NAMES = List.of(
            "lookup-keyToUid", "lookup-uidToKey", "lookup-info",
            "lookup-hash");

    /** Number of bytes used for the traceId suffix appended to every secondary-index key. */
    private static final int TRACE_ID_BYTES = 16;

    /** Span-key layout (see {@link stroom.planb.impl.serde.trace.SpanKeySerde}):
     * {@code traceId[16] ∥ parentSpanId[8] ∥ startTime[8] ∥ spanId[8]}. A span's children share the
     * prefix {@code traceId ∥ thisSpanId}; within that prefix they sort by {@code startTime ∥ spanId}
     * (the "locator"), i.e. start-time order. The root's parentSpanId is all-zero. */
    private static final int SPAN_ID_BYTES = 8;
    private static final int START_TIME_BYTES = 8;
    // A child's position within its parent: startTime[8] ∥ spanId[8]. The DFS cursor path is a list of
    // these, so a sibling scan can resume "after" a given child in start-time order.
    private static final int LOCATOR_BYTES = START_TIME_BYTES + SPAN_ID_BYTES;
    private static final byte[] NO_PARENT_SPAN_ID = new byte[SPAN_ID_BYTES];
    // All-0xFF locator — the inclusive upper bound of a parent's child key range.
    private static final byte[] LOCATOR_MAX = ffBytes(LOCATOR_BYTES);

    private static byte[] ffBytes(final int n) {
        final byte[] b = new byte[n];
        Arrays.fill(b, (byte) 0xFF);
        return b;
    }

    /**
     * Trace span-count at or below which {@link #buildRootFromStats} recomputes depth every merge
     * cycle (the bounded DFS is cheap at this size). Above it, depth is refreshed only when the
     * span count doubles — keeping the DFS off the hot path for the huge, ever-growing traces.
     */
    private static final long DEPTH_EXACT_SPAN_THRESHOLD = 10_000L;

    // Rows between snapshots in a CheckpointIndex — the cost of a random-access seek is one interval
    // of walking plus the page.
    private static final int CHECKPOINT_INTERVAL = 1_000;

    // Above this a trace's depth is walked iteratively rather than by recursing through descend().
    private static final long LARGE_TRACE_SPANS = 10_000L;

    // A fresh instance each call: a shared buffer would share its position/limit across concurrent use.
    private static ByteBuffer emptyValue() {
        return ByteBuffer.allocateDirect(0);
    }

    private final ByteBufferFactory byteBufferFactory;
    private final KeySerde<SpanKey> keySerde;
    private final Serde<SpanValue> valueSerde;

    /** Interns span names so an unbounded name never lands raw in a length-capped LMDB key. */
    private final LookupSerde lookupSerde;
    // Typed reference to the same object as valueSerde, held so that
    // publish / runRetention can call readInsertTime() without
    // going through the UID lookup table.
    private final SpanValueSerde spanValueSerde;
    private final UsedLookupsRecorder keyRecorder;
    private final UsedLookupsRecorder valueRecorder;
    private final Dbi<ByteBuffer> traceRootsDbi;
    /**
     * Traces whose root span has been received, in the order this node received them.
     * Key: {@code mergeTimeMs[8] big-endian ∥ traceId[16]}, value empty.
     *
     * <p>Stamped with the receiving node's clock rather than the span's claimed end time, so the
     * grace period {@code PathwaysProcessor} waits out is measured from arrival and is unaffected by
     * out-of-order delivery. Written by {@link #insert} and {@link #merge}; cleared by
     * {@link #runRetention} past the cut-off and when publishing retires the trace.
     */
    private final Dbi<ByteBuffer> traceRootsMergeTimeDbi;

    // -----------------------------------------------------------------------
    // Secondary sort indexes  (key = sortField ∥ traceId[16], value = empty)
    // Each enables an O(offset+length) sorted range scan via LmdbStream,
    // avoiding a full traceRootsDbi scan for every findTraces() call.
    // The set of indexes is defined once by TraceSecondaryIndex; this map opens
    // one DBI per index so the write/delete/update/query paths can iterate them
    // generically rather than naming each index individually.
    // -----------------------------------------------------------------------
    private final Map<TraceSecondaryIndex, Dbi<ByteBuffer>> secondaryIndexDbis;

    private final TraceRootKeySerde traceRootKeySerde;
    private final TraceRootValueSerde traceRootValueSerde;

    /**
     * Per-trace incremental aggregate accumulator: {@code traceId → TraceStats}. Updated as each
     * new span is inserted so {@link #mergeComplete()} reads O(1) stats rather than re-scanning
     * every span. See {@link TraceStats}.
     */
    private final Dbi<ByteBuffer> traceStatsDbi;
    /**
     * Distinct service-name set per trace: {@code traceId[16] ∥ internedName → ∅}. Lets a
     * genuinely-new name be detected (via {@link PutFlags#MDB_NOOVERWRITE}) so the cumulative
     * {@code serviceCount} in {@link TraceStats} is exact. The name goes through
     * {@link #lookupSerde} so an unbounded one cannot exceed the LMDB key limit — see
     * {@link #recordNewSpan}.
     */
    private final Dbi<ByteBuffer> traceServiceNamesDbi;
    private final TraceStatsSerde traceStatsSerde;

    /**
     * Trace IDs (hex) whose stored root should be recomputed over the fully-merged span set at
     * the end of the current merge cycle — see {@link #mergeComplete()}. Populated during
     * {@link #merge}. The per-batch root copy in merge() reflects only that batch's spans, so
     * depth/services/totalSpans must be re-derived once all batches are present.
     */
    private final Set<String> pendingRootRebuilds = ConcurrentHashMap.newKeySet();

    /** Spans to accept per trace, or 0 for unlimited. See {@link #isOverSpanLimit}. */
    private final long maxSpansPerTrace;

    /**
     * How this store labels the buckets it publishes into. Read from settings here rather than passed
     * to {@link #publish}, because how a store type buckets its data is its own business — the
     * caller only supplies the directory to build the buckets under.
     */
    private final BucketGranularity granularity;

    private TraceDb(final PlanBEnv env,
                    final ByteBuffers byteBuffers,
                    final ByteBufferFactory byteBufferFactory,
                    final PlanBDocument doc,
                    final TraceSettings settings,
                    final KeySerde<SpanKey> keySerde,
                    final Serde<SpanValue> valueSerde,
                    final LookupSerde lookupSerde,
                    final HashClashCommitRunnable hashClashCommitRunnable,
                    final boolean hasSecondaryIndexes) {
        super(env,
                byteBuffers,
                doc,
                true,
                hashClashCommitRunnable,
                new SchemaInfo(
                        CURRENT_SCHEMA_VERSION,
                        JsonUtil.writeValueAsString(new StateKeySchema.Builder().build()),
                        JsonUtil.writeValueAsString(new StateValueSchema.Builder().build())));
        this.byteBufferFactory = byteBufferFactory;
        this.maxSpansPerTrace = settings.getEffectiveMaxSpansPerTrace();
        this.granularity = settings.getGranularity();
        this.keySerde = keySerde;
        this.valueSerde = valueSerde;
        this.lookupSerde = lookupSerde;
        this.spanValueSerde = (SpanValueSerde) valueSerde;
        this.keyRecorder = keySerde.getUsedLookupsRecorder(env);
        this.valueRecorder = valueSerde.getUsedLookupsRecorder(env);

        traceRootKeySerde = new TraceRootKeySerde(byteBuffers);
        traceRootValueSerde = new TraceRootValueSerde(byteBufferFactory);
        traceStatsSerde = new TraceStatsSerde(byteBufferFactory);
        traceRootsDbi = env.openDbi("trace-roots", DbiFlags.MDB_CREATE);
        traceRootsMergeTimeDbi = env.openDbi("trace-roots-merge-time", DbiFlags.MDB_CREATE);
        traceStatsDbi = env.openDbi("trace-stats", DbiFlags.MDB_CREATE);
        traceServiceNamesDbi = env.openDbi("trace-service-names", DbiFlags.MDB_CREATE);

        // One DBI per secondary sort index, keyed by the index definition — but only for a store that is
        // actually queried. They exist solely to serve sorted/filtered findTraces, so in a store that is
        // only ever written and merged (a holding-area shard, a stream writer env, a publish delta)
        // maintaining them is pure write amplification. Left empty there, which makes the write helpers
        // no-ops; see hasSecondaryIndexes on TraceDb.create for why this must be consistent per env.
        final Map<TraceSecondaryIndex, Dbi<ByteBuffer>> indexDbis =
                new EnumMap<>(TraceSecondaryIndex.class);
        if (hasSecondaryIndexes) {
            for (final TraceSecondaryIndex index : TraceSecondaryIndex.values()) {
                indexDbis.put(index, env.openDbi(index.dbiName(), DbiFlags.MDB_CREATE));
            }
        }
        secondaryIndexDbis = indexDbis;
    }

    /** Opens a queryable store — i.e. with the secondary sort indexes. */
    public static TraceDb create(final Path path,
                                 final ByteBuffers byteBuffers,
                                 final ByteBufferFactory byteBufferFactory,
                                 final PlanBDocument doc,
                                 final boolean readOnly) {
        return create(path, byteBuffers, byteBufferFactory, doc, readOnly, true);
    }

    /**
     * @param hasSecondaryIndexes whether this env carries the secondary sort indexes that sorted
     *                            {@code findTraces} needs. Pass {@code false} for a store that is only
     *                            written and merged, never queried, to avoid maintaining them.
     *                            <p><b>Must be consistent for every open of a given env.</b> On a
     *                            read-only env, asking for a DBI the env does not contain throws, so a
     *                            store written without indexes cannot later be opened read-only with
     *                            them. Reading with {@code false} is always safe, which is why
     *                            {@link #merge} opens its source that way regardless of how it was
     *                            written.
     */
    public static TraceDb create(final Path path,
                                 final ByteBuffers byteBuffers,
                                 final ByteBufferFactory byteBufferFactory,
                                 final PlanBDocument doc,
                                 final boolean readOnly,
                                 final boolean hasSecondaryIndexes) {
        final TraceSettings settings;
        if (doc.getSettings() instanceof final TraceSettings traceSettings) {
            settings = traceSettings;
        } else {
            settings = new TraceSettings.Builder().build();
        }

        final HashClashCommitRunnable hashClashCommitRunnable = new HashClashCommitRunnable();
        final PlanBEnv env = new PlanBEnv(path,
                settings.getMaxStoreSize(),
                27,
                readOnly,
                hashClashCommitRunnable);
        try {
            final KeySerde<SpanKey> keySerde = new SpanKeySerde(byteBuffers);
            final LookupSerdeImpl lookupSerde = new LookupSerdeImpl(env, hashClashCommitRunnable, byteBuffers);
            final Serde<SpanValue> valueSerde = new SpanValueSerde(byteBufferFactory, lookupSerde);
            return new TraceDb(
                    env,
                    byteBuffers,
                    byteBufferFactory,
                    doc,
                    settings,
                    keySerde,
                    valueSerde,
                    lookupSerde,
                    hashClashCommitRunnable,
                    hasSecondaryIndexes);
        } catch (final RuntimeException e) {
            // Close the env if we get any exceptions to prevent them staying open.
            try {
                env.close();
            } catch (final Exception e2) {
                LOGGER.debug(LogUtil.message("store={}, message={}", doc.getName(), e.getMessage()), e);
            }
            throw e;
        }
    }

    // -----------------------------------------------------------------------
    // Secondary sort index — write / delete / update helpers
    // All indexes are defined by TraceSecondaryIndex; these helpers iterate that
    // set so no per-index code is needed here. Key layouts and comparators live
    // in TraceSecondaryIndex.
    // -----------------------------------------------------------------------

    // MDB_NOOVERWRITE throughout: an identical key (same sort value AND same traceId) is already the
    // entry we would write, so ignoring it keeps the index free of duplicates.
    private void writeSecondaryIndexes(final Txn<ByteBuffer> writeTxn,
                                       final byte[] traceIdBytes,
                                       final TraceRoot root) {
        if (secondaryIndexDbis.isEmpty()) {
            return;
        }
        for (final TraceSecondaryIndex index : TraceSecondaryIndex.values()) {
            final Dbi<ByteBuffer> indexDbi = secondaryIndexDbis.get(index);
            byteBuffers.useBytes(index.key(root, traceIdBytes), buf -> {
                indexDbi.put(writeTxn, buf, emptyValue(), PutFlags.MDB_NOOVERWRITE);
            });
        }
    }

    // Call before overwriting a root: index keys carry the sort value, so the old entries are
    // unreachable from the new root and would strand.
    private void deleteSecondaryIndexes(final Txn<ByteBuffer> writeTxn,
                                        final byte[] traceIdBytes,
                                        final TraceRoot oldRoot) {
        if (secondaryIndexDbis.isEmpty()) {
            return;
        }
        for (final TraceSecondaryIndex index : TraceSecondaryIndex.values()) {
            final Dbi<ByteBuffer> indexDbi = secondaryIndexDbis.get(index);
            byteBuffers.useBytes(index.key(oldRoot, traceIdBytes), buf -> {
                indexDbi.delete(writeTxn, buf);
            });
        }
    }

    // All of a trace's spans live in one shard, so without a limit one runaway trace can fill the
    // shard's fixed-size map, after which it can accept nothing — not even the deletes that would free
    // space. Dropping the excess keeps that failure local to the one bad trace. The comparison is
    // against the cumulative count, never decremented as spans age out, so a trace that has hit the
    // limit stays closed rather than reopening under retention.
    private boolean isOverSpanLimit(final TraceStats stats) {
        return maxSpansPerTrace > 0 && stats.spanCount() >= maxSpansPerTrace;
    }

    // Flags the trace so the missing spans are visible rather than silent. Called on the first span
    // actually dropped, so the write happens once per trace rather than once per dropped span, and a
    // trace that merely reaches the limit without losing anything is never flagged.
    private void recordTruncation(final Txn<ByteBuffer> writeTxn,
                                  final byte[] traceIdBytes,
                                  final TraceStats stats) {
        if (stats.truncated()) {
            return;
        }
        writeStats(writeTxn, traceIdBytes, new TraceStats(
                stats.spanCount(),
                stats.serviceCount(),
                stats.maxEnd(),
                stats.lastActivityMs(),
                stats.depth(),
                stats.spanCountAtLastDepth(),
                stats.hasError(),
                true));
        LOGGER.warn(() -> LogUtil.message(
                "Trace {} in '{}' has reached its limit of {} spans; further spans for it will be dropped.",
                HexStringUtil.encode(traceIdBytes), doc.getName(), maxSpansPerTrace));
    }

    /**
     * Follows the trace whose spans are currently being merged. Because spans iterate grouped by
     * traceId, the per-trace work — queueing the root rebuild and reading the stored span count —
     * only has to happen when the id changes, instead of once per span.
     */
    private final class MergeTraceCursor {

        private byte[] traceId;
        private long spanCount;
        private boolean overLimit;
        private boolean truncationRecorded;

        private void onSpan(final Txn<ByteBuffer> writeTxn, final byte[] tid) {
            if (traceId != null && Arrays.equals(traceId, tid)) {
                return;
            }
            traceId = tid;
            truncationRecorded = false;
            pendingRootRebuilds.add(HexStringUtil.encode(tid));
            final TraceStats stats = readStats(writeTxn, tid);
            spanCount = stats.spanCount();
            overLimit = isOverSpanLimit(stats);
        }

        // Counting here rather than on the next batch means the limit bites mid-trace. Reaching it is
        // not truncation; the trace is flagged only once a span is actually dropped.
        private void onSpanWritten() {
            spanCount++;
            if (maxSpansPerTrace > 0 && spanCount >= maxSpansPerTrace) {
                overLimit = true;
            }
        }

        // Latched, so the drop path does no LMDB reads for the remaining spans of a trace that may be
        // dropping millions.
        private void onSpanDropped(final Txn<ByteBuffer> writeTxn) {
            if (truncationRecorded) {
                return;
            }
            truncationRecorded = true;
            recordTruncation(writeTxn, traceId, readStats(writeTxn, traceId));
        }

        private boolean isOverLimit() {
            return overLimit;
        }
    }

    public void insert(final LmdbWriter writer, final Span span) {
        final SpanKey spanKey = SpanKey.create(span);
        final SpanValue spanValue = SpanValue.create(span);
        insert(writer, new SpanKV(spanKey, spanValue));
    }

    @Override
    public void insert(final LmdbWriter writer, final KV<SpanKey, SpanValue> kv) {
        final Txn<ByteBuffer> writeTxn = writer.getWriteTxn();

        final byte[] traceIdBytes = HexStringUtil.decode(kv.key().getTraceId());

        final TraceStats statsBefore = readStats(writeTxn, traceIdBytes);
        if (isOverSpanLimit(statsBefore)) {
            recordTruncation(writeTxn, traceIdBytes, statsBefore);
            writer.tryCommit();
            return;
        }

        // Spans are immutable (first write wins) — MDB_NOOVERWRITE makes a genuinely-new span
        // detectable so the per-trace stats are counted exactly once (re-delivery is a no-op).
        final boolean[] isNew = {false};
        keySerde.write(writeTxn, kv.key(), keyByteBuffer ->
                valueSerde.write(writeTxn, kv.val(), valueByteBuffer ->
                        isNew[0] = dbi.put(writeTxn, keyByteBuffer, valueByteBuffer,
                                PutFlags.MDB_NOOVERWRITE)));

        if (isNew[0]) {
            final SpanValue v = kv.val();
            recordNewSpan(writeTxn, traceIdBytes, v.getName(), v.getInsertTime(),
                    NanoTime.fromString(v.getEndTimeUnixNano()), isErrorStatus(v.getStatus()));
        }

        final boolean isRootSpan = NullSafe.isEmptyString(kv.key().getParentSpanId());

        if (isRootSpan) {
            // Root span: (re)derive depth, services, name and totalSpans from the span
            // set seen so far via the bounded streaming aggregate computation — it walks
            // the span index without materialising the whole trace, so it never OOMs on a
            // large/open-ended trace.  Also writes the trace-roots-merge-time entry that
            // drives the PathwaysProcessor grace-period clock.
            try {
                // The root span is in the store — either just written, or already there and the
                // write rejected as a duplicate. Empty means no live span at all; skip defensively.
                final Optional<TraceRoot> optNewRoot =
                        buildRootFromStats(writeTxn, traceIdBytes);
                if (optNewRoot.isEmpty()) {
                    return;
                }
                final TraceRootKey traceRootKey = new TraceRootKey(traceIdBytes);
                final TraceRoot newRoot = optNewRoot.get();

                // Delete stale secondary sort-index entries before overwriting.
                getTraceRoot(writeTxn, traceIdBytes).ifPresent(oldRoot ->
                        deleteSecondaryIndexes(writeTxn, traceIdBytes, oldRoot));

                traceRootKeySerde.write(traceRootKey, keyBuffer ->
                        traceRootValueSerde.write(newRoot, valueBuffer ->
                                traceRootsDbi.put(writeTxn, keyBuffer, valueBuffer)));

                writeSecondaryIndexes(writeTxn, traceIdBytes, newRoot);

                // Initialise the merge-time clock for the PathwaysProcessor.
                final long mergeTimeMs = System.currentTimeMillis();
                final byte[] mergeKeyBytes = new byte[Long.BYTES + traceIdBytes.length];
                ByteBuffer.wrap(mergeKeyBytes).putLong(mergeTimeMs).put(traceIdBytes);
                byteBuffers.useBytes(mergeKeyBytes, mergeTimeKey -> {
                    traceRootsMergeTimeDbi.put(writeTxn, mergeTimeKey, emptyValue(),
                            PutFlags.MDB_NOOVERWRITE);
                });
            } catch (final LmdbNativeException e) {
                throw e;
            } catch (final RuntimeException e) {
                LOGGER.warn("Failed to write trace root index for trace {}: {}",
                        kv.key().getTraceId(), e.getMessage(), e);
            }

        } else if (isNew[0]) {
            // Child span: fold it into the stored root incrementally, which is O(1) against
            // re-deriving from every span in the shard. Guarded on isNew because a re-delivered
            // span is already counted in the root — folding it again would inflate totalSpans
            // and drag the TOTAL_SPANS index with it. depth and services are left to the
            // root-span path, which recomputes them from the stats accumulator.
            try {
                final TraceRootKey traceRootKey = new TraceRootKey(traceIdBytes);

                final Optional<TraceRoot> optOldRoot = getTraceRoot(writeTxn, traceIdBytes);

                if (optOldRoot.isPresent()) {
                    final TraceRoot oldRoot = optOldRoot.get();

                    // Expand the time bounds with this span's start/end.
                    final NanoTime spanStart = NanoTime.fromString(kv.val().getStartTimeUnixNano());
                    final NanoTime spanEnd = NanoTime.fromString(kv.val().getEndTimeUnixNano());
                    final NanoTime newStart = oldRoot.getStartTime() == null
                            || spanStart.compareTo(oldRoot.getStartTime()) < 0
                            ? spanStart : oldRoot.getStartTime();
                    final NanoTime newEnd = oldRoot.getEndTime() == null
                            || spanEnd.compareTo(oldRoot.getEndTime()) > 0
                            ? spanEnd : oldRoot.getEndTime();

                    final long spanInsertMs = NanoTimeUtil.toInstant(
                            kv.val().getInsertTime()).toEpochMilli();
                    // recordNewSpan (called above) has already folded this span into the stats, so
                    // read the accumulator's monotonic error flag — the same source of truth as
                    // buildRootFromStats — rather than inspecting this span alone.
                    final TraceRoot newRoot = oldRoot.copy()
                            .startTime(newStart)
                            .endTime(newEnd)
                            .totalSpans(oldRoot.getTotalSpans() + 1)
                            .lastActivityMs(Math.max(oldRoot.getLastActivityMs(), spanInsertMs))
                            .error(readStats(writeTxn, traceIdBytes).hasError())
                            .build();

                    traceRootKeySerde.write(traceRootKey, keyBuffer ->
                            traceRootValueSerde.write(newRoot, valueBuffer ->
                                    traceRootsDbi.put(writeTxn, keyBuffer, valueBuffer)));

                    updateChildSpanIndexes(writeTxn, traceIdBytes, oldRoot, newRoot);
                }
            } catch (final LmdbNativeException e) {
                throw e;
            } catch (final RuntimeException e) {
                LOGGER.warn("Failed to incrementally update trace root for trace {}: {}",
                        kv.key().getTraceId(), e.getMessage(), e);
            }
        }

        writer.tryCommit();
    }

    // Moves the sort-index entries from oldRoot to newRoot, touching only the indexes whose key
    // actually changed. That limits the work to what a child span affects — total-spans always,
    // start-time and duration when a bound moves — without naming any index individually.
    private void updateChildSpanIndexes(final Txn<ByteBuffer> writeTxn,
                                        final byte[] traceIdBytes,
                                        final TraceRoot oldRoot,
                                        final TraceRoot newRoot) {
        if (secondaryIndexDbis.isEmpty()) {
            return;
        }
        for (final TraceSecondaryIndex index : TraceSecondaryIndex.values()) {
            final byte[] oldKey = index.key(oldRoot, traceIdBytes);
            final byte[] newKey = index.key(newRoot, traceIdBytes);
            if (Arrays.equals(oldKey, newKey)) {
                continue;
            }
            final Dbi<ByteBuffer> indexDbi = secondaryIndexDbis.get(index);
            byteBuffers.useBytes(oldKey, buf -> {
                indexDbi.delete(writeTxn, buf);
            });
            byteBuffers.useBytes(newKey, buf -> {
                indexDbi.put(writeTxn, buf, emptyValue(), PutFlags.MDB_NOOVERWRITE);
            });
        }
    }

    public void iterateTraces(final BiConsumer<byte[], Function<byte[], Trace>> consumer) {
        env.read(txn -> {
            try (final Stream<LmdbEntry> stream = LmdbStream.stream(txn, traceRootsDbi)) {
                stream.forEach(entry -> {
                    try {
                        final byte[] traceId = ByteBufferUtils.toBytes(entry.getKey());
                        consumer.accept(traceId, id -> getTrace(txn, id));
                    } catch (final RuntimeException e) {
                        LOGGER.debug(e::getMessage, e);
                    }
                });
            }
            return null;
        });
    }

    /**
     * Yields the traceId of every {@link #traceRootsMergeTimeDbi} entry merged at or before
     * {@code cutoffMs}. The DBI is merge-time ordered, so iteration stops at the first entry past the
     * cutoff — O(eligible) rather than a full scan.
     */
    public void iterateRootsMergedBefore(final long cutoffMs, final Consumer<byte[]> consumer) {
        env.read(txn -> {
            try (final Stream<LmdbEntry> stream = LmdbStream.stream(txn, traceRootsMergeTimeDbi)) {
                stream.takeWhile(entry -> entry.getKey().duplicate().getLong() <= cutoffMs)
                        .forEach(entry -> {
                            final ByteBuffer keyBuf = entry.getKey().duplicate();
                            keyBuf.getLong(); // skip the 8-byte mergeTimeMs prefix
                            final int remaining = keyBuf.remaining();
                            if (remaining != 16) {
                                LOGGER.warn("Corrupt trace-roots-merge-time key: expected 16 " +
                                        "traceId bytes, got {}", remaining);
                                return;
                            }
                            final byte[] traceIdBytes = new byte[16];
                            keyBuf.get(traceIdBytes);
                            consumer.accept(traceIdBytes);
                        });
            }
            return null;
        });
    }

    @Override
    public void merge(final Path source) {
        env.write(writer -> {
            // Sources are opened WITHOUT secondary indexes: merge only reads their span and trace-roots
            // DBIs, and reading that way works whether or not the source was written with indexes — which
            // matters because a read-only open cannot ask for a DBI the env lacks.
            try (final TraceDb sourceDb =
                         TraceDb.create(source, byteBuffers, byteBufferFactory, doc, true, false)) {
                // Validate that the source DB has the same schema.
                validateSchema(schemaInfo, sourceDb.getSchemaInfo());

                final MergeTraceCursor cursor = new MergeTraceCursor();
                sourceDb.env.read(readTxn -> {
                    try (final Stream<LmdbEntry> stream = LmdbStream.stream(readTxn, sourceDb.dbi)) {
                        stream.forEach(entry -> {
                            final ByteBuffer spanKeyBuf = entry.getKey().duplicate();
                            final byte[] tid;
                            if (spanKeyBuf.remaining() >= TRACE_ID_BYTES) {
                                tid = new byte[TRACE_ID_BYTES];
                                spanKeyBuf.get(tid);
                                // Queue EVERY trace whose spans are merged this cycle for a full
                                // root recompute at mergeComplete — not just traces whose root span
                                // appears. Without this, a trace whose children arrive after its
                                // root was processed (root in an earlier cycle) keeps stale
                                // depth/services/totalSpans.
                                cursor.onSpan(writer.getWriteTxn(), tid);
                            } else {
                                tid = null;
                            }

                            if (tid != null && cursor.isOverLimit()) {
                                cursor.onSpanDropped(writer.getWriteTxn());
                                return;
                            }

                            if (sourceDb.keySerde.usesLookup(entry.getKey()) ||
                                sourceDb.valueSerde.usesLookup(entry.getVal())) {
                                // We need to do a full read and merge (insert() records stats).
                                final SpanKey spanKey = sourceDb.keySerde.read(readTxn, entry.getKey());
                                final SpanValue spanValue = sourceDb.valueSerde.read(readTxn, entry.getVal());
                                insert(writer, new SpanKV(spanKey, spanValue));
                            } else {
                                // Quick merge. MDB_NOOVERWRITE: only a genuinely-new span is
                                // counted into the per-trace stats (re-delivery is a no-op).
                                if (dbi.put(writer.getWriteTxn(), entry.getKey(), entry.getVal(),
                                        PutFlags.MDB_NOOVERWRITE)) {
                                    if (tid != null) {
                                        final SpanValueSerde.SpanSummary s =
                                                spanValueSerde.readSummary(writer.getWriteTxn(), entry.getVal());
                                        recordNewSpan(writer.getWriteTxn(), tid, s.name(),
                                                s.insertTime(), s.endTime(), isErrorStatus(s.statusCode()));
                                        cursor.onSpanWritten();
                                    }
                                    writer.tryCommit();
                                }
                            }
                        });
                    }

                    // Merge trace roots.  For each entry successfully written (new traceId),
                    // also populate its secondary sort indexes so the target shard remains
                    // fully queryable without a subsequent full scan.
                    LmdbIterable.iterate(readTxn, sourceDb.traceRootsDbi, (key, val) -> {
                        final byte[] traceIdBytes = new byte[key.remaining()];
                        key.duplicate().get(traceIdBytes);
                        // Use MDB_NOOVERWRITE (NOT the shard's shared putFlags — which is
                        // overwrite mode for this store): only seed the root + its indexes when
                        // the traceId is genuinely new. Overwriting an existing root with this
                        // batch's *partial* value would clobber the more-complete stored value
                        // and strand its differently-valued depth/services/total-spans index
                        // entries (the value-addressed delete could no longer match them).
                        // mergeComplete recomputes the authoritative root + indexes for every
                        // queued traceId at the end of the cycle.
                        if (traceRootsDbi.put(writer.getWriteTxn(), key, val, PutFlags.MDB_NOOVERWRITE)) {
                            final TraceRoot root = traceRootValueSerde.read(val.duplicate());
                            writeSecondaryIndexes(writer.getWriteTxn(), traceIdBytes, root);
                            writer.tryCommit();
                        }
                        pendingRootRebuilds.add(HexStringUtil.encode(traceIdBytes));
                    });

                    // Write trace-roots-merge-time entries using the TARGET node's
                    // wall-clock merge time, derived from the source's trace-roots index.
                    // Copying source timestamps verbatim would preserve the SOURCE node's
                    // wall-clock time, which may already exceed the grace-period cutoff —
                    // defeating the intent of measuring receipt time at the TARGET node.
                    // Iterating traceRootsDbi covers both the quick-merge path (which
                    // bypasses insert()) and the full-read path (MDB_NOOVERWRITE silently
                    // rejects duplicates already written by insert() at the same ms).
                    final long targetMergeTimeMs = System.currentTimeMillis();
                    LmdbIterable.iterate(readTxn, sourceDb.traceRootsDbi, (key, val) -> {
                        final byte[] traceIdBytes = new byte[key.remaining()];
                        key.duplicate().get(traceIdBytes);
                        final byte[] mergeKeyBytes = new byte[Long.BYTES + traceIdBytes.length];
                        ByteBuffer.wrap(mergeKeyBytes).putLong(targetMergeTimeMs).put(traceIdBytes);
                        byteBuffers.useBytes(mergeKeyBytes, mergeTimeKey -> {
                            traceRootsMergeTimeDbi.put(writer.getWriteTxn(), mergeTimeKey,
                                    emptyValue(), PutFlags.MDB_NOOVERWRITE);
                        });
                        writer.tryCommit();
                    });

                    return null;
                });
            }
        });

        // Delete source now we have merged.
        FileUtil.deleteDir(source);
    }

    /**
     * Recomputes the stored {@link TraceRoot} for every trace whose root was merged during the
     * current cycle, deriving depth/services/totalSpans from the <em>fully-merged</em> span set
     * rather than the single batch that carried the root span. Called once per merge cycle
     * after all batches have been merged (see {@link #merge}), so the whole trace is present.
     */
    @Override
    public void mergeComplete() {
        if (pendingRootRebuilds.isEmpty()) {
            return;
        }
        // Snapshot and clear so the next cycle starts fresh even if this throws partway.
        final List<String> traceIds = new ArrayList<>(pendingRootRebuilds);
        pendingRootRebuilds.clear();

        env.write(writer -> {
            for (final String hex : traceIds) {
                try {
                    final Txn<ByteBuffer> writeTxn = writer.getWriteTxn();
                    final byte[] traceIdBytes = HexStringUtil.decode(hex);
                    // Bounded, streaming recompute over the fully-merged span set — exact
                    // totalSpans/services and (safety-valve aside) exact depth, plus the
                    // latest insert time as lastActivityMs (informational only — shown in the UI
                    // "Last Activity" column; retention and publishing age by the root's own end
                    // time, not activity), without materialising the whole trace. Empty ⇒ the
                    // trace has no live span at all ⇒ nothing to describe, so skip. A trace with
                    // spans but no ROOT span is not empty: it gets a flagged orphan root, which is
                    // what makes an archived orphan listable.
                    final Optional<TraceRoot> optRebuilt =
                            buildRootFromStats(writeTxn, traceIdBytes);
                    if (optRebuilt.isEmpty()) {
                        continue;
                    }
                    final TraceRoot rebuilt = optRebuilt.get();
                    final TraceRootKey traceRootKey = new TraceRootKey(traceIdBytes);

                    // Drop stale sort-index entries for the existing stored root, then overwrite.
                    getTraceRoot(writeTxn, traceIdBytes).ifPresent(oldRoot ->
                            deleteSecondaryIndexes(writeTxn, traceIdBytes, oldRoot));
                    traceRootKeySerde.write(traceRootKey, keyBuf ->
                            traceRootValueSerde.write(rebuilt, valBuf ->
                                    traceRootsDbi.put(writeTxn, keyBuf, valBuf)));
                    writeSecondaryIndexes(writeTxn, traceIdBytes, rebuilt);
                    writer.tryCommit();
                } catch (final LmdbNativeException e) {
                    throw e;
                } catch (final RuntimeException e) {
                    LOGGER.warn("Failed to rebuild trace root for trace {}: {}", hex, e.getMessage(), e);
                }
            }
            return null;
        });
    }

    // Earliest span start for a trace, read straight from the span keys
    // (traceId[16] ∥ parentSpanId[8] ∥ startTime[8] ∥ spanId[8]) so no value is decoded and no UID lookup
    // is touched. Empty when the trace has no spans.
    private Optional<NanoTime> earliestSpanStart(final Txn<ByteBuffer> txn, final byte[] traceIdBytes) {
        final int startTimeOffset = TRACE_ID_BYTES + SPAN_ID_BYTES;
        final long[] min = {Long.MAX_VALUE};
        byteBuffers.useBytes(traceIdBytes, prefixBuffer -> {
            final LmdbKeyRange keyRange = LmdbKeyRange.builder().prefix(prefixBuffer).build();
            try (final Stream<LmdbEntry> stream = LmdbStream.stream(txn, dbi, keyRange)) {
                stream.forEach(entry -> {
                    final ByteBuffer key = entry.getKey().duplicate().order(ByteOrder.BIG_ENDIAN);
                    if (key.remaining() >= startTimeOffset + START_TIME_BYTES) {
                        // Ignore zero: a key built without a start time would otherwise pull the
                        // minimum to the epoch and bucket the trace under 1970.
                        final long start = key.getLong(key.position() + startTimeOffset);
                        if (start > 0 && start < min[0]) {
                            min[0] = start;
                        }
                    }
                });
            }
        });
        return min[0] == Long.MAX_VALUE ? Optional.empty() : Optional.of(NanoTime.ofNanos(min[0]));
    }

    @Override
    public SpanValue get(final SpanKey key) {
        return env.read(readTxn -> keySerde.toBufferForGet(readTxn, key, optionalKeyByteBuffer ->
                optionalKeyByteBuffer.map(keyByteBuffer -> {
                    final ByteBuffer valueByteBuffer = dbi.get(readTxn, keyByteBuffer);
                    if (valueByteBuffer == null) {
                        return null;
                    }
                    return valueSerde.read(readTxn, valueByteBuffer);
                }).orElse(null)));
    }

    @Override
    public void search(final ExpressionCriteria criteria,
                       final FieldIndex fieldIndex,
                       final DateTimeSettings dateTimeSettings,
                       final ExpressionPredicateFactory expressionPredicateFactory,
                       final ValuesConsumer consumer) {
        env.read(readTxn -> {
            final ValuesExtractor valuesExtractor = createValuesExtractor(
                    fieldIndex,
                    getKeyExtractionFunction(readTxn),
                    getValExtractionFunction(readTxn));
            PlanBSearchHelper.search(
                    readTxn,
                    criteria,
                    fieldIndex,
                    dateTimeSettings,
                    expressionPredicateFactory,
                    consumer,
                    valuesExtractor,
                    dbi);
            return null;
        });
    }

    private Function<Context, SpanKey> getKeyExtractionFunction(final Txn<ByteBuffer> readTxn) {
        return context -> keySerde.read(readTxn, context.key().duplicate());
    }

    private Function<Context, SpanValue> getValExtractionFunction(final Txn<ByteBuffer> readTxn) {
        return context -> valueSerde.read(readTxn, context.val().duplicate());
    }

    public static ValuesExtractor createValuesExtractor(final FieldIndex fieldIndex,
                                                        final Function<Context, SpanKey> keyFunction,
                                                        final Function<Context, SpanValue> valFunction) {
        final String[] fields = fieldIndex.getFields();
        final TraceConverter[] converters = new TraceConverter[fields.length];
        for (int i = 0; i < fields.length; i++) {
            converters[i] = switch (fields[i]) {
                case TraceFields.TRACE_ID -> kv ->
                        ValString.create(kv.getKey().getTraceId());
                case TraceFields.PARENT_SPAN_ID -> kv ->
                        ValString.create(kv.getKey().getParentSpanId());
                case TraceFields.SPAN_ID -> kv ->
                        ValString.create(kv.getKey().getSpanId());
                default -> kv -> ValNull.INSTANCE;
            };
        }
        return (readTxn, key, value) -> {
            final Context context = new Context(readTxn, key, value);
            final LazyKV<SpanKey, SpanValue> lazyKV = new LazyKV<>(context, keyFunction, valFunction);
            final Val[] values = new Val[fields.length];
            for (int i = 0; i < fields.length; i++) {
                values[i] = converters[i].convert(lazyKV);
            }
            return Values.of(values);
        };
    }

    // The instant a trace's age is measured from for archive/delete decisions: the root span's own end
    // (getRootEndTime), falling back to start time when there is no end or it precedes the start. Gating on
    // the root's own end — not the trace's max end, which trailing spans inflate — bounds leaky/never-ending
    // traces; later spans then arrive as parentless orphans and are swept by insert time.
    private static NanoTime getAgeFrom(final TraceRoot root) {
        final NanoTime start = root.getStartTime();
        final NanoTime rootEnd = root.getRootEndTime();
        if (rootEnd != null && (start == null || !rootEnd.isLessThan(start))) {
            return rootEnd;
        }
        return start;
    }

    /**
     * The one publish path for a trace store. Stages every trace's spans into the bucket for its root's
     * start time, and retires a root once it is older than {@code publishBefore}.
     *
     * <p>A trace's root lives in the holding area until the cut-off, then it goes. Its spans move to the
     * bucket as they merge, which is what makes the bucket the queryable copy rather than a cold copy.
     * Spans arriving after the cut-off get a synthesized root from {@link #buildRootFromStats} and may land
     * in a different bucket; that is accepted — the cut-off is the operator's answer to "how long until all
     * of a trace's spans have arrived".
     *
     * @return the number of rows removed from the holding area — spans plus root-side rows, not traces
     */
    @Override
    public long publish(final Instant publishBefore,
                        final Path bucketBaseDir) {
        final PublishSelection selection = selectRoots(NanoTimeUtil.fromInstant(publishBefore));
        if (selection.isEmpty()) {
            return 0L;
        }
        stageSpans(selection, bucketBaseDir);
        return purgeStaged(selection);
    }

    // Buckets are labelled by the root's START time: the axis queries filter on, so the bucket a trace lands
    // in is the bucket ArchiveShardLocator opens for a query covering it.
    //
    // A trace is staged when it holds spans, because publishing takes every span it stages — so a span still
    // here is a span the archive has not got. That covers a single-span trace, whose root span is the one
    // span it has. A trace with none left is settled: it keeps its stored root until the cut-off retires it,
    // and staging it again would rewrite its bucket to carry nothing.
    private PublishSelection selectRoots(final NanoTime cutOff) {
        final Map<String, String> labels = new HashMap<>();
        final Map<String, TraceRoot> retiring = new HashMap<>();

        env.read(readTxn -> {
            final Set<String> tracesWithSpans = tracesWithSpans(readTxn);
            LmdbIterable.iterate(readTxn, traceRootsDbi, (key, val) -> {
                final TraceRoot root = traceRootValueSerde.read(val.duplicate());
                final NanoTime startTime = root.getStartTime();
                if (startTime == null) {
                    return;
                }
                final byte[] traceIdBytes = new byte[key.remaining()];
                key.duplicate().get(traceIdBytes);
                final String hex = HexStringUtil.encode(traceIdBytes);

                // Age on the root's own end (see getAgeFrom), so trailing spans cannot keep a never-ending
                // trace in the holding area forever.
                final NanoTime ageFrom = getAgeFrom(root);
                final boolean pastCutOff = ageFrom != null && ageFrom.isBefore(cutOff);

                // The root span usually arrives last. A synthesized root's start time is the earliest span's,
                // not the root span's, so staging on it would bucket the trace by a start time the real root
                // then contradicts — and since purgeStaged has by then deleted the children locally, only the
                // root span would reach the second bucket. Hold the trace back until the real root arrives,
                // or until the cut-off says it never will. The cost is that a trace whose root never arrives
                // is not queryable until the cut-off retires it.
                if (root.isOrphan() && !pastCutOff) {
                    return;
                }

                if (tracesWithSpans.contains(hex)) {
                    labels.put(hex, BucketGranularityUtil.label(
                            granularity, NanoTimeUtil.toInstant(startTime)));
                }
                // Independent of staging: a settled trace has nothing to stage but still has to retire.
                if (pastCutOff) {
                    retiring.put(hex, root);
                }
            });
            return null;
        });

        return new PublishSelection(labels, retiring);
    }

    // The traces the span DBI still holds a span for. Span keys are trace-id prefixed so one sequential scan
    // yields them grouped, and TraceIdHexCursor encodes once per trace rather than once per span.
    private Set<String> tracesWithSpans(final Txn<ByteBuffer> txn) {
        final Set<String> ids = new HashSet<>();
        final TraceIdHexCursor hex = new TraceIdHexCursor();
        LmdbIterable.iterate(txn, dbi, (key, val) -> {
            if (key.remaining() >= TRACE_ID_BYTES) {
                ids.add(hex.hexOf(key));
            }
        });
        return ids;
    }

    // Writes one local delta per date label, each holding the spans of the traces selectRoots assigned to
    // that label. A delta is throwaway: pushArchive merges it into the archive bucket for the same label on
    // the shared store, then it is deleted.
    //
    // Only selected traces are read, and each one is found by seeking to its trace-id prefix rather than by
    // filtering a full scan of the span DBI. The cost is then the spans actually copied, rather than the
    // whole shard read once per label — which matters because the label count is not bounded: a label comes
    // from the root's start time while retirement goes on its end, so a long-running trace, backfill, or a
    // batch of timed-out orphans can put many labels in play at once. Spans stream straight from the source
    // index, so peak memory is O(1) either way.
    private void stageSpans(final PublishSelection selection, final Path archiveBaseDir) {
        for (final Map.Entry<String, List<String>> group : selection.tracesByLabel().entrySet()) {
            final String label = group.getKey();
            final List<String> traceIdHexes = group.getValue();
            final Path deltaDir = archiveBaseDir.resolve(label);
            try {
                Files.createDirectories(deltaDir);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
            // Indexed, unlike the holding shard this is written from: a delta's index cost is one write per
            // root, not per span, and it keeps a staged batch readable in isolation.
            try (final TraceDb deltaDb =
                         TraceDb.create(deltaDir, byteBuffers, byteBufferFactory, doc, false)) {
                deltaDb.env.write(deltaWriter -> {
                    env.read(srcTxn -> {
                        for (final String traceIdHex : traceIdHexes) {
                            copySpansTo(deltaDb, deltaWriter, srcTxn, HexStringUtil.decode(traceIdHex));
                        }
                        return null;
                    });
                    return null;
                });
                // Clone the UID / hash lookup tables so the delta's span values decode when merged.
                copyLookupsTo(deltaDb);
            }
        }
    }

    // Every span of one trace, found by prefix since a span key starts with its trace id. Raw bytes across,
    // no decode/re-encode: a span's value is opaque here, and copyLookupsTo gives the delta the lookup tables
    // it needs to decode it later.
    private void copySpansTo(final TraceDb deltaDb,
                             final LmdbWriter deltaWriter,
                             final Txn<ByteBuffer> srcTxn,
                             final byte[] traceIdBytes) {
        byteBuffers.useBytes(traceIdBytes, prefixBuffer -> {
            final LmdbKeyRange keyRange = LmdbKeyRange.builder().prefix(prefixBuffer).build();
            LmdbIterable.iterate(srcTxn, dbi, keyRange, (key, val) -> {
                final byte[] rawKey = new byte[key.remaining()];
                key.duplicate().get(rawKey);
                final byte[] rawVal = new byte[val.remaining()];
                val.duplicate().get(rawVal);
                putDirect(deltaDb.dbi, deltaWriter.getWriteTxn(), rawKey, rawVal);
                deltaWriter.tryCommit();
            });
        });
    }

    // Remove what stageSpans copied: every non-root span, plus the root-side rows of a retired trace. A
    // retained span records its lookups so the deleteUnused below only drops entries nothing references.
    // Returns the number of rows removed.
    //
    // Iteration reads from readTxn while mutations go through writer.
    private long purgeStaged(final PublishSelection selection) {
        return env.write(writer -> {
            final Count count = new Count();
            env.read(readTxn -> {
                deleteStagedSpans(selection, readTxn, writer, count);
                retireRoots(selection, readTxn, writer, count);
                return null;
            });
            writer.commit();

            if (count.get() > 0 && !Thread.currentThread().isInterrupted()) {
                env.read(readTxn -> {
                    keyRecorder.deleteUnused(readTxn, writer);
                    valueRecorder.deleteUnused(readTxn, writer);
                    return null;
                });
            }
            return count.get();
        });
    }

    private void deleteStagedSpans(final PublishSelection selection,
                                   final Txn<ByteBuffer> readTxn,
                                   final LmdbWriter writer,
                                   final Count count) {
        final TraceIdHexCursor hex = new TraceIdHexCursor();
        LmdbIterable.iterate(readTxn, dbi, (key, val) -> {
            // Every staged span goes, the root span included — the bucket has its own copy and derives its
            // root from it, and the stored root here carries what the root span said. Keeping it back would
            // make "staged" and "removed" different sets, which is what stops either being a usable signal.
            final boolean remove = key.remaining() >= TRACE_ID_BYTES
                    && selection.isStaged(hex.hexOf(key));
            if (remove) {
                dbi.delete(writer.getWriteTxn(), key);
                count.increment();
            } else {
                keyRecorder.recordUsed(writer, key);
                valueRecorder.recordUsed(writer, val);
            }
            writer.tryCommit();
        });
    }

    private void retireRoots(final PublishSelection selection,
                             final Txn<ByteBuffer> readTxn,
                             final LmdbWriter writer,
                             final Count count) {
        for (final Map.Entry<String, TraceRoot> entry : selection.retiring().entrySet()) {
            final byte[] traceIdBytes = HexStringUtil.decode(entry.getKey());
            byteBuffers.useBytes(traceIdBytes, keyBuf -> {
                traceRootsDbi.delete(writer.getWriteTxn(), keyBuf);
            });
            deleteSecondaryIndexes(writer.getWriteTxn(), traceIdBytes, entry.getValue());
            deleteStatsOf(readTxn, writer, traceIdBytes);
            count.increment();
            writer.tryCommit();
        }

        // Merge-time entries for retired roots.
        if (!selection.retiring().isEmpty()) {
            LmdbIterable.iterate(readTxn, traceRootsMergeTimeDbi, (key, val) -> {
                final ByteBuffer keyBuf = key.duplicate();
                if (keyBuf.remaining() != Long.BYTES + TRACE_ID_BYTES) {
                    return;
                }
                keyBuf.getLong(); // skip the mergeTimeMs prefix
                final byte[] traceIdBytes = new byte[TRACE_ID_BYTES];
                keyBuf.get(traceIdBytes);
                if (selection.isRetiring(HexStringUtil.encode(traceIdBytes))) {
                    traceRootsMergeTimeDbi.delete(writer.getWriteTxn(), key);
                    writer.tryCommit();
                }
            });
        }
    }

    // Fail loudly rather than with an NPE if a sorted/indexed read is ever attempted against a store
    // opened without the secondary indexes (a holding-area shard, writer env or publish delta).
    private void requireSecondaryIndexes(final String operation) {
        if (secondaryIndexDbis.isEmpty()) {
            throw new IllegalStateException("Cannot " + operation
                    + ": this trace store was opened without secondary sort indexes, so it is not "
                    + "queryable. Query the archive buckets instead.");
        }
    }

    /**
     * Derives the hex traceId of a span key once per <em>trace</em> rather than once per span.
     *
     * <p>traceId is the leading field of a span key, so a full scan of the span DBI visits a trace's spans
     * contiguously. Reusing the previous result while the prefix is unchanged turns a {@code byte[16]} plus
     * a 32-char {@code String} per span into one per trace — for a trace at the default per-trace span limit
     * that is one allocation instead of 100,000, on a scan that runs several times per shard per merge cycle.
     */
    private static final class TraceIdHexCursor {

        private final byte[] last = new byte[TRACE_ID_BYTES];
        private String lastHex;

        private String hexOf(final ByteBuffer key) {
            final ByteBuffer k = key.duplicate();
            if (lastHex != null) {
                boolean same = true;
                for (int i = 0; i < TRACE_ID_BYTES; i++) {
                    if (k.get(k.position() + i) != last[i]) {
                        same = false;
                        break;
                    }
                }
                if (same) {
                    return lastHex;
                }
            }
            k.get(last);
            lastHex = HexStringUtil.encode(last);
            return lastHex;
        }
    }

    // The root span rides with its root entry, so both go together when the root ages out.
    private void deleteRootSpansOf(final Txn<ByteBuffer> readTxn,
                                   final LmdbWriter writer,
                                   final byte[] traceIdBytes,
                                   final Count changeCount) {
        byteBuffers.useBytes(childPrefix(traceIdBytes, NO_PARENT_SPAN_ID), prefixBuffer -> {
            final LmdbKeyRange keyRange = LmdbKeyRange.builder().prefix(prefixBuffer).build();
            LmdbIterable.iterate(readTxn, dbi, keyRange, (key, val) -> {
                dbi.delete(writer.getWriteTxn(), key);
                changeCount.increment();
            });
        });
    }

    // Removes a synthesized orphan root entirely: trace-roots entry, secondary indexes and per-trace
    // stats (an orphan has no root span and no merge-time entry). Called by retention and publishing
    // once an orphan trace's last live span has gone, so the root can't outlive its spans.
    private void deleteOrphanRoot(final Txn<ByteBuffer> readTxn,
                                  final LmdbWriter writer,
                                  final ByteBuffer key,
                                  final byte[] traceIdBytes,
                                  final TraceRoot value,
                                  final Count count) {
        traceRootsDbi.delete(writer.getWriteTxn(), key);
        deleteSecondaryIndexes(writer.getWriteTxn(), traceIdBytes, value);
        deleteStatsOf(readTxn, writer, traceIdBytes);
        count.increment();
    }

    // Drops a trace's derived state, but keeps the stats row itself for a TRUNCATED trace: readStats
    // returns TraceStats.EMPTY for a missing row (spanCount 0, truncated false), so dropping it would
    // unlatch the per-trace span cap and let a capped trace accept another full allowance — which is what
    // isOverSpanLimit's cumulative count exists to prevent. The retained row is reachable only by
    // runRetention's sweep, since every caller here is already iterating trace-roots and has just
    // removed this trace's entry. The bulky part, the distinct service-name set, always goes.
    private void deleteStatsOf(final Txn<ByteBuffer> readTxn,
                               final LmdbWriter writer,
                               final byte[] traceIdBytes) {
        if (!readStats(readTxn, traceIdBytes).truncated()) {
            byteBuffers.useBytes(traceIdBytes, keyBuf -> {
                traceStatsDbi.delete(writer.getWriteTxn(), keyBuf);
            });
        }
        byteBuffers.useBytes(traceIdBytes, prefixBuf -> {
            final LmdbKeyRange keyRange = LmdbKeyRange.builder().prefix(prefixBuf).build();
            LmdbIterable.iterate(readTxn, traceServiceNamesDbi, keyRange,
                    (key, val) -> traceServiceNamesDbi.delete(writer.getWriteTxn(), key));
        });
    }

    // lmdbjava will only accept direct (off-heap) buffers, so the raw bytes have to be copied into
    // fresh ones.
    private static void putDirect(final Dbi<ByteBuffer> targetDbi,
                                  final Txn<ByteBuffer> writeTxn,
                                  final byte[] rawKey,
                                  final byte[] rawVal) {
        final ByteBuffer directKey = ByteBuffer.allocateDirect(rawKey.length);
        directKey.put(rawKey).flip();
        final ByteBuffer directVal = ByteBuffer.allocateDirect(rawVal.length);
        directVal.put(rawVal).flip();
        targetDbi.put(writeTxn, directKey, directVal);
    }

    // Without these the UID / hash integers inside archived span values cannot be decoded. Only the
    // DBIs in LOOKUP_DBI_NAMES go; the bucket derives its own roots and indexes.
    private void copyLookupsTo(final TraceDb archive) {
        for (final String name : LOOKUP_DBI_NAMES) {
            copyNamedDbi(name, this.env, archive.env);
        }
    }

    // Both envs must already have the DBI open, i.e. the owning TraceDb must have been constructed.
    private static void copyNamedDbi(final String name,
                                     final PlanBEnv srcEnv,
                                     final PlanBEnv dstEnv) {
        final Dbi<ByteBuffer> srcDbi = srcEnv.openDbi(name, DbiFlags.MDB_CREATE);
        final Dbi<ByteBuffer> dstDbi = dstEnv.openDbi(name, DbiFlags.MDB_CREATE);
        srcEnv.read(readTxn -> {
            dstEnv.write(writer -> {
                LmdbIterable.iterate(readTxn, srcDbi, (key, val) ->
                        dstDbi.put(writer.getWriteTxn(), key, val));
                return null;
            });
            return null;
        });
    }

    @Override
    public long runRetention(final Instant deleteBefore, final boolean useStateTime) {
        return env.write(writer -> {
            final NanoTime nanoTime = NanoTimeUtil.fromInstant(deleteBefore);
            final long count = runRetention(writer, nanoTime);

            // Delete unused lookup keys.
            if (!Thread.currentThread().isInterrupted()) {
                env.read(readTxn -> {
                    keyRecorder.deleteUnused(readTxn, writer);
                    valueRecorder.deleteUnused(readTxn, writer);
                    return null;
                });
            }
            return count;
        });
    }

    private long runRetention(final LmdbWriter writer,
                               final NanoTime deleteBefore) {
        return env.read(readTxn -> {
            final Count changeCount = new Count();

            // Delete old spans.
            // Use readInsertTime() rather than the full valueSerde.read() to
            // avoid "Unable to find value for UID" errors when the UID lookup
            // table is incomplete (e.g. after a cross-node shard sync).
            LmdbIterable.iterate(readTxn, dbi, (key, val) -> {
                // Root spans are governed by the root entry's age decision (below), not by
                // their own insert time — so the root span rides with its root: retained
                // while the root is retained, and removed together with it once the root's
                // own end time is older than the cutoff. Retain and record its lookups here.
                if (isRootKey(key)) {
                    keyRecorder.recordUsed(writer, key);
                    valueRecorder.recordUsed(writer, val);
                    writer.tryCommit();
                    return;
                }

                final NanoTime insertTime = spanValueSerde.readInsertTime(val.duplicate());
                if (insertTime.isBefore(deleteBefore)) {
                    dbi.delete(writer.getWriteTxn(), key);
                    changeCount.increment();
                } else {
                    // Record used lookup keys (raw val bytes, no UID lookup needed).
                    keyRecorder.recordUsed(writer, key);
                    valueRecorder.recordUsed(writer, val);
                }
                writer.tryCommit();
            });

            final long deleteBeforeMs = NanoTimeUtil.toInstant(deleteBefore).toEpochMilli();

            // Delete trace roots that have aged out, plus their secondary sort index entries.
            //  - Real root: aged by the root span's OWN end time (not the trace's max end, which
            //    trailing leaked spans inflate) — this bounds a leaky/never-ending trace rather
            //    than keeping its root live forever; late spans that then arrive become orphans.
            //  - Orphan root (synthesized, no root span): there is no root end to age by, so it is
            //    removed once the trace goes quiet (lastActivityMs < cutoff). That co-expires with
            //    the insert-time span sweep above and clears the lingering trace-stats.
            LmdbIterable.iterate(readTxn, traceRootsDbi, (key, val) -> {
                final TraceRoot value = traceRootValueSerde.read(val.duplicate());
                final byte[] traceIdBytes = new byte[key.remaining()];
                key.duplicate().get(traceIdBytes);
                if (value.isOrphan()) {
                    // A synthesized orphan root should exist only while the trace still has a live
                    // span. Reap it once its last span has been swept (by the insert-time span loop
                    // above), rather than on a separate quiet timer — otherwise it lingers as an
                    // empty "ghost" row. Check the WRITE txn so this pass's span deletions are seen.
                    if (!hasAnySpan(writer.getWriteTxn(), traceIdBytes)) {
                        deleteOrphanRoot(readTxn, writer, key, traceIdBytes, value, changeCount);
                    }
                } else {
                    final NanoTime ageFrom = getAgeFrom(value);
                    if (ageFrom != null && ageFrom.isBefore(deleteBefore)) {
                        // Remove the root entry + its sort indexes, its root span(s), and stats.
                        traceRootsDbi.delete(writer.getWriteTxn(), key);
                        deleteSecondaryIndexes(writer.getWriteTxn(), traceIdBytes, value);
                        deleteRootSpansOf(readTxn, writer, traceIdBytes, changeCount);
                        deleteStatsOf(readTxn, writer, traceIdBytes);
                        changeCount.increment();
                    }
                }
                writer.tryCommit();
            });

            // Delete stale trace-roots-merge-time entries.
            LmdbIterable.iterate(readTxn, traceRootsMergeTimeDbi, (key, val) -> {
                final long mergeTimeMs = key.duplicate().getLong();
                if (mergeTimeMs < deleteBeforeMs) {
                    traceRootsMergeTimeDbi.delete(writer.getWriteTxn(), key);
                }
                writer.tryCommit();
            });

            // Reap span-cap latch rows left behind by deleteStatsOf. Every other stats deletion happens
            // while iterating trace-roots, so once a trace's root has gone its retained row is reachable
            // only from here — without this sweep it would live for the lifetime of the shard, and be
            // copied to and from the shared store on every merge cycle.
            LmdbIterable.iterate(readTxn, traceStatsDbi, (key, val) -> {
                final byte[] traceIdBytes = new byte[key.remaining()];
                key.duplicate().get(traceIdBytes);
                final boolean rootGone = byteBuffers.useBytes(traceIdBytes, keyBuf ->
                        traceRootsDbi.get(writer.getWriteTxn(), keyBuf) == null);
                if (rootGone && traceStatsSerde.read(val.duplicate()).lastActivityMs() < deleteBeforeMs) {
                    traceStatsDbi.delete(writer.getWriteTxn(), key);
                    changeCount.increment();
                }
                writer.tryCommit();
            });

            writer.commit();
            return changeCount.get();
        });
    }

    @Override
    public long condense(final Instant condenseBefore) {
        return 0;
    }


    /** Finds traces with no quick-filter applied. */
    public TracesResultPage findTraces(final FindTraceCriteria criteria) {
        return findTraces(criteria, null);
    }

    /**
     * Finds traces, applying {@code filterPredicate} (the quick-filter match on a {@link TraceRoot}) when
     * non-null. A pathway criterion forces a full {@code trace-roots} scan, since matching one means
     * inspecting every span; otherwise the requested sort column selects a secondary index, defaulting to
     * newest-first by start time. A quick filter costs an exact total by testing candidates, rather than
     * the O(1) or key-only counts available without one.
     */
    public TracesResultPage findTraces(final FindTraceCriteria criteria,
                                       final Predicate<TraceRoot> filterPredicate) {
        final List<TraceRoot> list = new ArrayList<>();
        final PageResponse.Builder builder = PageResponse.builder();

        final Comparator<Span> spanComparator = new CloseSpanComparator(criteria.getTemporalOrderingTolerance());
        final PathKeyFactory pathKeyFactory = new PathKeyFactoryImpl();
        if (criteria.getPathway() != null) {
            final TracePredicate tracePredicate = new TracePredicate(
                    spanComparator,
                    pathKeyFactory,
                    Map.of(criteria.getPathway().getPathKey(), criteria.getPathway().getRoot()));
            // Derive the time filter once so every root is judged against the same window.
            final TimeFilter timeFilter = timeFilter(criteria);

            // Pathway matching requires inspecting every span — no secondary-index shortcut.
            env.read(readTxn -> {
                final Count count = new Count();
                LmdbIterable.iterate(readTxn, traceRootsDbi, (key, val) -> {
                    try {
                        final TraceRootKey traceRootKey = traceRootKeySerde.read(key);
                        final TraceRoot root = traceRootValueSerde.read(val);
                        final TraceBuilder traceBuilder = new TraceBuilder(root.getTraceId());
                        byteBuffers.useBytes(traceRootKey.getTraceId(), prefixBuffer -> {
                            findSpans(readTxn, traceRootKey.getTraceId(), traceBuilder::addSpan);
                        });
                        final Trace trace = traceBuilder.build();

                        final long pos = count.getAndIncrement();
                        if (criteria.getPageRequest().getOffset() <= pos &&
                            criteria.getPageRequest().getLength() > list.size() &&
                            tracePredicate.test(trace) &&
                            matchesTimeRange(root, timeFilter) &&
                            (filterPredicate == null || filterPredicate.test(root))) {
                            list.add(root);
                        }
                    } catch (final RuntimeException e) {
                        // Drop the row rather than the query — an unreadable root or span set here
                        // costs one trace, not the whole page.
                        LOGGER.debug(e.getMessage(), e);
                    }
                });
                builder.offset(criteria.getPageRequest().getOffset());
                builder.length(list.size());
                builder.total(count.get());
                builder.exact(true);
                return list;
            });

        } else {
            // Sort-dispatched secondary index query.  Determine the requested column and direction.
            final stroom.util.shared.CriteriaFieldSort firstSort =
                    NullSafe.get(criteria.getSortList(), sorts -> sorts.isEmpty() ? null : sorts.getFirst());
            final String sortField  = firstSort != null ? firstSort.getId() : TraceRootField.TRACE_START;
            // Default: newest first (descending start-time).
            final boolean desc = firstSort == null || firstSort.isDesc();

            final Dbi<ByteBuffer> indexDbi;
            if (TraceRootField.TRACE_ID.equals(sortField)) {
                // Primary DBI key IS the traceId — last 16 bytes == all bytes.
                indexDbi = traceRootsDbi;
            } else {
                // Any secondary-indexed field; TRACE_START and unknowns fall back to start-time.
                requireSecondaryIndexes("sort trace roots by " + sortField);
                final TraceSecondaryIndex index = TraceSecondaryIndex.forField(sortField);
                indexDbi = secondaryIndexDbis.get(
                        index != null ? index : TraceSecondaryIndex.START_TIME);
            }

            return findTracesByIndex(criteria, indexDbi, desc, filterPredicate);
        }

        return new TracesResultPage(list, builder.build());
    }

    private static TimeFilter timeFilter(final FindTraceCriteria criteria) {
        return criteria.getTimeRange() == null
                ? null
                : DateExpressionParser.getTimeFilter(
                        criteria.getTimeRange(), DateTimeSettings.builder().build());
    }

    // Matches on the root's start time; a null filter or a root with no start time always matches.
    // The filter is derived once per query and passed in, because a relative range such as now()-1h
    // resolves to a later window on every parse — re-deriving it per row would judge the page against
    // a different window than the total.
    private static boolean matchesTimeRange(final TraceRoot root, final TimeFilter timeFilter) {
        if (timeFilter == null) {
            return true;
        }
        final NanoTime startTime = root.getStartTime();
        if (startTime == null) {
            LOGGER.debug("matchesTimeRange: startTime is null, passing trace {}", root.getTraceId());
            return true;
        }
        final long startMs = startTime.toEpochMillis();
        final boolean result = startMs >= timeFilter.getFrom() && startMs <= timeFilter.getTo();
        LOGGER.debug("matchesTimeRange: traceId={} startMs={} from={} to={} result={}",
                root.getTraceId(), startMs, timeFilter.getFrom(), timeFilter.getTo(), result);
        return result;
    }

    // Sorted query over indexDbi. With no time range this is O(offset+length) — skip/limit straight on
    // the raw index. With one, the match runs BEFORE skip/limit, or a descending query would page from
    // the wrong end of the index (the newest 100 entries for a window two hours back); the stream still
    // stops as soon as the page is full.
    private TracesResultPage findTracesByIndex(final FindTraceCriteria criteria,
                                               final Dbi<ByteBuffer> indexDbi,
                                               final boolean desc,
                                               final Predicate<TraceRoot> filterPredicate) {
        final List<TraceRoot> list = new ArrayList<>();
        final int offset = criteria.getPageRequest().getOffset();
        final int length = criteria.getPageRequest().getLength();

        // Derive the time filter once (if any) so it can drive both the page scan and the
        // exact count below.
        final TimeFilter timeFilter = timeFilter(criteria);
        final boolean hasFilter = filterPredicate != null;
        // Per-row match combining the (optional) time range and the (optional) quick filter.
        final Predicate<TraceRoot> rowMatch = root ->
                matchesTimeRange(root, timeFilter)
                && (!hasFilter || filterPredicate.test(root));

        final long total = env.read(readTxn -> {
            // ---- total ----
            final long count;
            if (timeFilter == null && !hasFilter) {
                // O(1): LMDB stat gives exact entry count without scanning all entries.
                count = traceRootsDbi.stat(readTxn).entries;
            } else if (!hasFilter) {
                // Time range only: exact count via a key-only walk of the chronologically
                // ordered START_TIME index (no TraceRoot deserialisation) — see countTracesInTimeRange.
                count = countTracesInTimeRange(readTxn, timeFilter);
            } else {
                // Quick filter active: no key-only shortcut — deserialise and test each candidate.
                // (Worst case a full-index scan when a filter is set with no time range.)
                try (final Stream<LmdbEntry> countStream =
                             LmdbStream.stream(readTxn, indexDbi, LmdbKeyRange.all())) {
                    count = countStream
                            .map(entry -> safeLookupTraceRoot(readTxn, entry))
                            .filter(Objects::nonNull)
                            .filter(rowMatch)
                            .count();
                }
            }

            // ---- page ----
            final LmdbKeyRange keyRange = desc ? LmdbKeyRange.allReverse() : LmdbKeyRange.all();
            try (final Stream<LmdbEntry> stream = LmdbStream.stream(readTxn, indexDbi, keyRange)) {
                if (timeFilter == null && !hasFilter) {
                    // Fast path: raw index skip/limit, deserialising only the page.
                    stream.skip(offset).limit(length).forEach(entry -> {
                        final TraceRoot root = safeLookupTraceRoot(readTxn, entry);
                        if (root != null) {
                            list.add(root);
                        }
                    });
                } else {
                    // Filter/time-range active: deserialise and test each entry BEFORE skip/limit so
                    // descending queries skip non-matching entries correctly; still lazy — the stream
                    // stops once the page is full.
                    stream
                            .map(entry -> safeLookupTraceRoot(readTxn, entry))
                            .filter(Objects::nonNull)
                            .filter(rowMatch)
                            .skip(offset)
                            .limit(length)
                            .forEach(list::add);
                }
            }
            return count;
        });

        return new TracesResultPage(list,
                PageResponse.builder()
                        .offset(offset)
                        .length(list.size())
                        .total(total)
                        .exact(true)
                        .build());
    }

    // The traceId is the last 16 bytes of every sort-index key, so a row resolves to its root without
    // reading the index value. Null when the root is missing or unreadable — one bad entry drops its
    // row rather than failing the query.
    private TraceRoot safeLookupTraceRoot(final Txn<ByteBuffer> readTxn, final LmdbEntry entry) {
        try {
            final ByteBuffer keyBuf = entry.getKey().duplicate();
            final byte[] keyBytes = new byte[keyBuf.remaining()];
            keyBuf.get(keyBytes);
            final byte[] traceIdBytes = Arrays.copyOfRange(
                    keyBytes, keyBytes.length - TRACE_ID_BYTES, keyBytes.length);
            return getTraceRoot(readTxn, traceIdBytes).orElse(null);
        } catch (final RuntimeException e) {
            LOGGER.debug("Error reading trace from sort index: {}", e.getMessage(), e);
            return null;
        }
    }

    // Start time is encoded in the START_TIME index key, so counting a window costs a key-only walk of
    // one contiguous range — no value read, no TraceRoot decode. That is what lets the pager report an
    // exact total under a time filter. Roots with a zero start time key at (0,0) and fall outside any
    // real window, so they are not counted even though matchesTimeRange would pass them.
    private long countTracesInTimeRange(final Txn<ByteBuffer> readTxn, final TimeFilter timeFilter) {
        requireSecondaryIndexes("count traces in a time range");
        final Dbi<ByteBuffer> startTimeDbi = secondaryIndexDbis.get(TraceSecondaryIndex.START_TIME);
        try (final Stream<LmdbEntry> stream =
                LmdbStream.stream(readTxn, startTimeDbi, startTimeKeyRange(timeFilter))) {
            // Not SIZED, so count() actually traverses the range (key-only, no value reads).
            return stream.count();
        }
    }

    /**
     * Counts traces per equal time-bucket over {@code timeFilter}'s window. Without a filter this is a
     * key-only walk of the chronologically-ordered {@link TraceSecondaryIndex#START_TIME} index — the
     * start time is decoded straight from the key ({@code secs[8] ∥ nanos[4]}), so no {@link TraceRoot}
     * is deserialised. With a quick filter each candidate root is deserialised and tested (the START_TIME
     * range already bounds the window, so only the quick-filter predicate is applied).
     */
    public long[] histogram(final TimeFilter timeFilter,
                            final long bucketWidthMs,
                            final int nBuckets,
                            final Predicate<TraceRoot> filterPredicate) {
        requireSecondaryIndexes("histogram traces");
        final long[] counts = new long[nBuckets];
        final long fromMs = timeFilter.getFrom();
        final Dbi<ByteBuffer> startTimeDbi = secondaryIndexDbis.get(TraceSecondaryIndex.START_TIME);
        env.read(readTxn -> {
            try (final Stream<LmdbEntry> stream =
                    LmdbStream.stream(readTxn, startTimeDbi, startTimeKeyRange(timeFilter))) {
                if (filterPredicate == null) {
                    stream.forEach(entry -> {
                        final ByteBuffer keyBuf = entry.getKey().duplicate();
                        final long secs = keyBuf.getLong();
                        final int nanos = keyBuf.getInt();
                        addToBucket(counts, secs * 1_000L + nanos / 1_000_000L, fromMs, bucketWidthMs, nBuckets);
                    });
                } else {
                    stream.map(entry -> safeLookupTraceRoot(readTxn, entry))
                            .filter(Objects::nonNull)
                            .filter(filterPredicate)
                            .forEach(root -> {
                                final NanoTime start = root.getStartTime();
                                if (start != null) {
                                    addToBucket(counts, start.toEpochMillis(), fromMs, bucketWidthMs, nBuckets);
                                }
                            });
                }
            }
            return null;
        });
        return counts;
    }

    private static void addToBucket(final long[] counts, final long ms, final long fromMs,
                                    final long bucketWidthMs, final int nBuckets) {
        long bucket = (ms - fromMs) / bucketWidthMs;
        if (bucket < 0) {
            bucket = 0;
        } else if (bucket >= nBuckets) {
            bucket = nBuckets - 1;
        }
        counts[(int) bucket]++;
    }

    // Contiguous START_TIME index range covering exactly the [from, to] millisecond window. The filter
    // compares at millisecond granularity, so the bounds span the first nanosecond of the from-ms to the
    // last nanosecond of the to-ms, both inclusive.
    private LmdbKeyRange startTimeKeyRange(final TimeFilter timeFilter) {
        final long from = timeFilter.getFrom();
        final long to = timeFilter.getTo();
        final ByteBuffer start = startTimeBound(
                from / 1_000L, (int) ((from % 1_000L) * 1_000_000L), (byte) 0x00);
        final ByteBuffer stop = startTimeBound(
                to / 1_000L, (int) ((to % 1_000L) * 1_000_000L + 999_999L), (byte) 0xFF);
        return LmdbKeyRange.builder()
                .start(start, true)
                .stop(stop, true)
                .build();
    }

    // traceIdFill is 0x00 for an inclusive lower bound, 0xFF for an inclusive upper one.
    private static ByteBuffer startTimeBound(final long secs, final int nanos, final byte traceIdFill) {
        final ByteBuffer buf = ByteBuffer.allocateDirect(Long.BYTES + Integer.BYTES + TRACE_ID_BYTES);
        buf.putLong(secs).putInt(nanos);
        for (int i = 0; i < TRACE_ID_BYTES; i++) {
            buf.put(traceIdFill);
        }
        buf.flip();
        return buf;
    }

    public Trace getTrace(final GetTraceRequest request) {
        return env.read(readTxn -> getTrace(readTxn, request.getTraceId()));
    }

    /**
     * Returns the full assembled {@link Trace} for the given raw trace-ID bytes,
     * or {@link Optional#empty()} if no spans exist for that trace ID in this shard.
     * Opens its own read transaction.
     */
    public Optional<Trace> findTrace(final byte[] traceId) {
        return env.read(readTxn -> {
            final TraceBuilder traceBuilder = new TraceBuilder(HexStringUtil.encode(traceId));
            byteBuffers.useBytes(traceId, prefixBuffer -> {
                findSpans(readTxn, traceId, traceBuilder::addSpan);
                return null;
            });
            // Return the trace even when it has no root span (orphan-only: root aged out or never
            // arrived) so the UI can render the available spans with a warning. Only a trace with
            // no spans at all is absent.
            if (!traceBuilder.hasSpans()) {
                return Optional.empty();
            }
            return Optional.of(traceBuilder.build());
        });
    }

    /**
     * Returns the full assembled {@link Trace} for the given raw trace-ID bytes, in its own read
     * transaction.
     */
    public Trace getTrace(final byte[] traceId) {
        return env.read(readTxn -> getTrace(readTxn, traceId));
    }

    public Trace getTrace(final Txn<ByteBuffer> txn, final byte[] traceId) {
        final TraceBuilder traceBuilder = new TraceBuilder(HexStringUtil.encode(traceId));
        byteBuffers.useBytes(traceId, prefixBuffer -> {
            findSpans(txn, traceId, traceBuilder::addSpan);
        });
        return traceBuilder.build();
    }

    public Trace getTrace(final Txn<ByteBuffer> txn, final String traceIdString) {
        final byte[] traceId = HexStringUtil.decode(traceIdString);
        final TraceBuilder traceBuilder = new TraceBuilder(traceIdString);
        byteBuffers.useBytes(traceId, prefixBuffer -> {
            findSpans(txn, traceId, traceBuilder::addSpan);
        });
        return traceBuilder.build();
    }

    // ------------------------------------------------------------------------
    // Lazy, streaming trace traversal
    //
    // Walk a trace top-down straight off the span index
    // (traceId ∥ parentSpanId ∥ spanId) instead of materialising the whole Trace
    // in memory. A node's children are the prefix scan (traceId ∥ thisSpanId); the
    // root is (traceId ∥ 0*8). Peak memory for a top-down consumer is its working
    // set (depth + per-node sibling width), never O(total spans). Reusable by the
    // aggregate computation below now, and by pathways / the UI later. All methods
    // run against a caller-supplied txn.
    // ------------------------------------------------------------------------

    /**
     * Returns the root span of a trace (the first span whose parentSpanId is
     * empty), or empty if the trace has no root span in this shard.
     */
    public Optional<Span> rootSpan(final Txn<ByteBuffer> txn, final byte[] traceIdBytes) {
        final Span[] holder = new Span[1];
        forEachChild(txn, traceIdBytes, NO_PARENT_SPAN_ID, span -> {
            if (holder[0] == null) {
                holder[0] = span;
            }
        });
        return Optional.ofNullable(holder[0]);
    }

    /** Convenience: {@link #rootSpan(Txn, byte[])} in its own read transaction. */
    public Optional<Span> rootSpan(final byte[] traceIdBytes) {
        return env.read(readTxn -> rootSpan(readTxn, traceIdBytes));
    }

    /**
     * Convenience: the direct children of {@code parentSpanIdBytes} as a list, in its
     * own read transaction. Holds only this node's children (bounded by its width) —
     * a lazy alternative to materialising the whole trace.
     */
    public List<Span> children(final byte[] traceIdBytes, final byte[] parentSpanIdBytes) {
        final List<Span> children = new ArrayList<>();
        env.read(readTxn -> {
            streamChildren(readTxn, traceIdBytes, parentSpanIdBytes, children::add);
            return null;
        });
        return children;
    }

    /**
     * Streams the direct children of {@code parentSpanIdBytes} within a trace to
     * {@code consumer}, reading each child span from the index on demand — holding
     * only one span at a time rather than the whole tree.
     */
    public void streamChildren(final Txn<ByteBuffer> txn,
                               final byte[] traceIdBytes,
                               final byte[] parentSpanIdBytes,
                               final Consumer<Span> consumer) {
        forEachChild(txn, traceIdBytes, parentSpanIdBytes, consumer);
    }

    private void forEachChild(final Txn<ByteBuffer> txn,
                              final byte[] traceIdBytes,
                              final byte[] parentSpanIdBytes,
                              final Consumer<Span> consumer) {
        byteBuffers.useBytes(childPrefix(traceIdBytes, parentSpanIdBytes), prefixBuffer -> {
            final LmdbKeyRange keyRange = LmdbKeyRange.builder().prefix(prefixBuffer).build();
            LmdbIterable.iterate(txn, dbi, keyRange, (key, val) -> {
                final SpanKey spanKey = keySerde.read(txn, key);
                final SpanValue spanValue = valueSerde.read(txn, val);
                consumer.accept(createSpan(spanKey, spanValue));
            });
        });
    }

    // Keys-only child walk for the depth DFS: yields each child's spanId, decoding no span values.
    private void forEachChildSpanId(final Txn<ByteBuffer> txn,
                                    final byte[] traceIdBytes,
                                    final byte[] parentSpanIdBytes,
                                    final Consumer<byte[]> spanIdConsumer) {
        byteBuffers.useBytes(childPrefix(traceIdBytes, parentSpanIdBytes), prefixBuffer -> {
            final LmdbKeyRange keyRange = LmdbKeyRange.builder().prefix(prefixBuffer).build();
            LmdbIterable.iterate(txn, dbi, keyRange, (key, val) ->
                    spanIdConsumer.accept(readSpanId(key)));
        });
    }

    private static byte[] childPrefix(final byte[] traceIdBytes, final byte[] parentSpanIdBytes) {
        final byte[] prefix = new byte[TRACE_ID_BYTES + SPAN_ID_BYTES];
        System.arraycopy(traceIdBytes, 0, prefix, 0, TRACE_ID_BYTES);
        System.arraycopy(parentSpanIdBytes, 0, prefix, TRACE_ID_BYTES, SPAN_ID_BYTES);
        return prefix;
    }

    // Trailing 8-byte spanId of a span key, read without disturbing the buffer.
    private static byte[] readSpanId(final ByteBuffer key) {
        final byte[] spanId = new byte[SPAN_ID_BYTES];
        final ByteBuffer k = key.duplicate();
        k.position(k.position() + TRACE_ID_BYTES + SPAN_ID_BYTES + START_TIME_BYTES);
        k.get(spanId);
        return spanId;
    }

    // The child's 16-byte locator (startTime ∥ spanId) — the key bytes after traceId ∥ parentSpanId,
    // read without disturbing the buffer. This is the child's position within its parent's
    // start-time-ordered child range, and what the DFS cursor stores per level.
    private static byte[] readLocator(final ByteBuffer key) {
        final byte[] locator = new byte[LOCATOR_BYTES];
        final ByteBuffer k = key.duplicate();
        k.position(k.position() + TRACE_ID_BYTES + SPAN_ID_BYTES);
        k.get(locator);
        return locator;
    }

    // The spanId (last 8 bytes) of a 16-byte locator.
    private static byte[] spanIdOf(final byte[] locator) {
        final byte[] spanId = new byte[SPAN_ID_BYTES];
        System.arraycopy(locator, START_TIME_BYTES, spanId, 0, SPAN_ID_BYTES);
        return spanId;
    }

    // ------------------------------------------------------------------------
    // Paged tree-order (pre-order DFS) reads for very large traces.
    //
    // Rows are streamed straight off the span index in depth-first tree order, one page at a time,
    // resumable via a cursor (the ancestor spanId path from the root to the last-emitted node).
    // Peak memory is O(depth + page), never O(total spans), so a 700k-span trace is viewable a
    // window at a time. Random access is provided by resuming from a nearby checkpoint cursor.
    // ------------------------------------------------------------------------

    /** One row of a paged tree-order read: a span, its depth (0 = root), and whether it has any children
     * (drives the client's expander). */
    public record SpanRow(Span span, int depth, boolean hasChildren) {
    }

    /**
     * Decides whether the DFS descends into a span's children. Used to honour per-span expand/collapse: a
     * closed span is still emitted, but its subtree is skipped. {@link #ALL} = fully expanded (descend
     * everywhere), the default that reproduces an unfiltered pre-order walk.
     */
    @FunctionalInterface
    public interface SpanOpenTest {

        SpanOpenTest ALL = (spanId, depth) -> true;

        boolean isOpen(byte[] spanId, int depth);
    }

    /**
     * A page of tree-order rows plus the cursor to resume after the last row ({@code nextCursor} =
     * the DFS path as a list of 16-byte locators, {@code startTime ∥ spanId}) and whether more rows
     * may follow.
     */
    public record SpanPage(List<SpanRow> rows, List<byte[]> nextCursor, boolean more) {
    }

    /** Runs {@code fn} in a read txn on this store's env — lets a caller hold a txn per store. */
    public <R> R read(final Function<Txn<ByteBuffer>, R> fn) {
        return env.read(fn);
    }

    /** A child found by a {@link ChildCursor}: the span plus its 16-byte locator (startTime ∥ spanId). */
    public record ChildSpan(Span span, byte[] locator) {
    }

    /**
     * Supplies a node's children in start-time (locator) order for the pre-order DFS. A single-store
     * cursor wraps one {@link TraceDb} + read txn; a merged cursor unions several stores so a trace
     * split across the live shard and archive bucket(s) walks as one tree.
     */
    public interface ChildCursor {

        // First child of parentSpanId whose locator is strictly greater than afterLocator (or the very
        // first child when null), skipping any spanId in excludeSpanIdHexes (cycle guard). null = none.
        ChildSpan firstChildAfter(byte[] parentSpanId, byte[] afterLocator, Set<String> excludeSpanIdHexes);
    }

    /** A {@link ChildCursor} over one store, bound to a read txn. */
    public static final class SingleStoreChildCursor implements ChildCursor {

        private final TraceDb db;
        private final Txn<ByteBuffer> txn;
        private final byte[] traceIdBytes;

        public SingleStoreChildCursor(final TraceDb db,
                                      final Txn<ByteBuffer> txn,
                                      final byte[] traceIdBytes) {
            this.db = db;
            this.txn = txn;
            this.traceIdBytes = traceIdBytes;
        }

        @Override
        public ChildSpan firstChildAfter(final byte[] parentSpanId,
                                         final byte[] afterLocator,
                                         final Set<String> excludeSpanIdHexes) {
            return db.firstChildAfter(txn, traceIdBytes, parentSpanId, afterLocator, excludeSpanIdHexes);
        }
    }

    /**
     * A {@link ChildCursor} that unions several archive buckets: returns the child with the smallest
     * locator across all delegates, so siblings from different buckets interleave in start-time order.
     * A trace's spans normally all sit in the bucket of its root, so most reads union a single bucket;
     * a trace splits only when late spans are bucketed by a synthesized orphan root (see
     * {@link #publish}). Duplicate spans (identical locator) collapse to one.
     */
    public static final class MergedChildCursor implements ChildCursor {

        private final List<ChildCursor> delegates;

        public MergedChildCursor(final List<ChildCursor> delegates) {
            this.delegates = delegates;
        }

        @Override
        public ChildSpan firstChildAfter(final byte[] parentSpanId,
                                         final byte[] afterLocator,
                                         final Set<String> excludeSpanIdHexes) {
            ChildSpan best = null;
            for (final ChildCursor delegate : delegates) {
                final ChildSpan hit =
                        delegate.firstChildAfter(parentSpanId, afterLocator, excludeSpanIdHexes);
                if (hit != null
                        && (best == null || Arrays.compareUnsigned(hit.locator(), best.locator()) < 0)) {
                    best = hit;
                }
            }
            return best;
        }
    }

    /**
     * Up to {@code limit} spans in pre-order (tree) order from {@code cursor}, resuming after
     * {@code cursorPath} (null/empty = start at the root). O(depth + limit).
     */
    public static SpanPage getSpanPage(final ChildCursor cursor,
                                       final List<byte[]> cursorPath,
                                       final int limit,
                                       final SpanOpenTest openTest) {
        final List<byte[]> path = new ArrayList<>();
        if (cursorPath != null) {
            path.addAll(cursorPath);
        }
        final List<SpanRow> rows = new ArrayList<>(Math.max(0, limit));
        for (int i = 0; i < limit; i++) {
            final Optional<Span> next = advancePreorder(cursor, path, openTest);
            if (next.isEmpty()) {
                return new SpanPage(rows, new ArrayList<>(path), false);
            }
            // depth 0 = root, matching the non-virtualized waterfall's indentation (path
            // includes the current node, so its size is the 1-based depth).
            rows.add(new SpanRow(next.get(), path.size() - 1, cursorHasChildren(cursor, path)));
        }
        return new SpanPage(rows, new ArrayList<>(path), true);
    }

    // Whether the node at the end of 'path' has any child span (drives the client's expander). One
    // start-bounded seek — O(log n).
    private static boolean cursorHasChildren(final ChildCursor cursor, final List<byte[]> path) {
        final byte[] last = path.get(path.size() - 1);
        return cursor.firstChildAfter(spanIdOf(last), null, Set.of()) != null;
    }

    /**
     * Sparse pre-order DFS checkpoints for a {@link ChildCursor}: {@code checkpoints.get(k)} is the DFS
     * path (list of 16-byte locators) at offset {@code (k + 1) * CHECKPOINT_INTERVAL}, and {@code total}
     * is the exact pre-order row count. Gives a traversal that unions several archive buckets cheap
     * random-access offset seeks: a per-bucket row count says nothing about a position in the union, so
     * the index has to describe the merged walk. Built once by {@link #buildCheckpoints} and cached by
     * the caller.
     */
    public record CheckpointIndex(List<List<byte[]>> checkpoints, int total) {

    }

    /**
     * Walks the whole pre-order traversal of {@code cursor} once, snapshotting the DFS path every
     * {@link #CHECKPOINT_INTERVAL} rows. O(n) — the caller should cache the result.
     */
    public static CheckpointIndex buildCheckpoints(final ChildCursor cursor, final SpanOpenTest openTest) {
        final List<List<byte[]>> checkpoints = new ArrayList<>();
        final List<byte[]> path = new ArrayList<>();
        int emitted = 0;
        while (advancePreorder(cursor, path, openTest).isPresent()) {
            emitted++;
            if (emitted % CHECKPOINT_INTERVAL == 0) {
                checkpoints.add(new ArrayList<>(path));
            }
        }
        return new CheckpointIndex(checkpoints, emitted);
    }

    /**
     * Random-access page from {@code cursor} using a prebuilt {@link CheckpointIndex}: seeds the DFS from
     * the nearest checkpoint at or before {@code offset}, then walks the remainder. {@code offset} is
     * clamped to the start of the last page, so an over-estimated last-page offset still returns the true
     * last page. O(CHECKPOINT_INTERVAL + limit).
     */
    public static SpanPage getSpanPageAtOffset(final ChildCursor cursor,
                                               final CheckpointIndex index,
                                               final int offset,
                                               final int limit,
                                               final SpanOpenTest openTest) {
        final int lastPageStart = index.total() <= 0 || limit <= 0
                ? 0
                : ((index.total() - 1) / limit) * limit;
        final int clamped = Math.max(0, Math.min(offset, lastPageStart));
        final int checkpointOffset = (clamped / CHECKPOINT_INTERVAL) * CHECKPOINT_INTERVAL;
        final List<byte[]> path = new ArrayList<>();
        if (checkpointOffset > 0) {
            final int idx = checkpointOffset / CHECKPOINT_INTERVAL - 1;
            if (idx < index.checkpoints().size()) {
                path.addAll(index.checkpoints().get(idx));
            }
        }
        final int skip = path.isEmpty() ? clamped : clamped - checkpointOffset;
        for (int i = 0; i < skip; i++) {
            if (advancePreorder(cursor, path, openTest).isEmpty()) {
                return new SpanPage(new ArrayList<>(), new ArrayList<>(path), false);
            }
        }
        return getSpanPage(cursor, path, limit, openTest);
    }

    // Advances 'path' in place to the next node in pre-order DFS and returns its span, or empty when
    // exhausted. 'path' is the chain of 16-byte locators (startTime ∥ spanId) from the root to the
    // current node (empty = before the root). Children come from 'cursor' (single store or merged).
    // Malformed cycles are skipped via the ancestor set.
    private static Optional<Span> advancePreorder(final ChildCursor cursor,
                                                  final List<byte[]> path,
                                                  final SpanOpenTest openTest) {
        final Set<String> ancestors = new HashSet<>();
        for (final byte[] loc : path) {
            ancestors.add(HexStringUtil.encode(spanIdOf(loc)));
        }

        // Start: first root span (child of the all-zero parent).
        if (path.isEmpty()) {
            final ChildSpan root = cursor.firstChildAfter(NO_PARENT_SPAN_ID, null, ancestors);
            if (root == null) {
                return Optional.empty();
            }
            path.add(root.locator());
            return Optional.of(root.span());
        }

        // 1. Descend into the first child of the current (last) node — but only if it is expanded.
        // A collapsed node was still emitted; we just skip its subtree (its children are hidden).
        final byte[] last = path.get(path.size() - 1);
        if (openTest.isOpen(spanIdOf(last), path.size() - 1)) {
            final ChildSpan child = cursor.firstChildAfter(spanIdOf(last), null, ancestors);
            if (child != null) {
                path.add(child.locator());
                return Optional.of(child.span());
            }
        }

        // 2. No child — walk up to the nearest ancestor that has a next sibling.
        for (int j = path.size() - 1; j >= 0; j--) {
            final byte[] parent = j == 0
                    ? NO_PARENT_SPAN_ID
                    : spanIdOf(path.get(j - 1));
            final Set<String> upperAncestors = new HashSet<>();
            for (int k = 0; k < j; k++) {
                upperAncestors.add(HexStringUtil.encode(spanIdOf(path.get(k))));
            }
            final ChildSpan sibling = cursor.firstChildAfter(parent, path.get(j), upperAncestors);
            if (sibling != null) {
                while (path.size() > j) {
                    path.remove(path.size() - 1);
                }
                path.add(sibling.locator());
                return Optional.of(sibling.span());
            }
        }
        return Optional.empty();
    }

    // A start-bounded range scan — O(log n) seek — so a wide (flat) level resumes cheaply. See
    // ChildCursor#firstChildAfter for the contract.
    public ChildSpan firstChildAfter(final Txn<ByteBuffer> txn,
                                     final byte[] traceIdBytes,
                                     final byte[] parentSpanIdBytes,
                                     final byte[] afterLocator,
                                     final Set<String> excludeSpanIdHexes) {
        final byte[] lo = childRangeKey(traceIdBytes, parentSpanIdBytes,
                afterLocator == null ? new byte[LOCATOR_BYTES] : afterLocator);
        final boolean startInclusive = afterLocator == null;
        final byte[] hi = childRangeKey(traceIdBytes, parentSpanIdBytes, LOCATOR_MAX);
        final ChildSpan[] holder = new ChildSpan[1];
        byteBuffers.useBytes(lo, loBuf -> {
            byteBuffers.useBytes(hi, hiBuf -> {
                final LmdbKeyRange range = LmdbKeyRange.builder()
                        .start(loBuf, startInclusive)
                        .stop(hiBuf, true)
                        .build();
                try (final Stream<LmdbEntry> stream = LmdbStream.stream(txn, dbi, range)) {
                    stream.filter(entry ->
                                    !excludeSpanIdHexes.contains(
                                            HexStringUtil.encode(readSpanId(entry.getKey()))))
                            .findFirst()
                            .ifPresent(entry -> {
                                final byte[] locator = readLocator(entry.getKey());
                                final SpanKey spanKey = keySerde.read(txn, entry.getKey());
                                final SpanValue spanValue = valueSerde.read(txn, entry.getVal());
                                holder[0] = new ChildSpan(createSpan(spanKey, spanValue), locator);
                            });
                }
            });
        });
        return holder[0];
    }

    private static byte[] childRangeKey(final byte[] traceIdBytes,
                                        final byte[] parentSpanIdBytes,
                                        final byte[] locatorSuffix) {
        final byte[] key = new byte[TRACE_ID_BYTES + SPAN_ID_BYTES + LOCATOR_BYTES];
        System.arraycopy(traceIdBytes, 0, key, 0, TRACE_ID_BYTES);
        System.arraycopy(parentSpanIdBytes, 0, key, TRACE_ID_BYTES, SPAN_ID_BYTES);
        System.arraycopy(locatorSuffix, 0, key, TRACE_ID_BYTES + SPAN_ID_BYTES, LOCATOR_BYTES);
        return key;
    }

    /**
     * A downsampled overview for a very large trace: at most {@code maxBars} representative spans
     * across the [{@code fromMs}, {@code toMs}] extent — the longest-duration span in each of
     * {@code maxBars} equal time buckets. One streaming pass over the trace's spans (bounded memory),
     * so the whole-trace shape can be shown without returning every span. Own read txn.
     */
    public List<Span> getOverviewSpans(final byte[] traceIdBytes,
                                       final long fromMs,
                                       final long toMs,
                                       final int maxBars) {
        final int bars = Math.max(1, maxBars);
        return env.read(txn -> {
            final long spanMs = Math.max(1L, toMs - fromMs);
            final Span[] best = new Span[bars];
            final long[] bestDur = new long[bars];
            byteBuffers.useBytes(traceIdBytes, prefixBuf -> {
                final LmdbKeyRange range = LmdbKeyRange.builder().prefix(prefixBuf).build();
                LmdbIterable.iterate(txn, dbi, range, (key, val) -> {
                    final SpanKey spanKey = keySerde.read(txn, key);
                    final SpanValue spanValue = valueSerde.read(txn, val);
                    final Span span = createSpan(spanKey, spanValue);
                    final long startMs = span.start() == null
                            ? fromMs
                            : span.start().toEpochMillis();
                    int bucket = (int) (((startMs - fromMs) * bars) / spanMs);
                    if (bucket < 0) {
                        bucket = 0;
                    } else if (bucket >= bars) {
                        bucket = bars - 1;
                    }
                    final long dur = span.duration() == null
                            ? 0L
                            : span.duration().getNanos();
                    if (best[bucket] == null || dur > bestDur[bucket]) {
                        best[bucket] = span;
                        bestDur[bucket] = dur;
                    }
                });
            });
            final List<Span> out = new ArrayList<>();
            for (final Span span : best) {
                if (span != null) {
                    out.add(span);
                }
            }
            return out;
        });
    }

    // Depth by iterative pre-order walk, for a trace deep enough that descend()'s recursion is a risk.
    private int walkDepth(final Txn<ByteBuffer> txn, final byte[] traceIdBytes) {
        final ChildCursor cursor = new SingleStoreChildCursor(this, txn, traceIdBytes);
        final List<byte[]> path = new ArrayList<>();
        int maxDepth = 0;
        while (advancePreorder(cursor, path, SpanOpenTest.ALL).isPresent()) {
            if (path.size() > maxDepth) {
                maxDepth = path.size();
            }
        }
        return maxDepth;
    }

    public static byte[] encodePath(final List<byte[]> path) {
        final byte[] out = new byte[path.size() * LOCATOR_BYTES];
        int p = 0;
        for (final byte[] locator : path) {
            System.arraycopy(locator, 0, out, p, LOCATOR_BYTES);
            p += LOCATOR_BYTES;
        }
        return out;
    }

    public static List<byte[]> decodePath(final byte[] bytes) {
        final List<byte[]> path = new ArrayList<>();
        for (int i = 0; i + LOCATOR_BYTES <= bytes.length; i += LOCATOR_BYTES) {
            final byte[] locator = new byte[LOCATOR_BYTES];
            System.arraycopy(bytes, i, locator, 0, LOCATOR_BYTES);
            path.add(locator);
        }
        return path;
    }

    // Folds one span into the per-trace stats in the caller's write txn — O(1), no rescan. Counts are
    // cumulative and are not decremented when spans later age out under retention. Both merge paths
    // call this only for a genuinely-new span, so re-delivery never double-counts.
    private void recordNewSpan(final Txn<ByteBuffer> writeTxn,
                               final byte[] traceIdBytes,
                               final String name,
                               final NanoTime insertTime,
                               final NanoTime endTime,
                               final boolean spanError) {
        // Distinct service-name set → detect a genuinely new name. The name is interned rather
        // than embedded raw because an LMDB key is capped at Db.MAX_KEY_LENGTH and a span name is
        // unbounded — an over-long one throws MDB_BAD_VALSIZE, which the callers rethrow, failing
        // the batch identically on every retry. The span value interns the same name through the
        // same lookup, so this reuses that entry rather than adding one.
        final byte[] nameBytes = name == null
                ? new byte[0]
                : name.getBytes(StandardCharsets.UTF_8);
        final boolean newName = byteBuffers.use(
                TRACE_ID_BYTES + lookupSerde.getStorageLength(nameBytes), nameKeyBuf -> {
                    nameKeyBuf.put(traceIdBytes);
                    lookupSerde.write(writeTxn, nameBytes, nameKeyBuf);
                    nameKeyBuf.flip();
                    return traceServiceNamesDbi.put(
                            writeTxn, nameKeyBuf, emptyValue(), PutFlags.MDB_NOOVERWRITE);
                });

        final long insertMs = insertTime == null
                ? 0L
                : NanoTimeUtil.toInstant(insertTime).toEpochMilli();

        byteBuffers.useBytes(traceIdBytes, keyBuf -> {
            final ByteBuffer existing = traceStatsDbi.get(writeTxn, keyBuf);
            final TraceStats prev = existing != null
                    ? traceStatsSerde.read(existing.duplicate())
                    : TraceStats.EMPTY;
            final NanoTime newMaxEnd = (endTime != null
                    && (prev.maxEnd() == null || endTime.compareTo(prev.maxEnd()) > 0))
                    ? endTime
                    : prev.maxEnd();
            final TraceStats next = new TraceStats(
                    prev.spanCount() + 1,
                    prev.serviceCount() + (newName ? 1 : 0),
                    newMaxEnd,
                    Math.max(prev.lastActivityMs(), insertMs),
                    prev.depth(),
                    prev.spanCountAtLastDepth(),
                    prev.hasError() || spanError,
                    prev.truncated());
            traceStatsSerde.write(next, valBuf -> traceStatsDbi.put(writeTxn, keyBuf, valBuf));
        });
    }

    private static boolean isErrorStatus(final StatusCode code) {
        return StatusCode.STATUS_CODE_ERROR.equals(code);
    }

    private static boolean isErrorStatus(final SpanStatus status) {
        return status != null && isErrorStatus(status.getCode());
    }

    // True when the span key's 8-byte parentSpanId is all-zero, i.e. it is a root span.
    private static boolean isRootKey(final ByteBuffer key) {
        final byte[] parentSpanId = new byte[SPAN_ID_BYTES];
        final ByteBuffer k = key.duplicate();
        k.position(k.position() + TRACE_ID_BYTES);
        k.get(parentSpanId);
        return Arrays.equals(parentSpanId, NO_PARENT_SPAN_ID);
    }

    // Builds the stored TraceRoot from the root span plus the per-trace stats, so a received root costs
    // no per-span rescan however large the trace grows: totalSpans and services come from the cumulative
    // counters, endTime and lastActivityMs from the ratcheted maxima. Depth is carried from the stats and
    // only re-walked when never computed or when the span count has doubled. Empty only when the trace
    // has no live span at all — spans without a root span get a flagged orphan root instead.
    private Optional<TraceRoot> buildRootFromStats(final Txn<ByteBuffer> txn,
                                                   final byte[] traceIdBytes) {
        final Optional<Span> optRoot = rootSpan(txn, traceIdBytes);
        if (optRoot.isEmpty()) {
            // Publishing takes every span it stages, root span included, so a trace that has been published
            // has no root span here. Its stored root stays authoritative: name, start time, root end and
            // depth are all fixed by data already handed over, and depth in particular cannot be re-derived
            // from the spans left behind. Only the stats-backed fields move as late spans arrive.
            final Optional<TraceRoot> stored = getTraceRoot(txn, traceIdBytes);
            if (stored.isPresent() && !stored.get().isOrphan()) {
                return Optional.of(refreshFromStats(txn, traceIdBytes, stored.get()));
            }

            // No root span and no rooted stored root. If the trace still has a live (orphan) span — its
            // root never arrived — synthesize a flagged orphan TraceRoot from the per-trace stats so it
            // stays listed (with a warning) and viewable as a rootless trace. Gate on an ACTUAL live span,
            // not the cumulative stats.spanCount() (which counts aged-out spans), to avoid ghost rows once
            // every span has gone.
            if (!hasAnySpan(txn, traceIdBytes)) {
                return Optional.empty();
            }
            final TraceStats orphanStats = readStats(txn, traceIdBytes);
            final NanoTime orphanEnd = orphanStats.maxEnd() != null
                    ? orphanStats.maxEnd()
                    : NanoTime.ZERO;
            // The archive bucket is labelled from the root's start time, so this has to be stable as spans
            // arrive — the earliest span start is fixed by data already received, the latest end is not.
            final NanoTime orphanStart = earliestSpanStart(txn, traceIdBytes).orElse(orphanEnd);
            return Optional.of(TraceRoot.builder()
                    .traceId(HexStringUtil.encode(traceIdBytes))
                    .name("")                       // no root operation name
                    .startTime(orphanStart)
                    .endTime(orphanEnd)
                    .services(orphanStats.serviceCount())
                    .depth(0)
                    .totalSpans((int) orphanStats.spanCount())
                    .lastActivityMs(orphanStats.lastActivityMs())
                    .rootEndTime(null)
                    .orphan(true)
                    .error(orphanStats.hasError())
                    .truncated(orphanStats.truncated())
                    .build());
        }
        final Span root = optRoot.get();
        TraceStats stats = readStats(txn, traceIdBytes);

        int depth = stats.depth();
        // Recompute depth when never computed, or (for a small trace) every cycle since the DFS
        // is cheap, or (for a large trace) only when the span count has doubled — so the DFS
        // stays off the hot path exactly for the huge, continuously-growing traces that motivated
        // this change, while normal traces keep an always-current depth.
        if (depth == 0
                || stats.spanCount() <= DEPTH_EXACT_SPAN_THRESHOLD
                || stats.spanCount() >= 2L * Math.max(1L, stats.spanCountAtLastDepth())) {
            if (stats.spanCount() > LARGE_TRACE_SPANS) {
                depth = walkDepth(txn, traceIdBytes);
            } else {
                // The path-visited guard in descend() skips back-edges so a cyclic trace terminates.
                final int[] maxLevel = {0};
                descend(txn, traceIdBytes, HexStringUtil.decode(root.getSpanId()), 1, maxLevel,
                        new HashSet<>());
                depth = maxLevel[0];
            }
            stats = new TraceStats(stats.spanCount(), stats.serviceCount(), stats.maxEnd(),
                    stats.lastActivityMs(), depth, stats.spanCount(), stats.hasError(),
                    stats.truncated());
            writeStats(txn, traceIdBytes, stats);
        }

        final NanoTime end = stats.spanCount() > 0 && stats.maxEnd() != null
                ? stats.maxEnd()
                : root.end();
        return Optional.of(TraceRoot.builder()
                .traceId(HexStringUtil.encode(traceIdBytes))
                .name(root.getName())
                .startTime(root.start())
                .endTime(end)
                .services(stats.serviceCount())
                .depth(depth)
                .totalSpans((int) stats.spanCount())
                .lastActivityMs(stats.lastActivityMs())
                .rootEndTime(root.end())
                .error(stats.hasError())
                .truncated(stats.truncated())
                .build());
    }

    private Optional<TraceRoot> getTraceRoot(final Txn<ByteBuffer> txn, final byte[] traceIdBytes) {
        final TraceRoot[] out = {null};
        traceRootKeySerde.write(new TraceRootKey(traceIdBytes), keyBuf -> {
            final ByteBuffer existing = traceRootsDbi.get(txn, keyBuf);
            if (existing != null) {
                out[0] = traceRootValueSerde.read(existing.duplicate());
            }
        });
        return Optional.ofNullable(out[0]);
    }

    // Moves the stored root's stats-backed fields on, leaving the fields fixed by the root span itself — its
    // name, start, own end, and the depth walked while its spans were still here — as they were stored.
    private TraceRoot refreshFromStats(final Txn<ByteBuffer> txn,
                                       final byte[] traceIdBytes,
                                       final TraceRoot stored) {
        final TraceStats stats = readStats(txn, traceIdBytes);
        return TraceRoot.builder()
                .traceId(stored.getTraceId())
                .name(stored.getName())
                .startTime(stored.getStartTime())
                .endTime(stats.maxEnd() != null ? stats.maxEnd() : stored.getEndTime())
                .services(stats.serviceCount())
                .depth(stored.getDepth())
                .totalSpans((int) stats.spanCount())
                .lastActivityMs(stats.lastActivityMs())
                .rootEndTime(stored.getRootEndTime())
                .orphan(false)
                .error(stats.hasError())
                .truncated(stats.truncated())
                .build();
    }

    // Cheap prefix existence check: does the span DBI hold >=1 span for this traceId? Gates orphan-root
    // synthesis/cleanup on a genuinely-live span, not the cumulative stats count (which counts aged-out spans).
    private boolean hasAnySpan(final Txn<ByteBuffer> txn, final byte[] traceIdBytes) {
        return byteBuffers.useBytes(traceIdBytes, prefixBuffer -> {
            final LmdbKeyRange keyRange = LmdbKeyRange.builder().prefix(prefixBuffer).build();
            try (final Stream<LmdbEntry> stream = LmdbStream.stream(txn, dbi, keyRange)) {
                return stream.findFirst().isPresent();
            }
        });
    }

    private TraceStats readStats(final Txn<ByteBuffer> txn, final byte[] traceIdBytes) {
        return byteBuffers.useBytes(traceIdBytes, keyBuf -> {
            final ByteBuffer existing = traceStatsDbi.get(txn, keyBuf);
            return existing != null
                    ? traceStatsSerde.read(existing.duplicate())
                    : TraceStats.EMPTY;
        });
    }

    private void writeStats(final Txn<ByteBuffer> writeTxn,
                            final byte[] traceIdBytes,
                            final TraceStats stats) {
        byteBuffers.useBytes(traceIdBytes, keyBuf -> {
            traceStatsSerde.write(stats, valBuf -> traceStatsDbi.put(writeTxn, keyBuf, valBuf));
        });
    }

    // DFS step recording the deepest level reached (root = level 1). path holds the spanIds on the
    // current root-to-node chain, so a child already on it is a back-edge from a malformed cyclic
    // trace and is skipped — which is what makes the walk terminate. path is O(current depth).
    private void descend(final Txn<ByteBuffer> txn,
                         final byte[] traceIdBytes,
                         final byte[] spanId,
                         final int level,
                         final int[] maxLevel,
                         final Set<String> path) {
        if (level > maxLevel[0]) {
            maxLevel[0] = level;
        }
        path.add(HexStringUtil.encode(spanId));
        forEachChildSpanId(txn, traceIdBytes, spanId, childSpanId -> {
            if (!path.contains(HexStringUtil.encode(childSpanId))) {
                descend(txn, traceIdBytes, childSpanId, level + 1, maxLevel, path);
            }
        });
        path.remove(HexStringUtil.encode(spanId));
    }

    private void findSpans(final Txn<ByteBuffer> txn,
                           final byte[] traceId,
                           final Consumer<Span> consumer) {
        byteBuffers.useBytes(traceId, prefixBuffer -> {
            final LmdbKeyRange keyRange = LmdbKeyRange.builder().prefix(prefixBuffer).build();
            LmdbIterable.iterate(txn, dbi, keyRange, (key, val) -> {
                final SpanKey spanKey = keySerde.read(txn, key);
                final SpanValue spanValue = valueSerde.read(txn, val);
                final Span span = createSpan(spanKey, spanValue);
                consumer.accept(span);
            });
        });
    }

    private static class TraceBuilder {

        private final String traceId;
        private final Map<String, Map<String, Span>> traceMap = new ConcurrentHashMap<>();

        public TraceBuilder(final String traceId) {
            this.traceId = traceId;
        }

        public void addSpan(final Span span) {
            traceMap.computeIfAbsent(NullSafe.getOrElse(span, Span::getParentSpanId, ""),
                            k -> new ConcurrentHashMap<>())
                    .put(span.getSpanId(), span);
        }

        public boolean hasSpans() {
            return !traceMap.isEmpty();
        }

        public Trace build() {
            final Map<String, List<Span>> parentSpanIdMap = traceMap
                    .entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                            Entry::getKey,
                            entry -> entry
                                    .getValue()
                                    .values()
                                    .stream()
                                    .sorted(Comparator.comparing(Span::start))
                                    .toList()));
            return Trace.builder().traceId(traceId).parentSpanIdMap(parentSpanIdMap).build();
        }
    }

    private Span createSpan(final SpanKey spanKey, final SpanValue spanValue) {
        return Span.builder()
                .traceId(spanKey.getTraceId())
                .spanId(spanKey.getSpanId())
                .parentSpanId(spanKey.getParentSpanId())
                .traceState(spanValue.getTraceState())
                .flags(spanValue.getFlags())
                .name(spanValue.getName())
                .kind(spanValue.getKind())
                .startTimeUnixNano(spanValue.getStartTimeUnixNano())
                .endTimeUnixNano(spanValue.getEndTimeUnixNano())
                .attributes(spanValue.getAttributes())
                .droppedAttributesCount(spanValue.getDroppedAttributesCount())
                .events(spanValue.getEvents())
                .droppedEventsCount(spanValue.getDroppedEventsCount())
                .links(spanValue.getLinks())
                .droppedLinksCount(spanValue.getDroppedLinksCount())
                .status(spanValue.getStatus())
                .build();
    }

    public interface TraceConverter extends Converter<SpanKey, SpanValue> {

    }
}
