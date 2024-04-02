package stroom.bytebuffer;

import jakarta.inject.Inject;

public class PooledByteBufferOutputFactory {

    private final ByteBufferPool byteBufferPool;

    @Inject
    public PooledByteBufferOutputFactory(final ByteBufferPool byteBufferPool) {
        this.byteBufferPool = byteBufferPool;
    }

    public PooledByteBufferOutput create(final int bufferSize) {
        return new PooledByteBufferOutput(byteBufferPool, bufferSize, -1);
    }
}
