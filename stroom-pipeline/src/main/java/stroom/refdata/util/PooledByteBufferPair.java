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

package stroom.refdata.util;

import stroom.util.logging.LambdaLogger;

import java.nio.ByteBuffer;

/**
 * Wrapper for a pair of {@link ByteBuffer}s obtained from a {@link ByteBufferPool} that can be used
 * with a try with resources block as it implements {@link AutoCloseable}.
 */
public class PooledByteBufferPair implements AutoCloseable {

    private final ByteBufferPool byteBufferPool;
    private ByteBuffer keyBuffer;
    private ByteBuffer valueBuffer;

    PooledByteBufferPair(final ByteBufferPool byteBufferPool,
                         final ByteBuffer keyBuffer,
                         final ByteBuffer valueBuffer) {
        this.byteBufferPool = byteBufferPool;
        this.keyBuffer = keyBuffer;
        this.valueBuffer = valueBuffer;
    }

    public ByteBuffer getKeyBuffer() {
        if (keyBuffer == null) {
            throw new RuntimeException(LambdaLogger.buildMessage("The keyBuffer has been returned to the pool"));
        }
        return keyBuffer;
    }

    public ByteBuffer getValueBuffer() {
        if (valueBuffer == null) {
            throw new RuntimeException(LambdaLogger.buildMessage("The valueBuffer has been returned to the pool"));
        }
        return valueBuffer;
    }

    public void release() {
        byteBufferPool.release(keyBuffer);
        keyBuffer = null;
        byteBufferPool.release(valueBuffer);
        valueBuffer = null;
    }

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
