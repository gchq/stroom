package stroom.planb.impl.db.serde;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.db.LookupDb;
import stroom.planb.impl.db.serde.ValSerdeUtil.Addition;
import stroom.query.language.functions.Val;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class VariableValSerde implements ValSerde {

    private static final int USE_LOOKUP_THRESHOLD = 32;

    private final LookupDb lookupDb;
    private final ByteBuffers byteBuffers;

    public VariableValSerde(final LookupDb lookupDb, final ByteBuffers byteBuffers) {
        this.lookupDb = lookupDb;
        this.byteBuffers = byteBuffers;
    }

    @Override
    public Val read(final Txn<ByteBuffer> readTxn, final ByteBuffer byteBuffer) {
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
    public void write(final Txn<ByteBuffer> writeTxn, final Val value, final Consumer<ByteBuffer> consumer) {
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
}
