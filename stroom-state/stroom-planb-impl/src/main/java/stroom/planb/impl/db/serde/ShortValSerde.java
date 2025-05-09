package stroom.planb.impl.db.serde;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.query.language.functions.Type;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValShort;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class ShortValSerde implements ValSerde {

    private final ByteBuffers byteBuffers;

    public ShortValSerde(final ByteBuffers byteBuffers) {
        this.byteBuffers = byteBuffers;
    }

    @Override
    public Val read(final Txn<ByteBuffer> readTxn, final ByteBuffer byteBuffer) {
        final short s = byteBuffer.getShort();
        return ValShort.create(s);
    }

    @Override
    public void write(final Txn<ByteBuffer> writeTxn, final Val value, final Consumer<ByteBuffer> consumer) {
        byteBuffers.use(Short.BYTES, byteBuffer -> {
            try {
                if (Type.SHORT.equals(value.type())) {
                    final ValShort valShort = (ValShort) value;
                    byteBuffer.putShort(valShort.getValue());
                } else {
                    final short s = Short.parseShort(value.toString());
                    byteBuffer.putShort(s);
                }
            } catch (final NumberFormatException | NullPointerException e) {
                throw new RuntimeException("Expected state key to be a short but could not parse '" +
                                           value +
                                           "' as short");
            }
            byteBuffer.flip();
            consumer.accept(byteBuffer);
        });
    }
}
