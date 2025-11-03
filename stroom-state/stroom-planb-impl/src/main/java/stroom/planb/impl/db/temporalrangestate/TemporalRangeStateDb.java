package stroom.planb.impl.db.temporalrangestate;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.entity.shared.ExpressionCriteria;
import stroom.lmdb2.KV;
import stroom.planb.impl.data.TemporalRangeState;
import stroom.planb.impl.data.TemporalRangeState.Key;
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
import stroom.planb.impl.serde.temporalrangestate.ByteRangeKeySerde;
import stroom.planb.impl.serde.temporalrangestate.IntegerRangeKeySerde;
import stroom.planb.impl.serde.temporalrangestate.LongRangeKeySerde;
import stroom.planb.impl.serde.temporalrangestate.ShortRangeKeySerde;
import stroom.planb.impl.serde.temporalrangestate.TemporalRangeKeySerde;
import stroom.planb.impl.serde.time.DayTimeSerde;
import stroom.planb.impl.serde.time.HourTimeSerde;
import stroom.planb.impl.serde.time.MillisecondTimeSerde;
import stroom.planb.impl.serde.time.MinuteTimeSerde;
import stroom.planb.impl.serde.time.NanoTimeSerde;
import stroom.planb.impl.serde.time.SecondTimeSerde;
import stroom.planb.impl.serde.time.TimeSerde;
import stroom.planb.impl.serde.valtime.ValTime;
import stroom.planb.impl.serde.valtime.ValTimeSerde;
import stroom.planb.impl.serde.valtime.ValTimeSerdeFactory;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.RangeType;
import stroom.planb.shared.TemporalPrecision;
import stroom.planb.shared.TemporalRangeStateSettings;
import stroom.query.api.DateTimeSettings;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValLong;
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
import org.lmdbjava.KeyRange;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public class TemporalRangeStateDb extends AbstractDb<Key, Val> {

    private static final int CURRENT_SCHEMA_VERSION = 1;

    private final TemporalRangeKeySerde keySerde;
    private final ValTimeSerde valueSerde;
    private final UsedLookupsRecorder keyRecorder;
    private final UsedLookupsRecorder valueRecorder;

    private TemporalRangeStateDb(final PlanBEnv env,
                                 final ByteBuffers byteBuffers,
                                 final PlanBDoc doc,
                                 final TemporalRangeStateSettings settings,
                                 final TemporalRangeKeySerde keySerde,
                                 final ValTimeSerde valueSerde,
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
        this.keySerde = keySerde;
        this.valueSerde = valueSerde;
        this.keyRecorder = keySerde.getUsedLookupsRecorder(env);
        this.valueRecorder = valueSerde.getUsedLookupsRecorder(env);
    }

    public static TemporalRangeStateDb create(final Path path,
                                              final ByteBuffers byteBuffers,
                                              final PlanBDoc doc,
                                              final boolean readOnly) {
        // Ensure all settings are non null.
        final TemporalRangeStateSettings settings;
        if (doc.getSettings() instanceof final TemporalRangeStateSettings temporalRangeStateSettings) {
            settings = temporalRangeStateSettings;
        } else {
            settings = new TemporalRangeStateSettings.Builder().build();
        }

        final HashClashCommitRunnable hashClashCommitRunnable = new HashClashCommitRunnable();
        final PlanBEnv env = new PlanBEnv(path,
                settings.getMaxStoreSize(),
                20,
                readOnly,
                hashClashCommitRunnable);
        try {
            final TimeSerde timeSerde = createTimeSerde(settings.getKeySchema().getTemporalPrecision());
            final TemporalRangeKeySerde keySerde = createKeySerde(
                    settings.getKeySchema().getRangeType(),
                    byteBuffers,
                    timeSerde);
            final ValTimeSerde valueSerde = ValTimeSerdeFactory.createValueSerde(
                    settings.getValueSchema().getStateValueType(),
                    settings.getValueSchema().getHashLength(),
                    env,
                    byteBuffers,
                    hashClashCommitRunnable);
            return new TemporalRangeStateDb(
                    env,
                    byteBuffers,
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

    private static TimeSerde createTimeSerde(final TemporalPrecision temporalPrecision) {
        return switch (temporalPrecision) {
            case NANOSECOND -> new NanoTimeSerde();
            case MILLISECOND -> new MillisecondTimeSerde();
            case SECOND -> new SecondTimeSerde();
            case MINUTE -> new MinuteTimeSerde();
            case HOUR -> new HourTimeSerde();
            case DAY -> new DayTimeSerde();
        };
    }

    private static TemporalRangeKeySerde createKeySerde(final RangeType rangeType,
                                                        final ByteBuffers byteBuffers,
                                                        final TimeSerde timeSerde) {
        return switch (rangeType) {
            case BYTE -> new ByteRangeKeySerde(byteBuffers, timeSerde);
            case SHORT -> new ShortRangeKeySerde(byteBuffers, timeSerde);
            case INT -> new IntegerRangeKeySerde(byteBuffers, timeSerde);
            case LONG -> new LongRangeKeySerde(byteBuffers, timeSerde);
        };
    }

    @Override
    public void insert(final LmdbWriter writer, final KV<Key, Val> kv) {
        final Txn<ByteBuffer> writeTxn = writer.getWriteTxn();
        keySerde.write(writeTxn, kv.key(), keyByteBuffer ->
                valueSerde.write(writeTxn, new ValTime(kv.val(), Instant.now()), valueByteBuffer ->
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
            try (final TemporalRangeStateDb sourceDb = TemporalRangeStateDb.create(source,
                    byteBuffers,
                    doc,
                    true)) {
                // Validate that the source DB has the same schema.
                validateSchema(schemaInfo, sourceDb.getSchemaInfo());

                // Merge.
                sourceDb.env.read(readTxn -> {
                    sourceDb.iterate(readTxn, kv -> {
                        if (sourceDb.keySerde.usesLookup(kv.key()) || sourceDb.valueSerde.usesLookup(kv.val())) {
                            // We need to do a full read and merge.
                            final Key key = sourceDb.keySerde.read(readTxn, kv.key());
                            final Val value = sourceDb.valueSerde.read(readTxn, kv.val()).val();
                            insert(writer, new TemporalRangeState(key, value));
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
    public Val get(final Key key) {
        return env.read(readTxn -> keySerde.toBufferForGet(readTxn, key, optionalKeyByteBuffer ->
                optionalKeyByteBuffer.map(keyByteBuffer -> {
                    final ByteBuffer valueByteBuffer = dbi.get(readTxn, keyByteBuffer);
                    if (valueByteBuffer == null) {
                        return null;
                    }
                    return NullSafe.get(valueSerde.read(readTxn, valueByteBuffer), ValTime::val);
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

    private Function<Context, Key> getKeyExtractionFunction(final Txn<ByteBuffer> readTxn) {
        return context -> keySerde.read(readTxn, context.kv().key().duplicate());
    }

    private Function<Context, Val> getValExtractionFunction(final Txn<ByteBuffer> readTxn) {
        return context -> NullSafe.get(valueSerde.read(readTxn, context.kv().val().duplicate()), ValTime::val);
    }

    public TemporalRangeState getState(final TemporalRangeStateRequest request) {
        return env.read(readTxn ->
                keySerde.toKeyStart(request.key(), start -> {
                    final KeyRange<ByteBuffer> keyRange = KeyRange.atLeastBackward(start);
                    TemporalRangeState result = null;
                    try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(readTxn, keyRange)) {
                        final Iterator<KeyVal<ByteBuffer>> iterator = cursor.iterator();
                        while (iterator.hasNext()
                               && !Thread.currentThread().isInterrupted()) {
                            final KeyVal<ByteBuffer> kv = iterator.next();
                            final Key key = keySerde.read(readTxn, kv.key());
                            if (key.getKeyEnd() < request.key()) {
                                return result;
                            } else if ((key.getTime().isBefore(request.effectiveTime()) ||
                                        key.getTime().equals(request.effectiveTime())) &&
                                       key.getKeyStart() <= request.key()) {
                                final Val value = valueSerde.read(readTxn, kv.val()).val();
                                result = new TemporalRangeState(key, value);
                            }
                        }
                    }
                    return result;
                }));
    }

    @Override
    public long deleteOldData(final Instant deleteBefore, final boolean useStateTime) {
        return env.write(writer -> {
            final long count = deleteOldData(writer, deleteBefore, useStateTime);

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
                               final Instant deleteBefore,
                               final boolean useStateTime) {
        return env.read(readTxn -> {
            long changeCount = 0;
            try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(readTxn)) {
                final Iterator<KeyVal<ByteBuffer>> iterator = cursor.iterator();
                while (iterator.hasNext()
                       && !Thread.currentThread().isInterrupted()) {
                    final KeyVal<ByteBuffer> kv = iterator.next();
                    final Key key = keySerde.read(readTxn, kv.key().duplicate());
                    final Instant time;
                    if (useStateTime) {
                        time = key.getTime();
                    } else {
                        final ValTime valTime = valueSerde.read(readTxn, kv.val().duplicate());
                        time = valTime.insertTime();
                    }

                    if (time.isBefore(deleteBefore)) {
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
        return env.readAndWrite((readTxn, writer) -> {
            long changeCount = 0;
            TemporalRangeState lastState = null;
            TemporalRangeState newState = null;
            try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(readTxn)) {
                final Iterator<KeyVal<ByteBuffer>> iterator = cursor.iterator();
                while (iterator.hasNext()
                       && !Thread.currentThread().isInterrupted()) {
                    final KeyVal<ByteBuffer> kv = iterator.next();
                    final Key key = keySerde.read(readTxn, kv.key().duplicate());
                    final ValTime valTime = valueSerde.read(readTxn, kv.val().duplicate());
                    TemporalRangeState state = new TemporalRangeState(key, valTime.val());
                    final Instant time = key.getTime();

                    if (lastState != null &&
                        Objects.equals(lastState.key().getKeyStart(), key.getKeyStart()) &&
                        Objects.equals(lastState.key().getKeyEnd(), key.getKeyEnd()) &&
                        Objects.equals(lastState.val(), state.val()) &&
                        time.isBefore(condenseBefore)) {

                        // Remember the last state to insert it again later.
                        if (newState == null) {
                            newState = lastState;
                        }

                        // Delete the last state.
                        deleteState(writer, lastState);
                        changeCount++;

                        // We might be forced to insert if we have reached the commit limit.
                        if (writer.shouldCommit()) {
                            deleteState(writer, state);
                            changeCount++;

                            // Insert new state.
                            insert(writer, newState);
                            newState = null;
                            state = null;
                        }

                    } else if (newState != null) {
                        // Delete the last state.
                        deleteState(writer, lastState);
                        changeCount++;

                        // Insert new state.
                        insert(writer, newState);
                        newState = null;
                    }

                    lastState = state;
                }
            }

            // Insert new state.
            if (newState != null) {
                // Delete the previous state as we are extending it.
                deleteState(writer, lastState);
                changeCount++;

                // Insert the new session.
                insert(writer, newState);
            }

            return changeCount;
        });
    }

    private void deleteState(final LmdbWriter writer, final TemporalRangeState state) {
        keySerde.write(writer.getWriteTxn(), state.key(), keyByteBuffer -> {
            dbi.delete(writer.getWriteTxn(), keyByteBuffer);
            writer.incrementChangeCount();
        });
    }

    public static ValuesExtractor createValuesExtractor(final FieldIndex fieldIndex,
                                                        final Function<Context, Key> keyFunction,
                                                        final Function<Context, Val> valFunction) {
        final String[] fields = fieldIndex.getFields();
        final TemporalRangeStateConverter[] converters =
                new TemporalRangeStateConverter[fields.length];
        for (int i = 0; i < fields.length; i++) {
            converters[i] = switch (fields[i]) {
                case TemporalRangeStateFields.KEY_START -> kv -> ValLong.create(kv.getKey().getKeyStart());
                case TemporalRangeStateFields.KEY_END -> kv -> ValLong.create(kv.getKey().getKeyEnd());
                case TemporalRangeStateFields.EFFECTIVE_TIME -> kv -> ValDate.create(kv.getKey().getTime());
                case TemporalRangeStateFields.VALUE_TYPE -> kv -> ValString.create(kv.getValue().type().toString());
                case TemporalRangeStateFields.VALUE -> LazyKV::getValue;
                default -> kv -> ValNull.INSTANCE;
            };
        }
        return (readTxn, kv) -> {
            final Context context = new Context(readTxn, kv);
            final LazyKV<Key, Val> lazyKV = new LazyKV<>(context, keyFunction, valFunction);
            final Val[] values = new Val[fields.length];
            for (int i = 0; i < fields.length; i++) {
                values[i] = converters[i].convert(lazyKV);
            }
            return Values.of(values);
        };
    }

    public interface TemporalRangeStateConverter extends Converter<Key, Val> {

    }
}
