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
        this.byteBufferPool = getByteBufferPool(new ByteBufferPoolConfig());
    }

    /**
     * @return The same instance of {@link ByteBufferPool} for each call, assuming the same instance of
     * {@link SimpleByteBufferPoolFactory} is used. Default config is used.
     */
    public ByteBufferPool getByteBufferPool() {
        return byteBufferPool;
    }

    /**
     * @param config
     * @return A brand-new instance of {@link ByteBufferPool} for each call. Uses the supplied config.
     */
    public static ByteBufferPool getByteBufferPool(ByteBufferPoolConfig config) {
        // Make sure this is using the same impl as ByteBufferModule
        final ByteBufferPool byteBufferPool = new ByteBufferPoolImpl10(() -> config);
        if (!ByteBufferModule.DEFAULT_BYTE_BUFFER_POOL.isAssignableFrom(byteBufferPool.getClass())) {
            throw new RuntimeException(LogUtil.message("Mismatch between {} and {}",
                    SimpleByteBufferPoolFactory.class.getName(), ByteBufferModule.class.getName()));
        }
        return byteBufferPool;
    }
}
