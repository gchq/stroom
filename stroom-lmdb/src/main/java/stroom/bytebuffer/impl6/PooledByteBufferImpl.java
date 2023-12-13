package stroom.bytebuffer.impl6;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.bytebuffer.PooledByteBuffer;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

class PooledByteBufferImpl implements PooledByteBuffer {

    private final PooledByteBufferSet byteBufferSet;
    private ByteBuffer byteBuffer;

    PooledByteBufferImpl(final PooledByteBufferSet byteBufferSet,
                         final ByteBuffer byteBuffer) {
        this.byteBufferSet = byteBufferSet;
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
            this.release();
        }
    }

    /**
     * Release the underlying {@link ByteBuffer} back to the pool. Once released,
     * the {@link ByteBuffer} cannot be used any more and you should not retain any
     * references to it. Identical behaviour to calling {@link stroom.bytebuffer.PooledByteBufferImpl#close()}.
     */
    @Override
    public void release() {
        byteBufferSet.release(byteBuffer);
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
        return "PooledByteBuffer{" +
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
        final PooledByteBufferImpl that = (PooledByteBufferImpl) o;
        return Objects.equals(byteBuffer, that.byteBuffer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(byteBuffer);
    }
}
