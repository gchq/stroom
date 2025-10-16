package stroom.planb.impl.serde.val;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.db.Db;
import stroom.planb.impl.db.HashLookupDb;
import stroom.planb.impl.db.PlanBEnv;
import stroom.planb.impl.db.UidLookupDb;
import stroom.planb.impl.db.UsedLookupsRecorder;
import stroom.planb.impl.db.VariableUsedLookupsRecorder;
import stroom.planb.impl.serde.val.ValSerdeUtil.Addition;
import stroom.query.language.functions.Val;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class VariableValSerde implements ValSerde {

    private static final int USE_UID_LOOKUP_THRESHOLD = 32;
    private static final int USE_HASH_LOOKUP_THRESHOLD = Db.MAX_KEY_LENGTH;

    private final UidLookupDb uidLookupDb;
    private final HashLookupDb hashLookupDb;
    private final ByteBuffers byteBuffers;

    public VariableValSerde(final UidLookupDb uidLookupDb,
                            final HashLookupDb hashLookupDb,
                            final ByteBuffers byteBuffers) {
        this.uidLookupDb = uidLookupDb;
        this.hashLookupDb = hashLookupDb;
        this.byteBuffers = byteBuffers;
    }

    @Override
    public Val read(final Txn<ByteBuffer> txn, final ByteBuffer byteBuffer) {
        // Read the variable type.
        final VariableValType valType = VariableValType.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(byteBuffer.get());
        return switch (valType) {
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
    }

    @Override
    public void write(final Txn<ByteBuffer> txn, final Val val, final Consumer<ByteBuffer> consumer) {
        final Addition prefix = new Addition(1, bb -> bb.put(VariableValType.DIRECT.getPrimitiveValue()));
        final Addition suffix = Addition.NONE;

        ValSerdeUtil.write(val, byteBuffers, valueByteBuffer -> {
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
            } else if (valueByteBuffer.remaining() > USE_UID_LOOKUP_THRESHOLD) {
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
