package stroom.planb.impl.db.state;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.entity.shared.ExpressionCriteria;
import stroom.lmdb2.KV;
import stroom.planb.impl.db.Db;
import stroom.planb.impl.db.LmdbWriter;
import stroom.planb.impl.db.LookupDb;
import stroom.planb.impl.db.hash.HashClashCount;
import stroom.planb.impl.db.hash.HashFactory;
import stroom.planb.impl.db.hash.HashFactoryFactory;
import stroom.planb.impl.db.serde.BooleanValSerde;
import stroom.planb.impl.db.serde.ByteValSerde;
import stroom.planb.impl.db.serde.DoubleValSerde;
import stroom.planb.impl.db.serde.FloatValSerde;
import stroom.planb.impl.db.serde.IntegerValSerde;
import stroom.planb.impl.db.serde.LimitedStringValSerde;
import stroom.planb.impl.db.serde.LongValSerde;
import stroom.planb.impl.db.serde.LookupValSerde;
import stroom.planb.impl.db.serde.ShortValSerde;
import stroom.planb.impl.db.serde.StringValSerde;
import stroom.planb.impl.db.serde.ValSerde;
import stroom.planb.impl.db.serde.VariableValSerde;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.StateKeySchema;
import stroom.planb.shared.StateKeyType;
import stroom.planb.shared.StateSettings;
import stroom.planb.shared.StateValueSchema;
import stroom.planb.shared.StateValueType;
import stroom.query.api.DateTimeSettings;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValuesConsumer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import java.nio.file.Path;

