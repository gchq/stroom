package stroom.planb.impl.db.serde;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.query.language.functions.Type;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValFloat;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class FloatValSerde implements ValSerde {

    private final ByteBuffers byteBuffers;

    public FloatValSerde(final ByteBuffers byteBuffers) {
        this.byteBuffers = byteBuffers;
    }

    @Override
    public Val read(final Txn<ByteBuffer> readTxn, final ByteBuffer byteBuffer) {
        final float f = byteBuffer.getFloat();
        return ValFloat.create(f);
    }

    @Override
    public void write(final Txn<ByteBuffer> writeTxn, final Val value, final Consumer<ByteBuffer> consumer) {
        byteBuffers.use(Float.BYTES, byteBuffer -> {
            try {
                if (Type.FLOAT.equals(value.type())) {
                    final ValFloat valFloat = (ValFloat) value;
                    byteBuffer.putFloat(valFloat.toFloat());
                } else {
                    final float f = Float.parseFloat(value.toString());
                    byteBuffer.putFloat(f);
                }
            } catch (final NumberFormatException | NullPointerException e) {
                throw new RuntimeException("Expected state key to be a float but could not parse '" +
                                           value +
                                           "' as float");
            }
            byteBuffer.flip();
            consumer.accept(byteBuffer);
        });
    }
}
