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

package stroom.data.store.impl.fs.s3v2;


import stroom.bytebuffer.ByteBufferPool;
import stroom.bytebuffer.impl6.ByteBufferPoolImpl6;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import com.github.luben.zstd.BufferPool;
import jakarta.inject.Inject;

import java.nio.ByteBuffer;

/**
 * Adapts our own {@link ByteBufferPool} into a {@link BufferPool} as used by {@link com.github.luben.zstd.Zstd}.
 */
public class ZstdBufferPool implements BufferPool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ZstdBufferPool.class);

    private final ByteBufferPoolImpl6 byteBufferPool;

    @Inject
    public ZstdBufferPool(final ByteBufferPool byteBufferPool) {
        // TODO Add release method to the ByteBufferPool iface, if we can, and/or get rid of
        //  all the different ByteBufferPool impls
        if (byteBufferPool instanceof ByteBufferPoolImpl6) {
            this.byteBufferPool = (ByteBufferPoolImpl6) byteBufferPool;
        } else {
            throw new IllegalStateException("Expecting byteBufferPool to be of type: " + ByteBufferPoolImpl6.class);
        }
    }

    @Override
    public ByteBuffer get(final int capacity) {
        LOGGER.debug("get() - capacity: {}", capacity);
        return byteBufferPool.getPooledByteBuffer(capacity).getByteBuffer();
    }

    @Override
    public void release(final ByteBuffer buffer) {
        LOGGER.debug(() -> LogUtil.message("release() - capacity: {}", NullSafe.get(buffer, ByteBuffer::capacity)));
        byteBufferPool.release(buffer);
    }
}
