package stroom.planb.impl.db.state;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.entity.shared.ExpressionCriteria;
import stroom.lmdb2.KV;
import stroom.planb.impl.db.Db;
import stroom.planb.impl.db.LmdbWriter;
import stroom.planb.impl.db.hash.HashClashCount;
import stroom.planb.shared.PlanBDoc;
import stroom.planb.shared.StateKeySchema;
import stroom.planb.shared.StateKeyType;
import stroom.planb.shared.StateSettings;
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

        final ValSerde stateValueSerde = new StandardStateValueSerde(byteBuffers);

        schema = switch (stateKeyType) {
            case BYTE -> new ByteKeySchema(env, byteBuffers, overwrite, stateValueSerde);
            case SHORT -> new ShortKeySchema(env, byteBuffers, overwrite, stateValueSerde);
            case INT -> new IntegerKeySchema(env, byteBuffers, overwrite, stateValueSerde);
            case LONG -> new LongKeySchema(env, byteBuffers, overwrite, stateValueSerde);
            case FLOAT -> new FloatKeySchema(env, byteBuffers, overwrite, stateValueSerde);
            case DOUBLE -> new DoubleKeySchema(env, byteBuffers, overwrite, stateValueSerde);
            case STRING -> new StringKeySchema(env, byteBuffers, overwrite, stateValueSerde);
            case HASHED -> new HashedKeySchema(env, byteBuffers, settings, hashClashCommitRunnable, stateValueSerde);
            case LOOKUP -> new LookupKeySchema(env, byteBuffers, settings, hashClashCommitRunnable, stateValueSerde);
            case VARIABLE -> new VariableKeySchema(env, byteBuffers, settings, hashClashCommitRunnable,
                    stateValueSerde);
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
