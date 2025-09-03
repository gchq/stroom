package stroom.planb.impl.serde.trace;

import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.planb.impl.serde.KeySerde;

import org.lmdbjava.Txn;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

public class SpanKeySerde implements KeySerde<SpanKey> {

    private static final int TRACE_ID_LENGTH = 16;
    private static final int PARENT_SPAN_ID_LENGTH = 8;
    private static final int SPAN_ID_LENGTH = 8;
    private static final int LENGTH = TRACE_ID_LENGTH + PARENT_SPAN_ID_LENGTH + SPAN_ID_LENGTH;
    private static final byte[] NO_PARENT_SPAN_ID = new byte[PARENT_SPAN_ID_LENGTH];

    private final ByteBuffers byteBuffers;

    public SpanKeySerde(final ByteBuffers byteBuffers) {
        this.byteBuffers = byteBuffers;
    }

    @Override
    public <R> R toBufferForGet(final Txn<ByteBuffer> txn,
                                final SpanKey value,
                                final Function<Optional<ByteBuffer>, R> function) {
        return byteBuffers.use(LENGTH, byteBuffer -> {
            write(value, byteBuffer);
            return function.apply(Optional.of(byteBuffer));
        });
    }

    @Override
    public void write(final Txn<ByteBuffer> txn, final SpanKey value, final Consumer<ByteBuffer> consumer) {
        byteBuffers.use(LENGTH, byteBuffer -> {
            write(value, byteBuffer);
            consumer.accept(byteBuffer);
        });
    }

    private void write(final SpanKey spanKey, final ByteBuffer byteBuffer) {
        final byte[] traceId = HexStringUtil.decode(spanKey.getTraceId());
        byte[] parentSpanId = HexStringUtil.decode(spanKey.getParentSpanId());
        final byte[] spanId = HexStringUtil.decode(spanKey.getSpanId());

        if (parentSpanId.length == 0) {
            parentSpanId = NO_PARENT_SPAN_ID;
        }

        if (traceId.length != TRACE_ID_LENGTH) {
            throw new IllegalArgumentException("Trace id not " + TRACE_ID_LENGTH + " bytes long");
        }
        if (parentSpanId.length != PARENT_SPAN_ID_LENGTH) {
            throw new IllegalArgumentException("Parent span id not " + PARENT_SPAN_ID_LENGTH + " bytes long");
        }
        if (spanId.length != SPAN_ID_LENGTH) {
            throw new IllegalArgumentException("Span id not " + SPAN_ID_LENGTH + " bytes long");
        }
        byteBuffer.put(traceId);
        byteBuffer.put(parentSpanId);
        byteBuffer.put(spanId);
        byteBuffer.flip();
    }

    @Override
    public SpanKey read(final Txn<ByteBuffer> txn, final ByteBuffer byteBuffer) {
        final byte[] traceId = new byte[TRACE_ID_LENGTH];
        final byte[] parentSpanId = new byte[PARENT_SPAN_ID_LENGTH];
        final byte[] spanId = new byte[SPAN_ID_LENGTH];
        byteBuffer.get(traceId);
        byteBuffer.get(parentSpanId);
        byteBuffer.get(spanId);
        return new SpanKey(HexStringUtil.encode(traceId),
                HexStringUtil.encode(parentSpanId),
                HexStringUtil.encode(spanId));
    }
}
