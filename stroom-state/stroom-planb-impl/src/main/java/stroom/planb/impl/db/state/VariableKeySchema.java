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
import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A schema that tries a number of different approaches to store keys in the most efficient way depending on their type.
 */
class VariableKeySchema extends AbstractSchema<String, StateValue> {

    private static final int USE_LOOKUP_THRESHOLD = 32;

    private final PutFlags[] putFlags;
    private final HashFactory hashFactory;
    private final HashClashCount hashClashCount;
    private final LookupDb keyLookup;
    private final StateValueSerde stateValueSerde;

    public VariableKeySchema(final PlanBEnv env,
                             final ByteBuffers byteBuffers,
                             final StateSettings settings,
                             final HashClashCount hashClashCount,
                             final StateValueSerde stateValueSerde) {
        super(env, byteBuffers);
        this.stateValueSerde = stateValueSerde;

        hashFactory = HashFactoryFactory.create(NullSafe.get(
                settings,
                StateSettings::getStateKeySchema,
                StateKeySchema::getHashLength));
        this.hashClashCount = hashClashCount;

        keyLookup = new LookupDb(
                env,
                byteBuffers,
                hashFactory,
                hashClashCount,
                "keys",
                settings.overwrite());
        final boolean overwrite = settings.overwrite();
        this.putFlags = overwrite
                ? new PutFlags[]{}
                : new PutFlags[]{PutFlags.MDB_NOOVERWRITE};
    }


    private <R> R useVariableKey(final Txn<ByteBuffer> txn,
                                 final String key,
                                 final BiFunction<Txn<ByteBuffer>, ByteBuffer, R> function,
                                 final boolean read) {
        // The string is not a number so just use the bytes.
        final byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > USE_LOOKUP_THRESHOLD) {
            return useLookup(txn, bytes, function, read);
        }

        // Just use the bytes as the key directly.
        return useBytes(txn, bytes, function);
    }

    private <R> R useLookup(final Txn<ByteBuffer> txn,
                            final byte[] bytes,
                            final BiFunction<Txn<ByteBuffer>, ByteBuffer, R> function,
                            final boolean read) {
        if (read) {
            return keyLookup.get(txn, bytes, (Optional<ByteBuffer> optionalIdByteBuffer) -> {
                if (optionalIdByteBuffer.isEmpty()) {
                    return null;
                } else {
                    final ByteBuffer idByteBuffer = optionalIdByteBuffer.get();
                    return useIdByteBuffer(txn, idByteBuffer, function);
                }
            });
        } else {
            return keyLookup.put(txn, bytes, idByteBuffer ->
                    useIdByteBuffer(txn, idByteBuffer, function));
        }
    }

    private <R> R useBytes(final Txn<ByteBuffer> txn,
                           final byte[] bytes,
                           final BiFunction<Txn<ByteBuffer>, ByteBuffer, R> function) {
        return byteBuffers.use(Byte.BYTES + bytes.length, keyByteBuffer -> {
            keyByteBuffer.put(VariableKeyType.DIRECT.getPrimitiveValue());
            keyByteBuffer.put(bytes);
            keyByteBuffer.flip();
            return function.apply(txn, keyByteBuffer);
        });
    }

    private <R> R useIdByteBuffer(final Txn<ByteBuffer> txn,
                                  final ByteBuffer idByteBuffer,
                                  final BiFunction<Txn<ByteBuffer>, ByteBuffer, R> function) {
        return byteBuffers.use(Byte.BYTES + idByteBuffer.limit(), keyByteBuffer -> {
            keyByteBuffer.put(VariableKeyType.LOOKUP.getPrimitiveValue());
            keyByteBuffer.put(idByteBuffer);
            keyByteBuffer.flip();
            return function.apply(txn, keyByteBuffer);
        });
    }

    @Override
    public void insert(final LmdbWriter writer, final KV<String, StateValue> kv) {
        useVariableKey(writer.getWriteTxn(), kv.key(), (txn, keyByteBuffer) -> {
            stateValueSerde.write(kv.val(), valueByteBuffer -> {
                if (dbi.put(txn, keyByteBuffer, valueByteBuffer, putFlags)) {
                    writer.tryCommit();
                }
            });
            return null;
        }, false);
    }

    @Override
    public StateValue get(final String key) {
        return env.read(readTxn -> useVariableKey(readTxn, key, (txn, keyByteBuffer) -> {
            final ByteBuffer valueByteBuffer = dbi.get(txn, keyByteBuffer);
            if (valueByteBuffer == null) {
                return null;
            }
            return stateValueSerde.read(valueByteBuffer);
        }, true));
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
                        final byte firstByte = keyVal.key().get(0);
                        final VariableKeyType keyType =
                                VariableKeyType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(firstByte);
                        if (Objects.requireNonNull(keyType) == VariableKeyType.LOOKUP) {// Omit the key type.
                            final ByteBuffer slice = keyVal.key().slice(1, keyVal.key().limit());
                            sourceKeyLookup.get(writer.getWriteTxn(), slice, optionalRealKey -> {
                                final ByteBuffer realKey = optionalRealKey.orElseThrow(() ->
                                        new RuntimeException("Unable to retrieve source key"));
                                keyLookup.put(writer.getWriteTxn(), realKey, keyIdBuffer -> {
                                    byteBuffers.use(Byte.BYTES + keyIdBuffer.limit(), keyByteBuffer -> {
                                        keyByteBuffer.put(VariableKeyType.LOOKUP.getPrimitiveValue());
                                        keyByteBuffer.put(keyIdBuffer);
                                        keyByteBuffer.flip();
                                        if (dbi.put(writer.getWriteTxn(), keyByteBuffer, keyVal.val(), putFlags)) {
                                            writer.tryCommit();
                                        }
                                    });
                                    return null;
                                });
                                return null;
                            });
                        } else {
                            if (dbi.put(writer.getWriteTxn(), keyVal.key(), keyVal.val(), putFlags)) {
                                writer.tryCommit();
                            }
                        }
                    }
                }
                return null;
            });
        });

        // Delete source now we have merged.
        FileUtil.deleteDir(source);
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
            final ByteBuffer key = context.kv().key().duplicate();
            final byte firstByte = key.get();
            final VariableKeyType keyType =
                    VariableKeyType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(firstByte);
            return switch (keyType) {
                case DIRECT -> ValString.create(ByteBufferUtils.toString(key));
                case LOOKUP -> {
                    final ByteBuffer byteBuffer = keyLookup.getValue(context.readTxn(), key);
                    if (byteBuffer == null) {
                        throw new RuntimeException("Unable to find key");
                    }
                    yield ValString.create(ByteBufferUtils.toString(byteBuffer));
                }
            };
        };
    }

    private Function<Context, StateValue> getStateValueExtractionFunction() {
        return context -> stateValueSerde.read(context.kv().val());
    }
}
