package stroom.planb.impl.db.serde;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.query.language.functions.Type;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValLong;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class LongValSerde implements ValSerde {

    private final ByteBuffers byteBuffers;

    public LongValSerde(final ByteBuffers byteBuffers) {
        this.byteBuffers = byteBuffers;
    }

    @Override
    public Val read(final Txn<ByteBuffer> readTxn, final ByteBuffer byteBuffer) {
        final long l = byteBuffer.getLong();
        return ValLong.create(l);
    }

    @Override
    public void write(final Txn<ByteBuffer> writeTxn, final Val value, final Consumer<ByteBuffer> consumer) {
        byteBuffers.use(Long.BYTES, byteBuffer -> {
            try {
                if (Type.LONG.equals(value.type())) {
                    final ValLong valLong = (ValLong) value;
                    byteBuffer.putLong(valLong.toLong());
                } else {
                    final long l = value.toLong();
                    byteBuffer.putLong(l);
                }
            } catch (final NumberFormatException | NullPointerException e) {
                throw new RuntimeException("Expected state key to be a long but could not parse '" +
                                           value +
                                           "' as long");
            }
            byteBuffer.flip();
            consumer.accept(byteBuffer);
        });
    }
}
