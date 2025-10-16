package stroom.planb.impl.serde.keyprefix;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.db.Db;
import stroom.planb.impl.db.HashLookupDb;
import stroom.planb.impl.db.PlanBEnv;
import stroom.planb.impl.db.UidLookupDb;
import stroom.planb.impl.db.UsedLookupsRecorder;
import stroom.planb.impl.db.VariableUsedLookupsRecorder;
import stroom.planb.impl.serde.val.ValSerdeUtil;
import stroom.planb.impl.serde.val.ValSerdeUtil.Addition;
import stroom.planb.impl.serde.val.VariableValType;
import stroom.query.language.functions.Val;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class VariableKeySerde implements KeyPrefixSerde {

    private static final int USE_HASH_LOOKUP_THRESHOLD = Db.MAX_KEY_LENGTH;

    private final int uidLookupThreshold;
    private final UidLookupDb uidLookupDb;
    private final HashLookupDb hashLookupDb;
    private final ByteBuffers byteBuffers;

    public VariableKeySerde(final UidLookupDb uidLookupDb,
                            final HashLookupDb hashLookupDb,
                            final ByteBuffers byteBuffers) {
        this.uidLookupDb = uidLookupDb;
        this.hashLookupDb = hashLookupDb;
        this.byteBuffers = byteBuffers;
        uidLookupThreshold = 32;
    }

    @Override
    public KeyPrefix read(final Txn<ByteBuffer> txn, final ByteBuffer byteBuffer) {
        // Read the variable type.
        final VariableValType valType = VariableValType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(byteBuffer.get());
        final Val val = switch (valType) {
            case DIRECT -> {
                // Read direct.
                yield ValSerdeUtil.read(byteBuffer);
            }
            case UID_LOOKUP -> {
                // Read via UI lookup.
                final ByteBuffer valueByteBuffer = uidLookupDb.getValue(txn, byteBuffer);
                yield ValSerdeUtil.read(valueByteBuffer);
            }
            case HASH_LOOKUP -> {
                // Read via hash lookup.
                final ByteBuffer valueByteBuffer = hashLookupDb.getValue(txn, byteBuffer);
                yield ValSerdeUtil.read(valueByteBuffer);
            }
        };

        return KeyPrefix.create(val);
    }

    @Override
    public void write(final Txn<ByteBuffer> txn, final KeyPrefix key, final Consumer<ByteBuffer> consumer) {
        final Addition prefix = new Addition(1, bb -> bb.put(VariableValType.DIRECT.getPrimitiveValue()));
        final Addition suffix = Addition.NONE;

        ValSerdeUtil.write(key.getVal(), byteBuffers, valueByteBuffer -> {
            if (valueByteBuffer.remaining() > USE_HASH_LOOKUP_THRESHOLD) {
                // We are going to store as a lookup so take off the variable type prefix.
                final ByteBuffer valueSlice = getValueSlice(valueByteBuffer);
                hashLookupDb.put(txn, valueSlice, idByteBuffer -> {
                    byteBuffers.use(idByteBuffer.remaining() + 1, prefixedBuffer -> {
                        // Add the variable type prefix to the lookup id.
                        prefixedBuffer.put(VariableValType.HASH_LOOKUP.getPrimitiveValue());
                        prefixedBuffer.put(idByteBuffer);
                        prefixedBuffer.flip();
                        consumer.accept(prefixedBuffer);
                    });
                    return null;
                });
            } else if (valueByteBuffer.remaining() > uidLookupThreshold) {
                // We are going to store as a lookup so take off the variable type prefix.
                final ByteBuffer valueSlice = getValueSlice(valueByteBuffer);
                uidLookupDb.put(txn, valueSlice, idByteBuffer -> {
                    byteBuffers.use(idByteBuffer.remaining() + 1, prefixedBuffer -> {
                        // Add the variable type prefix to the lookup id.
                        prefixedBuffer.put(VariableValType.UID_LOOKUP.getPrimitiveValue());
                        prefixedBuffer.put(idByteBuffer);
                        prefixedBuffer.flip();
                        consumer.accept(prefixedBuffer);
                    });
                    return null;
                });
            } else {
                // We have already added the direct variable prefix so just use the byte buffer.
                consumer.accept(valueByteBuffer);
            }
            return null;
        }, prefix, suffix);
    }

    @Override
    public <R> R toBufferForGet(final Txn<ByteBuffer> txn,
                                final KeyPrefix key,
                                final Function<Optional<ByteBuffer>, R> function) {
        final Addition prefix = new Addition(1, bb -> bb.put(VariableValType.DIRECT.getPrimitiveValue()));
        final Addition suffix = Addition.NONE;

        return ValSerdeUtil.write(key.getVal(), byteBuffers, valueByteBuffer -> {
            if (valueByteBuffer.remaining() > USE_HASH_LOOKUP_THRESHOLD) {
                // We are going to store as a lookup so take off the variable type prefix.
                final ByteBuffer valueSlice = getValueSlice(valueByteBuffer);
                return hashLookupDb.get(txn, valueSlice, optionalIdByteBuffer ->
                        optionalIdByteBuffer
                                .map(idByteBuffer ->
                                        byteBuffers.use(idByteBuffer.remaining() + 1,
                                                prefixedBuffer -> {
                                                    // Add the variable type prefix to the lookup id.
                                                    prefixedBuffer.put(VariableValType.HASH_LOOKUP.getPrimitiveValue());
                                                    prefixedBuffer.put(idByteBuffer);
                                                    prefixedBuffer.flip();
                                                    return function.apply(Optional.of(prefixedBuffer));
                                                }))
                                .orElse(null));
            } else if (valueByteBuffer.remaining() > uidLookupThreshold) {
                // We are going to store as a lookup so take off the variable type prefix.
                final ByteBuffer valueSlice = getValueSlice(valueByteBuffer);
                return uidLookupDb.get(txn, valueSlice, optionalIdByteBuffer ->
                        optionalIdByteBuffer
                                .map(idByteBuffer ->
                                        byteBuffers.use(idByteBuffer.remaining() + 1,
                                                prefixedBuffer -> {
                                                    // Add the variable type prefix to the lookup id.
                                                    prefixedBuffer.put(VariableValType.UID_LOOKUP.getPrimitiveValue());
                                                    prefixedBuffer.put(idByteBuffer);
                                                    prefixedBuffer.flip();
                                                    return function.apply(Optional.of(prefixedBuffer));
                                                }))
                                .orElse(null));
            } else {
                // We have already added the direct variable prefix so just use the byte buffer.
                return function.apply(Optional.of(valueByteBuffer));
            }
        }, prefix, suffix);
    }

    private ByteBuffer getValueSlice(final ByteBuffer byteBuffer) {
        return byteBuffer.slice(1, byteBuffer.remaining() - 1);
    }

    @Override
    public boolean usesLookup(final ByteBuffer byteBuffer) {
        // Read the variable type.
        final VariableValType valType = VariableValType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(byteBuffer.get(0));
        return !VariableValType.DIRECT.equals(valType);
    }

    @Override
    public UsedLookupsRecorder getUsedLookupsRecorder(final PlanBEnv env) {
        return new VariableUsedLookupsRecorder(env, uidLookupDb, hashLookupDb);
    }
}
