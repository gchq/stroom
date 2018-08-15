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

import org.junit.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;


public class TestPooledByteBufferOutputStream {

    @Test
    public void testWrite_noWrites() {

        ByteBufferPool byteBufferPool = new ByteBufferPool();
        PooledByteBufferOutputStream pooledByteBufferOutputStream = new PooledByteBufferOutputStream(
                byteBufferPool,
                2);

        PooledByteBuffer pooledByteBuffer = pooledByteBufferOutputStream.getPooledByteBuffer();

        assertThat(pooledByteBuffer.getByteBuffer().capacity()).isEqualTo(2);
    }

    @Test
    public void testWrite_expansion() throws IOException {

        ByteBufferPool byteBufferPool = new ByteBufferPool();

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(0);
        PooledByteBufferOutputStream pooledByteBufferOutputStream = new PooledByteBufferOutputStream(
                byteBufferPool,
                2);

        // fill the existing buffer
        pooledByteBufferOutputStream.write(new byte[]{0, 0});

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(0);

        // buffer replaced with a spawned bigger one, old one back to pool
        pooledByteBufferOutputStream.write(new byte[]{0, 0});

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(1);

        // buffer replaced with a spawned bigger one, old one back to pool
        pooledByteBufferOutputStream.write(new byte[]{0, 0});

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(2);

        PooledByteBuffer pooledByteBuffer = pooledByteBufferOutputStream.getPooledByteBuffer();

        assertThat(pooledByteBuffer.getByteBuffer().capacity()).isGreaterThanOrEqualTo(6);

        pooledByteBufferOutputStream.release();

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(3);

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


    @Test
    public void testRelease() throws IOException {

        ByteBufferPool byteBufferPool = new ByteBufferPool();

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
    public void testRelease2() throws IOException {

        ByteBufferPool byteBufferPool = new ByteBufferPool();

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