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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestPooledByteBufferOutputStream {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestPooledByteBufferOutputStream.class);

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



    @TestFactory
    @Execution(ExecutionMode.SAME_THREAD)
    Stream<DynamicTest> testExpansionWithDifferentWriteMethods() {

        AtomicInteger iteration = new AtomicInteger(1);

        final Map<String, BiConsumer<Integer, PooledByteBufferOutputStream>> writeMethodMap = Map.of(
                "byte", (cnt, pooledStream) -> {
                    for (int i = 0; i < cnt; i++) {
                        try {
                            pooledStream.write((byte) iteration.get());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                },
                "byteArray", (cnt, pooledStream) -> {
                    final byte[] arr = new byte[cnt];
                    Arrays.fill(arr, (byte) iteration.get());
//                    for (int i = 0; i < cnt; i++) {
//                        arr[i] = (byte) iteration.get();
//                    }
                    try {
                        pooledStream.write(arr);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                },
                "partialByteArray", (cnt, pooledStream) -> {
                    final byte[] arr = new byte[cnt * 2];
                    Arrays.fill(arr, (byte) iteration.get());
//                    for (int i = 0; i < cnt * 2; i++) {
//                        arr[i] = (byte) iteration.get();
//                    }
                    try {
                        pooledStream.write(arr, 2, cnt);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });

        return writeMethodMap.entrySet()
                .stream()
                .sorted(Entry.comparingByKey()) // Consistent order for when re-running a single test in the IDE
                .map(entry ->
                        DynamicTest.dynamicTest(entry.getKey(), () -> {
                            // Reset the counter for each dynamic test
                            iteration.set(1);

                            ByteBufferPool byteBufferPool = new ByteBufferPoolFactory().getByteBufferPool();
                            final PooledByteBufferOutputStream pooledStream = new PooledByteBufferOutputStream(
                                    byteBufferPool, 10);

                            // Initial write of 6 bytes
                            entry.getValue().accept(6, pooledStream);

                            Assertions.assertThat(pooledStream.getCurrentCapacity())
                                    .hasValue(10);
                            iteration.incrementAndGet();

                            // second write of 6 bytes, total 12
                            entry.getValue().accept(6, pooledStream);

                            Assertions.assertThat(pooledStream.getCurrentCapacity())
                                    .hasValue(100);
                            iteration.incrementAndGet();

                            // first write of 80 bytes, total 92
                            entry.getValue().accept(80, pooledStream);

                            Assertions.assertThat(pooledStream.getCurrentCapacity())
                                    .hasValue(100);
                            iteration.incrementAndGet();

                            // second write of 80 bytes, total 172
                            entry.getValue().accept(80, pooledStream);

                            Assertions.assertThat(pooledStream.getCurrentCapacity())
                                    .hasValue(1000);
                            iteration.incrementAndGet();

                            PooledByteBuffer pooledByteBuffer = pooledStream.getPooledByteBuffer();

                            ByteBuffer byteBuffer = pooledByteBuffer.getByteBuffer();

                            LOGGER.info(ByteBufferUtils.byteBufferInfo(byteBuffer));

                            Assertions.assertThat(byteBuffer.remaining())
                                    .isEqualTo(172);

                            iteration.set(1);
                            Stream.of(6, 6, 80, 80)
                                    .forEach(cnt -> {
                                        byte expValue = (byte) iteration.getAndIncrement();
                                        // Make sure the bytes are all set correctly
                                        // Each write pass used a different value
                                        for (int j = 0; j < cnt; j++) {
                                            Assertions.assertThat(byteBuffer.get())
                                                    .isEqualTo(expValue);
                                        }
                                    });
                        }));
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