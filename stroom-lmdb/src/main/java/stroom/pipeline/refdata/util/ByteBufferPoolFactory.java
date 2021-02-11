package stroom.pipeline.refdata.util;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Provides a single point for tests that don't want to use guice
 * to get an instance of a {@link ByteBufferPool}.
 */
@Singleton
public class ByteBufferPoolFactory {

    private final ByteBufferPool byteBufferPool;

    public ByteBufferPoolFactory() {
        this.byteBufferPool = new ByteBufferPoolImpl4(new ByteBufferPoolConfig());
    }

    @Inject
    public ByteBufferPoolFactory(final ByteBufferPoolConfig byteBufferPoolConfig) {
        this.byteBufferPool = new ByteBufferPoolImpl4(byteBufferPoolConfig);
    }

    public ByteBufferPool getByteBufferPool() {
        return byteBufferPool;
    }
}
