package stroom.planb.impl.db.serde;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.query.language.functions.Type;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValByte;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class ByteValSerde implements ValSerde {

    private final ByteBuffer reusableWriteBuffer = ByteBuffer.allocateDirect(Byte.BYTES);
    private final ByteBuffers byteBuffers;

    public ByteValSerde(final ByteBuffers byteBuffers) {
        this.byteBuffers = byteBuffers;
    }

    @Override
    public Val toVal(final Txn<ByteBuffer> readTxn, final ByteBuffer byteBuffer) {
        final byte b = byteBuffer.get();
        return ValByte.create(b);
    }

    @Override
    public void toBuffer(final Txn<ByteBuffer> writeTxn, final Val value, final Consumer<ByteBuffer> consumer) {
//        byteBuffers.use(Byte.BYTES, byteBuffer -> {
//            byteBuffer.put(getByte(value));
//            byteBuffer.flip();
//            consumer.accept(byteBuffer);
//        });

        // We are in a single write transaction so should be able to reuse the same buffer again and again.
        reusableWriteBuffer.put(0, getByte(value));
        consumer.accept(reusableWriteBuffer);
    }

    @Override
    public Val toBufferForGet(final Txn<ByteBuffer> txn,
                              final Val value,
                              final Function<Optional<ByteBuffer>, Val> function) {
        return byteBuffers.use(Byte.BYTES, byteBuffer -> {
            byteBuffer.put(getByte(value));
            byteBuffer.flip();
            return function.apply(Optional.of(byteBuffer));
        });
    }

    private byte getByte(final Val value) {
        try {
            if (Type.BYTE.equals(value.type())) {
                final ValByte valByte = (ValByte) value;
                return valByte.getValue();
            } else {
                return Byte.parseByte(value.toString());
            }
        } catch (final NumberFormatException | NullPointerException e) {
            throw new RuntimeException("Expected state key to be a byte but could not parse '" +
                                       value +
                                       "' as byte");
        }
    }
}
