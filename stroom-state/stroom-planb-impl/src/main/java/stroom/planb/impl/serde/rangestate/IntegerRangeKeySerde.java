package stroom.planb.impl.serde.rangestate;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.lmdb.serde.UnsignedBytes;
import stroom.lmdb.serde.UnsignedBytesInstances;
import stroom.planb.impl.data.RangeState.Key;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class IntegerRangeKeySerde implements RangeKeySerde {

    private static final UnsignedBytes UNSIGNED_BYTES = UnsignedBytesInstances.ofLength(4);

    private final ByteBuffer reusableWriteBuffer;
    private final ByteBuffers byteBuffers;
    private final int length;

    public IntegerRangeKeySerde(final ByteBuffers byteBuffers) {
        this.byteBuffers = byteBuffers;
        length = Integer.BYTES + Integer.BYTES;
        reusableWriteBuffer = ByteBuffer.allocateDirect(length);
    }

    @Override
    public Key read(final Txn<ByteBuffer> txn, final ByteBuffer byteBuffer) {
        final long start = UNSIGNED_BYTES.get(byteBuffer);
        final long end = UNSIGNED_BYTES.get(byteBuffer);
        return new Key(start, end);
    }

    @Override
    public void write(final Txn<ByteBuffer> txn, final Key key, final Consumer<ByteBuffer> consumer) {
//        byteBuffers.use(Byte.BYTES, byteBuffer -> {
//            byteBuffer.put(getByte(value));
//            byteBuffer.flip();
//            consumer.accept(byteBuffer);
//        });

        // We are in a single write transaction so should be able to reuse the same buffer again and again.
        reusableWriteBuffer.clear();
        writeInteger(key.getKeyStart(), reusableWriteBuffer);
        writeInteger(key.getKeyEnd(), reusableWriteBuffer);
        reusableWriteBuffer.flip();
        consumer.accept(reusableWriteBuffer);
    }

    @Override
    public <R> R toBufferForGet(final Txn<ByteBuffer> txn,
                                final Key key,
                                final Function<Optional<ByteBuffer>, R> function) {
        return byteBuffers.use(length, byteBuffer -> {
            writeInteger(key.getKeyStart(), byteBuffer);
            writeInteger(key.getKeyEnd(), byteBuffer);
            byteBuffer.flip();
            return function.apply(Optional.of(byteBuffer));
        });
    }

    @Override
    public <R> R toKeyStart(final long key, final Function<ByteBuffer, R> function) {
        return byteBuffers.use(length, byteBuffer -> {
            writeInteger(key, byteBuffer);
            ByteBufferUtils.padMax(byteBuffer, Integer.BYTES);
            byteBuffer.flip();
            return function.apply(byteBuffer);
        });
    }

    private void writeInteger(final long l, final ByteBuffer byteBuffer) {
        UNSIGNED_BYTES.put(byteBuffer, l);
    }
}
