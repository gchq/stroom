package stroom.bytebuffer.impl6;

import stroom.bytebuffer.ByteBufferSupport;
import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.PooledByteBuffer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Consumer;

class NonPooledByteBuffer implements PooledByteBuffer {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(NonPooledByteBuffer.class);

    private ByteBuffer byteBuffer;

    NonPooledByteBuffer(final ByteBuffer byteBuffer) {
        this.byteBuffer = byteBuffer;
    }

    /**
     * @return The underlying {@link ByteBuffer} that was obtained from the pool.
     * Depending on the implementation of the pool this method may block if the pool has no buffers when called.
     * The returned {@link ByteBuffer} must not be used once release/close are called.
     */
    @Override
    public ByteBuffer getByteBuffer() {
        Objects.requireNonNull(byteBuffer, "Already released");
        return byteBuffer;
    }

    /**
     * A buffer will be obtained from the pool and passed to the byteBufferConsumer to use.
     * On completion of byteBufferConsumer the buffer will be released and will not be available
     * for any further use.
     */
    @Override
    public void doWithByteBuffer(final Consumer<ByteBuffer> byteBufferConsumer) {
        try (this) {
            byteBufferConsumer.accept(byteBuffer);
        }
    }

    /**
     * Clears the underlying buffer if there is one.
     */
    @Override
    public void clear() {
        Objects.requireNonNull(byteBuffer, "Already released");
        byteBuffer.clear();
    }

    @Override
    public Integer capacity() {
        return byteBuffer.capacity();
    }

    /**
     * Release the underlying {@link ByteBuffer} back to the pool. Once released,
     * the {@link ByteBuffer} cannot be used any more and you should not retain any
     * references to it.
     */
    @Override
    public void close() {
        Objects.requireNonNull(byteBuffer, "Already released");
        if (byteBuffer.isDirect()) {
            try {
//                LOGGER.debug("Unmapping buffer {}", byteBuffer);
                ByteBufferSupport.unmap(byteBuffer);
            } catch (final Exception e) {
                LOGGER.error("Error releasing direct byte buffer", e);
            }
        }
        byteBuffer = null;
    }

    @Override
    public String toString() {
        return "NonPooledByteBuffer{" +
                "byteBuffer=" + ByteBufferUtils.byteBufferInfo(byteBuffer) +
                '}';
    }
}
