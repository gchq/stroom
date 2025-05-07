package stroom.planb.impl.db.state;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.entity.shared.ExpressionCriteria;
import stroom.lmdb.LmdbConfig;
import stroom.lmdb2.KV;
import stroom.planb.impl.db.LmdbWriter;
import stroom.planb.impl.db.LookupDb;
import stroom.planb.impl.db.hash.HashClashCount;
import stroom.planb.impl.db.hash.HashFactory;
import stroom.planb.impl.db.hash.HashFactoryFactory;
import stroom.planb.impl.db.state.StateSearchHelper.Context;
import stroom.planb.shared.StateKeySchema;
import stroom.planb.shared.StateSettings;
import stroom.query.api.DateTimeSettings;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.ValuesConsumer;
import stroom.util.io.FileUtil;
import stroom.util.shared.NullSafe;

import org.lmdbjava.CursorIterable;
import org.lmdbjava.CursorIterable.KeyVal;
import org.lmdbjava.Dbi;
import org.lmdbjava.PutFlags;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.function.Function;

public class LookupKeySchema extends AbstractSchema<String, StateValue> {

    private final HashFactory hashFactory;
    private final LookupDb keyLookup;
    private final HashClashCount hashClashCount;
    private final PutFlags[] putFlags;
    private final StateValueSerde stateValueSerde;

    LookupKeySchema(final PlanBEnv env,
                    final ByteBuffers byteBuffers,
                    final StateSettings settings,
                    final HashClashCount hashClashCount,
                    final StateValueSerde stateValueSerde) {
        super(env, byteBuffers);
        this.hashClashCount = hashClashCount;
        this.stateValueSerde = stateValueSerde;

        hashFactory = HashFactoryFactory.create(NullSafe.get(
                settings,
                StateSettings::getStateKeySchema,
                StateKeySchema::getHashLength));
        keyLookup = new LookupDb(env, byteBuffers, hashFactory, hashClashCount, "keys",
                settings.overwrite());
        this.putFlags = settings.overwrite()
                ? new PutFlags[]{}
                : new PutFlags[]{PutFlags.MDB_NOOVERWRITE};
    }

    private byte[] parseKey(String key) {
        return key.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void insert(final LmdbWriter writer, final KV<String, StateValue> kv) {
        final byte[] keyBytes = parseKey(kv.key());
        keyLookup.put(writer.getWriteTxn(), keyBytes, keyIdBuffer -> {
            stateValueSerde.write(kv.val(), valueByteBuffer -> {
                if (dbi.put(writer.getWriteTxn(), keyIdBuffer, valueByteBuffer, putFlags)) {
                    writer.tryCommit();
                }
            });
            return null;
        });
    }

    @Override
    public void merge(final Path source) {
        env.write(writer -> {
            final PlanBEnv sourceEnv = new PlanBEnv(
                    source,
                    LmdbConfig.DEFAULT_MAX_STORE_SIZE.getBytes(),
                    3,
                    true,
                    () -> {
                    });
            final LookupDb sourceKeyLookup = new LookupDb(
                    sourceEnv,
                    byteBuffers,
                    hashFactory,
                    hashClashCount,
                    "keys",
                    false);
            final Dbi<ByteBuffer> sourceDbi = sourceEnv.openDbi(NAME);
            sourceEnv.read(readTxn -> {
                try (final CursorIterable<ByteBuffer> cursorIterable = sourceDbi.iterate(readTxn)) {
                    for (final KeyVal<ByteBuffer> keyVal : cursorIterable) {
                        sourceKeyLookup.get(writer.getWriteTxn(), keyVal.key(), optionalRealKey -> {
                            final ByteBuffer realKey = optionalRealKey.orElseThrow(() ->
                                    new RuntimeException("Unable to retrieve source key"));
                            keyLookup.put(writer.getWriteTxn(), realKey, keyIdBuffer -> {
                                if (dbi.put(writer.getWriteTxn(), keyIdBuffer, keyVal.val(), putFlags)) {
                                    writer.tryCommit();
                                }
                                return null;
                            });
                            return null;
                        });
                    }
                }
                return null;
            });
        });

        // Delete source now we have merged.
        FileUtil.deleteDir(source);
    }

    @Override
    public StateValue get(final String key) {
        final byte[] keyBytes = parseKey(key);
        return env.read(readTxn -> keyLookup.get(readTxn, keyBytes, optionalRealKey -> {
            if (optionalRealKey.isEmpty()) {
                return null;
            }

            final ByteBuffer valueByteBuffer = dbi.get(readTxn, optionalRealKey.get());
            if (valueByteBuffer == null) {
                return null;
            }
            return stateValueSerde.read(valueByteBuffer);
        }));
    }

    @Override
    public void search(final ExpressionCriteria criteria,
                       final FieldIndex fieldIndex,
                       final DateTimeSettings dateTimeSettings,
                       final ExpressionPredicateFactory expressionPredicateFactory,
                       final ValuesConsumer consumer) {
        final ValuesExtractor valuesExtractor = StateSearchHelper.createValuesExtractor(
                fieldIndex,
                getKeyExtractionFunction(),
                getStateValueExtractionFunction());
        StateSearchHelper.search(
                criteria,
                fieldIndex,
                dateTimeSettings,
                expressionPredicateFactory,
                consumer,
                valuesExtractor,
                env,
                dbi);
    }

    private Function<Context, Val> getKeyExtractionFunction() {
        return context -> {
            final ByteBuffer byteBuffer = context.kv().key().duplicate();
            return ValString.create(ByteBufferUtils.toString(keyLookup.getValue(context.readTxn(), byteBuffer)));
        };
    }

    private Function<Context, StateValue> getStateValueExtractionFunction() {
        return context -> stateValueSerde.read(context.kv().val());
    }
}
