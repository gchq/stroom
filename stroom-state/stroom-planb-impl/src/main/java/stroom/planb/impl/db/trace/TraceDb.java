package stroom.planb.impl.db.trace;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.entity.shared.ExpressionCriteria;
import stroom.lmdb2.KV;
import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.planb.impl.data.SpanKV;
import stroom.planb.impl.db.AbstractDb;
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
import stroom.planb.impl.serde.trace.LookupSerdeImpl;
import stroom.planb.impl.serde.trace.SpanKey;
import stroom.planb.impl.serde.trace.SpanKeySerde;
import stroom.planb.impl.serde.trace.SpanValue;
import stroom.planb.impl.serde.trace.SpanValueSerde;
import stroom.planb.shared.AbstractPlanBSettings;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.StateSettings;
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

import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.function.Function;

public class TraceDb extends AbstractDb<SpanKey, SpanValue> {

    private static final int CURRENT_SCHEMA_VERSION = 1;

    private final ByteBufferFactory byteBufferFactory;
    private final KeySerde<SpanKey> keySerde;
    private final Serde<SpanValue> valueSerde;
    private final UsedLookupsRecorder keyRecorder;
    private final UsedLookupsRecorder valueRecorder;

    private TraceDb(final PlanBEnv env,
                    final ByteBuffers byteBuffers,
                    final ByteBufferFactory byteBufferFactory,
                    final PlanBDoc doc,
                    final StateSettings settings,
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
                        JsonUtil.writeValueAsString(settings.getKeySchema()),
                        JsonUtil.writeValueAsString(settings.getValueSchema())));
        this.byteBufferFactory = byteBufferFactory;
        this.keySerde = keySerde;
        this.valueSerde = valueSerde;
        this.keyRecorder = keySerde.getUsedLookupsRecorder(env);
        this.valueRecorder = valueSerde.getUsedLookupsRecorder(env);
    }

    public static TraceDb create(final Path path,
                                 final ByteBuffers byteBuffers,
                                 final ByteBufferFactory byteBufferFactory,
                                 final PlanBDoc doc,
                                 final boolean readOnly) {
        final StateSettings settings;
        if (doc.getSettings() instanceof final StateSettings stateSettings) {
            settings = stateSettings;
        } else {
            settings = new StateSettings.Builder().build();
        }

        final HashClashCommitRunnable hashClashCommitRunnable = new HashClashCommitRunnable();
        final Long mapSize = NullSafe.getOrElse(
                settings,
                AbstractPlanBSettings::getMaxStoreSize,
                AbstractPlanBSettings.DEFAULT_MAX_STORE_SIZE);
        final PlanBEnv env = new PlanBEnv(path,
                mapSize,
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

    @Override
    public void insert(final LmdbWriter writer, final KV<SpanKey, SpanValue> kv) {
        final Txn<ByteBuffer> writeTxn = writer.getWriteTxn();
        keySerde.write(writeTxn, kv.key(), keyByteBuffer ->
                valueSerde.write(writeTxn, kv.val(), valueByteBuffer ->
                        dbi.put(writeTxn, keyByteBuffer, valueByteBuffer, putFlags)));
        writer.tryCommit();
    }

    private void iterate(final Txn<ByteBuffer> txn,
                         final Consumer<KeyVal<ByteBuffer>> consumer) {
        try (final CursorIterable<ByteBuffer> cursorIterable = dbi.iterate(txn)) {
            for (final KeyVal<ByteBuffer> keyVal : cursorIterable) {
                consumer.accept(keyVal);
            }
        }
    }

    @Override
    public void merge(final Path source) {
        env.write(writer -> {
            try (final TraceDb sourceDb = TraceDb.create(source, byteBuffers, byteBufferFactory, doc, true)) {
                // Validate that the source DB has the same schema.
                validateSchema(schemaInfo, sourceDb.getSchemaInfo());

                // Merge.
                sourceDb.env.read(readTxn -> {
                    sourceDb.iterate(readTxn, kv -> {
                        if (sourceDb.keySerde.usesLookup(kv.key()) ||
                            sourceDb.valueSerde.usesLookup(kv.val())) {
                            // We need to do a full read and merge.
                            final SpanKey key = sourceDb.keySerde.read(readTxn, kv.key());
                            final SpanValue value = sourceDb.valueSerde.read(readTxn, kv.val());
                            insert(writer, new SpanKV(key, value));
                        } else {
                            // Quick merge.
                            if (dbi.put(writer.getWriteTxn(), kv.key(), kv.val(), putFlags)) {
                                writer.tryCommit();
                            }
                        }
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
        return context -> keySerde.read(readTxn, context.kv().key().duplicate());
    }

    private Function<Context, SpanValue> getValExtractionFunction(final Txn<ByteBuffer> readTxn) {
        return context -> valueSerde.read(readTxn, context.kv().val().duplicate());
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
        return (readTxn, kv) -> {
            final Context context = new Context(readTxn, kv);
            final LazyKV<SpanKey, SpanValue> lazyKV = new LazyKV<>(context, keyFunction, valFunction);
            final Val[] values = new Val[fields.length];
            for (int i = 0; i < fields.length; i++) {
                values[i] = converters[i].convert(lazyKV);
            }
            return Values.of(values);
        };
    }

    @Override
    public long deleteOldData(final Instant deleteBefore, final boolean useStateTime) {
        return env.write(writer -> {
            final NanoTime nanoTime = new NanoTime(deleteBefore.getEpochSecond(), deleteBefore.getNano());
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
            long changeCount = 0;
            try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(readTxn)) {
                final Iterator<KeyVal<ByteBuffer>> iterator = cursor.iterator();
                while (iterator.hasNext()
                       && !Thread.currentThread().isInterrupted()) {
                    final KeyVal<ByteBuffer> kv = iterator.next();
                    final SpanValue value = valueSerde.read(readTxn, kv.val().duplicate());

                    if (value.getInsertTime().isBefore(deleteBefore)) {
                        // If this is data we no longer want to retain then delete it.
                        dbi.delete(writer.getWriteTxn(), kv.key());
                        changeCount++;
                    } else {
                        // Record used lookup keys.
                        keyRecorder.recordUsed(writer, kv.key());
                        valueRecorder.recordUsed(writer, kv.val());
                    }
                    writer.tryCommit();
                }
            }
            writer.commit();
            return changeCount;
        });
    }

    @Override
    public long condense(final Instant condenseBefore) {
        return 0;
    }

    public interface TraceConverter extends Converter<SpanKey, SpanValue> {

    }
}
