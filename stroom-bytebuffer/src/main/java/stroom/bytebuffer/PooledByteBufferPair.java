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
import java.util.function.BiConsumer;

/**
 * Wrapper for a pair of {@link ByteBuffer}s obtained from a {@link ByteBufferPool} that can be used
 * with a try with resources block as it implements {@link AutoCloseable}.
 */
public interface PooledByteBufferPair extends AutoCloseable {

    ByteBuffer getKeyBuffer();

    ByteBuffer getValueBuffer();

    /**
     * The buffers will be obtained from the pool and passed to the byteBufferConsumer to use.
     * On completion of byteBufferPairConsumer the buffers will both be released and will not be available
     * for any further use.
     */
    void doWithByteBuffers(BiConsumer<ByteBuffer, ByteBuffer> byteBufferPairConsumer);

    void release();

    void clear();

    void close();
}
