package stroom.planb.impl.serde.temporalkey;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.serde.keyprefix.KeyPrefix;
import stroom.planb.impl.serde.time.TimeSerde;
import stroom.query.language.functions.Val;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class SimpleTemporalKeySerde implements TemporalKeySerde {

    private final ByteBuffer reusableWriteBuffer;
    private final ByteBuffers byteBuffers;
    private final TimeSerde timeSerde;
    private final int length;

    public SimpleTemporalKeySerde(final ByteBuffers byteBuffers,
                                  final TimeSerde timeSerde,
                                  final int prefixLength) {
        this.byteBuffers = byteBuffers;
        this.timeSerde = timeSerde;
        length = prefixLength + timeSerde.getSize();
        reusableWriteBuffer = ByteBuffer.allocateDirect(length);
    }

    abstract Val readPrefix(final ByteBuffer byteBuffer);

    abstract void writePrefix(final TemporalKey key, final ByteBuffer byteBuffer);

    @Override
    public TemporalKey read(final Txn<ByteBuffer> txn, final ByteBuffer byteBuffer) {
        final Val name = readPrefix(byteBuffer);
        final Instant time = timeSerde.read(byteBuffer);
        return new TemporalKey(KeyPrefix.create(name), time);
    }

    @Override
    public void write(final Txn<ByteBuffer> txn, final TemporalKey key, final Consumer<ByteBuffer> consumer) {
        // We are in a single write transaction so should be able to reuse the same buffer again and again.
        reusableWriteBuffer.clear();
        writePrefix(key, reusableWriteBuffer);
        timeSerde.write(reusableWriteBuffer, key.getTime());
        reusableWriteBuffer.flip();
        consumer.accept(reusableWriteBuffer);
    }

    @Override
    public <R> R toBufferForGet(final Txn<ByteBuffer> txn,
                                final TemporalKey key,
                                final Function<Optional<ByteBuffer>, R> function) {
        return byteBuffers.use(length, byteBuffer -> {
            writePrefix(key, byteBuffer);
            timeSerde.write(byteBuffer, key.getTime());
            byteBuffer.flip();
            return function.apply(Optional.of(byteBuffer));
        });
    }
}
