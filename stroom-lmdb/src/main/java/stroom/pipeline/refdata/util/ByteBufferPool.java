package stroom.pipeline.refdata.util;

import stroom.util.shared.Clearable;
import stroom.util.sysinfo.HasSystemInfo;
import stroom.util.sysinfo.SystemInfoResult;

import java.nio.ByteBuffer;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Provides a self populating pool of direct {@link ByteBuffer} instances to reduce the
 * overhead of allocating new {@link ByteBuffer} instances. The pool will issue buffers with
 * capacity >= minCapacity. All buffers issued by the pool will be cleared ready for use.
 * Pooled buffers MUST be returned to the pool once finished with and must not be mutated once
 * returned.
 * <p>
 * Depending on the implementation, the pool may block when requesting a buffer from the pool.
 */
public interface ByteBufferPool extends Clearable, HasSystemInfo {

    /**
     * Gets a {@link PooledByteBuffer} with capacity >= minCapacity.
     */
    PooledByteBuffer getPooledByteBuffer(int minCapacity);

    /**
     * Gets a pair of {@link PooledByteBuffer} instances, with capacity >= minKeyCapacity and minValueCapacity
     * respectively. Intended for use when dealing with keey/value pairs in LMDB.
     */
    PooledByteBufferPair getPooledBufferPair(int minKeyCapacity, int minValueCapacity);

    /**
     * Performs work using a pooled buffer with capacity >= minCapacity. The pooled buffer is returned
     * to the pool on completion of work. The buffer should not be used/mutated after completion of work.
     */
    <T> T getWithBuffer(int minCapacity, Function<ByteBuffer, T> work);

    /**
     * Performs work using a pooled buffer with capacity >= minCapacity. The pooled buffer is returned
     * to the pool on completion of work. The buffer should not be used/mutated after completion of work.
     */
    void doWithBuffer(int minCapacity, Consumer<ByteBuffer> work);

    /**
     * Performs work using a pair of pooled buffers with capacity >= minKeyCapacity and minValueCapacity respectively.
     * The pooled buffer is returned to the pool on completion of work. The buffers should not be
     * used/mutated after completion of work.
     */
    void doWithBufferPair(final int minKeyCapacity,
                          final int minValueCapacity,
                          final BiConsumer<ByteBuffer, ByteBuffer> work);

    /**
     * @return The number of buffers currently available in the pool and not on loan.
     */
    int getCurrentPoolSize();

    /**
     * Clears all buffers currently held in the pool. Does not effect buffers currently on loan.
     */
    @Override
    void clear();

    @Override
    SystemInfoResult getSystemInfo();
}
