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

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A lazy wrapper for a {@link ByteBuffer} obtained from a {@link ByteBufferPool} that can be used
 * with a try with resources block as it implements {@link AutoCloseable}. If not used
 * with a try with resources block then {@link PooledByteBufferImpl#close()} or
 * {@link PooledByteBufferImpl#close()} must be called when the underlying {@link ByteBuffer}
 * is no longer needed.
 * <p>
 * The wrapper is empty on creation and when getByteBuffer is called, it will be populated
 * with a {@link ByteBuffer} from the pool. Depending on the implementation of the pool this may block.
 */
class PooledByteBufferImpl implements PooledByteBuffer {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PooledByteBufferImpl.class);

    private ByteBuffer byteBuffer;
    private Supplier<ByteBuffer> byteBufferSupplier;
    private Consumer<ByteBuffer> releaseFunc;

    PooledByteBufferImpl(final Supplier<ByteBuffer> byteBufferSupplier,
                         final Consumer<ByteBuffer> byteBufferReleaseFunc) {
        this.byteBufferSupplier = byteBufferSupplier;
        this.releaseFunc = byteBufferReleaseFunc;
    }

    /**
     * @return The underlying {@link ByteBuffer} that was obtained from the pool.
     * Depending on the implementation of the pool this method may block if the pool has no buffers when called.
     * The returned {@link ByteBuffer} must not be used once release/close are called.
     */
    @Override
    public ByteBuffer getByteBuffer() {
        // lazily provide the ByteBuffer
        if (byteBuffer != null) {
            return byteBuffer;
        } else if (byteBufferSupplier == null) {
            throw new IllegalStateException(LogUtil.message("The byteBuffer has been returned to the pool"));
        } else {
            byteBuffer = byteBufferSupplier.get();
            return byteBuffer;
        }
    }

    /**
     * A buffer will be obtained from the pool and passed to the byteBufferConsumer to use.
     * On completion of byteBufferConsumer the buffer will be released and will not be available
     * for any further use.
     */
    @Override
    public void doWithByteBuffer(final Consumer<ByteBuffer> byteBufferConsumer) {
        try (this) {
            byteBufferConsumer.accept(getByteBuffer());
        }
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
    public Integer getCapacity() {
        Objects.requireNonNull(byteBuffer, "Already released");
        return byteBuffer.capacity();
    }

    /**
     * Release the underlying {@link ByteBuffer} back to the pool. Once released,
     * the {@link ByteBuffer} cannot be used any more and you should not retain any
     * references to it.
     */
    @Override
    public void close() {
        if (releaseFunc == null) {
            if (byteBuffer != null && byteBuffer.isDirect()) {
                try {
                    ByteBufferSupport.unmap(byteBuffer);
                } catch (final Exception e) {
                    LOGGER.error("Error releasing direct byte buffer", e);
                }
            }
            byteBuffer = null;
            byteBufferSupplier = null;
        } else if (byteBuffer != null) {
            releaseFunc.accept(byteBuffer);
            byteBuffer = null;
            byteBufferSupplier = null;
            releaseFunc = null;
        }
    }

    @Override
    public String toString() {
        return "PooledByteBuffer{" +
                "byteBuffer=" + ByteBufferUtils.byteBufferInfo(byteBuffer) +
                '}';
    }
}
