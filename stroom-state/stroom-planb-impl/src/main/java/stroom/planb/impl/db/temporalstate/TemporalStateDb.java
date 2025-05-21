package stroom.planb.impl.db.temporalstate;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.entity.shared.ExpressionCriteria;
import stroom.lmdb2.KV;
import stroom.planb.impl.db.AbstractDb;
import stroom.planb.impl.db.HashClashCommitRunnable;
import stroom.planb.impl.db.HashLookupDb;
import stroom.planb.impl.db.LmdbWriter;
import stroom.planb.impl.db.PlanBEnv;
import stroom.planb.impl.db.PlanBSearchHelper;
import stroom.planb.impl.db.PlanBSearchHelper.Context;
import stroom.planb.impl.db.PlanBSearchHelper.Converter;
import stroom.planb.impl.db.PlanBSearchHelper.LazyKV;
import stroom.planb.impl.db.PlanBSearchHelper.ValuesExtractor;
import stroom.planb.impl.db.UidLookupDb;
import stroom.planb.impl.db.UsedLookupsRecorder;
import stroom.planb.impl.db.hash.HashFactory;
import stroom.planb.impl.db.hash.HashFactoryFactory;
import stroom.planb.impl.db.serde.KeySerde;
import stroom.planb.impl.db.serde.time.DayTimeSerde;
import stroom.planb.impl.db.serde.time.HourTimeSerde;
import stroom.planb.impl.db.serde.time.MillisecondTimeSerde;
import stroom.planb.impl.db.serde.time.MinuteTimeSerde;
import stroom.planb.impl.db.serde.time.NanoTimeSerde;
import stroom.planb.impl.db.serde.time.SecondTimeSerde;
import stroom.planb.impl.db.serde.time.TimeSerde;
import stroom.planb.impl.db.serde.valtime.ValTime;
import stroom.planb.impl.db.serde.valtime.ValTimeSerde;
import stroom.planb.impl.db.serde.valtime.ValTimeSerdeFactory;
import stroom.planb.impl.db.temporalstate.TemporalState.Key;
import stroom.planb.shared.HashLength;
import stroom.planb.shared.StateKeySchema;
import stroom.planb.shared.StateKeyType;
import stroom.planb.shared.StateValueSchema;
import stroom.planb.shared.StateValueType;
import stroom.planb.shared.TemporalStateSettings;
import stroom.planb.shared.TimePrecision;
import stroom.query.api.DateTimeSettings;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.ValuesConsumer;
import stroom.util.io.FileUtil;
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

public class TemporalStateDb extends AbstractDb<Key, Val> {

    private static final String KEY_LOOKUP_DB_NAME = "key";

    private final TemporalStateSettings settings;
    private final KeySerde<Key> keySerde;
    private final ValTimeSerde valueSerde;
    private final TimeSerde timeSerde;
    private final UsedLookupsRecorder keyRecorder;
    private final UsedLookupsRecorder valueRecorder;

    private TemporalStateDb(final PlanBEnv env,
                            final ByteBuffers byteBuffers,
                            final Boolean overwrite,
                            final TemporalStateSettings settings,
                            final KeySerde<Key> keySerde,
                            final ValTimeSerde valueSerde,
                            final TimeSerde timeSerde,
                            final HashClashCommitRunnable hashClashCommitRunnable) {
        super(env, byteBuffers, overwrite, hashClashCommitRunnable);
        this.settings = settings;
        this.keySerde = keySerde;
        this.valueSerde = valueSerde;
        this.timeSerde = timeSerde;
        this.keyRecorder = keySerde.getUsedLookupsRecorder(env);
        this.valueRecorder = valueSerde.getUsedLookupsRecorder(env);
    }

