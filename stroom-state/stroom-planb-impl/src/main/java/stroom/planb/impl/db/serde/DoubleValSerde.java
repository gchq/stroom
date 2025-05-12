package stroom.planb.impl.db.serde;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.query.language.functions.Type;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDouble;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class DoubleValSerde implements ValSerde {

    private final ByteBuffer reusableWriteBuffer = ByteBuffer.allocateDirect(Double.BYTES);
    private final ByteBuffers byteBuffers;

    public DoubleValSerde(final ByteBuffers byteBuffers) {
        this.byteBuffers = byteBuffers;
    }

    @Override
    public Val toVal(final Txn<ByteBuffer> readTxn, final ByteBuffer byteBuffer) {
        final double f = byteBuffer.getDouble();
        return ValDouble.create(f);
    }

    @Override
    public void toBuffer(final Txn<ByteBuffer> writeTxn, final Val value, final Consumer<ByteBuffer> consumer) {
//        byteBuffers.use(Double.BYTES, byteBuffer -> {
//            byteBuffer.putDouble(getDouble(value));
//            byteBuffer.flip();
//            consumer.accept(byteBuffer);
//        });

        // We are in a single write transaction so should be able to reuse the same buffer again and again.
        reusableWriteBuffer.putDouble(0, getDouble(value));
        consumer.accept(reusableWriteBuffer);
    }

    @Override
    public Val toBufferForGet(final Txn<ByteBuffer> txn,
                              final Val value,
                              final Function<Optional<ByteBuffer>, Val> function) {
        return byteBuffers.use(Double.BYTES, byteBuffer -> {
            byteBuffer.putDouble(getDouble(value));
            byteBuffer.flip();
            return function.apply(Optional.of(byteBuffer));
        });
    }

    private double getDouble(final Val value) {
        try {
            if (Type.DOUBLE.equals(value.type())) {
                final ValDouble valDouble = (ValDouble) value;
                return valDouble.toDouble();
            } else {
                return Double.parseDouble(value.toString());
            }
        } catch (final NumberFormatException | NullPointerException e) {
            throw new RuntimeException("Expected state key to be a double but could not parse '" +
                                       value +
                                       "' as double");
        }
    }
}
