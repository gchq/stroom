package stroom.planb.impl.db.state;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.query.language.functions.Type;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValBoolean;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class BooleanValSerde implements ValSerde {

    private final ByteBuffers byteBuffers;

    public BooleanValSerde(final ByteBuffers byteBuffers) {
        this.byteBuffers = byteBuffers;
    }

    @Override
    public Val read(final Txn<ByteBuffer> readTxn, final ByteBuffer byteBuffer) {
        final byte b = byteBuffer.get();
        return ValBoolean.create(b != 0);
    }

    @Override
    public void write(final Txn<ByteBuffer> writeTxn, final Val value, final Consumer<ByteBuffer> consumer) {
        byteBuffers.use(Byte.BYTES, byteBuffer -> {
            try {
                if (Type.BOOLEAN.equals(value.type())) {
                    final ValBoolean valBoolean = (ValBoolean) value;
                    byteBuffer.put(valBoolean.toBoolean()
                            ? (byte) 1
                            : (byte) 0);
                } else {
                    final Boolean b = value.toBoolean();
                    if (b == null) {
                        byteBuffer.put((byte) 0);
                    } else {
                        byteBuffer.put(b
                                ? (byte) 1
                                : (byte) 0);
                    }
                }
            } catch (final NumberFormatException e) {
                throw new RuntimeException("Expected state key to be a byte but could not parse '" +
                                           value +
                                           "' as boolean");
            }
            byteBuffer.flip();
            consumer.accept(byteBuffer);
        });
    }
}
