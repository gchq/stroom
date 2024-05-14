package stroom.bytebuffer;

import java.nio.ByteBuffer;

/**
 * Provides a self populating pool of direct {@link ByteBuffer} instances to reduce the
 * overhead of allocating new {@link ByteBuffer} instances. The pool will issue buffers with
 * capacity >= minCapacity. All buffers issued by the pool will be cleared ready for use.
 * Pooled buffers MUST be returned to the pool once finished with and must not be mutated once
 * returned.
 */
public interface ByteBufferFactory {

    /**
     * Get a byte buffer from the pool or create a new one if we have no pooled buffers.
     *
     * @param size The minimum size of the buffer to get.
     * @return A byte buffer.
     */
    default ByteBuffer acquire(final int size) {
        return ByteBuffer.allocateDirect(size);
    }

    /**
     * Release a byte buffer back to the pool ready for use by another process.
     *
     * @param byteBuffer The byte buffer to release back to the pool.
     */
    default void release(final ByteBuffer byteBuffer) {
        if (byteBuffer != null && byteBuffer.isDirect()) {
            ByteBufferSupport.unmap(byteBuffer);
        }
    }
}
