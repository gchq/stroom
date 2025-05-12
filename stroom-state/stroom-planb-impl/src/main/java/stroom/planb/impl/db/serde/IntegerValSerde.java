package stroom.planb.impl.db.serde;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.query.language.functions.Type;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValInteger;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class IntegerValSerde implements ValSerde {

    private final ByteBuffer reusableWriteBuffer = ByteBuffer.allocateDirect(Integer.BYTES);
    private final ByteBuffers byteBuffers;

    public IntegerValSerde(final ByteBuffers byteBuffers) {
        this.byteBuffers = byteBuffers;
    }

    @Override
    public Val toVal(final Txn<ByteBuffer> readTxn, final ByteBuffer byteBuffer) {
        final int i = byteBuffer.getInt();
        return ValInteger.create(i);
    }

    @Override
    public void toBuffer(final Txn<ByteBuffer> writeTxn, final Val value, final Consumer<ByteBuffer> consumer) {
//        byteBuffers.use(Integer.BYTES, byteBuffer -> {
//            byteBuffer.putInt(getInt(value));
//            byteBuffer.flip();
//            consumer.accept(byteBuffer);
//        });

        // We are in a single write transaction so should be able to reuse the same buffer again and again.
        reusableWriteBuffer.putInt(0, getInt(value));
        consumer.accept(reusableWriteBuffer);
    }

    @Override
    public Val toBufferForGet(final Txn<ByteBuffer> txn,
                              final Val value,
                              final Function<Optional<ByteBuffer>, Val> function) {
        return byteBuffers.use(Integer.BYTES, byteBuffer -> {
            byteBuffer.putInt(getInt(value));
            byteBuffer.flip();
            return function.apply(Optional.of(byteBuffer));
        });
    }

    private int getInt(final Val value) {
        try {
            if (Type.INTEGER.equals(value.type())) {
                final ValInteger valInteger = (ValInteger) value;
                return valInteger.toInteger();
            } else {
                return value.toInteger();
            }
        } catch (final NumberFormatException | NullPointerException e) {
            throw new RuntimeException("Expected state key to be an integer but could not parse '" +
                                       value +
                                       "' as integer");
        }
    }
}
