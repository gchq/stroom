package stroom.planb.impl.db.state;

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
import stroom.planb.impl.db.hash.HashFactory;
import stroom.planb.impl.db.hash.HashFactoryFactory;
import stroom.planb.impl.db.serde.Serde;
import stroom.planb.impl.db.serde.val.BooleanValSerde;
import stroom.planb.impl.db.serde.val.ByteValSerde;
import stroom.planb.impl.db.serde.val.DoubleValSerde;
import stroom.planb.impl.db.serde.val.FloatValSerde;
import stroom.planb.impl.db.serde.val.HashLookupValSerde;
import stroom.planb.impl.db.serde.val.IntegerValSerde;
import stroom.planb.impl.db.serde.val.LimitedStringValSerde;
import stroom.planb.impl.db.serde.val.LongValSerde;
import stroom.planb.impl.db.serde.val.ShortValSerde;
import stroom.planb.impl.db.serde.val.StringValSerde;
import stroom.planb.impl.db.serde.val.UidLookupValSerde;
import stroom.planb.impl.db.serde.val.ValSerde;
import stroom.planb.impl.db.serde.val.VariableValSerde;
import stroom.planb.shared.HashLength;
import stroom.planb.shared.StateKeySchema;
import stroom.planb.shared.StateKeyType;
import stroom.planb.shared.StateSettings;
import stroom.planb.shared.StateValueSchema;
import stroom.planb.shared.StateValueType;
import stroom.query.api.DateTimeSettings;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.ValuesConsumer;
import stroom.util.io.FileUtil;
import stroom.util.shared.NullSafe;

import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Function;

public class StateDb extends AbstractDb<Val, Val> {

    private static final String KEY_LOOKUP_DB_NAME = "key";
    private static final String VALUE_LOOKUP_DB_NAME = "value";

    private final StateSettings settings;
    private final Serde<Val> keySerde;
    private final Serde<Val> valueSerde;

    private StateDb(final PlanBEnv env,
                    final ByteBuffers byteBuffers,
                    final Boolean overwrite,
                    final StateSettings settings,
                    final Serde<Val> keySerde,
                    final Serde<Val> valueSerde,
                    final HashClashCommitRunnable hashClashCommitRunnable) {
        super(env, byteBuffers, overwrite, hashClashCommitRunnable);
        this.settings = settings;
        this.keySerde = keySerde;
        this.valueSerde = valueSerde;
    }

    public static StateDb create(final Path path,
                                 final ByteBuffers byteBuffers,
                                 final StateSettings settings,
                                 final boolean readOnly) {
        final StateKeyType stateKeyType = NullSafe.getOrElse(
                settings,
                StateSettings::getStateKeySchema,
                StateKeySchema::getStateKeyType,
                StateKeyType.VARIABLE);
        final HashLength keyHashLength = NullSafe.getOrElse(
                settings,
                StateSettings::getStateKeySchema,
                StateKeySchema::getHashLength,
                HashLength.LONG);
        final StateValueType stateValueType = NullSafe.getOrElse(
                settings,
                StateSettings::getStateValueSchema,
                StateValueSchema::getStateValueType,
                StateValueType.VARIABLE);
        final HashLength valueHashLength = NullSafe.getOrElse(
                settings,
                StateSettings::getStateValueSchema,
                StateValueSchema::getHashLength,
                HashLength.LONG);
        final HashClashCommitRunnable hashClashCommitRunnable = new HashClashCommitRunnable();
        final PlanBEnv env = new PlanBEnv(path,
                settings.getMaxStoreSize(),
                10,
                readOnly,
                hashClashCommitRunnable);
        final Serde<Val> keySerde = createKeySerde(
                stateKeyType,
                keyHashLength,
                env,
                byteBuffers,
                hashClashCommitRunnable);
        final Serde<Val> valueSerde = createValueSerde(
                stateValueType,
                valueHashLength,
                env,
                byteBuffers,
                hashClashCommitRunnable);
        return new StateDb(
                env,
                byteBuffers,
                settings.overwrite(),
                settings,
                keySerde,
                valueSerde,
                hashClashCommitRunnable);
    }

