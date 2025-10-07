package stroom.planb.impl.db.trace;

import stroom.bytebuffer.impl6.ByteBuffers;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

public class TraceRootKeySerde {

    protected final ByteBuffers byteBuffers;

    public TraceRootKeySerde(final ByteBuffers byteBuffers) {
        this.byteBuffers = byteBuffers;
    }

    public void write(final TraceRootKey key,
                      final Consumer<ByteBuffer> consumer) {
        final byte[] bytes = key.getTraceId();
        byteBuffers.useBytes(bytes, consumer);
    }

    public TraceRootKey read(final ByteBuffer byteBuffer) {
        final byte[] bytes = new byte[16];
        byteBuffer.get(bytes);
        return new TraceRootKey(bytes);
    }
}
