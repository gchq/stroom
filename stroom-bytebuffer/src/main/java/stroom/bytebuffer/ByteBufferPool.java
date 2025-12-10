/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.bytebuffer;

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
     * respectively. Intended for use when dealing with key/value pairs in LMDB.
     */
    PooledByteBufferPair getPooledBufferPair(int minKeyCapacity, int minValueCapacity);

    /**
     * Performs work using a pooled buffer with capacity >= minCapacity. The pooled buffer is returned
     * to the pool on completion of work. The buffer should not be used/mutated after completion of work.
     */
    default <T> T getWithBuffer(final int minCapacity, final Function<ByteBuffer, T> work) {
        try (final PooledByteBuffer pooledKeyByteBuffer = getPooledByteBuffer(minCapacity)) {
            return work.apply(pooledKeyByteBuffer.getByteBuffer());
        }
    }

    /**
     * Performs work using a pooled buffer with capacity >= minCapacity. The pooled buffer is returned
     * to the pool on completion of work. The buffer should not be used/mutated after completion of work.
     */
    default void doWithBuffer(final int minCapacity, final Consumer<ByteBuffer> work) {
        try (final PooledByteBuffer pooledKeyByteBuffer = getPooledByteBuffer(minCapacity)) {
            work.accept(pooledKeyByteBuffer.getByteBuffer());
        }
    }

    /**
     * Performs work using a pair of pooled buffers with capacity >= minKeyCapacity and minValueCapacity respectively.
     * The pooled buffer is returned to the pool on completion of work. The buffers should not be
     * used/mutated after completion of work.
     */
    default void doWithBufferPair(final int minKeyCapacity,
                                  final int minValueCapacity,
                                  final BiConsumer<ByteBuffer, ByteBuffer> work) {
        try (final PooledByteBuffer pooledKeyByteBuffer = getPooledByteBuffer(minKeyCapacity);
                final PooledByteBuffer pooledValueBuffer = getPooledByteBuffer(minValueCapacity)) {
            work.accept(pooledKeyByteBuffer.getByteBuffer(), pooledValueBuffer.getByteBuffer());
        }
    }

    /**
     * @return The number of buffers currently available in the pool and not on loan.
     */
    int getCurrentPoolSize();

    /**
     * Clears all buffers currently held in the pool. Does not affect buffers currently on loan.
     */
    @Override
    default void clear() {

    }

    @Override
    SystemInfoResult getSystemInfo();
}