    private static Serde<Val> createKeySerde(final StateKeyType stateKeyType,
                                             final HashLength hashLength,
                                             final PlanBEnv env,
                                             final ByteBuffers byteBuffers,
                                             final HashClashCommitRunnable hashClashCommitRunnable) {
        return switch (stateKeyType) {
            case BOOLEAN -> new BooleanValSerde(byteBuffers);
            case BYTE -> new ByteValSerde(byteBuffers);
            case SHORT -> new ShortValSerde(byteBuffers);
            case INT -> new IntegerValSerde(byteBuffers);
            case LONG -> new LongValSerde(byteBuffers);
            case FLOAT -> new FloatValSerde(byteBuffers);
            case DOUBLE -> new DoubleValSerde(byteBuffers);
            case STRING -> new LimitedStringValSerde(byteBuffers);
            case UID_LOOKUP -> {
                final UidLookupDb uidLookupDb = new UidLookupDb(
                        env,
                        byteBuffers,
                        KEY_LOOKUP_DB_NAME);
                yield new UidLookupValSerde(uidLookupDb, byteBuffers);
            }
            case HASH_LOOKUP -> {
                final HashFactory valueHashFactory = HashFactoryFactory.create(hashLength);
                final HashLookupDb hashLookupDb = new HashLookupDb(
                        env,
                        byteBuffers,
                        valueHashFactory,
                        hashClashCommitRunnable,
                        KEY_LOOKUP_DB_NAME);
                yield new HashLookupValSerde(hashLookupDb, byteBuffers);
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
                yield new VariableValSerde(uidLookupDb, hashLookupDb, byteBuffers);
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
    public void insert(final LmdbWriter writer, final KV<Val, Val> kv) {
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
            try (final StateDb sourceDb = StateDb.create(source, byteBuffers, settings, true)) {
                sourceDb.env.read(readTxn -> {
                    sourceDb.iterate(readTxn, kv -> {
                        if (keySerde.usesLookup(kv.key()) || valueSerde.usesLookup(kv.val())) {
                            // We need to do a full read and merge.
                            final Val key = keySerde.read(readTxn, kv.key());
                            final Val value = valueSerde.read(readTxn, kv.val());
                            insert(writer, new State(key, value));
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
    public Val get(final Val key) {
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

    private Function<Context, Val> getKeyExtractionFunction(final Txn<ByteBuffer> readTxn) {
        return context -> keySerde.read(readTxn, context.kv().key().duplicate());
    }

    private Function<Context, Val> getValExtractionFunction(final Txn<ByteBuffer> readTxn) {
        return context -> valueSerde.read(readTxn, context.kv().val().duplicate());
    }

    public State getState(final StateRequest request) {
        final Val value = get(request.key());
        if (value == null) {
            return null;
        }
        return new State(request.key(), value);
    }

    public static ValuesExtractor createValuesExtractor(final FieldIndex fieldIndex,
                                                        final Function<Context, Val> keyFunction,
                                                        final Function<Context, Val> valFunction) {
        final String[] fields = fieldIndex.getFields();
        final StateConverter[] converters = new StateConverter[fields.length];
        for (int i = 0; i < fields.length; i++) {
            converters[i] = switch (fields[i]) {
                case StateFields.KEY -> LazyKV::getKey;
                case StateFields.VALUE_TYPE -> kv -> ValString.create(kv.getValue().type().toString());
                case StateFields.VALUE -> LazyKV::getValue;
                default -> kv -> ValNull.INSTANCE;
            };
        }
        return (readTxn, kv) -> {
            final Context context = new Context(readTxn, kv);
            final LazyKV<Val, Val> lazyKV = new LazyKV<>(context, keyFunction, valFunction);
            final Val[] values = new Val[fields.length];
            for (int i = 0; i < fields.length; i++) {
                values[i] = converters[i].convert(lazyKV);
            }
            return values;
        };
    }

    public interface StateConverter extends Converter<Val, Val> {

    }
}
