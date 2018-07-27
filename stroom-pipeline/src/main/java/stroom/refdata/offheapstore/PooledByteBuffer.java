/*
 * Copyright 2018 Crown Copyright
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
 *
 */

package stroom.refdata.offheapstore;

import stroom.util.logging.LambdaLogger;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Wrapper for a {@link ByteBuffer} obtained from a {@link ByteBufferPool} that can be used
 * with a try with resources block as it implements {@link AutoCloseable}. If not used
 * with a try with resources block then {@link PooledByteBuffer#close()} or
 * {@link PooledByteBuffer#release()} must be called when the underlying {@link ByteBuffer}
 * is no longer needed.
 */
public class PooledByteBuffer implements AutoCloseable {

    private ByteBuffer byteBuffer;
    private Supplier<ByteBuffer> byteBufferSupplier;
    private Consumer<ByteBuffer> releaseFunc;


    PooledByteBuffer(final Supplier<ByteBuffer> byteBufferSupplier,
                     final ByteBufferPool byteBufferPool) {
        this.byteBufferSupplier = byteBufferSupplier;
        this.releaseFunc = byteBufferPool::release;
    }

    /**
     * @return The underlying {@link ByteBuffer} that was obtained from the pool.
     */
    public ByteBuffer getByteBuffer() {
        // lazily provide the ByteBuffer
        if (byteBuffer != null) {
            return byteBuffer;
        } else if (byteBufferSupplier == null) {
            throw new IllegalStateException(LambdaLogger.buildMessage("The byteBuffer has been returned to the pool"));
        } else {
            byteBuffer = byteBufferSupplier.get();
            return byteBuffer;
        }
    }

    /**
     * Release the underlying {@link ByteBuffer} back to the pool. Once released,
     * the {@link ByteBuffer} cannot be used any more and you should not retain any
     * references to it. Identical behaviour to calling {@link PooledByteBuffer#close()}.
     */
    public void release() {
        releaseFunc.accept(byteBuffer);
        byteBuffer = null;
        byteBufferSupplier = null;
        releaseFunc = null;
    }

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
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final PooledByteBuffer that = (PooledByteBuffer) o;
        return Objects.equals(byteBuffer, that.byteBuffer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(byteBuffer);
    }
}