    public static TemporalStateDb create(final Path path,
                                         final ByteBuffers byteBuffers,
                                         final TemporalStateSettings settings,
                                         final boolean readOnly) {
        final StateKeyType stateKeyType = NullSafe.getOrElse(
                settings,
                TemporalStateSettings::getStateKeySchema,
                StateKeySchema::getStateKeyType,
                StateKeyType.VARIABLE);
        final HashLength keyHashLength = NullSafe.getOrElse(
                settings,
                TemporalStateSettings::getStateKeySchema,
                StateKeySchema::getHashLength,
                HashLength.LONG);
        final StateValueType stateValueType = NullSafe.getOrElse(
                settings,
                TemporalStateSettings::getStateValueSchema,
                StateValueSchema::getStateValueType,
                StateValueType.VARIABLE);
        final HashLength valueHashLength = NullSafe.getOrElse(
                settings,
                TemporalStateSettings::getStateValueSchema,
                StateValueSchema::getHashLength,
                HashLength.LONG);
        final HashClashCommitRunnable hashClashCommitRunnable = new HashClashCommitRunnable();
        final PlanBEnv env = new PlanBEnv(path,
                settings.getMaxStoreSize(),
                20,
                readOnly,
                hashClashCommitRunnable);
        final TimeSerde timeSerde = createTimeSerde(NullSafe.getOrElse(
                settings,
                TemporalStateSettings::getTimePrecision,
                TimePrecision.MILLISECOND));
        final KeySerde<Key> keySerde = createKeySerde(
                stateKeyType,
                keyHashLength,
                env,
                byteBuffers,
                timeSerde,
                hashClashCommitRunnable);
        final ValTimeSerde valueSerde = ValTimeSerdeFactory.createValueSerde(
                stateValueType,
                valueHashLength,
                env,
                byteBuffers,
                hashClashCommitRunnable);
        return new TemporalStateDb(
                env,
                byteBuffers,
                settings.overwrite(),
                settings,
                keySerde,
                valueSerde,
                timeSerde,
                hashClashCommitRunnable);
    }

    private static TimeSerde createTimeSerde(final TimePrecision timePrecision) {
        return switch (timePrecision) {
            case NANOSECOND -> new NanoTimeSerde();
            case MILLISECOND -> new MillisecondTimeSerde();
            case SECOND -> new SecondTimeSerde();
            case MINUTE -> new MinuteTimeSerde();
            case HOUR -> new HourTimeSerde();
            case DAY -> new DayTimeSerde();
        };
    }

