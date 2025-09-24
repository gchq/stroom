package stroom.planb.impl.db.trace;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.planb.impl.serde.time.NanoTimeSerde;
import stroom.planb.impl.serde.time.TimeSerde;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class TraceRootKeySerde {

    private static final TimeSerde TIME_SERDE = new NanoTimeSerde();

    protected final ByteBuffers byteBuffers;

    public TraceRootKeySerde(final ByteBuffers byteBuffers) {
        this.byteBuffers = byteBuffers;
    }

    public void write(final TraceRootKey key,
                      final Consumer<ByteBuffer> consumer) {
        final NanoTime nanoTime = key.getStartTime();
        final byte[] bytes = key.getTraceId();
        byteBuffers.use(TIME_SERDE.getSize() + bytes.length, byteBuffer -> {
            TIME_SERDE.write(byteBuffer, NanoTimeUtil.toInstant(nanoTime));
            byteBuffer.put(bytes);
            byteBuffer.flip();
            consumer.accept(byteBuffer);
        });
    }

    public TraceRootKey read(final ByteBuffer byteBuffer) {
        final NanoTime nanoTime = NanoTimeUtil.fromInstant(TIME_SERDE.read(byteBuffer));
        final byte[] bytes = new byte[16];
        byteBuffer.get(bytes);
        return new TraceRootKey(bytes, nanoTime);
    }
}
