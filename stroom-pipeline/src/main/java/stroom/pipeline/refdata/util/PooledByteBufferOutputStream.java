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

package stroom.pipeline.refdata.util;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.google.inject.assistedinject.Assisted;
import org.apache.hadoop.hbase.util.Bytes;

import javax.annotation.concurrent.NotThreadSafe;
import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/*
 * This class is derived from and copies parts of
 * org.apache.hadoop.hbase.io.ByteBufferOutputStream
 * which has the following licence.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * An output stream using a {@link ByteBuffer} supplied from a {@link ByteBufferPool}.
 * The underlying buffer will be continuously replaced with a larger buffer in the event
 * that there is insufficient room for the write() methods. The underlying buffer
 * is obtained from the pool lazily. Calling close/release or using this in a try with
 * resources block will ensure that the underlying buffer is returned to the pool.
 * Once getPooledByteBuffer is called, any calls to write(*) will throw and exception until
 * clear or release are called.
 */
@NotThreadSafe
public class PooledByteBufferOutputStream extends OutputStream implements AutoCloseable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PooledByteBufferOutputStream.class);

    // Borrowed from openJDK:
    // http://grepcode.com/file/repository.grepcode.com/java/root/jdk/openjdk/8-b132/java/util/ArrayList.java#221
    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    private PooledByteBuffer pooledByteBuffer;
    private int capacity;
    private Function<Integer, PooledByteBuffer> pooledByteBufferSupplier;
    private boolean isAvailableForWriting = true;

    @Inject
    public PooledByteBufferOutputStream(final ByteBufferPool byteBufferPool,
                                        @Assisted final int initialCapacity) {
        this.pooledByteBufferSupplier = byteBufferPool::getPooledByteBuffer;
        this.capacity = initialCapacity;
    }

    private PooledByteBuffer getCurrentPooledBuffer() {
        if (pooledByteBuffer == null) {
            pooledByteBuffer = pooledByteBufferSupplier.apply(capacity);
        }
        return pooledByteBuffer;
    }

    /**
     * Gets the current {@link PooledByteBuffer} object. The underlying buffer will be flipped
     * so that it is ready for reading. No writes should happen to this stream once getPooledByteBuffer
     * has been called.
     */
    public PooledByteBuffer getPooledByteBuffer() {
        final PooledByteBuffer pooledByteBuffer = getCurrentPooledBuffer();
        pooledByteBuffer.getByteBuffer().flip();
        isAvailableForWriting = false;
        return pooledByteBuffer;
    }

    @Override
    public void write(int b) throws IOException {
        checkWriteableState();
        checkSizeAndGrow(Bytes.SIZEOF_BYTE);
        getCurrentPooledBuffer().getByteBuffer().put((byte)b);
    }
    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    public void write(byte b[], int off, int len) throws IOException {
        checkWriteableState();
        Objects.checkFromIndexSize(off, len, b.length);
        checkSizeAndGrow(len);

        getCurrentPooledBuffer().getByteBuffer().put(b, off, len);
    }

    private void checkWriteableState() {
        if (!isAvailableForWriting) {
            throw new IllegalStateException(
                    "getPooledByteBuffer() has been called, you can no longer write to the stream.");
        }
    }

    private void checkSizeAndGrow(int extra) {
        final PooledByteBuffer currPooledByteBuffer = getCurrentPooledBuffer();
        final ByteBuffer curBuf = currPooledByteBuffer.getByteBuffer();

        long capacityNeeded = curBuf.position() + (long) extra;
        if (capacityNeeded > curBuf.limit()) {
            // guarantee it's possible to fit
            if (capacityNeeded > MAX_ARRAY_SIZE) {
                throw new BufferOverflowException();
            }
            final long currCapacity = curBuf.capacity();
            // Double the existing capacity unless that is not as big as capacityNeeded
            final long minNewCapacity = Math.max(
                    Math.min(
                            currCapacity * 2L,
                            MAX_ARRAY_SIZE),
                    capacityNeeded);

            // get a new bigger buffer from the pool
            final PooledByteBuffer newPooledByteBuffer = pooledByteBufferSupplier.apply((int) minNewCapacity);
            final ByteBuffer newBuf = newPooledByteBuffer.getByteBuffer();

            LOGGER.trace(() -> LogUtil.message(
                    "Replacing buffer (capacity {}) with new capacity {}, requested min capacity {}",
                    currCapacity,
                    newBuf.capacity(),
                    minNewCapacity));
            // get current buffer ready for reading
            curBuf.flip();

            // TODO @AT Consider using something like
            //  org.apache.hadoop.hbase.util.ByteBufferUtils#copyFromArrayToBuffer()
            //  i.e. sun.misc.Unsafe low level copying from one direct buffer to another
            // copy contents of curr to new
            newBuf.put(curBuf);

            try {
                // now swap the reference over and release the old buffer back to the pool
                pooledByteBuffer = newPooledByteBuffer;
            } finally {
                currPooledByteBuffer.release();
            }
        }
    }

    /**
     * Release the underlying {@link ByteBuffer} back to the pool. Once released,
     * the {@link ByteBuffer} cannot be used any more and you should not retain any
     * references to it. Identical behaviour to calling {@link PooledByteBufferOutputStream#close()}.
     */
    public void release() {
        isAvailableForWriting = true;
        if (pooledByteBuffer != null) {
            LOGGER.trace("Releasing pooledBuffer {}", pooledByteBuffer);
            pooledByteBuffer.release();
            pooledByteBuffer = null;
        }
    }

    /**
     * Clears the underlying buffer if there is one.
     */
    public void clear() {
        isAvailableForWriting = true;
        if (pooledByteBuffer != null) {
            pooledByteBuffer.clear();
        }
    }

    public Optional<Integer> getCurrentCapacity() {
        return Optional.ofNullable(pooledByteBuffer)
                .flatMap(PooledByteBuffer::getCapacity);
    }

    @Override
    public void close() {
        release();
    }

    @Override
    public String toString() {
        return "PooledByteBufferOutputStream{" +
                "pooledByteBuffer=" + ByteBufferUtils.byteBufferInfo(pooledByteBuffer.getByteBuffer()) +
                '}';
    }

    public interface Factory {
        PooledByteBufferOutputStream create(final int initialCapacity);
    }
}
