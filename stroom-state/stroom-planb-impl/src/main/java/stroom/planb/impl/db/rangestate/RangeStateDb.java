package stroom.planb.impl.db.rangestate;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.entity.shared.ExpressionCriteria;
import stroom.lmdb2.KV;
import stroom.planb.impl.data.RangeState;
import stroom.planb.impl.data.RangeState.Key;
import stroom.planb.impl.db.AbstractDb;
import stroom.planb.impl.db.HashClashCommitRunnable;
import stroom.planb.impl.db.LmdbWriter;
import stroom.planb.impl.db.PlanBEnv;
import stroom.planb.impl.db.PlanBSearchHelper;
import stroom.planb.impl.db.PlanBSearchHelper.Context;
import stroom.planb.impl.db.PlanBSearchHelper.Converter;
import stroom.planb.impl.db.PlanBSearchHelper.LazyKV;
import stroom.planb.impl.db.PlanBSearchHelper.ValuesExtractor;
import stroom.planb.impl.db.UsedLookupsRecorder;
import stroom.planb.impl.serde.rangestate.ByteRangeKeySerde;
import stroom.planb.impl.serde.rangestate.IntegerRangeKeySerde;
import stroom.planb.impl.serde.rangestate.LongRangeKeySerde;
import stroom.planb.impl.serde.rangestate.RangeKeySerde;
import stroom.planb.impl.serde.rangestate.ShortRangeKeySerde;
import stroom.planb.impl.serde.valtime.ValTime;
import stroom.planb.impl.serde.valtime.ValTimeSerde;
import stroom.planb.impl.serde.valtime.ValTimeSerdeFactory;
import stroom.planb.shared.HashLength;
import stroom.planb.shared.RangeKeySchema;
import stroom.planb.shared.RangeStateSettings;
import stroom.planb.shared.RangeType;
import stroom.planb.shared.StateValueSchema;
import stroom.planb.shared.StateValueType;
import stroom.query.api.DateTimeSettings;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
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

public class RangeStateDb extends AbstractDb<Key, Val> {

    private final RangeStateSettings settings;
    private final RangeKeySerde keySerde;
    private final ValTimeSerde valueSerde;
    private final UsedLookupsRecorder keyRecorder;
    private final UsedLookupsRecorder valueRecorder;

    private RangeStateDb(final PlanBEnv env,
                         final ByteBuffers byteBuffers,
                         final Boolean overwrite,
                         final RangeStateSettings settings,
                         final RangeKeySerde keySerde,
                         final ValTimeSerde valueSerde,
                         final HashClashCommitRunnable hashClashCommitRunnable) {
        super(env, byteBuffers, overwrite, hashClashCommitRunnable);
        this.settings = settings;
        this.keySerde = keySerde;
        this.valueSerde = valueSerde;
        this.keyRecorder = keySerde.getUsedLookupsRecorder(env);
        this.valueRecorder = valueSerde.getUsedLookupsRecorder(env);
    }

    public static RangeStateDb create(final Path path,
                                      final ByteBuffers byteBuffers,
                                      final RangeStateSettings settings,
                                      final boolean readOnly) {
        final HashClashCommitRunnable hashClashCommitRunnable = new HashClashCommitRunnable();
        final PlanBEnv env = new PlanBEnv(path,
                settings.getMaxStoreSize(),
                20,
                readOnly,
                hashClashCommitRunnable);
        final RangeType rangeType = NullSafe.getOrElse(
                settings,
                RangeStateSettings::getKeySchema,
                RangeKeySchema::getRangeType,
                RangeType.LONG);
        final StateValueType stateValueType = NullSafe.getOrElse(
                settings,
                RangeStateSettings::getValueSchema,
                StateValueSchema::getStateValueType,
                StateValueType.VARIABLE);
        final HashLength valueHashLength = NullSafe.getOrElse(
                settings,
                RangeStateSettings::getValueSchema,
                StateValueSchema::getHashLength,
                HashLength.LONG);
        final RangeKeySerde keySerde = createKeySerde(
                rangeType,
                byteBuffers);
        final ValTimeSerde valueSerde = ValTimeSerdeFactory.createValueSerde(
                stateValueType,
                valueHashLength,
                env,
                byteBuffers,
                hashClashCommitRunnable);
        return new RangeStateDb(
                env,
                byteBuffers,
                settings.overwrite(),
                settings,
                keySerde,
                valueSerde,
                hashClashCommitRunnable);
    }

