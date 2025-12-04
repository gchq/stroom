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

import stroom.util.logging.LogUtil;

import java.nio.ByteBuffer;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Wrapper for a pair of {@link ByteBuffer}s obtained from a {@link ByteBufferPool} that can be used
 * with a try with resources block as it implements {@link AutoCloseable}.
 */
class PooledByteBufferPairImpl implements PooledByteBufferPair {

    private final Consumer<ByteBuffer> byteBufferReleaseFunc;
    private ByteBuffer keyBuffer;
    private ByteBuffer valueBuffer;

    PooledByteBufferPairImpl(final Consumer<ByteBuffer> byteBufferReleaseFunc,
                             final ByteBuffer keyBuffer,
                             final ByteBuffer valueBuffer) {
        this.byteBufferReleaseFunc = byteBufferReleaseFunc;
        this.keyBuffer = keyBuffer;
        this.valueBuffer = valueBuffer;
    }

    @Override
    public ByteBuffer getKeyBuffer() {
        if (keyBuffer == null) {
            throw new RuntimeException(LogUtil.message("The keyBuffer has been returned to the pool"));
        }
        return keyBuffer;
    }

    @Override
    public ByteBuffer getValueBuffer() {
        if (valueBuffer == null) {
            throw new RuntimeException(LogUtil.message("The valueBuffer has been returned to the pool"));
        }
        return valueBuffer;
    }

    /**
     * The buffers will be obtained from the pool and passed to the byteBufferConsumer to use.
     * On completion of byteBufferPairConsumer the buffers will both be released and will not be available
     * for any further use.
     */
    @Override
    public void doWithByteBuffers(final BiConsumer<ByteBuffer, ByteBuffer> byteBufferPairConsumer) {
        try {
            byteBufferPairConsumer.accept(getKeyBuffer(), getValueBuffer());
        } finally {
            this.release();
        }
    }

    @Override
    public void release() {
        byteBufferReleaseFunc.accept(keyBuffer);
        keyBuffer = null;
        byteBufferReleaseFunc.accept(valueBuffer);
        valueBuffer = null;
    }

    @Override
    public void clear() {
        if (keyBuffer != null) {
            keyBuffer.clear();
        }
        if (valueBuffer != null) {
            valueBuffer.clear();
        }
    }

    @Override
    public void close() {
        release();
    }

    @Override
    public String toString() {
        return "PooledByteBufferPair{" +
                "keyBuffer=" + ByteBufferUtils.byteBufferInfo(keyBuffer) +
                ", valueBuffer=" + ByteBufferUtils.byteBufferInfo(valueBuffer) +
                '}';
    }
}
