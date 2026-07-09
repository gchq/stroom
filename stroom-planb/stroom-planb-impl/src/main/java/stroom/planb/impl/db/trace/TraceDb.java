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
        traceRootsDbi = env.openDbi("trace-roots", DbiFlags.MDB_CREATE);
        traceRootsMergeTimeDbi = env.openDbi("trace-roots-merge-time", DbiFlags.MDB_CREATE);

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
        keySerde.write(writeTxn, kv.key(), keyByteBuffer ->
                valueSerde.write(writeTxn, kv.val(), valueByteBuffer ->
                        dbi.put(writeTxn, keyByteBuffer, valueByteBuffer, putFlags)));

        final byte[] traceIdBytes = HexStringUtil.decode(kv.key().getTraceId());
        final boolean isRootSpan = NullSafe.isEmptyString(kv.key().getParentSpanId());

        if (isRootSpan) {
            // Root span: do the full getTrace() rebuild so that depth, services, name
            // and totalSpans are all computed correctly from the complete span set seen
            // so far.  Also writes the trace-roots-merge-time entry that drives the
            // PathwaysProcessor grace-period clock.
            try {
                final Trace trace = getTrace(writeTxn, kv.key().getTraceId());
                final TraceRootKey traceRootKey = new TraceRootKey(traceIdBytes);
                final TraceRoot newRoot = new TraceRoot(trace);

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

                    final TraceRoot newRoot = oldRoot.copy()
                            .startTime(newStart)
                            .endTime(newEnd)
                            .totalSpans(oldRoot.getTotalSpans() + 1)
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
                            if (sourceDb.keySerde.usesLookup(entry.getKey()) ||
                                sourceDb.valueSerde.usesLookup(entry.getVal())) {
                                // We need to do a full read and merge.
                                final SpanKey spanKey = sourceDb.keySerde.read(readTxn, entry.getKey());
                                final SpanValue spanValue = sourceDb.valueSerde.read(readTxn, entry.getVal());
                                insert(writer, new SpanKV(spanKey, spanValue));
                            } else {
                                // Quick merge.
                                if (dbi.put(writer.getWriteTxn(), entry.getKey(), entry.getVal(), putFlags)) {
                                    writer.tryCommit();
                                }
                            }
                        });
                    }

                    // Merge trace roots.  For each entry successfully written (new traceId),
                    // also populate the six sort indexes so the target shard remains
                    // fully queryable without a subsequent full scan.
                    LmdbIterable.iterate(readTxn, sourceDb.traceRootsDbi, (key, val) -> {
                        if (traceRootsDbi.put(writer.getWriteTxn(), key, val, putFlags)) {
                            final byte[] traceIdBytes = new byte[key.remaining()];
                            key.duplicate().get(traceIdBytes);
                            final TraceRoot root = traceRootValueSerde.read(val.duplicate());
                            writeSecondaryIndexes(writer.getWriteTxn(), traceIdBytes, root);
                            writer.tryCommit();
                        }
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

    /**
     * Archives whole traces whose <em>root-span start time</em> is older than
     * {@code archiveBefore} into dated LMDB environments under
     * {@code archiveBaseDir}, then deletes them from the live environment.
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
     * <p><b>Orphan spans.</b> Spans whose traceId has no root anywhere in this
     * shard (e.g. the root was lost) can never form a queryable trace and are
     * swept separately: those older than {@code archiveBefore} by insert time are
     * archived into a bucket labelled by their insert time and then deleted. This
     * keeps the live shard bounded even when no retention policy is configured.
     * Orphan spans produce no trace-root index entry and are reachable only by a
     * direct {@code getTrace(traceId)} lookup.
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

        // traceIdHex -> bucket label, for roots being archived.
        final Map<String, String> archivedRootLabels = new HashMap<>();
        // traceIdHex -> decoded root, used to rebuild the archive's sort indexes.
        final Map<String, TraceRoot> archivedRoots = new HashMap<>();
        // traceIdHex -> {rawTraceIdKey, rawRootVal}, for verbatim copy into the archive.
        final Map<String, byte[][]> archivedRootRawKV = new HashMap<>();
        // Every traceId that has a root, so orphan spans can be distinguished.
        final Set<String> allRootTraceIds = new HashSet<>();
        // bucket label -> list of {rawSpanKey, rawSpanVal}.
        final Map<String, List<byte[][]>> bucketSpans = new LinkedHashMap<>();

        // Pass 1: scan roots (to decide which traces to archive and how to label
        // them) then spans (grouped into buckets by their trace's label).
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
                allRootTraceIds.add(hex);

                final TraceRoot root = traceRootValueSerde.read(val.duplicate());
                final NanoTime startTime = root.getStartTime();
                if (startTime != null && startTime.isBefore(nanoTimeBefore)) {
                    final String label = ArchivalGranularityUtil.label(
                            granularity, NanoTimeUtil.toInstant(startTime));
                    archivedRootLabels.put(hex, label);
                    archivedRoots.put(hex, root);
                    final byte[] rawVal = new byte[val.remaining()];
                    val.duplicate().get(rawVal);
                    archivedRootRawKV.put(hex, new byte[][]{traceIdBytes, rawVal});
                }
            });

            // 1b: spans. Bucket a span with its archived root; if it is an orphan
            // (no root anywhere) archive it by insert time; otherwise (a live,
            // not-yet-archived rooted trace) leave it in place.
            LmdbIterable.iterate(readTxn, dbi, (key, val) -> {
                if (key.remaining() < TRACE_ID_BYTES) {
                    return;
                }
                final byte[] traceIdBytes = new byte[TRACE_ID_BYTES];
                key.duplicate().get(traceIdBytes);
                final String hex = HexStringUtil.encode(traceIdBytes);

                final String label;
                final String rootLabel = archivedRootLabels.get(hex);
                if (rootLabel != null) {
                    // Span of a trace being archived — goes in the root's bucket,
                    // whatever this span's own timestamp is.
                    label = rootLabel;
                } else if (!allRootTraceIds.contains(hex)) {
                    // Orphan span. Bucket by insert time (no queryable start axis).
                    final NanoTime insertTime = spanValueSerde.readInsertTime(val.duplicate());
                    if (!insertTime.isBefore(nanoTimeBefore)) {
                        return;
                    }
                    label = ArchivalGranularityUtil.label(
                            granularity, NanoTimeUtil.toInstant(insertTime));
                } else {
                    // Span of a live rooted trace that is not being archived.
                    return;
                }

                final byte[] rawKey = new byte[key.remaining()];
                key.duplicate().get(rawKey);
                final byte[] rawVal = new byte[val.remaining()];
                val.duplicate().get(rawVal);
                bucketSpans.computeIfAbsent(label, l -> new ArrayList<>())
                        .add(new byte[][]{rawKey, rawVal});
            });
            return null;
        });

        if (bucketSpans.isEmpty() && archivedRoots.isEmpty()) {
            return 0L;
        }

        // Invert archivedRootLabels into bucket label -> root traceId hexes.
        final Map<String, List<String>> bucketRootHexes = new LinkedHashMap<>();
        for (final Map.Entry<String, String> e : archivedRootLabels.entrySet()) {
            bucketRootHexes.computeIfAbsent(e.getValue(), l -> new ArrayList<>()).add(e.getKey());
        }

        // Pass 2: write each bucket to its own archive environment.
        //
        // Spans and roots are copied verbatim (the UID integers embedded in the
        // bytes reference the lookup tables cloned by copyLookupsTo). The archive's
        // six secondary sort indexes are rebuilt for this bucket's roots only, so
        // the archive is fully queryable without holding any other bucket's roots.
        final Set<String> allLabels = new LinkedHashSet<>();
        allLabels.addAll(bucketSpans.keySet());
        allLabels.addAll(bucketRootHexes.keySet());
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
                    final Txn<ByteBuffer> writeTxn = archiveWriter.getWriteTxn();

                    final List<byte[][]> spans = bucketSpans.get(label);
                    if (spans != null) {
                        for (final byte[][] rawKV : spans) {
                            putDirect(archiveDb.dbi, writeTxn, rawKV[0], rawKV[1]);
                        }
                    }

                    final List<String> rootHexes = bucketRootHexes.get(label);
                    if (rootHexes != null) {
                        for (final String hex : rootHexes) {
                            final byte[][] rawKV = archivedRootRawKV.get(hex);
                            putDirect(archiveDb.traceRootsDbi, writeTxn, rawKV[0], rawKV[1]);
                            archiveDb.writeSecondaryIndexes(writeTxn, rawKV[0], archivedRoots.get(hex));
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
                // Spans.
                LmdbIterable.iterate(readTxn, dbi, (key, val) -> {
                    boolean archived = false;
                    if (key.remaining() >= TRACE_ID_BYTES) {
                        final byte[] traceIdBytes = new byte[TRACE_ID_BYTES];
                        key.duplicate().get(traceIdBytes);
                        final String hex = HexStringUtil.encode(traceIdBytes);
                        if (archivedRootLabels.containsKey(hex)) {
                            archived = true;
                        } else if (!allRootTraceIds.contains(hex)) {
                            final NanoTime insertTime = spanValueSerde.readInsertTime(val.duplicate());
                            archived = insertTime.isBefore(nanoTimeBefore);
                        }
                    }

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

            // Delete old trace roots and their secondary sort index entries.
            LmdbIterable.iterate(readTxn, traceRootsDbi, (key, val) -> {
                final TraceRoot value = traceRootValueSerde.read(val.duplicate());
                if (value.getStartTime().isBefore(deleteBefore)) {
                    final byte[] traceIdBytes = new byte[key.remaining()];
                    key.duplicate().get(traceIdBytes);
                    // Remove the trace root and all associated sort indexes atomically.
                    traceRootsDbi.delete(writer.getWriteTxn(), key);
                    deleteSecondaryIndexes(writer.getWriteTxn(), traceIdBytes, value);
                    changeCount.increment();
                }
                writer.tryCommit();
            });

            // Delete stale trace-roots-merge-time entries.
            // Key layout: (mergeTimeMs_bigEndian || traceId) — read mergeTimeMs
            // from the first 8 bytes of the key.
            final long deleteBeforeMs = NanoTimeUtil.toInstant(deleteBefore).toEpochMilli();
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

        // Use a single-element array to capture the total count from inside the lambda,
        // since the lambda requires effectively-final variables.
        final long[] totalRef = {0L};

        env.read(readTxn -> {
            // O(1): LMDB stat gives exact entry count without scanning all entries.
            totalRef[0] = traceRootsDbi.stat(readTxn).entries;

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

        // Determine the reported total and exactness.
        //
        // No time range: use the O(1) LMDB stat count — always exact.
        //
        // Time range active: the LMDB stat count is the *unfiltered* total and would mislead
        // the data grid into thinking old rows are still valid.  Instead:
        //   • If the page is not full (list.size() < length) the stream exhausted all matching
        //     entries, so we know the exact total = offset + list.size().
        //   • If the page is full there may be more entries beyond this page, so we report
        //     exact=false which causes the pagination control to show "?" for the total.
        final long reportedTotal;
        final boolean exact;
        if (criteria.getTimeRange() == null) {
            reportedTotal = totalRef[0];
            exact = true;
        } else if (list.size() < length) {
            // Last (or only) page — we have seen all matching entries.
            reportedTotal = (long) offset + list.size();
            exact = true;
        } else {
            // Full page — there may be more.
            reportedTotal = (long) offset + list.size();
            exact = false;
        }
        return new TracesResultPage(list,
                PageResponse.builder()
                        .offset(offset)
                        .length(list.size())
                        .total(reportedTotal)
                        .exact(exact)
                        .build());
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
            if (!traceBuilder.hasSpans() || !traceBuilder.hasRoot()) {
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
