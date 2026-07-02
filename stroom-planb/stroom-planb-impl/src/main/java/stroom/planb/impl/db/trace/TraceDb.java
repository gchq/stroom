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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TraceDb extends AbstractDb<SpanKey, SpanValue> {

    private static final int CURRENT_SCHEMA_VERSION = 1;
    /**
     * Names of all DBIs that must be copied verbatim into every archive partition
     * so that archives are independently queryable. Update this list whenever a
     * new DBI is added to {@link TraceDb}.
     */
    private static final List<String> ARCHIVE_DBI_NAMES = List.of(
            "lookup-keyToUid", "lookup-uidToKey", "lookup-info",
            "lookup-hash",
            "trace-roots",
            "trace-roots-merge-time");

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
                20,
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

        // Add trace root if this is one.
        final byte[] traceIdBytes = HexStringUtil.decode(kv.key().getTraceId());
        if (NullSafe.isEmptyString(kv.key().getParentSpanId())) {
            try {
                // TODO : We are currently assuming that we get the root last but we might want to reevaluate depth etc
                //  later.
                final Trace trace = getTrace(writeTxn, kv.key().getTraceId());
                final TraceRootKey key = new TraceRootKey(traceIdBytes);
                final TraceRoot value = new TraceRoot(trace);

                traceRootKeySerde.write(key, keyBuffer ->
                        traceRootValueSerde.write(value, valueBuffer ->
                                traceRootsDbi.put(writeTxn, keyBuffer, valueBuffer)));

                // Use the wall-clock time at which this root span was merged into
                // the store. The grace period ticks from this moment, giving time
                // for late-arriving child spans to be received before the trace is
                // handed to PathwaysProcessor.
                final long mergeTimeMs = System.currentTimeMillis();
                final byte[] mergeKeyBytes = new byte[Long.BYTES + traceIdBytes.length];
                ByteBuffer.wrap(mergeKeyBytes).putLong(mergeTimeMs).put(traceIdBytes);
                byteBuffers.useBytes(mergeKeyBytes, mergeTimeKey -> {
                    traceRootsMergeTimeDbi.put(writeTxn, mergeTimeKey, emptyValue(),
                            PutFlags.MDB_NOOVERWRITE);
                });
            } catch (final RuntimeException e) {
                // Log at WARN — a silent failure here leaves the trace permanently absent
                // from trace-roots-merge-time, so PathwaysProcessor will never process it.
                LOGGER.warn("Failed to write trace root index for trace {}: {}",
                        kv.key().getTraceId(), e.getMessage(), e);
            }
        }

        writer.tryCommit();
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

                    // Merge trace roots.
                    LmdbIterable.iterate(readTxn, sourceDb.traceRootsDbi, (key, val) -> {
                        if (traceRootsDbi.put(writer.getWriteTxn(), key, val, putFlags)) {
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
     * Archives trace entries whose insert time is older than archiveBefore
     * into dated LMDB environments under archiveBaseDir, then deletes them from
     * the main environment.
     *
     * Three passes:
     * 1. Read all span+root entries to archive, grouped by date label.
     * 2. Write each group to a new archive LMDB environment.
     * 3. Delete the archived entries from the main environment.
     */
    @Override
    public long archiveOldData(final Instant archiveBefore,
                               final ArchivalGranularity granularity,
                               final Path archiveBaseDir) {
        final NanoTime nanoTimeBefore = NanoTimeUtil.fromInstant(archiveBefore);

        // Pass 1: collect raw key/value bytes to archive, grouped by date label.
        //
        // We read insertTime from the first 8 bytes of the value (NanoTimeSerde
        // writes a single long) via readInsertTime(), which does NOT touch the
        // UID lookup table.  This avoids "Unable to find value for UID" errors
        // that would be thrown by the full valueSerde.read() path.
        final Map<String, List<byte[][]>> toArchive = new LinkedHashMap<>();
        env.read(readTxn -> {
            LmdbIterable.iterate(readTxn, dbi, (key, val) -> {
                final NanoTime insertTime = spanValueSerde.readInsertTime(val.duplicate());
                if (insertTime.isBefore(nanoTimeBefore)) {
                    final Instant insertInstant = NanoTimeUtil.toInstant(insertTime);
                    final String label = ArchivalGranularityUtil.label(granularity, insertInstant);
                    // Copy raw bytes — LMDB buffers are only valid for the lifetime
                    // of the enclosing read transaction.
                    final byte[] rawKey = new byte[key.remaining()];
                    key.duplicate().get(rawKey);
                    final byte[] rawVal = new byte[val.remaining()];
                    val.duplicate().get(rawVal);
                    toArchive.computeIfAbsent(label, l -> new ArrayList<>())
                            .add(new byte[][]{rawKey, rawVal});
                }
            });
            return null;
        });

        if (toArchive.isEmpty()) {
            return 0L;
        }

        // Pass 2: write raw bytes to archive LMDB environments.
        //
        // We bypass insert() / valueSerde.write() because we are doing a
        // verbatim byte copy — the UID integers embedded in the value bytes
        // already reference the source shard's lookup table.  After writing
        // the span data we call copyLookupsTo() to replicate those lookup
        // tables into the archive so it remains independently queryable.
        for (final Map.Entry<String, List<byte[][]>> entry : toArchive.entrySet()) {
            final Path archiveDir = archiveBaseDir.resolve(entry.getKey());
            try {
                Files.createDirectories(archiveDir);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
            try (final TraceDb archiveDb =
                         TraceDb.create(archiveDir, byteBuffers, byteBufferFactory, doc, false)) {
                archiveDb.env.write(archiveWriter -> {
                    for (final byte[][] rawKV : entry.getValue()) {
                        // lmdbjava requires direct (off-heap) ByteBuffers.
                        final ByteBuffer directKey =
                                ByteBuffer.allocateDirect(rawKV[0].length);
                        directKey.put(rawKV[0]).flip();
                        final ByteBuffer directVal =
                                ByteBuffer.allocateDirect(rawKV[1].length);
                        directVal.put(rawKV[1]).flip();
                        archiveDb.dbi.put(archiveWriter.getWriteTxn(), directKey, directVal);
                    }
                    return null;
                });
                // Copy UID / hash lookup tables and trace-root index so the
                // archive is independently queryable without the source shard.
                copyLookupsTo(archiveDb);
            }
        }

        // Pass 3: delete archived entries from the main environment.
        return env.write(writer -> {
            final long count = deleteOldData(writer, nanoTimeBefore);
            if (count > 0 && !Thread.currentThread().isInterrupted()) {
                env.read(readTxn -> {
                    keyRecorder.deleteUnused(readTxn, writer);
                    valueRecorder.deleteUnused(readTxn, writer);
                    return null;
                });
            }
            return count;
        });
    }

    /**
     * Copies all lookup named-DBs (UID forward/reverse maps, hash map) and
     * the trace-root index from this shard's LMDB environment to the archive
     * shard's environment.  The archive thereby becomes self-contained and
     * queryable without access to the source shard.
     */
    private void copyLookupsTo(final TraceDb archive) {
        for (final String name : ARCHIVE_DBI_NAMES) {
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

            // Delete old trace roots.
            LmdbIterable.iterate(readTxn, traceRootsDbi, (key, val) -> {
                final TraceRoot value = traceRootValueSerde.read(val.duplicate());
                if (value.getStartTime().isBefore(deleteBefore)) {
                    // If this is data we no longer want to retain then delete it.
                    traceRootsDbi.delete(writer.getWriteTxn(), key);
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

            // Just find traces in the requested range.
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
                            tracePredicate.test(trace)) {
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
            // Just find traces in the requested range.
            env.read(readTxn -> {
                final Count count = new Count();

                LmdbIterable.iterate(readTxn, traceRootsDbi, (key, val) -> {
                    final long pos = count.getAndIncrement();
                    if (criteria.getPageRequest().getOffset() <= pos &&
                        criteria.getPageRequest().getLength() > list.size()) {
                        final TraceRoot root = traceRootValueSerde.read(val);
                        list.add(root);
                    }
                });

                builder.offset(criteria.getPageRequest().getOffset());
                builder.length(list.size());
                builder.total(count.get());
                builder.exact(true);
                return list;
            });
        }

        return new TracesResultPage(list, builder.build());
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
