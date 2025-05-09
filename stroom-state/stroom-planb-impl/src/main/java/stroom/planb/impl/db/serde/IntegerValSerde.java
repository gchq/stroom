package stroom.planb.impl.db.serde;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.query.language.functions.Type;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValInteger;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class IntegerValSerde implements ValSerde {

    private final ByteBuffers byteBuffers;

    public IntegerValSerde(final ByteBuffers byteBuffers) {
        this.byteBuffers = byteBuffers;
    }

    @Override
    public Val read(final Txn<ByteBuffer> readTxn, final ByteBuffer byteBuffer) {
        final int i = byteBuffer.getInt();
        return ValInteger.create(i);
    }

    @Override
    public void write(final Txn<ByteBuffer> writeTxn, final Val value, final Consumer<ByteBuffer> consumer) {
        byteBuffers.use(Integer.BYTES, byteBuffer -> {
            try {
                if (Type.INTEGER.equals(value.type())) {
                    final ValInteger valInteger = (ValInteger) value;
                    byteBuffer.putInt(valInteger.toInteger());
                } else {
                    final int i = value.toInteger();
                    byteBuffer.putInt(i);
                }
            } catch (final NumberFormatException | NullPointerException e) {
                throw new RuntimeException("Expected state key to be an integer but could not parse '" +
                                           value +
                                           "' as integer");
            }
            byteBuffer.flip();
            consumer.accept(byteBuffer);
        });
    }
}
