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

import com.google.inject.assistedinject.Assisted;
import org.apache.hadoop.hbase.util.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.annotation.concurrent.NotThreadSafe;
import javax.inject.Inject;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
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
 * The underlying buffer will continuously replaced with a larger buffer in the event
 * that there is insufficient room for the write() methods. The underlying buffer
 * is obtained from the pool lazily. Calling close/release or using this in a try with
 * resources block will ensure that the underlying buffer is returned to the pool.
 */
@NotThreadSafe
public class PooledByteBufferOutputStream extends OutputStream implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(PooledByteBufferOutputStream.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(PooledByteBufferOutputStream.class);

    // Borrowed from openJDK:
    // http://grepcode.com/file/repository.grepcode.com/java/root/jdk/openjdk/8-b132/java/util/ArrayList.java#221
    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    private PooledByteBuffer pooledByteBuffer;
    private int capacity;
    private Function<Integer, PooledByteBuffer> pooledByteBufferSupplier;

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
        PooledByteBuffer pooledByteBuffer = getCurrentPooledBuffer();
        pooledByteBuffer.getByteBuffer().flip();
        return pooledByteBuffer;
    }

    @Override
    public void write(int b) throws IOException {
        checkSizeAndGrow(Bytes.SIZEOF_BYTE);
        getCurrentPooledBuffer().getByteBuffer().put((byte)b);
    }
    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    protected void checkSizeAndGrow(int extra) {
        PooledByteBuffer currPooledByteBuffer = getCurrentPooledBuffer();
        ByteBuffer curBuf = currPooledByteBuffer.getByteBuffer();

        long capacityNeeded = curBuf.position() + (long) extra;
        if (capacityNeeded > curBuf.limit()) {
            // guarantee it's possible to fit
            if (capacityNeeded > MAX_ARRAY_SIZE) {
                throw new BufferOverflowException();
            }
            // double until hit the cap
            long nextCapacity = Math.min(curBuf.capacity() * 2L, MAX_ARRAY_SIZE);
            // but make sure there is enough if twice the existing capacity is still too small
            nextCapacity = Math.max(nextCapacity, capacityNeeded);

            LOGGER.trace("Replacing buffer with new capacity {}", nextCapacity);

            // get a new bigger buffer from the pool
            PooledByteBuffer newPooledByteBuffer = pooledByteBufferSupplier.apply((int) nextCapacity);
            ByteBuffer newBuf = newPooledByteBuffer.getByteBuffer();

            // get current buffer ready for reading
            curBuf.flip();

            // copy contents of curr to new
            newBuf.put(curBuf);

            // now swap the reference over and release the old buffer back to the pool
            pooledByteBuffer = newPooledByteBuffer;
            currPooledByteBuffer.release();
        }
    }


    /**
     * Release the underlying {@link ByteBuffer} back to the pool. Once released,
     * the {@link ByteBuffer} cannot be used any more and you should not retain any
     * references to it. Identical behaviour to calling {@link PooledByteBufferOutputStream#close()}.
     */
    public void release() {
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
        if (pooledByteBuffer != null) {
            pooledByteBuffer.clear();
        }
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
