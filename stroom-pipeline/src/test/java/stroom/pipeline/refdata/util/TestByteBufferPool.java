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

import stroom.pipeline.refdata.ReferenceDataConfig;
import stroom.pipeline.refdata.store.ByteBufferPoolFactory;
import stroom.util.sysinfo.SystemInfoResult;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

class TestByteBufferPool {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestByteBufferPool.class);

    private static final long RANDOM_SEED = 345649236493L;

    private ByteBufferPool getByteBufferPool() {
        return new ByteBufferPoolFactory(new ReferenceDataConfig()).getByteBufferPool();
    }

    @Test
    void doWithBuffer() {

        ByteBufferPool byteBufferPool = getByteBufferPool();

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(0);
        int minCapacity = 100;
        byteBufferPool.doWithBuffer(minCapacity, buffer -> {

            assertThat(buffer).isNotNull();
            assertThat(buffer.isDirect()).isTrue();
            assertThat(buffer.capacity()).isEqualTo(minCapacity);

            assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(0);

        });

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(1);
    }
    @Test
    void doWithBuffer_sameSize() {

        ByteBufferPool byteBufferPool = getByteBufferPool();

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(0);

        getAndReleaseBuffer(byteBufferPool, 10);

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(1);

        getAndReleaseBuffer(byteBufferPool, 10);

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(1);
    }

    @Test
    void doWithBuffer_differentSize() {

        ByteBufferPool byteBufferPool = getByteBufferPool();

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(0);

        getAndReleaseBuffer(byteBufferPool, 10);

        //will use the 10 buffer
        getAndReleaseBuffer(byteBufferPool, 2);

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(1);

        //will create a new buffer
        getAndReleaseBuffer(byteBufferPool, 1000);

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(2);

        int minCapacity = 123;
        int expectedCapacity = 1000;
        byteBufferPool.doWithBuffer(minCapacity, buffer -> {

            assertThat(buffer).isNotNull();
            assertThat(buffer.isDirect()).isTrue();
            assertThat(buffer.capacity()).isEqualTo(expectedCapacity);

            assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(1);

        });

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(2);
    }

    @Test
    void testBufferClearing() {
        ByteBufferPool byteBufferPool = getByteBufferPool();

        PooledByteBuffer pooledByteBuffer = byteBufferPool.getPooledByteBuffer(10);

        pooledByteBuffer.getByteBuffer().putLong(Long.MAX_VALUE);

        assertThat(pooledByteBuffer.getByteBuffer().position()).isEqualTo(8);
        assertThat(pooledByteBuffer.getByteBuffer().capacity()).isEqualTo(10);

        // hold a ref to the buffer so we can test it later, shouldn't normally do this
        ByteBuffer firstByteBuffer = pooledByteBuffer.getByteBuffer();

        pooledByteBuffer.release();

        PooledByteBuffer pooledByteBuffer2 = byteBufferPool.getPooledByteBuffer(10);

        // got same instance from pool
        assertThat(pooledByteBuffer2.getByteBuffer()).isSameAs(firstByteBuffer);

        // buffer has been cleared
        assertThat(pooledByteBuffer2.getByteBuffer().position()).isEqualTo(0);
        assertThat(pooledByteBuffer2.getByteBuffer().capacity()).isEqualTo(10);
    }

    @Test
    void testConcurrency() throws InterruptedException {
        int threadCount = 50;
        int minCapacity = 10;
        final ByteBufferPool byteBufferPool = getByteBufferPool();

        assertPoolSizeAfterMultipleConcurrentGetRequests(threadCount, minCapacity, byteBufferPool);
        
        LOGGER.info(byteBufferPool.getSystemInfo().toString());

        //re-run the same thing and the pool size should be the same at the end
        assertPoolSizeAfterMultipleConcurrentGetRequests(threadCount, minCapacity, byteBufferPool);

        LOGGER.info(byteBufferPool.getSystemInfo().toString());
    }

    @Test
    void testGetSystemInfo() {
        final ByteBufferPool byteBufferPool = getByteBufferPool();

        PooledByteBuffer buffer1 = byteBufferPool.getPooledByteBuffer(1);
        PooledByteBuffer buffer2 = byteBufferPool.getPooledByteBuffer(1);
        PooledByteBuffer buffer3 = byteBufferPool.getPooledByteBuffer(10);
        PooledByteBuffer buffer4 = byteBufferPool.getPooledByteBuffer(10);
        PooledByteBuffer buffer5 = byteBufferPool.getPooledByteBuffer(10);
        PooledByteBuffer buffer6 = byteBufferPool.getPooledByteBuffer(100);

        buffer1.getByteBuffer();
        buffer2.getByteBuffer();
        buffer3.getByteBuffer();
        buffer4.getByteBuffer();
        buffer5.getByteBuffer();
        buffer6.getByteBuffer();

        buffer1.release();
        buffer2.release();
        buffer3.release();
        buffer4.release();
        buffer5.release();
        buffer6.release();

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(6);

        SystemInfoResult systemInfoResult = byteBufferPool.getSystemInfo();
        LOGGER.info("health: {}", systemInfoResult);

        Map<Integer, Long> counts = (Map<Integer, Long>) systemInfoResult.getDetails().get("Buffer capacity counts");

        assertThat(counts).containsExactly(
                entry(1, 2L),
                entry(10, 3L),
                entry(100, 1L));
    }


    @Test
    void testGetByteBuffer_fail() {
        assertThatThrownBy(() -> {
            final ByteBufferPool byteBufferPool = getByteBufferPool();
            assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(0);
            PooledByteBuffer pooledByteBuffer = byteBufferPool.getPooledByteBuffer(10);
            pooledByteBuffer.getByteBuffer();
            pooledByteBuffer.release();

            assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(1);

            // will throw exception as buffer has been released
            pooledByteBuffer.getByteBuffer();
        }).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testGetByteBuffer() {
        int capacity = 10;
        final ByteBufferPool byteBufferPool = getByteBufferPool();
        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(0);
        PooledByteBuffer pooledByteBuffer = byteBufferPool.getPooledByteBuffer(capacity);

        ByteBuffer theBuffer = pooledByteBuffer.getByteBuffer();

        assertThat(theBuffer.capacity()).isEqualTo(capacity);
    }

    private void assertPoolSizeAfterMultipleConcurrentGetRequests(final int threadCount, final int minCapacity, final ByteBufferPool byteBufferPool) {
        final CountDownLatch countDownLatch = new CountDownLatch(threadCount);
        final ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        final List<CompletableFuture<Void>> completableFutures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {

            completableFutures.add(CompletableFuture.runAsync(() -> {

                final PooledByteBuffer pooledByteBuffer = byteBufferPool.getPooledByteBuffer(minCapacity);
                pooledByteBuffer.getByteBuffer();
                countDownLatch.countDown();
//                LOGGER.debug("latch count {}", countDownLatch.getCount());

                try {
                    // wait for all threads to have got a new buffer from the pool
                    countDownLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Thread interrupted", e);
                }
                pooledByteBuffer.release();
            }, executorService));
        }

        completableFutures.forEach(completableFuture -> {
            try {
                completableFuture.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        });

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(threadCount);

        completableFutures.clear();
    }

    @Test
    void testSinglePoolPerformance() throws ExecutionException, InterruptedException {
        final int threads = 10;
        // Set to true for profiling in visualvm
        final boolean inProfilingMode = false;

        final int iterations = inProfilingMode
                ? 5_000_000
                :1_000;

        if (inProfilingMode) {
//         wait for visualvm to spin up
            sleep(10_000);
        }

        final ExecutorService executorService = Executors.newFixedThreadPool(threads);
        final ByteBufferPool byteBufferPool = getByteBufferPool();

        doPerfTest(iterations, byteBufferPool, executorService);
    }

    //    @Disabled // for manual perf testing only
    @Test
    void testPoolPerformanceComparison() throws ExecutionException, InterruptedException {

        final int threads = 10;
        // Set to true for profiling in visualvm
        final boolean inProfilingMode = false;

        final int iterations = inProfilingMode
                ? 5_000_000
                :1_000;

        if (inProfilingMode) {
//         wait for visualvm to spin up
            sleep(10_000);
        }

        final ExecutorService executorService = Executors.newFixedThreadPool(threads);
        final ByteBufferPool nonPooledByteBufferPool = new NonPooledByteBufferPool();
        final ByteBufferPool byteBufferPool = new ByteBufferPoolImpl();
        final ByteBufferPool byteBufferPool2 = new ByteBufferPoolImpl2();
        final ByteBufferPool byteBufferPool3 = new ByteBufferPoolImpl3();
        final ByteBufferPool byteBufferPool4 = new ByteBufferPoolImpl4(new ReferenceDataConfig());
        final ByteBufferPool byteBufferPool5 = new ByteBufferPoolImpl5();

        // Do them all twice so on the second run the jvm is all warmed up and the pools
        // have buffers in the pool
        IntStream.rangeClosed(1, 2).forEach(i -> {
            try {
                doPerfTest(iterations, nonPooledByteBufferPool, executorService);
                doPerfTest(iterations, byteBufferPool, executorService);
                doPerfTest(iterations, byteBufferPool2, executorService);
                doPerfTest(iterations, byteBufferPool3, executorService);
                doPerfTest(iterations, byteBufferPool4, executorService);
                doPerfTest(iterations, byteBufferPool5, executorService);

                LOGGER.info("---------------------------------------------------------");
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void simulateUsingBuffer(final ByteBuffer buffer, final int requestedCapacity) {
        // Check buffer is in a ready state for us
        Assertions.assertThat(buffer.capacity())
                .isGreaterThanOrEqualTo(requestedCapacity);
        Assertions.assertThat(buffer.position())
                .isEqualTo(0);
        Assertions.assertThat(buffer.limit())
                .isEqualTo(buffer.capacity());

        // Fill the buffer up to the capacity we asked for as the buffer may be bigger than what
        // we need.
        for (int i = 0; i < requestedCapacity; i++) {
            buffer.put((byte) 1);
        }
    }

    private void doPerfTest(final int iterations,
                            final ByteBufferPool byteBufferPool,
                            final ExecutorService executorService) throws ExecutionException, InterruptedException {

        LOGGER.info("Using pool {}", byteBufferPool.getClass().getName());

        CountDownLatch countDownLatch = new CountDownLatch(1);

        //use consistent seed for a common set of random numbers for each run
        Random random = new Random(RANDOM_SEED);

        LOGGER.info("Scheduling tasks");
        // submit all the runnables but they will wait till the countDownLatch is counted down
        final List<? extends Future<?>> futures = IntStream.rangeClosed(1, iterations)
                .boxed()
                .map(i -> {
                    return executorService.submit(() -> {
                        try {
                            // wait till all tasks are scheduled.
                            countDownLatch.await(10, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        }
                        int capacity = random.nextInt(2000) + 1;

                        // Using the pool
                        try (PooledByteBuffer pooledByteBuffer = byteBufferPool.getPooledByteBuffer(capacity)) {
                            ByteBuffer buffer = pooledByteBuffer.getByteBuffer();
                            simulateUsingBuffer(buffer, capacity);
                        }

                    });
                })
                .collect(Collectors.toList());

        final Instant startTime = Instant.now();

        LOGGER.info("About to release the latch");
        // release the tasks
        countDownLatch.countDown();

        for (Future<?> future : futures) {
            future.get();
        }

        LOGGER.info("Duration for {} is {}",
                byteBufferPool.getClass().getSimpleName(), Duration.between(startTime, Instant.now()).toMillis());
        LOGGER.info("System info:{}", byteBufferPool.getSystemInfo());
    }

    private void getAndReleaseBuffer(ByteBufferPool byteBufferPool, int minCapacity) {
        //will create a new buffer
        PooledByteBuffer pooledByteBuffer = byteBufferPool.getPooledByteBuffer(minCapacity);
        pooledByteBuffer.getByteBuffer();
        pooledByteBuffer.release();
    }

    private void sleep(final long millis) {
        // Wait for visualvm to spin up
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static final class NonPooledByteBufferPool implements ByteBufferPool {

        private ByteBuffer getBuffer(final int minCapacity) {
            return ByteBuffer.allocateDirect(minCapacity);
        }

        @Override
        public PooledByteBuffer getPooledByteBuffer(final int minCapacity) {
            return new PooledByteBuffer(() -> this.getBuffer(minCapacity), byteBuffer -> {});
        }

        @Override
        public PooledByteBufferPair getPooledBufferPair(final int minKeyCapacity, final int minValueCapacity) {
            return new PooledByteBufferPair(
                    byteBuffer -> {},
                    ByteBuffer.allocateDirect(minKeyCapacity),
                    ByteBuffer.allocateDirect(minValueCapacity));
        }

        @Override
        public <T> T getWithBuffer(final int minCapacity, final Function<ByteBuffer, T> work) {
            final ByteBuffer buffer = getBuffer(minCapacity);
            return work.apply(buffer);
        }

        @Override
        public void doWithBuffer(final int minCapacity, final Consumer<ByteBuffer> work) {
            final ByteBuffer buffer = getBuffer(minCapacity);
            work.accept(buffer);
        }

        @Override
        public int getCurrentPoolSize() {
            return 0;
        }

        @Override
        public void clear() {

        }

        @Override
        public SystemInfoResult getSystemInfo() {
            return new SystemInfoResult("Unpooled", "desc", new HashMap<>());
        }
    }

}