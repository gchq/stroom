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

import java.nio.ByteBuffer;
import java.util.function.Consumer;

/**
 * A lazy wrapper for a {@link ByteBuffer} obtained from a {@link ByteBufferPool} that can be used
 * with a try with resources block as it implements {@link AutoCloseable}. If not used
 * with a try with resources block then {@link PooledByteBuffer#close()} or
 * {@link PooledByteBuffer#close()} must be called when the underlying {@link ByteBuffer}
 * is no longer needed.
 * <p>
 * The wrapper is empty on creation and when getByteBuffer is called, it will be populated
 * with a {@link ByteBuffer} from the pool. Depending on the implementation of the pool this may block.
 */
public interface PooledByteBuffer extends AutoCloseable {

    /**
     * @return The underlying {@link ByteBuffer} that was obtained from the pool.
     * Depending on the implementation of the pool this method may block if the pool has no buffers when called.
     * The returned {@link ByteBuffer} must not be used once release/close are called.
     */
    ByteBuffer getByteBuffer();

    /**
     * A buffer will be obtained from the pool and passed to the byteBufferConsumer to use.
     * On completion of byteBufferConsumer the buffer will be released and will not be available
     * for any further use.
     */
    void doWithByteBuffer(final Consumer<ByteBuffer> byteBufferConsumer);

    /**
     * Clears the underlying buffer if there is one.
     */
    void clear();

    Integer getCapacity();

    /**
     * Release the underlying {@link ByteBuffer} back to the pool. Once released,
     * the {@link ByteBuffer} cannot be used any more and you should not retain any
     * references to it.
     */
    void close();
}
