package stroom.planb.impl.db.serde;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.query.language.functions.Type;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValShort;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class ShortValSerde implements ValSerde {

    private final ByteBuffer reusableWriteBuffer = ByteBuffer.allocateDirect(Short.BYTES);
    private final ByteBuffers byteBuffers;

    public ShortValSerde(final ByteBuffers byteBuffers) {
        this.byteBuffers = byteBuffers;
    }

    @Override
    public Val toVal(final Txn<ByteBuffer> readTxn, final ByteBuffer byteBuffer) {
        final short s = byteBuffer.getShort();
        return ValShort.create(s);
    }

    @Override
    public void toBuffer(final Txn<ByteBuffer> writeTxn, final Val value, final Consumer<ByteBuffer> consumer) {
//        byteBuffers.use(Short.BYTES, byteBuffer -> {
//            byteBuffer.putShort();
//            byteBuffer.flip();
//            consumer.accept(byteBuffer);
//        })

        // We are in a single write transaction so should be able to reuse the same buffer again and again.
        reusableWriteBuffer.putShort(0, getShort(value));
        consumer.accept(reusableWriteBuffer);
    }

    @Override
    public Val toBufferForGet(final Txn<ByteBuffer> txn,
                              final Val value,
                              final Function<Optional<ByteBuffer>, Val> function) {
        return byteBuffers.use(Short.BYTES, byteBuffer -> {
            byteBuffer.putShort(getShort(value));
            byteBuffer.flip();
            return function.apply(Optional.of(byteBuffer));
        });
    }

    private short getShort(final Val value) {
        try {
            if (Type.SHORT.equals(value.type())) {
                final ValShort valShort = (ValShort) value;
                return valShort.getValue();
            } else {
                return Short.parseShort(value.toString());
            }
        } catch (final NumberFormatException | NullPointerException e) {
            throw new RuntimeException("Expected state key to be a short but could not parse '" +
                                       value +
                                       "' as short");
        }
    }
}
