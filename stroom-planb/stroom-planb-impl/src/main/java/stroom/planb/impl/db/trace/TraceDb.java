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
import stroom.pathways.shared.otel.trace.Trace;
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.planb.impl.data.ArchivalGranularityUtil;
import stroom.planb.impl.data.SpanKV;
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
import stroom.planb.impl.serde.trace.LookupSerdeImpl;
import stroom.planb.impl.serde.trace.SpanKey;
import stroom.planb.impl.serde.trace.SpanKeySerde;
import stroom.planb.impl.serde.trace.SpanValue;
import stroom.planb.impl.serde.trace.SpanValueSerde;
import stroom.planb.shared.ArchivalGranularity;
import stroom.planb.shared.PlanBDocument;
import stroom.planb.shared.StateKeySchema;
import stroom.planb.shared.StateValueSchema;
import stroom.planb.shared.TraceSettings;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.TimeFilter;
import stroom.query.api.TimeRange;
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
import org.lmdbjava.PutFlags;
import org.lmdbjava.Txn;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TraceDb extends AbstractDb<SpanKey, SpanValue> {

    private static final int CURRENT_SCHEMA_VERSION = 1;
    /**
     * Names of the lookup named-DBs that must be copied verbatim into every
     * archive partition so that the UID / hash integers embedded in archived
     * span values can still be decoded.
     *
     * <p>The {@code trace-roots*} DBIs are deliberately <em>not</em> in this list.
     * Each archive receives only the roots (and the six secondary sort indexes
     * for those roots) whose start time falls in that archive's bucket, written
     * explicitly by {@link #archiveOldData}. Copying the trace-root DBIs wholesale
     * would place a full snapshot of every root into every bucket, causing the
     * same root to appear in multiple archives (duplicate results, inflated
     * counts) and mis-aligning the archive contents with the start-time bucket
     * label that {@link stroom.planb.impl.data.ArchiveShardLocator} selects on.
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

    // Tree-order (DFS) random access for very large traces: snapshot a resume-cursor every
    // CHECKPOINT_INTERVAL rows, but only for traces larger than CHECKPOINT_MIN_SPANS (smaller traces
    // load whole). Keeps checkpoint storage to ~totalSpans / CHECKPOINT_INTERVAL entries per trace.
    private static final int CHECKPOINT_INTERVAL = 1_000;
    private static final long CHECKPOINT_MIN_SPANS = 10_000L;

    /**
     * Returns a fresh zero-byte direct {@link ByteBuffer} for use as an empty
     * LMDB value. A new instance is returned on each call to avoid shared-state
     * issues with {@code ByteBuffer} position/limit under concurrent use.
     */
    private static ByteBuffer emptyValue() {
        return ByteBuffer.allocateDirect(0);
    }

    private final ByteBufferFactory byteBufferFactory;
    private final KeySerde<SpanKey> keySerde;
    private final Serde<SpanValue> valueSerde;
    // Typed reference to the same object as valueSerde, held so that
    // archiveOldData / deleteOldData can call readInsertTime() without
    // going through the UID lookup table.
    private final SpanValueSerde spanValueSerde;
    private final UsedLookupsRecorder keyRecorder;
    private final UsedLookupsRecorder valueRecorder;
    private final Dbi<ByteBuffer> traceRootsDbi;
    /**
     * Time-ordered secondary index of traces whose root span has been received,
     * keyed by the wall-clock time at which the merge processor wrote the entry.
     * Key: (mergeTimeMs 8-byte big-endian ∥ traceId 16 bytes), value: empty.
     * Using merge time rather than the span's claimed end time ensures the
     * grace period used by {@code PathwaysProcessor} is measured from when the
     * root span was received by the system, independent of out-of-order delivery.
     *
     * <p>Lifecycle:
     * <ul>
     *   <li><b>Written</b> by the merge processor when a root span (empty
     *       {@code parentSpanId}) is inserted via {@link #insert}.</li>
     *   <li><b>Cleared</b> from the live shard by retention and archiving
     *       policies via {@link #deleteOldData}.</li>
     *   <li><b>Copied</b> into archive shards by {@link #copyLookupsTo} so
     *       that archives remain independently queryable (e.g. for fan-out
     *       time-range queries).</li>
     * </ul>
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

    //    private final Dbi<ByteBuffer> traceUpdateTimeDbi;
//    private final Dbi<ByteBuffer> updateTimeDbi;
    private final TraceRootKeySerde traceRootKeySerde;
    private final TraceRootValueSerde traceRootValueSerde;
//    private final TimeSerde updateTimeSerde;

    /**
     * Per-trace incremental aggregate accumulator: {@code traceId → TraceStats}. Updated as each
     * new span is inserted so {@link #mergeComplete()} reads O(1) stats rather than re-scanning
     * every span. See {@link TraceStats}.
     */
    private final Dbi<ByteBuffer> traceStatsDbi;
    /**
     * Distinct service-name set per trace: {@code traceId[16] ∥ nameBytes → ∅}. Lets a
     * genuinely-new name be detected (via {@link PutFlags#MDB_NOOVERWRITE}) so the cumulative
     * {@code serviceCount} in {@link TraceStats} is exact.
     */
    private final Dbi<ByteBuffer> traceServiceNamesDbi;
    private final Dbi<ByteBuffer> traceDfsCheckpointsDbi;
    private final TraceStatsSerde traceStatsSerde;

    /**
     * Trace IDs (hex) whose stored root should be recomputed over the fully-merged span set at
     * the end of the current merge cycle — see {@link #mergeComplete()}. Populated during
     * {@link #merge}. The per-batch root copy in merge() reflects only that batch's spans, so
     * depth/services/totalSpans must be re-derived once all batches are present.
     */
    private final Set<String> pendingRootRebuilds = ConcurrentHashMap.newKeySet();

    private TraceDb(final PlanBEnv env,
                    final ByteBuffers byteBuffers,
                    final ByteBufferFactory byteBufferFactory,
                    final PlanBDocument doc,
                    final TraceSettings settings,
                    final KeySerde<SpanKey> keySerde,
                    final Serde<SpanValue> valueSerde,
                    final HashClashCommitRunnable hashClashCommitRunnable) {
        super(env,
                byteBuffers,
                doc,
                settings.overwrite(),
                hashClashCommitRunnable,
                new SchemaInfo(
                        CURRENT_SCHEMA_VERSION,
                        JsonUtil.writeValueAsString(new StateKeySchema.Builder().build()),
                        JsonUtil.writeValueAsString(new StateValueSchema.Builder().build())));
        this.byteBufferFactory = byteBufferFactory;
        this.keySerde = keySerde;
        this.valueSerde = valueSerde;
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
        traceDfsCheckpointsDbi = env.openDbi("trace-dfs-checkpoints", DbiFlags.MDB_CREATE);

        // Open one DBI per secondary sort index, keyed by the index definition.
        final Map<TraceSecondaryIndex, Dbi<ByteBuffer>> indexDbis =
                new EnumMap<>(TraceSecondaryIndex.class);
        for (final TraceSecondaryIndex index : TraceSecondaryIndex.values()) {
            indexDbis.put(index, env.openDbi(index.dbiName(), DbiFlags.MDB_CREATE));
        }
        secondaryIndexDbis = indexDbis;

//        traceUpdateTimeDbi = env.openDbi("trace-update-time", DbiFlags.MDB_CREATE);
//        updateTimeDbi = env.openDbi("update-time", DbiFlags.MDB_CREATE);
//
//        updateTimeSerde = new MillisecondTimeSerde();
    }

    public static TraceDb create(final Path path,
                                 final ByteBuffers byteBuffers,
                                 final ByteBufferFactory byteBufferFactory,
                                 final PlanBDocument doc,
                                 final boolean readOnly) {
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
                    hashClashCommitRunnable);
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

    /**
     * Writes every secondary sort index entry for {@code root} in a single
     * transaction. All indexes use {@link PutFlags#MDB_NOOVERWRITE} — if the
     * exact same key already exists (same sort-field value AND same traceId)
     * the put is silently ignored, preventing duplicate entries.
     */
    private void writeSecondaryIndexes(final Txn<ByteBuffer> writeTxn,
                                       final byte[] traceIdBytes,
                                       final TraceRoot root) {
        for (final TraceSecondaryIndex index : TraceSecondaryIndex.values()) {
            final Dbi<ByteBuffer> indexDbi = secondaryIndexDbis.get(index);
            byteBuffers.useBytes(index.key(root, traceIdBytes), buf -> {
                indexDbi.put(writeTxn, buf, emptyValue(), PutFlags.MDB_NOOVERWRITE);
            });
        }
    }

    /**
     * Deletes every secondary sort index entry for the given {@code oldRoot}.
     * Called before overwriting a trace root so that stale entries (with the
     * previous sort-field values) are removed.
     */
    private void deleteSecondaryIndexes(final Txn<ByteBuffer> writeTxn,
                                        final byte[] traceIdBytes,
                                        final TraceRoot oldRoot) {
        for (final TraceSecondaryIndex index : TraceSecondaryIndex.values()) {
            final Dbi<ByteBuffer> indexDbi = secondaryIndexDbis.get(index);
            byteBuffers.useBytes(index.key(oldRoot, traceIdBytes), buf -> {
                indexDbi.delete(writeTxn, buf);
            });
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
        // Spans are immutable (first write wins) — MDB_NOOVERWRITE makes a genuinely-new span
        // detectable so the per-trace stats are counted exactly once (re-delivery is a no-op).
        final boolean[] isNew = {false};
        keySerde.write(writeTxn, kv.key(), keyByteBuffer ->
                valueSerde.write(writeTxn, kv.val(), valueByteBuffer ->
                        isNew[0] = dbi.put(writeTxn, keyByteBuffer, valueByteBuffer,
                                PutFlags.MDB_NOOVERWRITE)));

        final byte[] traceIdBytes = HexStringUtil.decode(kv.key().getTraceId());

        if (isNew[0]) {
            final SpanValue v = kv.val();
            recordNewSpan(writeTxn, traceIdBytes, v.getName(), v.getInsertTime(),
                    NanoTime.fromString(v.getEndTimeUnixNano()));
        }

        final boolean isRootSpan = NullSafe.isEmptyString(kv.key().getParentSpanId());

        if (isRootSpan) {
            // Root span: (re)derive depth, services, name and totalSpans from the span
            // set seen so far via the bounded streaming aggregate computation — it walks
            // the span index without materialising the whole trace, so it never OOMs on a
            // large/open-ended trace.  Also writes the trace-roots-merge-time entry that
            // drives the PathwaysProcessor grace-period clock.
            try {
                // The root span was just written, so a root is present; skip defensively
                // if somehow absent.
                final Optional<TraceRoot> optNewRoot =
                        buildRootFromStats(writeTxn, traceIdBytes);
                if (optNewRoot.isEmpty()) {
                    return;
                }
                final TraceRootKey traceRootKey = new TraceRootKey(traceIdBytes);
                final TraceRoot newRoot = optNewRoot.get();

                // Delete stale secondary sort-index entries before overwriting.
                traceRootKeySerde.write(traceRootKey, traceRootKeyBuf -> {
                    final ByteBuffer existing = traceRootsDbi.get(writeTxn, traceRootKeyBuf);
                    if (existing != null) {
                        final TraceRoot oldRoot = traceRootValueSerde.read(existing.duplicate());
                        deleteSecondaryIndexes(writeTxn, traceIdBytes, oldRoot);
                    }
                });

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
            } catch (final RuntimeException e) {
                LOGGER.warn("Failed to write trace root index for trace {}: {}",
                        kv.key().getTraceId(), e.getMessage(), e);
            }

        } else {
            // Child span: if the root has already been received, do an O(1) incremental
            // update — read the existing TraceRoot, increment totalSpans, expand the
            // startTime/endTime bounds, then overwrite.  This is far cheaper than calling
            // getTrace() (O(spans-in-shard)) on every child span.
            //
            // depth and services are left unchanged here; they will be correctly computed
            // when the root span is processed (root spans always call getTrace()).
            try {
                final TraceRootKey traceRootKey = new TraceRootKey(traceIdBytes);

                // Read existing root (returns null if root has not yet been seen).
                final TraceRoot[] oldRootRef = {null};
                traceRootKeySerde.write(traceRootKey, traceRootKeyBuf -> {
                    final ByteBuffer existing = traceRootsDbi.get(writeTxn, traceRootKeyBuf);
                    if (existing != null) {
                        oldRootRef[0] = traceRootValueSerde.read(existing.duplicate());
                    }
                });

                if (oldRootRef[0] != null) {
                    final TraceRoot oldRoot = oldRootRef[0];

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
                    final TraceRoot newRoot = oldRoot.copy()
                            .startTime(newStart)
                            .endTime(newEnd)
                            .totalSpans(oldRoot.getTotalSpans() + 1)
                            .lastActivityMs(Math.max(oldRoot.getLastActivityMs(), spanInsertMs))
                            .build();

                    traceRootKeySerde.write(traceRootKey, keyBuffer ->
                            traceRootValueSerde.write(newRoot, valueBuffer ->
                                    traceRootsDbi.put(writeTxn, keyBuffer, valueBuffer)));

                    updateChildSpanIndexes(writeTxn, traceIdBytes, oldRoot, newRoot);
                }
            } catch (final RuntimeException e) {
                LOGGER.warn("Failed to incrementally update trace root for trace {}: {}",
                        kv.key().getTraceId(), e.getMessage(), e);
            }
        }

        writer.tryCommit();
    }

    /**
     * Migrates the secondary sort index entries from {@code oldRoot} to
     * {@code newRoot} after a child span is merged.
     *
     * <p>For each index only the entries whose key actually changed are touched:
     * if {@code index.key(oldRoot)} equals {@code index.key(newRoot)} the index is
     * skipped entirely. This naturally limits work to the indexes affected by a
     * child span (total-spans always; start-time when the earliest start moves;
     * duration when either bound moves) while leaving operation/services/depth —
     * whose values don't change on a child span — untouched, all without naming
     * any index individually.
     */
    private void updateChildSpanIndexes(final Txn<ByteBuffer> writeTxn,
                                        final byte[] traceIdBytes,
                                        final TraceRoot oldRoot,
                                        final TraceRoot newRoot) {
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

//    private void updateInsertOrder( final Txn<ByteBuffer> writeTxn,
//                                    final byte[] traceIdBytes) {
//        // Write update time for processing new traces.
//        final Instant updateTime =  Instant.now();
//        byteBuffers.useBytes(traceIdBytes, idBuffer -> {
//            byteBuffers.use(updateTimeSerde.getSize(), timeBuffer -> {
//                updateTimeSerde.write(timeBuffer, updateTime);
//                timeBuffer.flip();
//
//                traceUpdateTimeDbi.put(writeTxn, idBuffer, timeBuffer);
//                byteBuffers.use(traceIdBytes.length + updateTimeSerde.getSize(), keyBuffer -> {
//                    keyBuffer.put(traceIdBytes);
//                    updateTimeSerde.write(keyBuffer, updateTime);
//                    keyBuffer.flip();
//                    updateTimeDbi.put(writeTxn, keyBuffer, VALUE);
//                });
//            });
//        });
//    }

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
     * Iterates the {@code trace-roots-merge-time} DBI in ascending merge-time order,
     * yielding the raw traceId bytes for every entry whose merge time is
     * ≤ {@code cutoffMs}. Iteration stops as soon as the current entry's merge
     * time exceeds the cutoff — O(eligible) range scan rather than a full scan.
     *
     * <p>Key layout: 8-byte big-endian mergeTimeMs ∥ 16-byte traceId.
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
            try (final TraceDb sourceDb = TraceDb.create(source, byteBuffers, byteBufferFactory, doc, true)) {
                // Validate that the source DB has the same schema.
                validateSchema(schemaInfo, sourceDb.getSchemaInfo());

                // Merge.
                sourceDb.env.read(readTxn -> {
                    try (final Stream<LmdbEntry> stream = LmdbStream.stream(readTxn, sourceDb.dbi)) {
                        stream.forEach(entry -> {
                            // Queue EVERY trace whose spans are merged this cycle for a full
                            // root recompute at mergeComplete — not just traces whose root span
                            // appears. A span key is prefixed with the 16-byte traceId. Without
                            // this, a trace whose children arrive after its root was processed
                            // (root in an earlier cycle) keeps stale depth/services/totalSpans.
                            final ByteBuffer spanKeyBuf = entry.getKey().duplicate();
                            final byte[] tid;
                            if (spanKeyBuf.remaining() >= TRACE_ID_BYTES) {
                                tid = new byte[TRACE_ID_BYTES];
                                spanKeyBuf.get(tid);
                                pendingRootRebuilds.add(HexStringUtil.encode(tid));
                            } else {
                                tid = null;
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
                                                s.insertTime(), s.endTime());
                                    }
                                    writer.tryCommit();
                                }
                            }
                        });
                    }

                    // Merge trace roots.  For each entry successfully written (new traceId),
                    // also populate the six sort indexes so the target shard remains
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
            final Txn<ByteBuffer> writeTxn = writer.getWriteTxn();
            for (final String hex : traceIds) {
                final byte[] traceIdBytes = HexStringUtil.decode(hex);
                // Bounded, streaming recompute over the fully-merged span set — exact
                // totalSpans/services and (safety-valve aside) exact depth, plus the
                // latest insert time as lastActivityMs (informational only — shown in the UI
                // "Last Activity" column; retention/archival now age by the root's own end
                // time, not activity), without materialising the whole trace. Empty ⇒ no
                // root span present (a traceId whose only spans are orphans) ⇒ no queryable
                // root, so skip.
                final Optional<TraceRoot> optRebuilt =
                        buildRootFromStats(writeTxn, traceIdBytes);
                if (optRebuilt.isEmpty()) {
                    continue;
                }
                final TraceRoot rebuilt = optRebuilt.get();
                final TraceRootKey traceRootKey = new TraceRootKey(traceIdBytes);

                // Drop stale sort-index entries for the existing stored root, then overwrite.
                traceRootKeySerde.write(traceRootKey, keyBuf -> {
                    final ByteBuffer existing = traceRootsDbi.get(writeTxn, keyBuf);
                    if (existing != null) {
                        deleteSecondaryIndexes(writeTxn, traceIdBytes,
                                traceRootValueSerde.read(existing.duplicate()));
                    }
                });
                traceRootKeySerde.write(traceRootKey, keyBuf ->
                        traceRootValueSerde.write(rebuilt, valBuf ->
                                traceRootsDbi.put(writeTxn, keyBuf, valBuf)));
                writeSecondaryIndexes(writeTxn, traceIdBytes, rebuilt);
                writer.tryCommit();
            }
            return null;
        });
    }

    /**
     * Refresh archived roots' {@code totalSpans} from the per-trace span counter for the traces the
     * just-completed archival merge touched. The counter is seeded to the exact count when a bucket is
     * created ({@link #archiveOldData}) and maintained by {@code recordNewSpan} during each re-merge, so a
     * bucket created under this code keeps an exact Total Spans as it grows across cycles — the stored root
     * value would otherwise go stale (frozen at first archival).
     *
     * <p>Forward-only: buckets created before this maintenance were never seeded, so their counter reflects
     * only spans merged in since; such traces are not retroactively corrected. Also counts only THIS
     * bucket, so a trace spanning multiple date buckets is under-counted by its other buckets (rare — a
     * trace longer than the archival granularity).
     */
    public void refreshArchivedRootSpanCounts() {
        if (pendingRootRebuilds.isEmpty()) {
            return;
        }
        final List<String> hexes = new ArrayList<>(pendingRootRebuilds);
        pendingRootRebuilds.clear();
        env.write(writer -> {
            final Txn<ByteBuffer> txn = writer.getWriteTxn();
            for (final String hex : hexes) {
                final byte[] tid = HexStringUtil.decode(hex);
                setRootTotalSpans(txn, tid, (int) readStats(txn, tid).spanCount());
                writer.tryCommit();
            }
            return null;
        });
    }

    // Key-only prefix count of the span DBI for a traceId (no value reads).
    private long countSpansForTrace(final Txn<ByteBuffer> txn, final byte[] traceIdBytes) {
        final long[] count = {0L};
        byteBuffers.useBytes(traceIdBytes, prefixBuffer -> {
            final LmdbKeyRange keyRange = LmdbKeyRange.builder().prefix(prefixBuffer).build();
            try (final Stream<LmdbEntry> stream = LmdbStream.stream(txn, dbi, keyRange)) {
                count[0] = stream.count();
            }
        });
        return count[0];
    }

    // Overwrite only the spanCount of a trace's stats (keeping other fields), so recordNewSpan increments
    // from an accurate base on the next archival merge.
    private void seedStatsSpanCount(final Txn<ByteBuffer> txn, final byte[] traceIdBytes, final long count) {
        final TraceStats s = readStats(txn, traceIdBytes);
        writeStats(txn, traceIdBytes, new TraceStats(
                count, s.serviceCount(), s.maxEnd(), s.lastActivityMs(), s.depth(), count));
    }

    // Rewrite a (non-orphan) root's totalSpans and its secondary indexes. No-op if absent/orphan/unchanged.
    private void setRootTotalSpans(final Txn<ByteBuffer> txn, final byte[] traceIdBytes, final int total) {
        final TraceRootKey key = new TraceRootKey(traceIdBytes);
        traceRootKeySerde.write(key, keyBuf -> {
            final ByteBuffer existing = traceRootsDbi.get(txn, keyBuf);
            if (existing != null) {
                final TraceRoot root = traceRootValueSerde.read(existing.duplicate());
                if (!root.isOrphan() && root.getTotalSpans() != total) {
                    deleteSecondaryIndexes(txn, traceIdBytes, root);
                    final TraceRoot updated = root.copy().totalSpans(total).build();
                    traceRootValueSerde.write(updated, valBuf -> traceRootsDbi.put(txn, keyBuf, valBuf));
                    writeSecondaryIndexes(txn, traceIdBytes, updated);
                }
            }
        });
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

//    public Trace getTrace(final TraceRequest request) {
//        final SpanValue value = get(request.key());
//        if (value == null) {
//            return null;
//        }
//        return new Trace(request.key(), value);
//    }

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

    // Age axis for archive/delete decisions: the root span's own end (getRootEndTime), falling back to
    // start time for a legacy root (or a ZERO/invalid end). Gating on the root's own end — not the
    // trace's max end, which trailing spans inflate — bounds leaky/never-ending traces; later spans
    // then arrive as parentless orphans and are swept by insert time.
    private static NanoTime archivalAgeAxis(final TraceRoot root) {
        final NanoTime start = root.getStartTime();
        final NanoTime rootEnd = root.getRootEndTime();
        if (rootEnd != null && (start == null || !rootEnd.isLessThan(start))) {
            return rootEnd;
        }
        return start;
    }

    /**
     * Archives whole traces whose <em>root span ended</em> before {@code archiveBefore} (see
     * {@link #archivalAgeAxis}) into dated LMDB environments under {@code archiveBaseDir}
     * (bucketed by the root's start time), then deletes them from the live environment.
     *
     * <p><b>Why start time, not merge/insert time.</b> Queries filter traces on
     * the root's start time, and {@link stroom.planb.impl.data.ArchiveShardLocator}
     * selects which archives to open by comparing the query time range against the
     * archive's date-label bucket. For that selection to be accurate the bucket
     * label must be derived from the same axis the query uses — the trace start
     * time. Bucketing by merge/insert time would put a trace that started at
     * 23:55 but merged at 00:05 the next day into the wrong bucket, making it
     * invisible to a query for its real start day once purged from the live shard.
     *
     * <p><b>Self-contained by trace.</b> Every span of a rooted trace is placed in
     * that trace's bucket regardless of the individual span's timestamp, so a
     * trace whose spans straddle a bucket boundary (e.g. midnight) stays whole in
     * a single archive. Each archive is given only <em>its own</em> roots plus the
     * six secondary sort indexes for those roots (rebuilt via
     * {@link #writeSecondaryIndexes}), so fan-out time-range queries against the
     * archive behave exactly as against the live shard — with no cross-bucket
     * duplication of roots.
     *
     * <p><b>Orphan spans.</b> A span whose traceId has no root span in this shard — the root
     * never arrived, or (with age-based gating) it was archived/deleted while the child
     * remained, leaving a traceId whose only remaining spans are orphans (its root has been
     * removed). Such spans can never form a queryable trace and are swept separately: those
     * older than {@code archiveBefore} by insert time are archived into a bucket labelled by
     * their insert time and then deleted, keeping the live shard bounded even when no retention
     * policy is configured. They produce no trace-root index entry; note that
     * {@code getTrace(traceId)} cannot currently assemble such a trace as it requires a root.
     *
     * <p>Three passes:
     * <ol>
     *   <li>Scan roots then spans, grouping each archived root and its spans (and
     *       any old orphan spans) by bucket label; capture raw bytes.</li>
     *   <li>Write each bucket to its own archive env — spans, roots, the roots'
     *       secondary sort indexes and the lookup tables.</li>
     *   <li>Delete the archived spans, roots, secondary indexes and merge-time
     *       entries from the live env, then drop now-unused lookup keys.</li>
     * </ol>
     */
    @Override
    public long archiveOldData(final Instant archiveBefore,
                               final ArchivalGranularity granularity,
                               final Path archiveBaseDir) {
        final NanoTime nanoTimeBefore = NanoTimeUtil.fromInstant(archiveBefore);

        // traceIdHex -> bucket label, for AGED roots being archived (bucketed by start time).
        final Map<String, String> archivedRootLabels = new HashMap<>();
        // traceIdHex -> decoded root, used to rebuild the archive's sort indexes.
        final Map<String, TraceRoot> archivedRoots = new HashMap<>();
        // traceIdHex -> {rawTraceIdKey, rawRootVal}, for verbatim copy into the archive.
        final Map<String, byte[][]> archivedRootRawKV = new HashMap<>();
        // The distinct insert-time bucket labels that have (non-root) spans to archive.
        // Only labels are held here — NOT the spans — so memory stays O(labels), never
        // O(spans): the OOM fix. Pass 2 re-derives each span's label and streams it.
        final Set<String> spanLabels = new LinkedHashSet<>();

        // Pass 1: scan roots (to decide which traces to archive and how to label
        // them), then spans (only to discover the set of bucket labels that have
        // spans — the spans themselves are streamed later, not buffered).
        //
        // Span insert time is read via readInsertTime() — the first 8 bytes of the
        // value — which does NOT touch the UID lookup table, avoiding
        // "Unable to find value for UID" errors on an incomplete lookup table.
        // TraceRoot values are self-contained (no UID lookup) so decoding them here
        // is safe too.
        env.read(readTxn -> {
            // 1a: trace roots.
            LmdbIterable.iterate(readTxn, traceRootsDbi, (key, val) -> {
                final byte[] traceIdBytes = new byte[key.remaining()];
                key.duplicate().get(traceIdBytes);
                final String hex = HexStringUtil.encode(traceIdBytes);

                final TraceRoot root = traceRootValueSerde.read(val.duplicate());
                // Archive a real root once its own end time is older than the cutoff (aged),
                // regardless of ongoing activity — so a leaky/never-ending trace is bounded
                // rather than kept live forever. Bucket by the root's START time — the axis
                // queries filter on — and its root span rides along (see spanBucketLabel).
                // Orphan roots (synthesized, no root span) are NOT archived here: their non-root
                // spans still archive by insert time (below), and the synthetic root itself is
                // cleaned by retention's quiet gate so the orphan trace stays visible meanwhile.
                final NanoTime startTime = root.getStartTime();
                final NanoTime ageAxis = archivalAgeAxis(root);
                if (!root.isOrphan()
                        && startTime != null && ageAxis != null && ageAxis.isBefore(nanoTimeBefore)) {
                    final String label = ArchivalGranularityUtil.label(
                            granularity, NanoTimeUtil.toInstant(startTime));
                    archivedRootLabels.put(hex, label);
                    archivedRoots.put(hex, root);
                    final byte[] rawVal = new byte[val.remaining()];
                    val.duplicate().get(rawVal);
                    archivedRootRawKV.put(hex, new byte[][]{traceIdBytes, rawVal});
                }
            });

            // 1b: spans — record only the distinct labels that have spans to archive
            // (no per-span buffering).
            LmdbIterable.iterate(readTxn, dbi, (key, val) -> {
                final String label = spanBucketLabel(
                        key, val, archivedRootLabels, nanoTimeBefore, granularity);
                if (label != null) {
                    spanLabels.add(label);
                }
            });
            return null;
        });

        if (archivedRoots.isEmpty() && spanLabels.isEmpty()) {
            return 0L;
        }

        // Invert archivedRootLabels into bucket label -> root traceId hexes.
        final Map<String, List<String>> bucketRootHexes = new LinkedHashMap<>();
        for (final Map.Entry<String, String> e : archivedRootLabels.entrySet()) {
            bucketRootHexes.computeIfAbsent(e.getValue(), l -> new ArrayList<>()).add(e.getKey());
        }

        // Pass 2: write each bucket to its own archive environment.
        //
        // Spans are STREAMED straight from the source index into their bucket's
        // archive env — one label at a time, holding only the current span — rather
        // than buffered (which OOMs on a large/open-ended trace). Peak memory is
        // O(labels + roots), never O(spans). Spans and roots are copied verbatim (the
        // UID integers embedded in the bytes reference the lookup tables cloned by
        // copyLookupsTo); the archive's six secondary sort indexes are rebuilt for
        // this bucket's roots only, so the archive is fully queryable in isolation.
        final Set<String> allLabels = new LinkedHashSet<>();
        allLabels.addAll(bucketRootHexes.keySet());
        allLabels.addAll(spanLabels);
        for (final String label : allLabels) {
            final Path archiveDir = archiveBaseDir.resolve(label);
            try {
                Files.createDirectories(archiveDir);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
            try (final TraceDb archiveDb =
                         TraceDb.create(archiveDir, byteBuffers, byteBufferFactory, doc, false)) {
                archiveDb.env.write(archiveWriter -> {
                    // Roots for this bucket + their secondary indexes.
                    final List<String> rootHexes = bucketRootHexes.get(label);
                    if (rootHexes != null) {
                        for (final String hex : rootHexes) {
                            final byte[][] rawKV = archivedRootRawKV.get(hex);
                            putDirect(archiveDb.traceRootsDbi, archiveWriter.getWriteTxn(),
                                    rawKV[0], rawKV[1]);
                            archiveDb.writeSecondaryIndexes(archiveWriter.getWriteTxn(),
                                    rawKV[0], archivedRoots.get(hex));
                        }
                    }

                    // Spans for this bucket — streamed from the source index, never
                    // buffered. Same predicate as pass 1b / pass 3, so archive and
                    // delete stay symmetric.
                    env.read(srcTxn -> {
                        LmdbIterable.iterate(srcTxn, dbi, (key, val) -> {
                            final String spanLabel = spanBucketLabel(key, val,
                                    archivedRootLabels, nanoTimeBefore, granularity);
                            if (label.equals(spanLabel)) {
                                final byte[] rawKey = new byte[key.remaining()];
                                key.duplicate().get(rawKey);
                                final byte[] rawVal = new byte[val.remaining()];
                                val.duplicate().get(rawVal);
                                putDirect(archiveDb.dbi, archiveWriter.getWriteTxn(), rawKey, rawVal);
                                archiveWriter.tryCommit();
                            }
                        });
                        return null;
                    });

                    // Seed each archived root's exact per-bucket span count: the root value was copied
                    // verbatim with the live cumulative count, which goes stale as later cycles merge
                    // more spans into this bucket. Setting it (and the stats counter) to the actual
                    // count now lets the trace list report a true Total Spans and lets later merges
                    // increment from an accurate base.
                    if (rootHexes != null) {
                        for (final String hex : rootHexes) {
                            final byte[] tid = archivedRootRawKV.get(hex)[0];
                            final long count = archiveDb.countSpansForTrace(
                                    archiveWriter.getWriteTxn(), tid);
                            archiveDb.seedStatsSpanCount(archiveWriter.getWriteTxn(), tid, count);
                            archiveDb.setRootTotalSpans(archiveWriter.getWriteTxn(), tid, (int) count);
                        }
                    }
                    return null;
                });
                // Clone the UID / hash lookup tables so the archive decodes span
                // values without access to the source shard.
                copyLookupsTo(archiveDb);
            }
        }

        // Pass 3: delete the archived spans, roots, secondary indexes and
        // merge-time entries from the live environment.
        //
        // The span scan re-applies the SAME predicate as pass 1b (archived iff the
        // trace's root is being archived, or it is an orphan older than the cutoff)
        // so that exactly the archived spans are deleted — keeping archive and
        // delete symmetric. Every RETAINED span's lookups are recorded via
        // recordUsed so that the subsequent deleteUnused only drops UID/hash lookup
        // entries no longer referenced by any surviving span.
        return env.write(writer -> {
            final Count count = new Count();
            env.read(readTxn -> {
                // Spans. Delete exactly those archived in pass 2 — the SAME predicate
                // (spanBucketLabel != null): non-root spans older than the cutoff, and the
                // root spans of aged roots. Retained spans have their lookups recorded so
                // deleteUnused keeps them.
                LmdbIterable.iterate(readTxn, dbi, (key, val) -> {
                    final boolean archived = spanBucketLabel(
                            key, val, archivedRootLabels, nanoTimeBefore, granularity) != null;
                    if (archived) {
                        dbi.delete(writer.getWriteTxn(), key);
                        count.increment();
                    } else {
                        // Retained — record its lookups so deleteUnused keeps them.
                        keyRecorder.recordUsed(writer, key);
                        valueRecorder.recordUsed(writer, val);
                    }
                    writer.tryCommit();
                });

                // Archived roots + their secondary sort indexes.
                for (final Map.Entry<String, TraceRoot> e : archivedRoots.entrySet()) {
                    final byte[] traceIdBytes = HexStringUtil.decode(e.getKey());
                    byteBuffers.useBytes(traceIdBytes, keyBuf -> {
                        traceRootsDbi.delete(writer.getWriteTxn(), keyBuf);
                    });
                    deleteSecondaryIndexes(writer.getWriteTxn(), traceIdBytes, e.getValue());
                    deleteStatsOf(readTxn, writer, traceIdBytes);
                    count.increment();
                    writer.tryCommit();
                }

                // Merge-time entries for archived roots. Key: mergeTimeMs[8] ∥ traceId[16].
                if (!archivedRoots.isEmpty()) {
                    LmdbIterable.iterate(readTxn, traceRootsMergeTimeDbi, (key, val) -> {
                        final ByteBuffer keyBuf = key.duplicate();
                        if (keyBuf.remaining() != Long.BYTES + TRACE_ID_BYTES) {
                            return;
                        }
                        keyBuf.getLong(); // skip mergeTimeMs prefix
                        final byte[] traceIdBytes = new byte[TRACE_ID_BYTES];
                        keyBuf.get(traceIdBytes);
                        if (archivedRoots.containsKey(HexStringUtil.encode(traceIdBytes))) {
                            traceRootsMergeTimeDbi.delete(writer.getWriteTxn(), key);
                            writer.tryCommit();
                        }
                    });
                }

                // Reap synthesized orphan roots whose spans were just archived away. Archival runs
                // frequently and (unlike a real root) leaves the orphan root behind, so without this
                // an orphan root outlives its spans and lingers as an empty "ghost" row. An orphan
                // root should exist only while the trace still has a live span — check the WRITE txn
                // so the span deletions above are visible.
                LmdbIterable.iterate(readTxn, traceRootsDbi, (key, val) -> {
                    final TraceRoot value = traceRootValueSerde.read(val.duplicate());
                    if (value.isOrphan()) {
                        final byte[] traceIdBytes = new byte[key.remaining()];
                        key.duplicate().get(traceIdBytes);
                        if (!hasAnySpan(writer.getWriteTxn(), traceIdBytes)) {
                            deleteOrphanRoot(readTxn, writer, key, traceIdBytes, value, count);
                            writer.tryCommit();
                        }
                    }
                });

                writer.commit();
                return null;
            });

            // Drop lookup keys that are no longer referenced by any remaining span.
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

    /**
     * The archive bucket label a span belongs to, or {@code null} if it should stay in
     * the live shard. Two rules, matching the retention model:
     * <ul>
     *   <li><b>Root span</b> — rides with its root entry: it takes the root's archive
     *       label (the root's <em>start</em>-time bucket) when the root is aged (its own end
     *       older than the cutoff), and {@code null} (retained) while the root is younger.</li>
     *   <li><b>Non-root span</b> — ages by its own <em>insert</em> (receipt) time: its
     *       insert-time bucket when older than the cutoff, else {@code null} (retained). So a
     *       recent child of an aged root is left behind as an orphan and swept on a later
     *       cycle by insert time.</li>
     * </ul>
     * Reads only the insert time (no UID lookup). Shared by pass 1b (label discovery),
     * pass 2 (streaming write) and pass 3 (delete) so bucketing is defined in one place.
     */
    private String spanBucketLabel(final ByteBuffer key,
                                   final ByteBuffer val,
                                   final Map<String, String> archivedRootLabels,
                                   final NanoTime nanoTimeBefore,
                                   final ArchivalGranularity granularity) {
        if (key.remaining() < TRACE_ID_BYTES) {
            return null;
        }
        if (isRootKey(key)) {
            final byte[] traceIdBytes = new byte[TRACE_ID_BYTES];
            key.duplicate().get(traceIdBytes);
            // Aged root (root end before cutoff) -> its start bucket; younger root -> null
            // (retained). archivedRootLabels holds only the roots selected for archival.
            return archivedRootLabels.get(HexStringUtil.encode(traceIdBytes));
        }
        final NanoTime insertTime = spanValueSerde.readInsertTime(val.duplicate());
        if (insertTime.isBefore(nanoTimeBefore)) {
            return ArchivalGranularityUtil.label(granularity, NanoTimeUtil.toInstant(insertTime));
        }
        return null;
    }

    /**
     * Deletes the retained root span(s) of a trace (prefix {@code traceId ∥ 0*8}) from the
     * live span DBI — used when a root ages out and its root entry is removed. Increments
     * {@code changeCount} per deleted span.
     */
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
    // stats (an orphan has no root span and no merge-time entry). Called by retention and archival
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

    /**
     * Deletes a trace's incremental stats + distinct-name set (prefix {@code traceId}) — used
     * when the trace's root is removed by retention/archival, so the counters don't leak.
     */
    private void deleteStatsOf(final Txn<ByteBuffer> readTxn,
                               final LmdbWriter writer,
                               final byte[] traceIdBytes) {
        byteBuffers.useBytes(traceIdBytes, keyBuf -> {
            traceStatsDbi.delete(writer.getWriteTxn(), keyBuf);
        });
        byteBuffers.useBytes(traceIdBytes, prefixBuf -> {
            final LmdbKeyRange keyRange = LmdbKeyRange.builder().prefix(prefixBuf).build();
            LmdbIterable.iterate(readTxn, traceServiceNamesDbi, keyRange,
                    (key, val) -> traceServiceNamesDbi.delete(writer.getWriteTxn(), key));
        });
        // Per-trace DFS checkpoints are derived data too — drop them when the trace is removed.
        byteBuffers.useBytes(traceIdBytes, prefixBuf -> {
            final LmdbKeyRange keyRange = LmdbKeyRange.builder().prefix(prefixBuf).build();
            LmdbIterable.iterate(readTxn, traceDfsCheckpointsDbi, keyRange,
                    (key, val) -> traceDfsCheckpointsDbi.delete(writer.getWriteTxn(), key));
        });
    }

    /**
     * lmdbjava requires direct (off-heap) buffers; copy the raw key/value bytes
     * into fresh direct buffers and put them into {@code targetDbi}.
     */
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

    /**
     * Copies the lookup named-DBs (UID forward/reverse maps, hash map) from this
     * shard's LMDB environment to the archive shard's environment so that the UID
     * integers embedded in archived span values remain decodable. Trace-root DBIs
     * are NOT copied here — see {@link #LOOKUP_DBI_NAMES} and {@link #archiveOldData},
     * which write each archive's own roots and secondary indexes explicitly.
     */
    private void copyLookupsTo(final TraceDb archive) {
        for (final String name : LOOKUP_DBI_NAMES) {
            copyNamedDbi(name, this.env, archive.env);
        }
    }

    /**
     * Iterates every entry in the named DBI of {@code srcEnv} and puts them
     * verbatim into the same-named DBI of {@code dstEnv}.  Both envs must
     * already have the DBI open (i.e. the owning {@link TraceDb} must have
     * been constructed before this is called).
     */
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
    public long deleteOldData(final Instant deleteBefore, final boolean useStateTime) {
        return env.write(writer -> {
            final NanoTime nanoTime = NanoTimeUtil.fromInstant(deleteBefore);
            final long count = deleteOldData(writer, nanoTime);

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

    private long deleteOldData(final LmdbWriter writer,
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
                    // If this is data we no longer want to retain then delete it.
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
                    final NanoTime ageAxis = archivalAgeAxis(value);
                    if (ageAxis != null && ageAxis.isBefore(deleteBefore)) {
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
            // Key layout: (mergeTimeMs_bigEndian || traceId) — read mergeTimeMs
            // from the first 8 bytes of the key.
            LmdbIterable.iterate(readTxn, traceRootsMergeTimeDbi, (key, val) -> {
                final long mergeTimeMs = key.duplicate().getLong();
                if (mergeTimeMs < deleteBeforeMs) {
                    traceRootsMergeTimeDbi.delete(writer.getWriteTxn(), key);
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


    /**
     * Finds traces using the appropriate secondary sort index.
     *
     * <p>Dispatch logic:
     * <ol>
     *   <li>If a {@link stroom.pathways.shared.pathway.Pathway} is set, fall back to the
     *       full {@code trace-roots} scan with {@code TracePredicate} matching.</li>
     *   <li>Otherwise, dispatch to {@link #findTracesByIndex} using the DBI that matches
     *       the requested sort column.  The default sort (no criteria or {@code Trace Start})
     *       is start-time descending — newest traces first.</li>
     * </ol>
     */
    public TracesResultPage findTraces(final FindTraceCriteria criteria) {
        final List<TraceRoot> list = new ArrayList<>();
        final PageResponse.Builder builder = PageResponse.builder();

        final Comparator<Span> spanComparator = new CloseSpanComparator(criteria.getTemporalOrderingTolerance());
        final PathKeyFactory pathKeyFactory = new PathKeyFactoryImpl();
        if (criteria.getPathway() != null) {
            final TracePredicate tracePredicate = new TracePredicate(
                    spanComparator,
                    pathKeyFactory,
                    Map.of(criteria.getPathway().getPathKey(), criteria.getPathway().getRoot()));

            // Pathway matching requires inspecting every span — no secondary-index shortcut.
            env.read(readTxn -> {
                final Count count = new Count();
                LmdbIterable.iterate(readTxn, traceRootsDbi, (key, val) -> {
                    try {
                        final TraceRootKey traceRootKey = traceRootKeySerde.read(key);
                        final TraceRoot root = traceRootValueSerde.read(val);
                        final TraceBuilder traceBuilder = new TraceBuilder(root.getTraceId());
                        // Get all the spans.
                        byteBuffers.useBytes(traceRootKey.getTraceId(), prefixBuffer -> {
                            findSpans(readTxn, traceRootKey.getTraceId(), traceBuilder::addSpan);
                        });
                        final Trace trace = traceBuilder.build();

                        final long pos = count.getAndIncrement();
                        if (criteria.getPageRequest().getOffset() <= pos &&
                            criteria.getPageRequest().getLength() > list.size() &&
                            tracePredicate.test(trace) &&
                            matchesTimeRange(root, criteria)) {
                            list.add(root);
                        }
                    } catch (final RuntimeException e) {
                        // Expected exception if no trace root.
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
                final TraceSecondaryIndex index = TraceSecondaryIndex.forField(sortField);
                indexDbi = secondaryIndexDbis.get(
                        index != null ? index : TraceSecondaryIndex.START_TIME);
            }

            return findTracesByIndex(criteria, indexDbi, desc);
        }

        return new TracesResultPage(list, builder.build());
    }

    /**
     * Returns {@code true} if the given trace root falls within the criteria's time range
     * (filtering on trace start time). A {@code null} time range, or a trace root with no
     * start time, is always considered to match.
     */
    private static boolean matchesTimeRange(final TraceRoot root, final FindTraceCriteria criteria) {
        final TimeRange timeRange = criteria.getTimeRange();
        if (timeRange == null) {
            return true;
        }
        final NanoTime startTime = root.getStartTime();
        if (startTime == null) {
            LOGGER.debug("matchesTimeRange: startTime is null, passing trace {}", root.getTraceId());
            return true;
        }
        final long startMs = startTime.toEpochMillis();
        final TimeFilter timeFilter =
                DateExpressionParser.getTimeFilter(
                        timeRange, DateTimeSettings.builder().build());
        final boolean result = startMs >= timeFilter.getFrom() && startMs <= timeFilter.getTo();
        LOGGER.debug("matchesTimeRange: traceId={} startMs={} from={} to={} result={}",
                root.getTraceId(), startMs, timeFilter.getFrom(), timeFilter.getTo(), result);
        return result;
    }

    /**
     * Performs a sorted query over {@code indexDbi} using {@link LmdbStream}.
     *
     * <p>When no time range is active, this is O(offset+length): the stream is
     * consumed only as far as needed via {@code skip().limit()} on the raw index.
     *
     * <p>When a time range filter is active, {@link #matchesTimeRange} is applied
     * <em>before</em> {@code skip().limit()} so that pagination operates over
     * filtered results. This prevents descending queries from consuming the wrong
     * end of the index (e.g. the newest 100 entries when the time window is 1-2 h
     * in the past). The stream is still terminated lazily once {@code length}
     * matching roots have been collected.
     *
     * @param criteria  pagination and sort criteria; {@code getPageRequest()} must not be null
     * @param indexDbi  the secondary (or primary) DBI to scan
     * @param desc      {@code true} for descending order ({@link LmdbKeyRange#allReverse()});
     *                  {@code false} for ascending
     */
    private TracesResultPage findTracesByIndex(final FindTraceCriteria criteria,
                                               final Dbi<ByteBuffer> indexDbi,
                                               final boolean desc) {
        final List<TraceRoot> list = new ArrayList<>();
        final int offset = criteria.getPageRequest().getOffset();
        final int length = criteria.getPageRequest().getLength();

        // Derive the time filter once (if any) so it can drive both the page scan and the
        // exact count below.
        final TimeFilter timeFilter = criteria.getTimeRange() == null
                ? null
                : DateExpressionParser.getTimeFilter(
                        criteria.getTimeRange(), DateTimeSettings.builder().build());

        // Use single-element arrays to capture totals from inside the lambda,
        // since the lambda requires effectively-final variables.
        final long[] totalRef = {0L};

        env.read(readTxn -> {
            if (timeFilter == null) {
                // O(1): LMDB stat gives exact entry count without scanning all entries.
                totalRef[0] = traceRootsDbi.stat(readTxn).entries;
            } else {
                // Time range active: the unfiltered stat count would mislead the grid. Count
                // the matching entries exactly via a key-only walk of the chronologically
                // ordered START_TIME index (no TraceRoot deserialisation) — see
                // countTracesInTimeRange.
                totalRef[0] = countTracesInTimeRange(readTxn, timeFilter);
            }

            final LmdbKeyRange keyRange = desc ? LmdbKeyRange.allReverse() : LmdbKeyRange.all();
            try (final Stream<LmdbEntry> stream = LmdbStream.stream(readTxn, indexDbi, keyRange)) {
                if (criteria.getTimeRange() == null) {
                    // No time range: classic O(offset+length) raw index scan.
                    stream
                            .skip(offset)
                            .limit(length)
                            .forEach(entry -> {
                                try {
                                    final TraceRoot root = lookupTraceRoot(readTxn, entry);
                                    if (root != null) {
                                        list.add(root);
                                    }
                                } catch (final RuntimeException e) {
                                    LOGGER.debug("Error reading trace from sort index: {}",
                                            e.getMessage(), e);
                                }
                            });
                } else {
                    // Time range active: filter BEFORE skip/limit so that descending
                    // queries correctly skip entries outside the window (e.g. desc sort
                    // by start-time + time range 1-2 h ago must not consume the newest
                    // N raw entries and then return zero results).
                    stream
                            .map(entry -> {
                                try {
                                    return lookupTraceRoot(readTxn, entry);
                                } catch (final RuntimeException e) {
                                    LOGGER.debug("Error reading trace from sort index: {}",
                                            e.getMessage(), e);
                                    return null;
                                }
                            })
                            .filter(Objects::nonNull)
                            .filter(root -> matchesTimeRange(root, criteria))
                            .skip(offset)
                            .limit(length)
                            .forEach(list::add);
                }
            }
            return null;
        });

        // totalRef now holds an exact total in both cases: the O(1) LMDB stat count when no
        // time range is active, or the exact count of entries matching the time window
        // (via countTracesInTimeRange). Either way the total is exact, so the pager shows the
        // true count rather than "?".
        return new TracesResultPage(list,
                PageResponse.builder()
                        .offset(offset)
                        .length(list.size())
                        .total(totalRef[0])
                        .exact(true)
                        .build());
    }

    /**
     * Counts trace roots whose start time falls within {@code timeFilter}, by walking only the
     * <em>keys</em> of the chronologically-ordered {@link TraceSecondaryIndex#START_TIME} index
     * over the matching contiguous range. The start time is encoded in the key, so this never
     * reads a value or deserialises a {@link TraceRoot} — far cheaper than a filtered full scan,
     * and lets the pager report an exact total under a time filter (cost is O(matching entries)).
     *
     * <p>The index key is {@code (startSecs[8] ∥ startNanos[4] ∥ traceId[16])}, big-endian.
     * {@link #matchesTimeRange} compares at millisecond granularity ({@code from <= startMs <= to}),
     * so the bounds are built to cover exactly that millisecond window: the lower bound sits at
     * the first nanosecond of the {@code from} millisecond (traceId {@code 0x00}s) and the upper
     * bound at the last nanosecond of the {@code to} millisecond (traceId {@code 0xFF}s), both
     * inclusive.
     *
     * <p>Roots with a {@code null} start time are keyed at {@code (0,0)} and so are excluded here
     * even though {@link #matchesTimeRange} would pass them; null start times are abnormal.
     */
    private long countTracesInTimeRange(final Txn<ByteBuffer> readTxn, final TimeFilter timeFilter) {
        final Dbi<ByteBuffer> startTimeDbi = secondaryIndexDbis.get(TraceSecondaryIndex.START_TIME);
        try (final Stream<LmdbEntry> stream =
                LmdbStream.stream(readTxn, startTimeDbi, startTimeKeyRange(timeFilter))) {
            // Not SIZED, so count() actually traverses the range (key-only, no value reads).
            return stream.count();
        }
    }

    /**
     * Distinct trace-IDs (hex) whose start time falls within {@code timeFilter}, from a key-only walk of
     * the {@link TraceSecondaryIndex#START_TIME} index (the traceId is the key suffix, so no value is read
     * and no {@link TraceRoot} is deserialised). Used to compute an exact distinct trace count across the
     * live shard + archive buckets, where summing per-store totals would double-count split traces.
     */
    public Set<String> windowTraceIds(final TimeFilter timeFilter) {
        return env.read(readTxn -> {
            final Set<String> ids = new HashSet<>();
            final Dbi<ByteBuffer> startTimeDbi = secondaryIndexDbis.get(TraceSecondaryIndex.START_TIME);
            try (final Stream<LmdbEntry> stream =
                    LmdbStream.stream(readTxn, startTimeDbi, startTimeKeyRange(timeFilter))) {
                stream.forEach(entry -> {
                    final ByteBuffer keyBuf = entry.getKey().duplicate();
                    final byte[] traceId = new byte[TRACE_ID_BYTES];
                    keyBuf.position(keyBuf.limit() - TRACE_ID_BYTES);
                    keyBuf.get(traceId);
                    ids.add(HexStringUtil.encode(traceId));
                });
            }
            return ids;
        });
    }

    // Contiguous START_TIME index range covering exactly the [from, to] millisecond window (see
    // countTracesInTimeRange for the key layout / bound rationale).
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

    /**
     * Builds a direct {@link ByteBuffer} bound key for the START_TIME index: {@code secs[8] ∥
     * nanos[4] ∥ traceId[16]} with the traceId suffix filled with {@code traceIdFill}
     * ({@code 0x00} for an inclusive lower bound, {@code 0xFF} for an inclusive upper bound).
     */
    private static ByteBuffer startTimeBound(final long secs, final int nanos, final byte traceIdFill) {
        final ByteBuffer buf = ByteBuffer.allocateDirect(Long.BYTES + Integer.BYTES + TRACE_ID_BYTES);
        buf.putLong(secs).putInt(nanos);
        for (int i = 0; i < TRACE_ID_BYTES; i++) {
            buf.put(traceIdFill);
        }
        buf.flip();
        return buf;
    }

    /**
     * Extracts the trace-ID from the last {@value #TRACE_ID_BYTES} bytes of a sort-index
     * entry's key and looks up the corresponding {@link TraceRoot} in {@link #traceRootsDbi}.
     *
     * @return the {@link TraceRoot}, or {@code null} if no matching root is found
     */
    private TraceRoot lookupTraceRoot(final Txn<ByteBuffer> readTxn, final LmdbEntry entry) {
        final ByteBuffer keyBuf = entry.getKey().duplicate();
        final byte[] keyBytes = new byte[keyBuf.remaining()];
        keyBuf.get(keyBytes);
        final byte[] traceIdBytes = Arrays.copyOfRange(
                keyBytes, keyBytes.length - TRACE_ID_BYTES, keyBytes.length);

        final TraceRoot[] rootRef = {null};
        traceRootKeySerde.write(new TraceRootKey(traceIdBytes), traceRootKeyBuf -> {
            final ByteBuffer traceRootVal = traceRootsDbi.get(readTxn, traceRootKeyBuf);
            if (traceRootVal != null) {
                rootRef[0] = traceRootValueSerde.read(traceRootVal.duplicate());
            }
        });
        return rootRef[0];
    }

    public Trace getTrace(final GetTraceRequest request) {
        return env.read(readTxn -> getTrace(readTxn, request.getTraceId()));
    }

    /**
     * Returns the full assembled {@link Trace} for the given raw trace-ID bytes,
     * or {@link Optional#empty()} if no spans exist for that trace ID in this shard.
     * Opening its own read transaction. Suitable for use as a method reference
     * ({@code traceDb::findTrace}) in {@link java.util.function.Function} contexts.
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
     * Returns the full assembled {@link Trace} for the given raw trace-ID bytes,
     * opening its own read transaction. Suitable for use as a method reference
     * ({@code traceDb::getTrace}) in {@link java.util.function.Function} contexts.
     */
    public Trace getTrace(final byte[] traceId) {
        return env.read(readTxn -> getTrace(readTxn, traceId));
    }

    public Trace getTrace(final Txn<ByteBuffer> txn, final byte[] traceId) {
        final TraceBuilder traceBuilder = new TraceBuilder(HexStringUtil.encode(traceId));
        // Get all the spans.
        byteBuffers.useBytes(traceId, prefixBuffer -> {
            findSpans(txn, traceId, traceBuilder::addSpan);
        });
        return traceBuilder.build();
    }

    public Trace getTrace(final Txn<ByteBuffer> txn, final String traceIdString) {
        final byte[] traceId = HexStringUtil.decode(traceIdString);
        final TraceBuilder traceBuilder = new TraceBuilder(traceIdString);
        // Get all the spans.
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

    /**
     * Keys-only child walk: yields each child's raw 8-byte spanId without
     * deserialising span values. Used by the depth DFS.
     */
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

    /** Trailing 8-byte spanId of a span key, read without disturbing the buffer. */
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

    /** One row of a paged tree-order read: a span and its depth (root = 1). */
    public record SpanRow(Span span, int depth) {
    }

    /**
     * A page of tree-order rows plus the cursor to resume after the last row ({@code nextCursor} =
     * the DFS path as a list of 8-byte spanIds) and whether more rows may follow.
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
     * A {@link ChildCursor} that unions several stores (live shard + archive bucket(s)): returns the
     * child with the smallest locator across all delegates, so siblings from different stores interleave
     * in start-time order. Duplicate spans (identical locator — only during transient archival overlap)
     * collapse to one.
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
                                       final int limit) {
        final List<byte[]> path = new ArrayList<>();
        if (cursorPath != null) {
            path.addAll(cursorPath);
        }
        final List<SpanRow> rows = new ArrayList<>(Math.max(0, limit));
        for (int i = 0; i < limit; i++) {
            final Optional<Span> next = advancePreorder(cursor, path);
            if (next.isEmpty()) {
                return new SpanPage(rows, new ArrayList<>(path), false);
            }
            // depth 0 = root, matching the non-virtualized waterfall's indentation (path
            // includes the current node, so its size is the 1-based depth).
            rows.add(new SpanRow(next.get(), path.size() - 1));
        }
        return new SpanPage(rows, new ArrayList<>(path), true);
    }

    /**
     * Single-store convenience: {@code limit} spans in pre-order order from this store, in its own read
     * txn. Resumes after {@code cursorPath} (null/empty = start at the root).
     */
    public SpanPage getSpanPage(final byte[] traceIdBytes,
                                final List<byte[]> cursorPath,
                                final int limit) {
        return env.read(readTxn ->
                getSpanPage(new SingleStoreChildCursor(this, readTxn, traceIdBytes), cursorPath, limit));
    }

    /**
     * Sparse pre-order DFS checkpoints for a {@link ChildCursor}: {@code checkpoints.get(k)} is the DFS
     * path (list of 16-byte locators) at offset {@code (k + 1) * CHECKPOINT_INTERVAL}, and {@code total}
     * is the exact pre-order row count. The in-memory equivalent of the {@code trace-dfs-checkpoints}
     * DBI, used to give a merged (live + archive) traversal — which has no on-disk checkpoints — cheap
     * random-access offset seeks. Built once by {@link #buildCheckpoints} and cached by the caller.
     */
    public record CheckpointIndex(List<List<byte[]>> checkpoints, int total) {

    }

    /**
     * Walks the whole pre-order traversal of {@code cursor} once, snapshotting the DFS path every
     * {@link #CHECKPOINT_INTERVAL} rows. O(n) — the caller should cache the result.
     */
    public static CheckpointIndex buildCheckpoints(final ChildCursor cursor) {
        final List<List<byte[]>> checkpoints = new ArrayList<>();
        final List<byte[]> path = new ArrayList<>();
        int emitted = 0;
        while (advancePreorder(cursor, path).isPresent()) {
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
                                               final int limit) {
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
            if (advancePreorder(cursor, path).isEmpty()) {
                return new SpanPage(new ArrayList<>(), new ArrayList<>(path), false);
            }
        }
        return getSpanPage(cursor, path, limit);
    }

    // Advances 'path' in place to the next node in pre-order DFS and returns its span, or empty when
    // exhausted. 'path' is the chain of 16-byte locators (startTime ∥ spanId) from the root to the
    // current node (empty = before the root). Children come from 'cursor' (single store or merged).
    // Malformed cycles are skipped via the ancestor set.
    private static Optional<Span> advancePreorder(final ChildCursor cursor, final List<byte[]> path) {
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

        // 1. Descend into the first child of the current (last) node.
        final byte[] last = path.get(path.size() - 1);
        final ChildSpan child = cursor.firstChildAfter(spanIdOf(last), null, ancestors);
        if (child != null) {
            path.add(child.locator());
            return Optional.of(child.span());
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

    // First child of 'parentSpanIdBytes' whose locator (startTime ∥ spanId) is strictly greater than
    // 'afterLocator' (or the very first child when null), skipping any child already in
    // 'excludeSpanIdHexes' (cycle guard). A start-bounded range scan → O(log n) seek, so wide (flat)
    // levels resume cheaply, and children are visited in start-time order. Returns null if none.
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
     * Random-access tree-order page: rows {@code [offset, offset+limit)} in pre-order DFS. Resumes
     * from the nearest checkpoint at or before {@code offset} and walks at most CHECKPOINT_INTERVAL
     * rows to reach it, so a scrollbar drag anywhere is O(CHECKPOINT_INTERVAL + limit). Falls back to
     * walking from the start when no checkpoints exist (small / not-yet-checkpointed traces). Own read
     * txn.
     */
    public SpanPage getSpanPageAtOffset(final byte[] traceIdBytes,
                                        final int offset,
                                        final int limit) {
        return env.read(readTxn -> {
            final ChildCursor cursor = new SingleStoreChildCursor(this, readTxn, traceIdBytes);
            final int checkpointOffset = (offset / CHECKPOINT_INTERVAL) * CHECKPOINT_INTERVAL;
            final List<byte[]> checkpoint = readCheckpoint(readTxn, traceIdBytes, checkpointOffset);
            final List<byte[]> path = new ArrayList<>();
            final int skip;
            if (checkpoint == null) {
                skip = offset;                 // no checkpoint — walk from the start
            } else {
                path.addAll(checkpoint);
                skip = offset - checkpointOffset;
            }
            for (int i = 0; i < skip; i++) {
                if (advancePreorder(cursor, path).isEmpty()) {
                    return new SpanPage(new ArrayList<>(), new ArrayList<>(path), false);
                }
            }
            final List<SpanRow> rows = new ArrayList<>(Math.max(0, limit));
            for (int i = 0; i < limit; i++) {
                final Optional<Span> next = advancePreorder(cursor, path);
                if (next.isEmpty()) {
                    return new SpanPage(rows, new ArrayList<>(path), false);
                }
                // depth 0 = root, matching the non-virtualized waterfall's indentation (path
                // includes the current node, so its size is the 1-based depth).
                rows.add(new SpanRow(next.get(), path.size() - 1));
            }
            return new SpanPage(rows, new ArrayList<>(path), true);
        });
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

    // Rebuilds the sparse DFS checkpoints for a trace (prefix-clear + re-snapshot) and returns the
    // trace depth (longest simple path) from the same walk. A resume-cursor is stored every
    // CHECKPOINT_INTERVAL rows; offset 0 is never stored (an empty cursor = start).
    private int rebuildCheckpointsAndDepth(final Txn<ByteBuffer> txn, final byte[] traceIdBytes) {
        deleteCheckpointsOf(txn, traceIdBytes);
        final ChildCursor cursor = new SingleStoreChildCursor(this, txn, traceIdBytes);
        final List<byte[]> path = new ArrayList<>();
        int emitted = 0;
        int maxDepth = 0;
        while (advancePreorder(cursor, path).isPresent()) {
            emitted++;
            if (path.size() > maxDepth) {
                maxDepth = path.size();
            }
            if (emitted % CHECKPOINT_INTERVAL == 0) {
                storeCheckpoint(txn, traceIdBytes, emitted, path);
            }
        }
        return maxDepth;
    }

    private void storeCheckpoint(final Txn<ByteBuffer> txn,
                                 final byte[] traceIdBytes,
                                 final int offset,
                                 final List<byte[]> path) {
        final byte[] key = checkpointKey(traceIdBytes, offset);
        final byte[] val = encodePath(path);
        byteBuffers.useBytes(key, keyBuf -> {
            byteBuffers.useBytes(val, valBuf -> {
                traceDfsCheckpointsDbi.put(txn, keyBuf, valBuf);
            });
        });
    }

    // Returns the stored cursor path at exactly 'offset', or null if none (incl. offset <= 0).
    private List<byte[]> readCheckpoint(final Txn<ByteBuffer> txn,
                                        final byte[] traceIdBytes,
                                        final int offset) {
        if (offset <= 0) {
            return null;
        }
        final List<byte[]> result = new ArrayList<>();
        final boolean[] found = {false};
        byteBuffers.useBytes(checkpointKey(traceIdBytes, offset), keyBuf -> {
            final ByteBuffer value = traceDfsCheckpointsDbi.get(txn, keyBuf);
            if (value != null) {
                found[0] = true;
                final byte[] bytes = new byte[value.remaining()];
                value.duplicate().get(bytes);
                result.addAll(decodePath(bytes));
            }
        });
        return found[0] ? result : null;
    }

    private void deleteCheckpointsOf(final Txn<ByteBuffer> txn, final byte[] traceIdBytes) {
        final List<byte[]> keys = new ArrayList<>();
        byteBuffers.useBytes(traceIdBytes, prefixBuf -> {
            final LmdbKeyRange range = LmdbKeyRange.builder().prefix(prefixBuf).build();
            try (final Stream<LmdbEntry> stream = LmdbStream.stream(txn, traceDfsCheckpointsDbi, range)) {
                stream.forEach(entry -> {
                    final byte[] k = new byte[entry.getKey().remaining()];
                    entry.getKey().duplicate().get(k);
                    keys.add(k);
                });
            }
        });
        for (final byte[] k : keys) {
            byteBuffers.useBytes(k, keyBuf -> {
                traceDfsCheckpointsDbi.delete(txn, keyBuf);
            });
        }
    }

    private static byte[] checkpointKey(final byte[] traceIdBytes, final int offset) {
        final byte[] key = new byte[TRACE_ID_BYTES + Integer.BYTES];
        System.arraycopy(traceIdBytes, 0, key, 0, TRACE_ID_BYTES);
        ByteBuffer.wrap(key).putInt(TRACE_ID_BYTES, offset); // big-endian offset suffix
        return key;
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

    /**
     * Records a newly-inserted span into the per-trace incremental stats, in the caller's write
     * txn: bump {@code spanCount}, add the service name to the distinct-name set (bump
     * {@code serviceCount} if new), and ratchet {@code maxEnd} / {@code lastActivityMs}. O(1).
     * Counts are cumulative — not decremented when spans later age out under retention. Called
     * once per genuinely-new span (both merge paths), so re-delivery never double-counts.
     */
    private void recordNewSpan(final Txn<ByteBuffer> writeTxn,
                               final byte[] traceIdBytes,
                               final String name,
                               final NanoTime insertTime,
                               final NanoTime endTime) {
        // Distinct service-name set → detect a genuinely new name.
        final byte[] nameBytes = name == null
                ? new byte[0]
                : name.getBytes(StandardCharsets.UTF_8);
        final byte[] nameKey = new byte[TRACE_ID_BYTES + nameBytes.length];
        System.arraycopy(traceIdBytes, 0, nameKey, 0, TRACE_ID_BYTES);
        System.arraycopy(nameBytes, 0, nameKey, TRACE_ID_BYTES, nameBytes.length);
        final boolean[] newName = {false};
        byteBuffers.useBytes(nameKey, nameKeyBuf -> {
            newName[0] = traceServiceNamesDbi.put(
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
                    prev.serviceCount() + (newName[0] ? 1 : 0),
                    newMaxEnd,
                    Math.max(prev.lastActivityMs(), insertMs),
                    prev.depth(),
                    prev.spanCountAtLastDepth());
            traceStatsSerde.write(next, valBuf -> traceStatsDbi.put(writeTxn, keyBuf, valBuf));
        });
    }

    /** The 8-byte parentSpanId of a span key, read without disturbing the buffer. */
    private static boolean isRootKey(final ByteBuffer key) {
        final byte[] parentSpanId = new byte[SPAN_ID_BYTES];
        final ByteBuffer k = key.duplicate();
        k.position(k.position() + TRACE_ID_BYTES);
        k.get(parentSpanId);
        return Arrays.equals(parentSpanId, NO_PARENT_SPAN_ID);
    }

    /**
     * Builds the stored {@link TraceRoot} from the per-trace incremental stats
     * ({@link #recordNewSpan}) plus the root span — <b>O(1)</b>, no per-span rescan (this is what
     * removes the merge-cycle cost that scaled with a growing trace). Returns empty if the trace
     * has no root span (a traceId whose only spans are orphans), matching the previous skip guard.
     *
     * <ul>
     *   <li>{@code totalSpans} / {@code services} — read from the cumulative counters;</li>
     *   <li>{@code endTime} / {@code lastActivityMs} — the ratcheted max span end / insert;</li>
     *   <li>{@code depth} — carried from the stats, recomputed by the bounded DFS only when never
     *       computed or {@code spanCount >= 2 × spanCountAtLastDepth} (depth is stable, so the DFS
     *       stays off the per-cycle hot path — amortised O(log N) runs over a trace's life).</li>
     * </ul>
     */
    private Optional<TraceRoot> buildRootFromStats(final Txn<ByteBuffer> txn,
                                                   final byte[] traceIdBytes) {
        final Optional<Span> optRoot = rootSpan(txn, traceIdBytes);
        if (optRoot.isEmpty()) {
            // No root span. If the trace still has a live (orphan) span — its root aged out under
            // retention/archival, or never arrived — synthesize a flagged orphan TraceRoot from
            // the per-trace stats so it stays listed (with a warning) and viewable as a rootless
            // trace. Gate on an ACTUAL live span, not the cumulative stats.spanCount() (which
            // counts aged-out spans), to avoid ghost rows once every span has gone.
            if (!hasAnySpan(txn, traceIdBytes)) {
                return Optional.empty();
            }
            final TraceStats orphanStats = readStats(txn, traceIdBytes);
            final NanoTime orphanEnd = orphanStats.maxEnd() != null
                    ? orphanStats.maxEnd()
                    : NanoTime.ZERO;
            return Optional.of(TraceRoot.builder()
                    .traceId(HexStringUtil.encode(traceIdBytes))
                    .name("")                       // no root operation name
                    .startTime(orphanEnd)           // no root start; use the max span end as the
                    .endTime(orphanEnd)             // single timestamp (duration 0)
                    .services(orphanStats.serviceCount())
                    .depth(0)
                    .totalSpans((int) orphanStats.spanCount())
                    .lastActivityMs(orphanStats.lastActivityMs())
                    .rootEndTime(null)
                    .orphan(true)
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
            if (stats.spanCount() > CHECKPOINT_MIN_SPANS) {
                // Large trace: one pre-order walk both computes depth (longest simple path) and
                // rebuilds the sparse DFS checkpoints for random-access paging — same gated cadence
                // as the depth recompute, so no extra hot-path cost.
                depth = rebuildCheckpointsAndDepth(txn, traceIdBytes);
            } else {
                // Small trace (loads whole; no checkpoints): bounded DFS for depth only. The
                // path-visited guard in descend() skips back-edges so a cyclic trace terminates.
                final int[] maxLevel = {0};
                descend(txn, traceIdBytes, HexStringUtil.decode(root.getSpanId()), 1, maxLevel,
                        new HashSet<>());
                depth = maxLevel[0];
            }
            stats = new TraceStats(stats.spanCount(), stats.serviceCount(), stats.maxEnd(),
                    stats.lastActivityMs(), depth, stats.spanCount());
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
                // The root span's own end time — fixed, unlike endTime (= max end across all
                // spans). A large gap between the two flags trailing leaked activity (a pooled/
                // background thread emitting spans under the trace long after the root finished).
                .rootEndTime(root.end())
                .build());
    }

    // Cheap prefix existence check: does the span DBI hold >=1 span for this traceId? Gates orphan-root
    // synthesis/cleanup on a genuinely-live span, not the cumulative stats count (which counts aged-out spans).
    private boolean hasAnySpan(final Txn<ByteBuffer> txn, final byte[] traceIdBytes) {
        final boolean[] found = {false};
        byteBuffers.useBytes(traceIdBytes, prefixBuffer -> {
            final LmdbKeyRange keyRange = LmdbKeyRange.builder().prefix(prefixBuffer).build();
            try (final Stream<LmdbEntry> stream = LmdbStream.stream(txn, dbi, keyRange)) {
                found[0] = stream.findFirst().isPresent();
            }
        });
        return found[0];
    }

    private TraceStats readStats(final Txn<ByteBuffer> txn, final byte[] traceIdBytes) {
        final TraceStats[] out = {TraceStats.EMPTY};
        byteBuffers.useBytes(traceIdBytes, keyBuf -> {
            final ByteBuffer existing = traceStatsDbi.get(txn, keyBuf);
            if (existing != null) {
                out[0] = traceStatsSerde.read(existing.duplicate());
            }
        });
        return out[0];
    }

    private void writeStats(final Txn<ByteBuffer> writeTxn,
                            final byte[] traceIdBytes,
                            final TraceStats stats) {
        byteBuffers.useBytes(traceIdBytes, keyBuf -> {
            traceStatsSerde.write(stats, valBuf -> traceStatsDbi.put(writeTxn, keyBuf, valBuf));
        });
    }

    /**
     * DFS step: records the deepest level reached (root = level 1) and descends into
     * children. {@code path} holds the spanIds on the current root→node path; a child
     * already on the path is a back-edge (a malformed cyclic trace) and is skipped, so the
     * walk always terminates. {@code path} is O(current depth).
     */
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
            // Get all the spans.
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

        /**
         * Returns {@code true} if at least one span with no parent (i.e. parentSpanId
         * is {@code null}, stored under the empty-string key) is present. A trace
         * may have child spans but no root if the root was lost (e.g. queue purge).
         */
        public boolean hasRoot() {
            return traceMap.containsKey("");
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
