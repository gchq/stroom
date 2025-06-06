package stroom.planb.impl.serde.temporalrangestate;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.lmdb.serde.UnsignedBytes;
import stroom.lmdb.serde.UnsignedBytesInstances;
import stroom.planb.impl.data.TemporalRangeState.Key;
import stroom.planb.impl.serde.time.TimeSerde;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class LongRangeKeySerde implements TemporalRangeKeySerde {

    private static final UnsignedBytes UNSIGNED_BYTES = UnsignedBytesInstances.ofLength(8);

    private final ByteBuffer reusableWriteBuffer;
    private final ByteBuffers byteBuffers;
    private final TimeSerde timeSerde;
    private final int length;

    public LongRangeKeySerde(final ByteBuffers byteBuffers, final TimeSerde timeSerde) {
        this.byteBuffers = byteBuffers;
        this.timeSerde = timeSerde;
        length = Long.BYTES + Long.BYTES + timeSerde.getSize();
        reusableWriteBuffer = ByteBuffer.allocateDirect(length);
    }

    @Override
    public Key read(final Txn<ByteBuffer> txn, final ByteBuffer byteBuffer) {
        final long start = UNSIGNED_BYTES.get(byteBuffer);
        final long end = UNSIGNED_BYTES.get(byteBuffer);
        final Instant effectiveTime = timeSerde.read(byteBuffer);
        return new Key(start, end, effectiveTime);
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
        writeLong(key.getKeyStart(), reusableWriteBuffer);
        writeLong(key.getKeyEnd(), reusableWriteBuffer);
        timeSerde.write(reusableWriteBuffer, key.getTime());
        reusableWriteBuffer.flip();
        consumer.accept(reusableWriteBuffer);
    }

    @Override
    public <R> R toBufferForGet(final Txn<ByteBuffer> txn,
                                final Key key,
                                final Function<Optional<ByteBuffer>, R> function) {
        return byteBuffers.use(length, byteBuffer -> {
            writeLong(key.getKeyStart(), byteBuffer);
            writeLong(key.getKeyEnd(), byteBuffer);
            timeSerde.write(byteBuffer, key.getTime());
            byteBuffer.flip();
            return function.apply(Optional.of(byteBuffer));
        });
    }

    @Override
    public <R> R toKeyStart(final long key, final Function<ByteBuffer, R> function) {
        return byteBuffers.use(length, byteBuffer -> {
            writeLong(key, byteBuffer);
            ByteBufferUtils.padMax(byteBuffer, Long.BYTES);
            byteBuffer.flip();
            return function.apply(byteBuffer);
        });
    }

    private void writeLong(final long l, final ByteBuffer byteBuffer) {
        UNSIGNED_BYTES.put(byteBuffer, l);
    }
}
