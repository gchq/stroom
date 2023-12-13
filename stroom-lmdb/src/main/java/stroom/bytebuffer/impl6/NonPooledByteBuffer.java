package stroom.bytebuffer.impl6;

import stroom.bytebuffer.ByteBufferSupport;
import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.PooledByteBuffer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public class NonPooledByteBuffer implements PooledByteBuffer {

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
        return byteBuffer;
    }

    /**
     * A buffer will be obtained from the pool and passed to the byteBufferConsumer to use.
     * On completion of byteBufferConsumer the buffer will be released and will not be available
     * for any further use.
     */
    @Override
    public void doWithByteBuffer(final Consumer<ByteBuffer> byteBufferConsumer) {
        try {
            byteBufferConsumer.accept(byteBuffer);
        } finally {
            release();
        }
    }

    /**
     * Release the underlying {@link ByteBuffer} back to the pool. Once released,
     * the {@link ByteBuffer} cannot be used any more and you should not retain any
     * references to it. Identical behaviour to calling {@link stroom.bytebuffer.PooledByteBufferImpl#close()}.
     */
    @Override
    public void release() {
        if (byteBuffer != null && byteBuffer.isDirect()) {
            try {
                LOGGER.debug("Unmapping buffer {}", byteBuffer);
                ByteBufferSupport.unmap((MappedByteBuffer) byteBuffer);
            } catch (final Exception e) {
                LOGGER.error("Error releasing direct byte buffer", e);
            }
        }
        byteBuffer = null;
    }

    /**
     * Clears the underlying buffer if there is one.
     */
    @Override
    public void clear() {
        if (byteBuffer != null) {
            byteBuffer.clear();
        }
    }

    @Override
    public Optional<Integer> getCapacity() {
        return Optional.ofNullable(byteBuffer)
                .map(ByteBuffer::capacity);
    }

    /**
     * Same as calling {@link stroom.bytebuffer.PooledByteBufferImpl#release()}
     */
    @Override
    public void close() {
        release();
    }

    @Override
    public String toString() {
        return "NonPooledByteBuffer{" +
                "byteBuffer=" + ByteBufferUtils.byteBufferInfo(byteBuffer) +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final NonPooledByteBuffer that = (NonPooledByteBuffer) o;
        return Objects.equals(byteBuffer, that.byteBuffer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(byteBuffer);
    }
}
