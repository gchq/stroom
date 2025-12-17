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

package stroom.bytebuffer.impl6;

import stroom.bytebuffer.ByteBufferSupport;

import java.nio.ByteBuffer;

/**
 * Provides a self populating pool of direct {@link ByteBuffer} instances to reduce the
 * overhead of allocating new {@link ByteBuffer} instances. The pool will issue buffers with
 * capacity >= minCapacity. All buffers issued by the pool will be cleared ready for use.
 * Pooled buffers MUST be returned to the pool once finished with and must not be mutated once
 * returned.
 * <p>
 * Depending on the implementation, the pool may block when requesting a buffer from the pool.
 */
public class SimpleByteBufferFactory implements ByteBufferFactory {

    /**
     * Get a byte buffer from the pool or create a new one if we have no pooled buffers.
     *
     * @param size The minimum size of the buffer to get.
     * @return A byte buffer.
     */
    public ByteBuffer acquire(final int size) {
        return ByteBuffer.allocateDirect(size);
    }

    /**
     * Release a byte buffer back to the pool ready for use by another process.
     *
     * @param byteBuffer The byte buffer to release back to the pool.
     */
    public void release(final ByteBuffer byteBuffer) {
        if (byteBuffer != null && byteBuffer.isDirect()) {
            ByteBufferSupport.unmap(byteBuffer);
        }
    }
}