public class StateDb implements Db<String, Val> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StateDb.class);

    private final PlanBEnv env;
    private final Schema<String, Val> schema;

    public StateDb(final Path path,
                   final ByteBuffers byteBuffers) {
        this(
                path,
                byteBuffers,
                StateSettings.builder().build(),
                false);
    }

    public StateDb(final Path path,
                   final ByteBuffers byteBuffers,
                   final StateSettings settings,
                   final boolean readOnly) {
        HashClashCommitRunnable hashClashCommitRunnable = new HashClashCommitRunnable();
        env = new PlanBEnv(path, settings.getMaxStoreSize(), 3, readOnly, hashClashCommitRunnable);

        final boolean overwrite = settings.overwrite();

        // Legacy Plan B state stores use the LONG_STRING state key type even though this is less efficient.
        final StateKeyType stateKeyType = NullSafe.getOrElse(
                settings,
                StateSettings::getStateKeySchema,
                StateKeySchema::getStateKeyType,
                StateKeyType.HASHED);

        final StateValueType stateValueType = NullSafe.getOrElse(
                settings,
                StateSettings::getStateValueSchema,
                StateValueSchema::getStateValueType,
                StateValueType.STRING);

        final ValSerde valSerde = switch (stateValueType) {
            case BOOLEAN -> new BooleanValSerde(byteBuffers);
            case BYTE -> new ByteValSerde(byteBuffers);
            case SHORT -> new ShortValSerde(byteBuffers);
            case INT -> new IntegerValSerde(byteBuffers);
            case LONG -> new LongValSerde(byteBuffers);
            case FLOAT -> new FloatValSerde(byteBuffers);
            case DOUBLE -> new DoubleValSerde(byteBuffers);
            case STRING -> new StringValSerde(byteBuffers);
            case LOOKUP -> {
                final HashFactory valueHashFactory = HashFactoryFactory.create(NullSafe.get(
                        settings,
                        StateSettings::getStateValueSchema,
                        StateValueSchema::getHashLength));
                final LookupDb lookupDb = new LookupDb(
                        env,
                        byteBuffers,
                        valueHashFactory,
                        hashClashCommitRunnable,
                        "value",
                        overwrite);
                yield new LookupValSerde(lookupDb, byteBuffers);
            }
            case VARIABLE -> {
                final HashFactory valueHashFactory = HashFactoryFactory.create(NullSafe.get(
                        settings,
                        StateSettings::getStateValueSchema,
                        StateValueSchema::getHashLength));
                final LookupDb lookupDb = new LookupDb(
                        env,
                        byteBuffers,
                        valueHashFactory,
                        hashClashCommitRunnable,
                        "value",
                        overwrite);
                yield new VariableValSerde(lookupDb, byteBuffers);
            }
        };

        schema = switch (stateKeyType) {
            case BOOLEAN -> new SimpleKeySchema(env, byteBuffers, overwrite, new BooleanValSerde(byteBuffers), valSerde);
            case BYTE -> new SimpleKeySchema(env, byteBuffers, overwrite, new ByteValSerde(byteBuffers), valSerde);
            case SHORT -> new SimpleKeySchema(env, byteBuffers, overwrite, new ShortValSerde(byteBuffers), valSerde);
            case INT -> new SimpleKeySchema(env, byteBuffers, overwrite, new IntegerValSerde(byteBuffers), valSerde);
            case LONG -> new SimpleKeySchema(env, byteBuffers, overwrite, new LongValSerde(byteBuffers), valSerde);
            case FLOAT -> new SimpleKeySchema(env, byteBuffers, overwrite, new FloatValSerde(byteBuffers), valSerde);
            case DOUBLE -> new SimpleKeySchema(env, byteBuffers, overwrite, new DoubleValSerde(byteBuffers), valSerde);
            case STRING -> new SimpleKeySchema(env,
                    byteBuffers,
                    overwrite,
                    new LimitedStringValSerde(byteBuffers, 511),
                    valSerde);
            case HASHED -> new HashedKeySchema(env, byteBuffers, settings, hashClashCommitRunnable, valSerde);
            case LOOKUP -> {
                final HashFactory valueHashFactory = HashFactoryFactory.create(NullSafe.get(
                        settings,
                        StateSettings::getStateKeySchema,
                        StateKeySchema::getHashLength));
                final LookupDb lookupDb = new LookupDb(
                        env,
                        byteBuffers,
                        valueHashFactory,
                        hashClashCommitRunnable,
                        "key",
                        overwrite);
                yield new SimpleKeySchema(env,
                        byteBuffers,
                        overwrite,
                        new LookupValSerde(lookupDb, byteBuffers),
                        valSerde);
            }
            case VARIABLE -> {
                final HashFactory valueHashFactory = HashFactoryFactory.create(NullSafe.get(
                        settings,
                        StateSettings::getStateKeySchema,
                        StateKeySchema::getHashLength));
                final LookupDb lookupDb = new LookupDb(
                        env,
                        byteBuffers,
                        valueHashFactory,
                        hashClashCommitRunnable,
                        "key",
                        overwrite);
                yield new SimpleKeySchema(env,
                        byteBuffers,
                        overwrite,
                        new VariableValSerde(lookupDb, byteBuffers),
                        valSerde);
            }
        };
    }

    public static StateDb create(final Path path,
                                 final ByteBuffers byteBuffers,
                                 final PlanBDoc doc,
                                 final boolean readOnly) {
        return new StateDb(path, byteBuffers, getSettings(doc), readOnly);
    }

    private static StateSettings getSettings(final PlanBDoc doc) {
        if (doc.getSettings() instanceof final StateSettings settings) {
            return settings;
        }
        return StateSettings.builder().build();
    }

    public State getState(final StateRequest request) {
        final Val value = get(request.key());
        if (value == null) {
            return null;
        }
        return new State(request.key(), value);
    }

    @Override
    public void insert(final LmdbWriter writer, final KV<String, Val> kv) {
        schema.insert(writer, kv);
    }

    @Override
    public void merge(final Path source) {
        schema.merge(source);
    }

    @Override
    public void condense(final long condenseBeforeMs, final long deleteBeforeMs) {
        schema.condense(condenseBeforeMs, deleteBeforeMs);
    }

    @Override
    public Val get(final String key) {
        return schema.get(key);
    }

    @Override
    public void search(final ExpressionCriteria criteria,
                       final FieldIndex fieldIndex,
                       final DateTimeSettings dateTimeSettings,
                       final ExpressionPredicateFactory expressionPredicateFactory,
                       final ValuesConsumer consumer) {
        schema.search(criteria, fieldIndex, dateTimeSettings, expressionPredicateFactory, consumer);
    }

    @Override
    public LmdbWriter createWriter() {
        return env.createWriter();
    }

    @Override
    public void lock(final Runnable runnable) {
        env.lock(runnable);
    }

    @Override
    public void close() {
        env.close();
    }

    @Override
    public long count() {
        return schema.count();
    }

    @Override
    public boolean isReadOnly() {
        return env.isReadOnly();
    }

    @Override
    public String getInfo() {
        return schema.getInfo();
    }

    private static class HashClashCommitRunnable implements Runnable, HashClashCount {

        private int hashClashes;

        @Override
        public void increment() {
            // We must have had a hash clash here because we didn't find a row for the key even
            // though the db contains the key hash.
            hashClashes++;
        }

        @Override
        public void run() {
            if (hashClashes > 0) {
                // We prob don't want to warn but will keep for now until we know how big the issue is.
                LOGGER.warn(() -> "We had " + hashClashes + " hash clashes since last commit");
                hashClashes = 0;
            }
        }
    }
}
