package stroom.planb.impl.db.state;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.entity.shared.ExpressionCriteria;
import stroom.lmdb2.KV;
import stroom.planb.impl.data.State;
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
import stroom.planb.impl.serde.keyprefix.KeyPrefix;
import stroom.planb.impl.serde.keyprefix.KeyPrefixSerde;
import stroom.planb.impl.serde.keyprefix.KeyPrefixSerdeFactory;
import stroom.planb.impl.serde.valtime.ValTime;
import stroom.planb.impl.serde.valtime.ValTimeSerde;
import stroom.planb.impl.serde.valtime.ValTimeSerdeFactory;
import stroom.planb.shared.AbstractPlanBSettings;
import stroom.planb.shared.HashLength;
import stroom.planb.shared.KeyType;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.StateKeySchema;
import stroom.planb.shared.StateSettings;
import stroom.planb.shared.StateValueSchema;
import stroom.planb.shared.StateValueType;
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

public class StateDb extends AbstractDb<KeyPrefix, Val> {

    private static final int CURRENT_SCHEMA_VERSION = 1;

    private final KeySerde<KeyPrefix> keySerde;
    private final ValTimeSerde valueSerde;
    private final UsedLookupsRecorder keyRecorder;
    private final UsedLookupsRecorder valueRecorder;

    private StateDb(final PlanBEnv env,
                    final ByteBuffers byteBuffers,
                    final PlanBDoc doc,
                    final StateSettings settings,
                    final KeySerde<KeyPrefix> keySerde,
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

    public static StateDb create(final Path path,
                                 final ByteBuffers byteBuffers,
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
            final KeyType keyType = NullSafe.getOrElse(
                    settings,
                    StateSettings::getKeySchema,
                    StateKeySchema::getKeyType,
                    StateKeySchema.DEFAULT_KEY_TYPE);
            final HashLength keyHashLength = NullSafe.getOrElse(
                    settings,
                    StateSettings::getKeySchema,
                    StateKeySchema::getHashLength,
                    StateKeySchema.DEFAULT_HASH_LENGTH);
            final StateValueType stateValueType = NullSafe.getOrElse(
                    settings,
                    StateSettings::getValueSchema,
                    StateValueSchema::getStateValueType,
                    StateValueSchema.DEFAULT_VALUE_TYPE);
            final HashLength valueHashLength = NullSafe.getOrElse(
                    settings,
                    StateSettings::getValueSchema,
                    StateValueSchema::getHashLength,
                    StateValueSchema.DEFAULT_HASH_LENGTH);
            final KeyPrefixSerde keySerde = KeyPrefixSerdeFactory.createKeySerde(
                    keyType,
                    keyHashLength,
                    env,
                    byteBuffers,
                    hashClashCommitRunnable);
            final ValTimeSerde valueSerde = ValTimeSerdeFactory.createValueSerde(
                    stateValueType,
                    valueHashLength,
                    env,
                    byteBuffers,
                    hashClashCommitRunnable);
            return new StateDb(
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

    @Override
    public void insert(final LmdbWriter writer, final KV<KeyPrefix, Val> kv) {
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
            try (final StateDb sourceDb = StateDb.create(source, byteBuffers, doc, true)) {
                // Validate that the source DB has the same schema.
                validateSchema(schemaInfo, sourceDb.getSchemaInfo());

                // Merge.
                sourceDb.env.read(readTxn -> {
                    sourceDb.iterate(readTxn, kv -> {
                        if (sourceDb.keySerde.usesLookup(kv.key()) || sourceDb.valueSerde.usesLookup(kv.val())) {
                            // We need to do a full read and merge.
                            final KeyPrefix key = sourceDb.keySerde.read(readTxn, kv.key());
                            final ValTime value = sourceDb.valueSerde.read(readTxn, kv.val());
                            insert(writer, new State(key, value.val()));
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
    public Val get(final KeyPrefix key) {
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

    private Function<Context, KeyPrefix> getKeyExtractionFunction(final Txn<ByteBuffer> readTxn) {
        return context -> keySerde.read(readTxn, context.kv().key().duplicate());
    }

    private Function<Context, Val> getValExtractionFunction(final Txn<ByteBuffer> readTxn) {
        return context -> NullSafe.get(valueSerde.read(readTxn, context.kv().val().duplicate()), ValTime::val);
    }

    public State getState(final StateRequest request) {
        final Val value = get(request.key());
        if (value == null) {
            return null;
        }
        return new State(request.key(), value);
    }

    public static ValuesExtractor createValuesExtractor(final FieldIndex fieldIndex,
                                                        final Function<Context, KeyPrefix> keyFunction,
                                                        final Function<Context, Val> valFunction) {
        final String[] fields = fieldIndex.getFields();
        final StateConverter[] converters = new StateConverter[fields.length];
        for (int i = 0; i < fields.length; i++) {
            converters[i] = switch (fields[i]) {
                case StateFields.KEY -> kv -> kv.getKey().getVal();
                case StateFields.VALUE_TYPE -> kv -> ValString.create(kv.getValue().type().toString());
                case StateFields.VALUE -> LazyKV::getValue;
                default -> kv -> ValNull.INSTANCE;
            };
        }
        return (readTxn, kv) -> {
            final Context context = new Context(readTxn, kv);
            final LazyKV<KeyPrefix, Val> lazyKV = new LazyKV<>(context, keyFunction, valFunction);
            final Val[] values = new Val[fields.length];
            for (int i = 0; i < fields.length; i++) {
                values[i] = converters[i].convert(lazyKV);
            }
            return Values.of(values);
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

    public interface StateConverter extends Converter<KeyPrefix, Val> {

    }
}
