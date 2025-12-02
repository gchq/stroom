package stroom.planb.impl.db.trace;

import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBufferPoolOutput;
import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.otel.trace.TraceRoot;
import stroom.planb.impl.serde.trace.HexStringUtil;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class TraceRootValueSerde {

    protected final ByteBufferFactory byteBufferFactory;
    private int bufferSize = 128;

    public TraceRootValueSerde(final ByteBufferFactory byteBufferFactory) {
        this.byteBufferFactory = byteBufferFactory;
    }

    public void write(final TraceRoot traceRoot,
                      final Consumer<ByteBuffer> consumer) {
        try (final ByteBufferPoolOutput output =
                new ByteBufferPoolOutput(byteBufferFactory, bufferSize, -1)) {
            output.write(HexStringUtil.decode(traceRoot.getTraceId()));
            output.writeString(traceRoot.getName());
            writeNanoTime(output, traceRoot.getStartTime());
            writeNanoTime(output, traceRoot.getEndTime());
            output.writeInt(traceRoot.getServices());
            output.writeInt(traceRoot.getDepth());
            output.writeInt(traceRoot.getTotalSpans());
            final ByteBuffer byteBuffer = output.getByteBuffer();
            byteBuffer.flip();
            consumer.accept(byteBuffer);
            bufferSize = Math.max(bufferSize, byteBuffer.capacity());
        }
    }

    public TraceRoot read(final ByteBuffer byteBuffer) {
        try (final Input input = new ByteBufferInput(byteBuffer)) {
            final byte[] traceId = new byte[16];
            input.readBytes(traceId);
            final String name = input.readString();
            final NanoTime startTimeUnixNano = readNanoTime(input);
            final NanoTime endTimeUnixNano = readNanoTime(input);
            final int services = input.readInt();
            final int depth = input.readInt();
            final int totalSpans = input.readInt();
            return new TraceRoot(
                    HexStringUtil.encode(traceId),
                    name,
                    startTimeUnixNano,
                    endTimeUnixNano,
                    services,
                    depth,
                    totalSpans);
        }
    }

    private NanoTime readNanoTime(final Input input) {
        return new NanoTime(input.readLong(), input.readInt());
    }

    private void writeNanoTime(final Output output, final NanoTime nanoTime) {
        output.writeLong(nanoTime.getSeconds());
        output.writeInt(nanoTime.getNanos());
    }
}
