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


import stroom.pipeline.refdata.store.ByteBufferPoolFactory;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TestPooledByteBufferOutputStream {

    private ByteBufferPool getByteBufferPool() {
        return new ByteBufferPoolFactory().getByteBufferPool();
    }

    @Test
    void testWrite_noWrites() {

        ByteBufferPool byteBufferPool = getByteBufferPool();
        PooledByteBufferOutputStream pooledByteBufferOutputStream = new PooledByteBufferOutputStream(
                byteBufferPool,
                2);
        int initialCapacity = pooledByteBufferOutputStream.getPooledByteBuffer().getByteBuffer().capacity();

        PooledByteBuffer pooledByteBuffer = pooledByteBufferOutputStream.getPooledByteBuffer();

        assertThat(pooledByteBuffer.getByteBuffer().capacity())
                .isEqualTo(initialCapacity);
    }

    @Test
    void testWrite_expansion() throws IOException {

        ByteBufferPool byteBufferPool = getByteBufferPool();

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(0);
        PooledByteBufferOutputStream pooledByteBufferOutputStream = new PooledByteBufferOutputStream(
                byteBufferPool,
                10);

        // fill the existing buffer
        writeBytes(pooledByteBufferOutputStream, 10);

        assertThat(byteBufferPool.getCurrentPoolSize())
                .isEqualTo(0);

        // buffer replaced with a spawned bigger one, old one back to pool
        writeBytes(pooledByteBufferOutputStream, 90);

        assertThat(byteBufferPool.getCurrentPoolSize())
                .isEqualTo(1);

        // buffer replaced with a spawned bigger one, old one back to pool
        writeBytes(pooledByteBufferOutputStream, 900);

        assertThat(byteBufferPool.getCurrentPoolSize())
                .isEqualTo(2);

        PooledByteBuffer pooledByteBuffer = pooledByteBufferOutputStream.getPooledByteBuffer();

        assertThat(pooledByteBuffer.getByteBuffer().capacity())
                .isGreaterThanOrEqualTo(1000);

        pooledByteBufferOutputStream.release();

        assertThat(byteBufferPool.getCurrentPoolSize())
                .isEqualTo(3);

        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


        // grabs a buffer from pool
        pooledByteBufferOutputStream.write(new byte[]{0, 0});

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(2);

        // swaps for a bigger buffer already in the pool
        pooledByteBufferOutputStream.write(new byte[]{0, 0});

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(2);

        // swaps for a bigger buffer already in the pool
        pooledByteBufferOutputStream.write(new byte[]{0, 0});

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(2);

        pooledByteBuffer = pooledByteBufferOutputStream.getPooledByteBuffer();

        assertThat(pooledByteBuffer.getByteBuffer().capacity()).isGreaterThanOrEqualTo(6);

        pooledByteBufferOutputStream.release();

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(3);
    }

    private void writeBytes(final PooledByteBufferOutputStream outputStream, final int count) {
        IntStream.rangeClosed(1, count)
                .forEach(i -> {
                    try {
                        outputStream.write((byte) 0);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
    }


    @Test
    void testRelease() throws IOException {

        ByteBufferPool byteBufferPool = getByteBufferPool();

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(0);

        PooledByteBufferOutputStream pooledByteBufferOutputStream = new PooledByteBufferOutputStream(
                byteBufferPool,
                2);

        pooledByteBufferOutputStream.write(new byte[]{0, 0});

        // release the PooledBuffer first
        pooledByteBufferOutputStream.getPooledByteBuffer().release();

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(1);

        // now release the output stream
        pooledByteBufferOutputStream.release();

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(1);
    }

    @Test
    void testRelease2() throws IOException {

        ByteBufferPool byteBufferPool = getByteBufferPool();

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(0);

        PooledByteBufferOutputStream pooledByteBufferOutputStream = new PooledByteBufferOutputStream(
                byteBufferPool,
                2);

        pooledByteBufferOutputStream.write(new byte[]{0, 0});

        // release the output stream first
        pooledByteBufferOutputStream.release();

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(1);

        // now release the PooledBuffer
        pooledByteBufferOutputStream.getPooledByteBuffer().release();

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(1);
    }
}