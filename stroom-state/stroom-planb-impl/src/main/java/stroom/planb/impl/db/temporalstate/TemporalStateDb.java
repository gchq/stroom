package stroom.planb.impl.db.temporalstate;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.entity.shared.ExpressionCriteria;
import stroom.lmdb2.KV;
import stroom.planb.impl.db.HashClashCommitRunnable;
import stroom.planb.impl.db.HashLookupDb;
import stroom.planb.impl.db.LmdbWriter;
import stroom.planb.impl.db.UidLookupDb;
import stroom.planb.impl.db.hash.HashFactory;
import stroom.planb.impl.db.hash.HashFactoryFactory;
import stroom.planb.impl.db.serde.Serde;
import stroom.planb.impl.db.serde.time.DayTimeSerde;
import stroom.planb.impl.db.serde.time.HourTimeSerde;
import stroom.planb.impl.db.serde.time.MillisecondTimeSerde;
import stroom.planb.impl.db.serde.time.MinuteTimeSerde;
import stroom.planb.impl.db.serde.time.NanoTimeSerde;
import stroom.planb.impl.db.serde.time.SecondTimeSerde;
import stroom.planb.impl.db.serde.time.TimeSerde;
import stroom.planb.impl.db.serde.val.BooleanValSerde;
import stroom.planb.impl.db.serde.val.ByteValSerde;
import stroom.planb.impl.db.serde.val.DoubleValSerde;
import stroom.planb.impl.db.serde.val.FloatValSerde;
import stroom.planb.impl.db.serde.val.HashLookupValSerde;
import stroom.planb.impl.db.serde.val.IntegerValSerde;
import stroom.planb.impl.db.serde.val.LongValSerde;
import stroom.planb.impl.db.serde.val.ShortValSerde;
import stroom.planb.impl.db.serde.val.StringValSerde;
import stroom.planb.impl.db.serde.val.UidLookupValSerde;
import stroom.planb.impl.db.serde.val.ValSerde;
import stroom.planb.impl.db.serde.val.VariableValSerde;
import stroom.planb.impl.db.state.AbstractDb;
import stroom.planb.impl.db.state.PlanBEnv;
import stroom.planb.impl.db.state.StateSearchHelper;
import stroom.planb.impl.db.state.StateSearchHelper.Context;
import stroom.planb.impl.db.state.StateSearchHelper.Converter;
import stroom.planb.impl.db.state.StateSearchHelper.LazyKV;
import stroom.planb.impl.db.state.ValuesExtractor;
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
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public class TemporalStateDb extends AbstractDb<Key, Val> {

    private static final String KEY_LOOKUP_DB_NAME = "key";
    private static final String VALUE_LOOKUP_DB_NAME = "value";

    private final TemporalStateSettings settings;
    private final Serde<Key> keySerde;
    private final Serde<Val> valueSerde;
    private final TimeSerde timeSerde;

    private TemporalStateDb(final PlanBEnv env,
                            final ByteBuffers byteBuffers,
                            final Boolean overwrite,
                            final TemporalStateSettings settings,
                            final Serde<Key> keySerde,
                            final Serde<Val> valueSerde,
                            final TimeSerde timeSerde,
                            final HashClashCommitRunnable hashClashCommitRunnable) {
        super(env, byteBuffers, overwrite, hashClashCommitRunnable);
        this.settings = settings;
        this.keySerde = keySerde;
        this.valueSerde = valueSerde;
        this.timeSerde = timeSerde;
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
                10,
                readOnly,
                hashClashCommitRunnable);
        final TimeSerde timeSerde = createTimeSerde(NullSafe.getOrElse(
                settings,
                TemporalStateSettings::getTimePrecision,
                TimePrecision.MILLISECOND));
        final Serde<Key> keySerde = createKeySerde(
                stateKeyType,
                keyHashLength,
                env,
                byteBuffers,
                timeSerde,
                hashClashCommitRunnable);
        final Serde<Val> valueSerde = createValueSerde(
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

    private static Serde<Key> createKeySerde(final StateKeyType stateKeyType,
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
            case STRING -> new LimitedStringKeySerde(byteBuffers, 511 - timeSerde.getSize(), timeSerde);
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

    private static ValSerde createValueSerde(final StateValueType stateValueType,
                                             final HashLength hashLength,
                                             final PlanBEnv env,
                                             final ByteBuffers byteBuffers,
                                             final HashClashCommitRunnable hashClashCommitRunnable) {
        return switch (stateValueType) {
            case BOOLEAN -> new BooleanValSerde(byteBuffers);
            case BYTE -> new ByteValSerde(byteBuffers);
            case SHORT -> new ShortValSerde(byteBuffers);
            case INT -> new IntegerValSerde(byteBuffers);
            case LONG -> new LongValSerde(byteBuffers);
            case FLOAT -> new FloatValSerde(byteBuffers);
            case DOUBLE -> new DoubleValSerde(byteBuffers);
            case STRING -> new StringValSerde(byteBuffers);
            case UID_LOOKUP -> {
                final UidLookupDb uidLookupDb = new UidLookupDb(
                        env,
                        byteBuffers,
                        VALUE_LOOKUP_DB_NAME);
                yield new UidLookupValSerde(uidLookupDb, byteBuffers);
            }
            case HASH_LOOKUP -> {
                final HashFactory valueHashFactory = HashFactoryFactory.create(hashLength);
                final HashLookupDb hashLookupDb = new HashLookupDb(
                        env,
                        byteBuffers,
                        valueHashFactory,
                        hashClashCommitRunnable,
                        VALUE_LOOKUP_DB_NAME);
                yield new HashLookupValSerde(hashLookupDb, byteBuffers);
            }
            case VARIABLE -> {
                final HashFactory valueHashFactory = HashFactoryFactory.create(hashLength);
                final UidLookupDb uidLookupDb = new UidLookupDb(
                        env,
                        byteBuffers,
                        VALUE_LOOKUP_DB_NAME);
                final HashLookupDb hashLookupDb = new HashLookupDb(
                        env,
                        byteBuffers,
                        valueHashFactory,
                        hashClashCommitRunnable,
                        VALUE_LOOKUP_DB_NAME);
                yield new VariableValSerde(uidLookupDb, hashLookupDb, byteBuffers);
            }
        };
    }

    @Override
    public void insert(final LmdbWriter writer, final KV<Key, Val> kv) {
        final Txn<ByteBuffer> writeTxn = writer.getWriteTxn();
        keySerde.write(writeTxn, kv.key(), keyByteBuffer ->
                valueSerde.write(writeTxn, kv.val(), valueByteBuffer ->
                        dbi.put(writeTxn, keyByteBuffer, valueByteBuffer, putFlags)));
        writer.tryCommit();
    }

    private boolean hasLookup() {
        final List<String> dbNames = env.getDbNames();
        return dbNames.contains(KEY_LOOKUP_DB_NAME) || dbNames.contains(VALUE_LOOKUP_DB_NAME);
    }

    private void iterate(final Consumer<KeyVal<ByteBuffer>> consumer) {
        env.read(readTxn -> {
            try (final CursorIterable<ByteBuffer> cursorIterable = dbi.iterate(readTxn)) {
                for (final KeyVal<ByteBuffer> keyVal : cursorIterable) {
                    consumer.accept(keyVal);
                }
            }
            return null;
        });
    }

    private void readAll(final Consumer<TemporalState> consumer) {
        env.read(readTxn -> {
            try (final CursorIterable<ByteBuffer> cursorIterable = dbi.iterate(readTxn)) {
                for (final KeyVal<ByteBuffer> keyVal : cursorIterable) {
                    final Key key = keySerde.read(readTxn, keyVal.key());
                    final Val value = valueSerde.read(readTxn, keyVal.val());
                    consumer.accept(new TemporalState(key, value));
                }
            }
            return null;
        });
    }

    @Override
    public void merge(final Path source) {
        env.write(writer -> {
            try (final TemporalStateDb sourceDb = TemporalStateDb.create(source, byteBuffers, settings, true)) {
                if (sourceDb.hasLookup()) {
                    // We need to do a full read and merge.
                    sourceDb.readAll(state -> insert(writer, state));

                } else {
                    // Quick merge.
                    sourceDb.iterate(kv -> {
                        if (dbi.put(writer.getWriteTxn(), kv.key(), kv.val(), putFlags)) {
                            writer.tryCommit();
                        }
                    });
                }
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
            StateSearchHelper.search(
                    readTxn,
                    criteria,
                    fieldIndex,
                    dateTimeSettings,
                    expressionPredicateFactory,
                    consumer,
                    valuesExtractor,
                    env,
                    dbi);
            return null;
        });
    }

    private Function<Context, Key> getKeyExtractionFunction(final Txn<ByteBuffer> readTxn) {
        return context -> keySerde.read(readTxn, context.kv().key().duplicate());
    }

    private Function<Context, Val> getValExtractionFunction(final Txn<ByteBuffer> readTxn) {
        return context -> valueSerde.read(readTxn, context.kv().val().duplicate());
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
                                    final Val val = valueSerde.read(readTxn, kv.val());
                                    return new TemporalState(key, val);
                                }
                            }
                            return null;

                        }).orElse(null)));
    }

    // TODO: Note that LMDB does not free disk space just because you delete entries, instead it just frees pages for
    //  reuse. We might want to create a new compacted instance instead of deleting in place.
    @Override
    public void condense(final long condenseBeforeMs, final long deleteBeforeMs) {
        condense(Instant.ofEpochMilli(condenseBeforeMs), Instant.ofEpochMilli(deleteBeforeMs));
    }

    public void condense(final Instant condenseBefore,
                         final Instant deleteBefore) {
        env.read(readTxn -> {
            env.write(writer -> {
                Key lastKey = null;
                Val lastValue = null;
                try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(readTxn)) {
                    final Iterator<KeyVal<ByteBuffer>> iterator = cursor.iterator();
                    while (iterator.hasNext()
                           && !Thread.currentThread().isInterrupted()) {
                        final KeyVal<ByteBuffer> kv = iterator.next();
                        final Key key = keySerde.read(readTxn, kv.key());
                        final Val value = valueSerde.read(readTxn, kv.val());

                        if (key.getEffectiveTime().isBefore(deleteBefore)) {
                            // If this is data we no longer want to retain then delete it.
                            dbi.delete(writer.getWriteTxn(), kv.key(), kv.val());
                            writer.tryCommit();

                        } else {
                            if (lastKey != null &&
                                Objects.equals(lastKey.getName(), key.getName()) &&
                                lastValue.equals(value)) {
                                if (key.getEffectiveTime().isBefore(condenseBefore)) {
                                    // If the key and value are the same then delete the duplicate entry.
                                    dbi.delete(writer.getWriteTxn(), kv.key(), kv.val());
                                    writer.tryCommit();
                                }
                            }

                            lastKey = key;
                            lastValue = value;
                        }
                    }
                }
            });
            return null;
        });
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

    public interface TemporalStateConverter extends Converter<Key, Val> {

    }
}
