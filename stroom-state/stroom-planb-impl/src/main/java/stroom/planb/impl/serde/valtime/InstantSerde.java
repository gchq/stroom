package stroom.planb.impl.serde.valtime;

import stroom.planb.impl.serde.Serde;
import stroom.planb.impl.serde.time.TimeSerde;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.function.Consumer;

public class InstantSerde implements Serde<Instant> {

    final TimeSerde timeSerde;
    private final ByteBuffer reusableWriteBuffer;

    public InstantSerde(final TimeSerde timeSerde) {
        this.timeSerde = timeSerde;
        reusableWriteBuffer = ByteBuffer.allocateDirect(timeSerde.getSize());
    }

    @Override
    public final Instant read(final Txn<ByteBuffer> txn,
                              final ByteBuffer byteBuffer) {
        return this.timeSerde.read(byteBuffer);
    }

    @Override
    public final void write(final Txn<ByteBuffer> txn, final Instant value, final Consumer<ByteBuffer> consumer) {
        // We are in a single write transaction so should be able to reuse the same buffer again and again.
        reusableWriteBuffer.clear();
        timeSerde.write(reusableWriteBuffer, value);
        reusableWriteBuffer.flip();
        consumer.accept(reusableWriteBuffer);
    }
}
