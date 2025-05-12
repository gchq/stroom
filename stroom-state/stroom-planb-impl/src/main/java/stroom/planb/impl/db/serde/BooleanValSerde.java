package stroom.planb.impl.db.serde;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.query.language.functions.Type;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValBoolean;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class BooleanValSerde implements ValSerde {

    private final ByteBuffer reusableWriteBuffer = ByteBuffer.allocateDirect(Byte.BYTES);
    private final ByteBuffers byteBuffers;

    public BooleanValSerde(final ByteBuffers byteBuffers) {
        this.byteBuffers = byteBuffers;
    }

    @Override
    public Val toVal(final Txn<ByteBuffer> readTxn, final ByteBuffer byteBuffer) {
        final byte b = byteBuffer.get();
        return ValBoolean.create(b != 0);
    }

    @Override
    public void toBuffer(final Txn<ByteBuffer> writeTxn, final Val value, final Consumer<ByteBuffer> consumer) {
//        byteBuffers.use(Byte.BYTES, byteBuffer -> {
//            byteBuffer.put(getBoolean(value)
//                    ? (byte) 1
//                    : (byte) 0);
//            byteBuffer.flip();
//            consumer.accept(byteBuffer);
//        });

        // We are in a single write transaction so should be able to reuse the same buffer again and again.
        reusableWriteBuffer.put(0, getBoolean(value)
                ? (byte) 1
                : (byte) 0);
        consumer.accept(reusableWriteBuffer);
    }

    @Override
    public Val toBufferForGet(final Txn<ByteBuffer> txn,
                              final Val value,
                              final Function<Optional<ByteBuffer>, Val> function) {
        return byteBuffers.use(Byte.BYTES, byteBuffer -> {
            byteBuffer.put(getBoolean(value)
                    ? (byte) 1
                    : (byte) 0);
            byteBuffer.flip();
            return function.apply(Optional.of(byteBuffer));
        });
    }

    private boolean getBoolean(final Val value) {
        try {
            if (Type.BOOLEAN.equals(value.type())) {
                final ValBoolean valBoolean = (ValBoolean) value;
                return Objects.requireNonNullElse(valBoolean.toBoolean(), false);
            } else {
                return Objects.requireNonNullElse(value.toBoolean(), false);
            }
        } catch (final NumberFormatException | NullPointerException e) {
            throw new RuntimeException("Expected state key to be a byte but could not parse '" +
                                       value +
                                       "' as boolean");
        }
    }
}
