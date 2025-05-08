package stroom.planb.impl.db.state;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.query.language.functions.Type;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValByte;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class ByteValSerde implements ValSerde {

    private final ByteBuffers byteBuffers;

    public ByteValSerde(final ByteBuffers byteBuffers) {
        this.byteBuffers = byteBuffers;
    }

    @Override
    public Val read(final Txn<ByteBuffer> readTxn, final ByteBuffer byteBuffer) {
        final byte b = byteBuffer.get();
        return ValByte.create(b);
    }

    @Override
    public void write(final Txn<ByteBuffer> writeTxn, final Val value, final Consumer<ByteBuffer> consumer) {
        byteBuffers.use(Byte.BYTES, byteBuffer -> {
            try {
                if (Type.BYTE.equals(value.type())) {
                    final ValByte valByte = (ValByte) value;
                    byteBuffer.put(valByte.getValue());
                } else {
                    final byte b = Byte.parseByte(value.toString());
                    byteBuffer.put(b);
                }
            } catch (final NumberFormatException e) {
                throw new RuntimeException("Expected state key to be a byte but could not parse '" +
                                           value +
                                           "' as byte");
            }
            byteBuffer.flip();
            consumer.accept(byteBuffer);
        });
    }
}
