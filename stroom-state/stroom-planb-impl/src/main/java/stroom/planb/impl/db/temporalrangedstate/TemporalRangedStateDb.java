package stroom.planb.impl.db.temporalrangedstate;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.entity.shared.ExpressionCriteria;
import stroom.lmdb2.KV;
import stroom.planb.impl.db.HashClashCommitRunnable;
import stroom.planb.impl.db.HashLookupDb;
import stroom.planb.impl.db.LmdbWriter;
import stroom.planb.impl.db.PlanBSearchHelper.ValuesExtractor;
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
import stroom.planb.impl.db.AbstractDb;
import stroom.planb.impl.db.PlanBEnv;
import stroom.planb.impl.db.PlanBSearchHelper;
import stroom.planb.impl.db.PlanBSearchHelper.Context;
import stroom.planb.impl.db.PlanBSearchHelper.Converter;
import stroom.planb.impl.db.PlanBSearchHelper.LazyKV;
import stroom.planb.impl.db.temporalrangedstate.TemporalRangedState.Key;
import stroom.planb.shared.HashLength;
import stroom.planb.shared.RangeType;
import stroom.planb.shared.StateValueSchema;
import stroom.planb.shared.StateValueType;
import stroom.planb.shared.TemporalRangedStateSettings;
import stroom.planb.shared.TimePrecision;
import stroom.query.api.DateTimeSettings;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValLong;
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
import java.util.function.Consumer;
import java.util.function.Function;

public class TemporalRangedStateDb extends AbstractDb<Key, Val> {

    private static final String VALUE_LOOKUP_DB_NAME = "value";

    private final TemporalRangedStateSettings settings;
    private final TemporalRangeKeySerde keySerde;
    private final Serde<Val> valueSerde;

    private TemporalRangedStateDb(final PlanBEnv env,
                                  final ByteBuffers byteBuffers,
                                  final Boolean overwrite,
                                  final TemporalRangedStateSettings settings,
                                  final TemporalRangeKeySerde keySerde,
                                  final Serde<Val> valueSerde,
                                  final HashClashCommitRunnable hashClashCommitRunnable) {
        super(env, byteBuffers, overwrite, hashClashCommitRunnable);
        this.settings = settings;
        this.keySerde = keySerde;
        this.valueSerde = valueSerde;
    }

    public static TemporalRangedStateDb create(final Path path,
                                               final ByteBuffers byteBuffers,
                                               final TemporalRangedStateSettings settings,
                                               final boolean readOnly) {
        final RangeType rangeType = NullSafe.getOrElse(
                settings,
                TemporalRangedStateSettings::getRangeType,
                RangeType.LONG);
        final StateValueType stateValueType = NullSafe.getOrElse(
                settings,
                TemporalRangedStateSettings::getStateValueSchema,
                StateValueSchema::getStateValueType,
                StateValueType.VARIABLE);
        final HashLength valueHashLength = NullSafe.getOrElse(
                settings,
                TemporalRangedStateSettings::getStateValueSchema,
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
                TemporalRangedStateSettings::getTimePrecision,
                TimePrecision.MILLISECOND));
        final TemporalRangeKeySerde keySerde = createKeySerde(
                rangeType,
                byteBuffers,
                timeSerde);
        final Serde<Val> valueSerde = createValueSerde(
                stateValueType,
                valueHashLength,
                env,
                byteBuffers,
                hashClashCommitRunnable);
        return new TemporalRangedStateDb(
                env,
                byteBuffers,
                settings.overwrite(),
                settings,
                keySerde,
                valueSerde,
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
            try (final TemporalRangedStateDb sourceDb = TemporalRangedStateDb.create(source,
                    byteBuffers,
                    settings,
                    true)) {
                sourceDb.env.read(readTxn -> {
                    sourceDb.iterate(readTxn, kv -> {
                        if (keySerde.usesLookup(kv.key()) || valueSerde.usesLookup(kv.val())) {
                            // We need to do a full read and merge.
                            final Key key = keySerde.read(readTxn, kv.key());
                            final Val value = valueSerde.read(readTxn, kv.val());
                            insert(writer, new TemporalRangedState(key, value));
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

    public TemporalRangedState getState(final TemporalRangedStateRequest request) {
        return env.read(readTxn ->
                keySerde.toKeyStart(request.key(), start -> {
                    final KeyRange<ByteBuffer> keyRange = KeyRange.atLeastBackward(start);
                    TemporalRangedState result = null;
                    try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(readTxn, keyRange)) {
                        final Iterator<KeyVal<ByteBuffer>> iterator = cursor.iterator();
                        while (iterator.hasNext()
                               && !Thread.currentThread().isInterrupted()) {
                            final KeyVal<ByteBuffer> kv = iterator.next();
                            final Key key = keySerde.read(readTxn, kv.key());
                            if (key.getKeyEnd() < request.key()) {
                                return result;
                            } else if ((key.getEffectiveTime().isBefore(request.effectiveTime()) ||
                                        key.getEffectiveTime().equals(request.effectiveTime())) &&
                                       key.getKeyStart() <= request.key()) {
                                final Val value = valueSerde.read(readTxn, kv.val());
                                result = new TemporalRangedState(key, value);
                            }
                        }
                    }
                    return result;
                }));
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
                        final Key key = keySerde.read(readTxn, kv.key().duplicate());
                        final Val value = valueSerde.read(readTxn, kv.val().duplicate());

                        if (key.getEffectiveTime().isBefore(deleteBefore)) {
                            // If this is data we no longer want to retain then delete it.
                            dbi.delete(writer.getWriteTxn(), kv.key());
                            writer.tryCommit();

                        } else {
                            if (lastKey != null &&
                                lastKey.getKeyStart() == key.getKeyStart() &&
                                lastKey.getKeyEnd() == key.getKeyEnd() &&
                                lastValue.equals(value)) {
                                if (key.getEffectiveTime().isBefore(condenseBefore)) {
                                    // If the key and value are the same then delete the duplicate entry.
                                    dbi.delete(writer.getWriteTxn(), kv.key());
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
        final TemporalRangedStateDb.TemporalRangedStateConverter[] converters = new TemporalRangedStateDb.TemporalRangedStateConverter[fields.length];
        for (int i = 0; i < fields.length; i++) {
            converters[i] = switch (fields[i]) {
                case TemporalRangedStateFields.KEY_START -> kv -> ValLong.create(kv.getKey().getKeyStart());
                case TemporalRangedStateFields.KEY_END -> kv -> ValLong.create(kv.getKey().getKeyEnd());
                case TemporalRangedStateFields.EFFECTIVE_TIME -> kv -> ValDate.create(kv.getKey().getEffectiveTime());
                case TemporalRangedStateFields.VALUE_TYPE -> kv -> ValString.create(kv.getValue().type().toString());
                case TemporalRangedStateFields.VALUE -> LazyKV::getValue;
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

    public interface TemporalRangedStateConverter extends Converter<Key, Val> {

    }
}
