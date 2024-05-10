package stroom.bytebuffer;

import stroom.util.logging.LogUtil;

/**
 * For use in tests ONLY.
 * <p>
 * Provides a single point for tests that don't want to use guice
 * to get an instance of a {@link ByteBufferPool}.
 * Should be kept in step with {@link ByteBufferModule}.
 */
public class SimpleByteBufferPoolFactory {

    private final ByteBufferPool byteBufferPool;

    public SimpleByteBufferPoolFactory() {

        // Make sure this is using the same impl as ByteBufferModule
        this.byteBufferPool = new ByteBufferPoolImpl8(ByteBufferPoolConfig::new);

        if (!ByteBufferModule.DEFAULT_BYTE_BUFFER_POOL.isAssignableFrom(byteBufferPool.getClass())) {
            throw new RuntimeException(LogUtil.message("Mismatch between {} and {}",
                    this.getClass().getName(), ByteBufferModule.class.getName()));
        }
    }

    public ByteBufferPool getByteBufferPool() {
        return byteBufferPool;
    }
}