    private static KeySerde<Key> createKeySerde(final StateKeyType stateKeyType,
                                                final HashLength hashLength,
                                                final PlanBEnv env,
                                                final ByteBuffers byteBuffers,
                                                final TimeSerde timeSerde,
                                                final HashClashCommitRunnable hashClashCommitRunnable) {
        return switch (stateKeyType) {
            case BOOLEAN -> new BooleanKeySerde(byteBuffers, timeSerde);
            case BYTE -> new ByteKeySerde(byteBuffers, timeSerde);
            case SHORT -> new ShortKeySerde(byteBuffers, timeSerde);
            case INT -> new IntegerKeySerde(byteBuffers, timeSerde);
            case LONG -> new LongKeySerde(byteBuffers, timeSerde);
            case FLOAT -> new FloatKeySerde(byteBuffers, timeSerde);
            case DOUBLE -> new DoubleKeySerde(byteBuffers, timeSerde);
            case STRING -> new LimitedStringKeySerde(byteBuffers, timeSerde);
            case UID_LOOKUP -> {
                final UidLookupDb uidLookupDb = new UidLookupDb(
                        env,
                        byteBuffers,
                        KEY_LOOKUP_DB_NAME);
                yield new UidLookupKeySerde(uidLookupDb, byteBuffers, timeSerde);
            }
            case HASH_LOOKUP -> {
                final HashFactory valueHashFactory = HashFactoryFactory.create(hashLength);
                final HashLookupDb hashLookupDb = new HashLookupDb(
                        env,
                        byteBuffers,
                        valueHashFactory,
                        hashClashCommitRunnable,
                        KEY_LOOKUP_DB_NAME);
                yield new HashLookupKeySerde(hashLookupDb, byteBuffers, timeSerde);
            }
            case VARIABLE -> {
                final HashFactory valueHashFactory = HashFactoryFactory.create(hashLength);
                final UidLookupDb uidLookupDb = new UidLookupDb(
                        env,
                        byteBuffers,
                        KEY_LOOKUP_DB_NAME);
                final HashLookupDb hashLookupDb = new HashLookupDb(
                        env,
                        byteBuffers,
                        valueHashFactory,
                        hashClashCommitRunnable,
                        KEY_LOOKUP_DB_NAME);
                yield new VariableKeySerde(uidLookupDb, hashLookupDb, byteBuffers, timeSerde);
            }
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
            try (final TemporalStateDb sourceDb = TemporalStateDb.create(source, byteBuffers, settings, true)) {
                sourceDb.env.read(readTxn -> {
                    sourceDb.iterate(readTxn, kv -> {
                        if (keySerde.usesLookup(kv.key()) || valueSerde.usesLookup(kv.val())) {
                            // We need to do a full read and merge.
                            final Key key = keySerde.read(readTxn, kv.key());
                            final Val value = valueSerde.read(readTxn, kv.val()).val();
                            insert(writer, new TemporalState(key, value));
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

    public TemporalState getState(final TemporalStateRequest request) {
        return env.read(readTxn ->
                keySerde.toBufferForGet(readTxn, request.key(), optionalKeyByteBuffer ->
                        optionalKeyByteBuffer.map(keyByteBuffer -> {
                            final ByteBuffer prefix = keyByteBuffer.slice(0,
                                    keyByteBuffer.remaining() - timeSerde.getSize());
                            final KeyRange<ByteBuffer> keyRange = KeyRange.atLeastBackward(keyByteBuffer);
                            try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(readTxn, keyRange)) {
                                final Iterator<KeyVal<ByteBuffer>> iterator = cursor.iterator();
                                if (iterator.hasNext() && !Thread.currentThread().isInterrupted()) {
                                    final KeyVal<ByteBuffer> kv = iterator.next();
                                    if (!ByteBufferUtils.containsPrefix(kv.key(), prefix)) {
                                        return null;
                                    }

                                    final Key key = keySerde.read(readTxn, kv.key());
                                    final Val val = valueSerde.read(readTxn, kv.val()).val();
                                    return new TemporalState(key, val);
                                }
                            }
                            return null;

                        }).orElse(null)));
    }

    public static ValuesExtractor createValuesExtractor(final FieldIndex fieldIndex,
                                                        final Function<Context, Key> keyFunction,
                                                        final Function<Context, Val> valFunction) {
        final String[] fields = fieldIndex.getFields();
        final TemporalStateConverter[] converters = new TemporalStateConverter[fields.length];
        for (int i = 0; i < fields.length; i++) {
            converters[i] = switch (fields[i]) {
                case TemporalStateFields.KEY -> kv -> kv.getKey().getName();
                case TemporalStateFields.EFFECTIVE_TIME -> kv -> ValDate.create(kv.getKey().getEffectiveTime());
                case TemporalStateFields.VALUE_TYPE -> kv -> ValString.create(kv.getValue().type().toString());
                case TemporalStateFields.VALUE -> LazyKV::getValue;
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
            return values;
        };
    }

    @Override
    public long deleteOldData(final Instant deleteBefore, final boolean useStateTime) {
        return env.read(readTxn -> env.write(writer -> {
            long changeCount = 0;
            try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(readTxn)) {
                final Iterator<KeyVal<ByteBuffer>> iterator = cursor.iterator();
                while (iterator.hasNext()
                       && !Thread.currentThread().isInterrupted()) {
                    final KeyVal<ByteBuffer> kv = iterator.next();
                    final Key key = keySerde.read(readTxn, kv.key().duplicate());
                    final Instant time;
                    if (useStateTime) {
                        time = key.getEffectiveTime();
                    } else {
                        final ValTime valTime = valueSerde.read(readTxn, kv.val().duplicate());
                        time = valTime.insertTime();
                    }

                    if (time.isBefore(deleteBefore)) {
                        // If this is data we no longer want to retain then delete it.
                        dbi.delete(writer.getWriteTxn(), kv.key());
                        writer.tryCommit();
                        changeCount++;
                    } else {
                        // Record used lookup keys.
                        keyRecorder.recordUsed(writer, kv.key());
                        valueRecorder.recordUsed(writer, kv.val());
                    }
                }
            }

            // Delete unused lookup keys.
            keyRecorder.deleteUnused(readTxn, writer);
            valueRecorder.deleteUnused(readTxn, writer);

            return changeCount;
        }));
    }

    @Override
    public long condense(final Instant condenseBefore) {
        return env.read(readTxn -> env.write(writer -> {
            long changeCount = 0;
            TemporalState lastState = null;
            TemporalState newState = null;
            try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(readTxn)) {
                final Iterator<KeyVal<ByteBuffer>> iterator = cursor.iterator();
                while (iterator.hasNext()
                       && !Thread.currentThread().isInterrupted()) {
                    final KeyVal<ByteBuffer> kv = iterator.next();
                    final Key key = keySerde.read(readTxn, kv.key().duplicate());
                    final ValTime valTime = valueSerde.read(readTxn, kv.val().duplicate());
                    TemporalState state = new TemporalState(key, valTime.val());
                    final Instant time = key.getEffectiveTime();

                    if (lastState != null &&
                        Objects.equals(lastState.key().getName(), key.getName()) &&
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
        }));
    }

    private void deleteState(final LmdbWriter writer, final TemporalState state) {
        keySerde.write(writer.getWriteTxn(), state.key(), keyByteBuffer -> {
            dbi.delete(writer.getWriteTxn(), keyByteBuffer);
            writer.incrementChangeCount();
        });
    }

    public interface TemporalStateConverter extends Converter<Key, Val> {

    }
}
