package stroom.planb.impl.db.serde;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.query.language.functions.Type;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDouble;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class DoubleValSerde implements ValSerde {

    private final ByteBuffers byteBuffers;

    public DoubleValSerde(final ByteBuffers byteBuffers) {
        this.byteBuffers = byteBuffers;
    }

    @Override
    public Val read(final Txn<ByteBuffer> readTxn, final ByteBuffer byteBuffer) {
        final double f = byteBuffer.getDouble();
        return ValDouble.create(f);
    }

    @Override
    public void write(final Txn<ByteBuffer> writeTxn, final Val value, final Consumer<ByteBuffer> consumer) {
        byteBuffers.use(Double.BYTES, byteBuffer -> {
            try {
                if (Type.DOUBLE.equals(value.type())) {
                    final ValDouble valDouble = (ValDouble) value;
                    byteBuffer.putDouble(valDouble.toDouble());
                } else {
                    final double f = Double.parseDouble(value.toString());
                    byteBuffer.putDouble(f);
                }
            } catch (final NumberFormatException | NullPointerException e) {
                throw new RuntimeException("Expected state key to be a double but could not parse '" +
                                           value +
                                           "' as double");
            }
            byteBuffer.flip();
            consumer.accept(byteBuffer);
        });
    }
}
