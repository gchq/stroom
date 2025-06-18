package stroom.planb.impl.serde.temporalkey;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.db.Db;
import stroom.planb.impl.db.KeyLength;
import stroom.planb.impl.serde.keyprefix.KeyPrefix;
import stroom.planb.impl.serde.time.TimeSerde;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValString;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class LimitedStringKeySerde implements TemporalKeySerde {

    private final ByteBuffer reusableWriteBuffer;
    private final ByteBuffers byteBuffers;
    private final TimeSerde timeSerde;
    private final int limit;

    public LimitedStringKeySerde(final ByteBuffers byteBuffers,
                                 final TimeSerde timeSerde) {
        this.byteBuffers = byteBuffers;
        this.timeSerde = timeSerde;
        this.limit = Db.MAX_KEY_LENGTH - timeSerde.getSize();
        reusableWriteBuffer = ByteBuffer.allocateDirect(limit);
    }

    @Override
    public TemporalKey read(final Txn<ByteBuffer> txn, final ByteBuffer byteBuffer) {
        // Slice off the end to get the effective time.
        final ByteBuffer timeSlice = byteBuffer.slice(byteBuffer.remaining() - timeSerde.getSize(),
                timeSerde.getSize());
        final Instant time = timeSerde.read(timeSlice);

        // Slice off the name.
        final ByteBuffer nameSlice = byteBuffer.slice(0,
                byteBuffer.remaining() - timeSerde.getSize());

        // Read via lookup.
        final Val val = ValString.create(ByteBufferUtils.toString(nameSlice));
        return new TemporalKey(KeyPrefix.create(val), time);
    }

    @Override
    public void write(final Txn<ByteBuffer> txn, final TemporalKey key, final Consumer<ByteBuffer> consumer) {
        final byte[] bytes = key.getPrefix().getVal().toString().getBytes(StandardCharsets.UTF_8);
        KeyLength.check(bytes.length, limit);

        // We are in a single write transaction so should be able to reuse the same buffer again and again.
        reusableWriteBuffer.clear();
        reusableWriteBuffer.put(bytes);
        timeSerde.write(reusableWriteBuffer, key.getTime());
        reusableWriteBuffer.flip();
        consumer.accept(reusableWriteBuffer);
    }

    @Override
    public <R> R toBufferForGet(final Txn<ByteBuffer> txn,
                                final TemporalKey key,
                                final Function<Optional<ByteBuffer>, R> function) {
        final byte[] bytes = key.getPrefix().getVal().toString().getBytes(StandardCharsets.UTF_8);
        KeyLength.check(bytes.length, limit);
        return byteBuffers.use(bytes.length + timeSerde.getSize(), byteBuffer -> {
            byteBuffer.put(bytes);
            timeSerde.write(byteBuffer, key.getTime());
            byteBuffer.flip();
            return function.apply(Optional.of(byteBuffer));
        });
    }
}
