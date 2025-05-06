package stroom.planb.impl.db.state;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.entity.shared.ExpressionCriteria;
import stroom.lmdb.LmdbConfig;
import stroom.lmdb.serde.UnsignedBytes;
import stroom.lmdb.serde.UnsignedBytesInstances;
import stroom.lmdb2.KV;
import stroom.planb.impl.db.LmdbWriter;
import stroom.planb.impl.db.LookupDb;
import stroom.planb.impl.db.ValUtil;
import stroom.planb.impl.db.hash.Hash;
import stroom.planb.impl.db.hash.HashClashCount;
import stroom.planb.impl.db.hash.HashFactory;
import stroom.planb.impl.db.hash.HashFactoryFactory;
import stroom.planb.shared.HashLength;
import stroom.planb.shared.StateKeySchema;
import stroom.planb.shared.StateSettings;
import stroom.query.api.DateTimeSettings;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ValDouble;
import stroom.query.language.functions.ValFloat;
import stroom.query.language.functions.ValInteger;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.ValuesConsumer;
import stroom.util.io.FileUtil;
import stroom.util.shared.NullSafe;

import net.openhft.hashing.LongHashFunction;
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

/**
 * A schema that tries a number of different approaches to store keys in the most efficient way depending on their type.
 */
class AutoKeySchema extends AbstractSchema<String, StateValue> {

    private final StateSettings settings;
    private final PutFlags[] putFlags;
    private final HashFactory hashFactory;
    private final HashClashCount hashClashCount;
    private final LookupDb keyLookup;
    private final StateValueSerde stateValueSerde;
    private final HashedKeySupport hashedKeySupport;

    public AutoKeySchema(final PlanBEnv env,
                         final ByteBuffers byteBuffers,
                         final StateSettings settings,
                         final HashClashCount hashClashCount) {
        super(env, byteBuffers);
        this.settings = settings;

        hashFactory = HashFactoryFactory.create(NullSafe.get(
                settings,
                StateSettings::getStateKeySchema,
                StateKeySchema::getHashLength));
        this.hashClashCount = hashClashCount;

        if (settings.getStateKeySchema().isDeduplicateLargeKeys()) {
            keyLookup = new LookupDb(
                    env,
                    byteBuffers,
                    hashFactory,
                    hashClashCount,
                    "keys",
                    settings.overwrite());
        } else {
            keyLookup = null;
        }
        final boolean overwrite = settings.overwrite();
        this.putFlags = overwrite
                ? new PutFlags[]{}
                : new PutFlags[]{PutFlags.MDB_NOOVERWRITE};
        stateValueSerde = new StateValueSerde(byteBuffers);

        // Create a special hash factory for prefixing hashes with our auto key type.
        final HashFactory hashedKeyHashFactory = AutoKeyHashFactoryFactory
                .create(settings.getStateKeySchema().getHashLength());
        hashedKeySupport = new HashedKeySupport(
                env,
                dbi,
                byteBuffers,
                hashedKeyHashFactory,
                overwrite,
                hashClashCount);
    }


