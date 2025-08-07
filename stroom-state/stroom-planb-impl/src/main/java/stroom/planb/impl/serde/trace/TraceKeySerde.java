package stroom.planb.impl.serde.trace;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.data.TraceKey;
import stroom.planb.impl.serde.KeySerde;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class TraceKeySerde implements KeySerde<TraceKey> {

    private static final int TRACE_ID_LENGTH = 16;
    private static final int PARENT_SPAN_ID_LENGTH = 8;
    private static final int SPAN_ID_LENGTH = 8;
    private static final int LENGTH = TRACE_ID_LENGTH + PARENT_SPAN_ID_LENGTH + SPAN_ID_LENGTH;

    private final ByteBuffers byteBuffers;

    public TraceKeySerde(final ByteBuffers byteBuffers) {
        this.byteBuffers = byteBuffers;
    }

    @Override
    public <R> R toBufferForGet(final Txn<ByteBuffer> txn,
                                final TraceKey value,
                                final Function<Optional<ByteBuffer>, R> function) {
        return byteBuffers.use(LENGTH, byteBuffer -> {
            byteBuffer.put(value.getTraceId());
            byteBuffer.put(value.getParentSpanId());
            byteBuffer.put(value.getSpanId());
            byteBuffer.flip();
            return function.apply(Optional.of(byteBuffer));
        });
    }

    @Override
    public void write(final Txn<ByteBuffer> txn, final TraceKey value, final Consumer<ByteBuffer> consumer) {
        byteBuffers.use(LENGTH, byteBuffer -> {
            byteBuffer.put(value.getTraceId());
            byteBuffer.put(value.getParentSpanId());
            byteBuffer.put(value.getSpanId());
            byteBuffer.flip();
            consumer.accept(byteBuffer);
        });
    }

    @Override
    public TraceKey read(final Txn<ByteBuffer> txn, final ByteBuffer byteBuffer) {
        final byte[] traceId = new byte[TRACE_ID_LENGTH];
        final byte[] parentSpanId = new byte[PARENT_SPAN_ID_LENGTH];
        final byte[] spanId = new byte[SPAN_ID_LENGTH];
        byteBuffer.get(traceId);
        byteBuffer.get(parentSpanId);
        byteBuffer.get(spanId);
        return new TraceKey(traceId, parentSpanId, spanId);
    }
}
