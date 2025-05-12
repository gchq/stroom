package stroom.planb.impl.db.serde;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.query.language.functions.Type;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValFloat;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class FloatValSerde implements ValSerde {

    private final ByteBuffer reusableWriteBuffer = ByteBuffer.allocateDirect(Float.BYTES);
    private final ByteBuffers byteBuffers;

    public FloatValSerde(final ByteBuffers byteBuffers) {
        this.byteBuffers = byteBuffers;
    }

    @Override
    public Val toVal(final Txn<ByteBuffer> readTxn, final ByteBuffer byteBuffer) {
        final float f = byteBuffer.getFloat();
        return ValFloat.create(f);
    }

    @Override
    public void toBuffer(final Txn<ByteBuffer> writeTxn, final Val value, final Consumer<ByteBuffer> consumer) {
//        byteBuffers.use(Float.BYTES, byteBuffer -> {
//            byteBuffer.putFloat(getFloat(value));
//            byteBuffer.flip();
//            consumer.accept(byteBuffer);
//        });

        // We are in a single write transaction so should be able to reuse the same buffer again and again.
        reusableWriteBuffer.putFloat(0, getFloat(value));
        consumer.accept(reusableWriteBuffer);
    }

    @Override
    public Val toBufferForGet(final Txn<ByteBuffer> txn,
                              final Val value,
                              final Function<Optional<ByteBuffer>, Val> function) {
        return byteBuffers.use(Float.BYTES, byteBuffer -> {
            byteBuffer.putFloat(getFloat(value));
            byteBuffer.flip();
            return function.apply(Optional.of(byteBuffer));
        });
    }

    private float getFloat(final Val value) {
        try {
            if (Type.FLOAT.equals(value.type())) {
                final ValFloat valFloat = (ValFloat) value;
                return valFloat.toFloat();
            } else {
                return Float.parseFloat(value.toString());
            }
        } catch (final NumberFormatException | NullPointerException e) {
            throw new RuntimeException("Expected state key to be a float but could not parse '" +
                                       value +
                                       "' as float");
        }
    }
}
