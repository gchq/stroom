package stroom.planb.impl.db.serde;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.db.LookupDb;
import stroom.planb.impl.db.serde.ValSerdeUtil.Addition;
import stroom.query.language.functions.Val;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class VariableValSerde implements ValSerde {

    private static final int USE_LOOKUP_THRESHOLD = 32;

    private final LookupDb lookupDb;
    private final ByteBuffers byteBuffers;

    public VariableValSerde(final LookupDb lookupDb, final ByteBuffers byteBuffers) {
        this.lookupDb = lookupDb;
        this.byteBuffers = byteBuffers;
    }

    @Override
    public Val toVal(final Txn<ByteBuffer> readTxn, final ByteBuffer byteBuffer) {
        // Read the variable type.
        final byte b = byteBuffer.get();
        if (b == VariableValType.DIRECT.getPrimitiveValue()) {
            // Read direct.
            return ValSerdeUtil.read(byteBuffer);
        }

        // Read via lookup.
        final ByteBuffer valueByteBuffer = lookupDb.getValue(readTxn, byteBuffer);
        return ValSerdeUtil.read(valueByteBuffer);
    }

    @Override
    public void toBuffer(final Txn<ByteBuffer> writeTxn, final Val value, final Consumer<ByteBuffer> consumer) {
        ValSerdeUtil.write(value, byteBuffers, valueByteBuffer -> {
            if (valueByteBuffer.remaining() > USE_LOOKUP_THRESHOLD) {
                // We are going to store as a lookup so take off the variable type prefix.
                final ByteBuffer slice = valueByteBuffer.slice(1, valueByteBuffer.remaining() - 1);
                lookupDb.put(writeTxn, slice, idByteBuffer -> {
                    byteBuffers.use(valueByteBuffer.remaining() + 1, prefixedBuffer -> {
                        // Add the variable type prefix to the lookup id.
                        prefixedBuffer.put(VariableValType.LOOKUP.getPrimitiveValue());
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
        }, new Addition(1, bb -> bb.put(VariableValType.DIRECT.getPrimitiveValue())), Addition.NONE);
    }

    @Override
    public Val toBufferForGet(final Txn<ByteBuffer> txn,
                              final Val value,
                              final Function<Optional<ByteBuffer>, Val> function) {
        return ValSerdeUtil.write(value, byteBuffers, valueByteBuffer -> {
            if (valueByteBuffer.remaining() > USE_LOOKUP_THRESHOLD) {
                // We are going to store as a lookup so take off the variable type prefix.
                final ByteBuffer slice = valueByteBuffer.slice(1, valueByteBuffer.remaining() - 1);
                return lookupDb.get(txn, slice, optionalIdByteBuffer ->
                        optionalIdByteBuffer
                                .map(idByteBuffer ->
                                        byteBuffers.use(valueByteBuffer.remaining() + 1, prefixedBuffer -> {
                                            // Add the variable type prefix to the lookup id.
                                            prefixedBuffer.put(VariableValType.LOOKUP.getPrimitiveValue());
                                            prefixedBuffer.put(idByteBuffer);
                                            prefixedBuffer.flip();
                                            return function.apply(Optional.of(prefixedBuffer));
                                        }))
                                .orElse(null));
            } else {
                // We have already added the direct variable prefix so just use the byte buffer.
                return function.apply(Optional.of(valueByteBuffer));
            }
        }, new Addition(1, bb -> bb.put(VariableValType.DIRECT.getPrimitiveValue())), Addition.NONE);
    }
}
