package stroom.bytebuffer;

import stroom.bytebuffer.impl6.ByteBufferPoolImpl6;

/**
 * Provides a single point for tests that don't want to use guice
 * to get an instance of a {@link ByteBufferPool}.
 */
public class ByteBufferPoolFactory {

    private final ByteBufferPool byteBufferPool;

    public ByteBufferPoolFactory() {
        this.byteBufferPool = new ByteBufferPoolImpl6(ByteBufferPoolConfig::new);
    }

    public ByteBufferPool getByteBufferPool() {
        return byteBufferPool;
    }
}