    private <R> R useAutoKey(final Txn<ByteBuffer> txn,
                             final String key,
                             final BiFunction<Txn<ByteBuffer>, ByteBuffer, R> function,
                             final boolean read) throws UseHashException {
        Objects.requireNonNull(key, "Key is null");
        if (!key.isEmpty()) {
            final char firstChar = key.charAt(0);
            if (Character.isDigit(firstChar) || firstChar == '-' || firstChar == '+') {
                if (!key.contains(".")) {
                    try {
                        final long l = Long.parseLong(key);
                        if (l == 0) {
                            // Zero is a special case and only requires the type be stored.
                            return byteBuffers.use(Byte.BYTES, keyByteBuffer -> {
                                keyByteBuffer.put(AutoKeyType.ZERO.getPrimitiveValue());
                                keyByteBuffer.flip();
                                return function.apply(txn, keyByteBuffer);
                            });
                        } else if (l > 0) {
                            // We have a positive integer, so we can store unsigned with a variable byte length.
                            final UnsignedBytes unsignedBytes = UnsignedBytesInstances.forValue(l);
                            return byteBuffers.use(Byte.BYTES + unsignedBytes.length(), keyByteBuffer -> {
                                keyByteBuffer.put(AutoKeyType.UNSIGNED.getPrimitiveValue());
                                unsignedBytes.put(keyByteBuffer, l);
                                keyByteBuffer.flip();
                                return function.apply(txn, keyByteBuffer);
                            });
                        } else if (l >= Byte.MIN_VALUE) {
                            // We have a negative byte.
                            return byteBuffers.use(Byte.BYTES + Byte.BYTES, keyByteBuffer -> {
                                keyByteBuffer.put(AutoKeyType.BYTE.getPrimitiveValue());
                                keyByteBuffer.put((byte) l);
                                keyByteBuffer.flip();
                                return function.apply(txn, keyByteBuffer);
                            });
                        } else if (l >= Short.MIN_VALUE) {
                            // We have a negative short.
                            return byteBuffers.use(Byte.BYTES + Short.BYTES, keyByteBuffer -> {
                                keyByteBuffer.put(AutoKeyType.SHORT.getPrimitiveValue());
                                keyByteBuffer.putShort((short) l);
                                keyByteBuffer.flip();
                                return function.apply(txn, keyByteBuffer);
                            });
                        } else if (l >= Integer.MIN_VALUE) {
                            // We have a negative integer.
                            return byteBuffers.use(Byte.BYTES + Integer.BYTES, keyByteBuffer -> {
                                keyByteBuffer.put(AutoKeyType.INT.getPrimitiveValue());
                                keyByteBuffer.putInt((int) l);
                                keyByteBuffer.flip();
                                return function.apply(txn, keyByteBuffer);
                            });
                        } else {
                            // All other negative integers should be stored as negative longs.
                            return byteBuffers.use(Byte.BYTES + Long.BYTES, keyByteBuffer -> {
                                keyByteBuffer.put(AutoKeyType.LONG.getPrimitiveValue());
                                keyByteBuffer.putLong(l);
                                keyByteBuffer.flip();
                                return function.apply(txn, keyByteBuffer);
                            });
                        }

                    } catch (final NumberFormatException e) {
                        // Ignore.
                    }
                }

                // Try double or float.
                try {
                    final double d = Double.parseDouble(key);
                    final float f = (float) d;
                    // If there is no reduction in precision using a float then use a float.
                    if ((double) f == d) {
                        return byteBuffers.use(Byte.BYTES + Float.BYTES, keyByteBuffer -> {
                            keyByteBuffer.put(AutoKeyType.FLOAT.getPrimitiveValue());
                            keyByteBuffer.putFloat(f);
                            keyByteBuffer.flip();
                            return function.apply(txn, keyByteBuffer);
                        });
                    } else {
                        return byteBuffers.use(Byte.BYTES + Double.BYTES, keyByteBuffer -> {
                            keyByteBuffer.put(AutoKeyType.DOUBLE.getPrimitiveValue());
                            keyByteBuffer.putDouble(d);
                            keyByteBuffer.flip();
                            return function.apply(txn, keyByteBuffer);
                        });
                    }
                } catch (final NumberFormatException e) {
                    // Ignore.
                }
            }
        }

        // The string is not a number so just use the bytes.
        byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
        if (keyLookup != null && bytes.length > settings.getStateKeySchema().getDeduplicateThreshold()) {
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

        if (bytes.length > 510) {
            throw new UseHashException();
        }

        // Just use the bytes as the key directly.
        return byteBuffers.use(Byte.BYTES + bytes.length, keyByteBuffer -> {
            keyByteBuffer.put(AutoKeyType.BYTES.getPrimitiveValue());
            keyByteBuffer.put(bytes);
            keyByteBuffer.flip();
            return function.apply(txn, keyByteBuffer);
        });
    }

    private <R> R useIdByteBuffer(final Txn<ByteBuffer> txn,
                                  final ByteBuffer idByteBuffer,
                                  final BiFunction<Txn<ByteBuffer>, ByteBuffer, R> function) {
        return byteBuffers.use(Byte.BYTES + idByteBuffer.limit(), keyByteBuffer -> {
            keyByteBuffer.put(AutoKeyType.LOOKUP.getPrimitiveValue());
            keyByteBuffer.put(idByteBuffer);
            keyByteBuffer.flip();
            return function.apply(txn, keyByteBuffer);
        });
    }

    private static class UseHashException extends Exception {

    }

    @Override
    public void insert(final LmdbWriter writer, final KV<String, StateValue> kv) {
        try {
            useAutoKey(writer.getWriteTxn(), kv.key(), (txn, keyByteBuffer) -> {
                stateValueSerde.write(kv.val(), valueByteBuffer -> {
                    if (dbi.put(txn, keyByteBuffer, valueByteBuffer, putFlags)) {
                        writer.tryCommit();
                    }
                });
                return null;
            }, false);
        } catch (final UseHashException e) {
            hashedKeySupport.insert(writer, kv.key(), kv.val());
        }
    }

    @Override
    public StateValue get(final String key) {
        return env.read(readTxn -> {
            try {
                return useAutoKey(readTxn, key, (txn, keyByteBuffer) -> {
                    final ByteBuffer valueByteBuffer = dbi.get(txn, keyByteBuffer);
                    if (valueByteBuffer == null) {
                        return null;
                    }
                    return stateValueSerde.read(valueByteBuffer);
                }, true);
            } catch (final UseHashException e) {
                return hashedKeySupport.get(key);
            }
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
                        final byte firstByte = keyVal.key().get(0);
                        final AutoKeyType autoKeyType =
                                AutoKeyType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(firstByte);
                        switch (autoKeyType) {
                            case HASH -> hashedKeySupport.merge(writer, keyVal);
                            case LOOKUP -> {
                                // Omit the key type.
                                final ByteBuffer slice = keyVal.key().slice(1, keyVal.key().limit());
                                sourceKeyLookup.get(writer.getWriteTxn(), slice, optionalRealKey -> {
                                    final ByteBuffer realKey = optionalRealKey.orElseThrow(() ->
                                            new RuntimeException("Unable to retrieve source key"));
                                    keyLookup.put(writer.getWriteTxn(), realKey, keyIdBuffer -> {
                                        byteBuffers.use(Byte.BYTES + keyIdBuffer.limit(), keyByteBuffer -> {
                                            keyByteBuffer.put(AutoKeyType.LOOKUP.getPrimitiveValue());
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
                            }
                            default -> {
                                if (dbi.put(writer.getWriteTxn(), keyVal.key(), keyVal.val(), putFlags)) {
                                    writer.tryCommit();
                                }
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
        StateSearchHelper.search(
                criteria,
                fieldIndex,
                dateTimeSettings,
                expressionPredicateFactory,
                consumer,
                getValExtractors(fieldIndex),
                env,
                dbi);
    }

    public ValExtractor[] getValExtractors(final FieldIndex fieldIndex) {
        final ValExtractor[] extractors = new ValExtractor[fieldIndex.size()];
        for (int i = 0; i < fieldIndex.getFields().length; i++) {
            final String field = fieldIndex.getField(i);
            extractors[i] = switch (field) {
                case StateFields.KEY -> (readTxn, kv) -> {

                    final ByteBuffer key = kv.key().duplicate();
                    final byte firstByte = key.get();
                    final AutoKeyType autoKeyType =
                            AutoKeyType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(firstByte);
                    switch (autoKeyType) {
                        case ZERO -> {
                            return ValInteger.create(0);
                        }
                        case BYTE -> {
                            return ValInteger.create(key.get());
                        }
                        case SHORT -> {
                            return ValInteger.create(key.getShort());
                        }
                        case INT -> {
                            return ValInteger.create(key.getInt());
                        }
                        case LONG -> {
                            return ValLong.create(key.getLong());
                        }
                        case FLOAT -> {
                            return ValFloat.create(key.getFloat());
                        }
                        case DOUBLE -> {
                            return ValDouble.create(key.getDouble());
                        }
                        case UNSIGNED -> {
                            final UnsignedBytes unsignedBytes =
                                    UnsignedBytesInstances.ofLength(key.limit() - Byte.BYTES);
                            return ValLong.create(unsignedBytes.get(key));
                        }
                        case BYTES -> {
                            return ValString.create(ByteBufferUtils.toString(key));
                        }
                        case HASH -> {
                            final int keyLength = kv.val().getInt(0);
                            return ValString.create(ByteBufferUtils.toString(kv.val().slice(Integer.BYTES, keyLength)));
                        }
                        case LOOKUP -> {
                            final ByteBuffer byteBuffer = keyLookup.getValue(readTxn, key);
                            if (byteBuffer == null) {
                                throw new RuntimeException("Unable to find key");
                            }
                            return ValString.create(ByteBufferUtils.toString(byteBuffer));
                        }
                        default -> throw new RuntimeException("Unexpected auto key type " + autoKeyType);
                    }

                };
                case StateFields.VALUE_TYPE -> (readTxn, kv) -> {
                    final ByteBuffer key = kv.key().duplicate();
                    final ByteBuffer value = kv.val().duplicate();
                    final byte firstByte = key.get();
                    final AutoKeyType autoKeyType =
                            AutoKeyType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(firstByte);
                    if (AutoKeyType.HASH.equals(autoKeyType)) {
                        final int keyLength = value.getInt(0);
                        final int valueStart = Integer.BYTES + keyLength;
                        return ValUtil.getType(value.slice(valueStart, value.limit() - valueStart));
                    }

                    // If we aren't using a hash key then the value is just the value.
                    return ValUtil.getType(value);

                };
                case StateFields.VALUE -> (readTxn, kv) -> {
                    final ByteBuffer key = kv.key().duplicate();
                    final ByteBuffer value = kv.val().duplicate();
                    final byte firstByte = key.get();
                    final AutoKeyType autoKeyType =
                            AutoKeyType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(firstByte);
                    if (AutoKeyType.HASH.equals(autoKeyType)) {
                        final int keyLength = value.getInt(0);
                        final int valueStart = Integer.BYTES + keyLength;
                        return ValUtil.getValue(value.slice(valueStart, value.limit() - valueStart));
                    }

                    // If we aren't using a hash key then the value is just the value.
                    return ValUtil.getValue(value);
                };
                default -> (readTxn, kv) -> ValNull.INSTANCE;
            };
        }
        return extractors;
    }

    public static class AutoKeyHashFactoryFactory {

        public static HashFactory create(final HashLength hashLength) {
            if (hashLength == null || HashLength.LONG.equals(hashLength)) {
                return new AutoKeyLongHashFactory();
            }
            return new AutoKeyIntegerHashFactory();
        }
    }

    public static class AutoKeyLongHashFactory implements HashFactory {

        @Override
        public Hash create(final byte[] bytes) {
            return new AutoKeyLongHash(LongHashFunction.xx3().hashBytes(bytes));
        }

        @Override
        public Hash create(final ByteBuffer byteBuffer) {
            return create(ByteBufferUtils.toBytes(byteBuffer));
        }

        @Override
        public int hashLength() {
            return Long.BYTES;
        }

        private static class AutoKeyLongHash implements Hash {

            private final long hash;

            public AutoKeyLongHash(final long hash) {
                this.hash = hash;
            }

            @Override
            public void write(final ByteBuffer byteBuffer) {
                byteBuffer.put(AutoKeyType.HASH.getPrimitiveValue());
                byteBuffer.putLong(hash);
            }

            @Override
            public int len() {
                return Byte.BYTES + Long.BYTES;
            }
        }
    }

    public static class AutoKeyIntegerHashFactory implements HashFactory {

        @Override
        public Hash create(final byte[] bytes) {
            return new AutoKeyIntegerHash(Long.hashCode(LongHashFunction.xx3().hashBytes(bytes)));
        }

        @Override
        public Hash create(final ByteBuffer byteBuffer) {
            return create(ByteBufferUtils.toBytes(byteBuffer));
        }

        @Override
        public int hashLength() {
            return Integer.BYTES;
        }

        private static class AutoKeyIntegerHash implements Hash {

            private final int hash;

            public AutoKeyIntegerHash(final int hash) {
                this.hash = hash;
            }

            @Override
            public void write(final ByteBuffer byteBuffer) {
                byteBuffer.put(AutoKeyType.HASH.getPrimitiveValue());
                byteBuffer.putInt(hash);
            }

            @Override
            public int len() {
                return Byte.BYTES + Integer.BYTES;
            }
        }
    }
}