    private static RangeKeySerde createKeySerde(final RangeType rangeType,
                                                final ByteBuffers byteBuffers) {
        return switch (rangeType) {
            case BYTE -> new ByteRangeKeySerde(byteBuffers);
            case SHORT -> new ShortRangeKeySerde(byteBuffers);
            case INT -> new IntegerRangeKeySerde(byteBuffers);
            case LONG -> new LongRangeKeySerde(byteBuffers);
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
            try (final RangeStateDb sourceDb = RangeStateDb.create(source,
                    byteBuffers,
                    settings,
                    true)) {
                sourceDb.env.read(readTxn -> {
                    sourceDb.iterate(readTxn, kv -> {
                        if (sourceDb.keySerde.usesLookup(kv.key()) || sourceDb.valueSerde.usesLookup(kv.val())) {
                            // We need to do a full read and merge.
                            final Key key = sourceDb.keySerde.read(readTxn, kv.key());
                            final Val value = sourceDb.valueSerde.read(readTxn, kv.val()).val();
                            insert(writer, new RangeState(key, value));
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

    public RangeState getState(final RangeStateRequest request) {
        return env.read(readTxn ->
                keySerde.toKeyStart(request.key(), start -> {
                    final KeyRange<ByteBuffer> keyRange = KeyRange.atLeastBackward(start);
                    RangeState result = null;
                    try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(readTxn, keyRange)) {
                        final Iterator<KeyVal<ByteBuffer>> iterator = cursor.iterator();
                        while (iterator.hasNext()
                               && !Thread.currentThread().isInterrupted()) {
                            final KeyVal<ByteBuffer> kv = iterator.next();
                            final Key key = keySerde.read(readTxn, kv.key());
                            if (key.getKeyEnd() < request.key()) {
                                return result;
                            } else if (key.getKeyStart() <= request.key()) {
                                final Val value = valueSerde.read(readTxn, kv.val()).val();
                                result = new RangeState(key, value);
                            }
                        }
                    }
                    return result;
                }));
    }

    public static ValuesExtractor createValuesExtractor(final FieldIndex fieldIndex,
                                                        final Function<Context, Key> keyFunction,
                                                        final Function<Context, Val> valFunction) {
        final String[] fields = fieldIndex.getFields();
        final RangeStateConverter[] converters = new RangeStateConverter[fields.length];
        for (int i = 0; i < fields.length; i++) {
            converters[i] = switch (fields[i]) {
                case RangeStateFields.KEY_START -> kv -> ValLong.create(kv.getKey().getKeyStart());
                case RangeStateFields.KEY_END -> kv -> ValLong.create(kv.getKey().getKeyEnd());
                case RangeStateFields.VALUE_TYPE -> kv -> ValString.create(kv.getValue().type().toString());
                case RangeStateFields.VALUE -> LazyKV::getValue;
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
        return env.readAndWrite((readTxn, writer) -> {
            long changeCount = 0;
            try (final CursorIterable<ByteBuffer> cursor = dbi.iterate(readTxn)) {
                final Iterator<KeyVal<ByteBuffer>> iterator = cursor.iterator();
                while (iterator.hasNext()
                       && !Thread.currentThread().isInterrupted()) {
                    final KeyVal<ByteBuffer> kv = iterator.next();
                    final ValTime value = valueSerde.read(readTxn, kv.val().duplicate());

                    if (value.insertTime().isBefore(deleteBefore)) {
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
        });
    }

    @Override
    public long condense(final Instant condenseBefore) {
        return 0;
    }

    public interface RangeStateConverter extends Converter<Key, Val> {

    }
}
