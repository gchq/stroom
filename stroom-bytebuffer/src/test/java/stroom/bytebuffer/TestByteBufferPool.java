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

import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBufferPoolImpl6;
import stroom.bytebuffer.impl6.ByteBufferPoolImpl7;
import stroom.util.logging.LogUtil;
import stroom.util.sysinfo.SystemInfoResult;

import org.eclipse.jetty.io.MappedByteBufferPool;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
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
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestByteBufferPool {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestByteBufferPool.class);

    private static final long RANDOM_SEED = 345649236493L;

    private ByteBufferPool getByteBufferPool() {
        return new ByteBufferPoolFactory().getByteBufferPool();
    }

    @Test
    void doWithBuffer() {

        final ByteBufferPool byteBufferPool = getByteBufferPool();

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(0);
        final int minCapacity = 100;
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

        final ByteBufferPool byteBufferPool = getByteBufferPool();

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(0);

        getAndReleaseBuffer(byteBufferPool, 10);

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(1);

        getAndReleaseBuffer(byteBufferPool, 10);

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(1);
    }

    @Test
    void doWithBuffer_differentSize() {

        final ByteBufferPool byteBufferPool = getByteBufferPool();

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(0);

        getAndReleaseBuffer(byteBufferPool, 10);

        //will use the 10 buffer
        getAndReleaseBuffer(byteBufferPool, 2);

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(1);

        //will create a new buffer
        getAndReleaseBuffer(byteBufferPool, 1000);

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(2);

        final int minCapacity = 123;
        final int expectedCapacity = 1000;
        byteBufferPool.doWithBuffer(minCapacity, buffer -> {

            assertThat(buffer).isNotNull();
            assertThat(buffer.isDirect()).isTrue();
            assertThat(buffer.capacity()).isEqualTo(expectedCapacity);

            assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(1);

        });

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(2);
    }

    @Test
    void testBufferReleasing() {
        final ByteBufferPool byteBufferPool = getByteBufferPool();

        final PooledByteBuffer pooledByteBuffer = byteBufferPool.getPooledByteBuffer(10);

        pooledByteBuffer.getByteBuffer().putLong(Long.MAX_VALUE);

        assertThat(pooledByteBuffer.getByteBuffer().position()).isEqualTo(8);
        assertThat(pooledByteBuffer.getByteBuffer().capacity()).isEqualTo(10);

        // hold a ref to the buffer so we can test it later, shouldn't normally do this
        final ByteBuffer firstByteBuffer = pooledByteBuffer.getByteBuffer();

        pooledByteBuffer.close();

        final PooledByteBuffer pooledByteBuffer2 = byteBufferPool.getPooledByteBuffer(10);

        // got same instance from pool
        assertThat(pooledByteBuffer2.getByteBuffer()).isSameAs(firstByteBuffer);

        // buffer has been cleared
        assertThat(pooledByteBuffer2.getByteBuffer().position()).isEqualTo(0);
        assertThat(pooledByteBuffer2.getByteBuffer().capacity()).isEqualTo(10);
    }

    @Test
    void testConcurrency() throws InterruptedException {
        final int threadCount = 50;
        final int minCapacity = 10;
        final ByteBufferPool byteBufferPool = getByteBufferPool();

        assertPoolSizeAfterMultipleConcurrentGetRequests(threadCount, minCapacity, byteBufferPool);

        LOGGER.info(byteBufferPool.getSystemInfo().toString());

        //re-run the same thing and the pool size should be the same at the end
        assertPoolSizeAfterMultipleConcurrentGetRequests(threadCount, minCapacity, byteBufferPool);

        LOGGER.info(byteBufferPool.getSystemInfo().toString());
    }

    @Test
    void testClear() {
        final int threadCount = 50;
        final int minCapacity = 10;
        final ByteBufferPool byteBufferPool = getByteBufferPool();

        assertPoolSizeAfterMultipleConcurrentGetRequests(threadCount, minCapacity, byteBufferPool);

        LOGGER.info(byteBufferPool.getSystemInfo().toString());

        byteBufferPool.clear();

        LOGGER.info(byteBufferPool.getSystemInfo().toString());

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(0);

        //re-run the same thing and the pool size should be the same at the end
        assertPoolSizeAfterMultipleConcurrentGetRequests(threadCount, minCapacity, byteBufferPool);

        LOGGER.info(byteBufferPool.getSystemInfo().toString());
    }

    @Test
    void testGetSystemInfo() {
        final ByteBufferPool byteBufferPool = getByteBufferPool();

        final PooledByteBuffer buffer1 = byteBufferPool.getPooledByteBuffer(1);
        final PooledByteBuffer buffer2 = byteBufferPool.getPooledByteBuffer(1);
        final PooledByteBuffer buffer3 = byteBufferPool.getPooledByteBuffer(10);
        final PooledByteBuffer buffer4 = byteBufferPool.getPooledByteBuffer(10);
        final PooledByteBuffer buffer5 = byteBufferPool.getPooledByteBuffer(10);
        final PooledByteBuffer buffer6 = byteBufferPool.getPooledByteBuffer(100);

        buffer1.getByteBuffer();
        buffer2.getByteBuffer();
        buffer3.getByteBuffer();
        buffer4.getByteBuffer();
        buffer5.getByteBuffer();
        buffer6.getByteBuffer();

        buffer1.close();
        buffer2.close();
        buffer3.close();
        buffer4.close();
        buffer5.close();
        buffer6.close();

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(6);

        final SystemInfoResult systemInfoResult = byteBufferPool.getSystemInfo();
        LOGGER.info("health: {}", systemInfoResult);

        assertQueueSize(systemInfoResult, 1, 2);
        assertQueueSize(systemInfoResult, 10, 3);
        assertQueueSize(systemInfoResult, 100, 1);
    }

    private void assertQueueSize(final SystemInfoResult systemInfoResult,
                                 final int bufferSize,
                                 final int expectedQueueSize) {

        final int actualQueueSize = ((Map<Integer, Map<String, Integer>>) systemInfoResult.getDetails()
                .get("Pooled buffers (grouped by buffer capacity)"))
                .get(bufferSize)
                .get("Buffers available in pool");

        assertThat(actualQueueSize).isEqualTo(expectedQueueSize);
    }


    @Test
    void testGetByteBuffer_fail() {
        assertThatThrownBy(() -> {
            final ByteBufferPool byteBufferPool = getByteBufferPool();
            assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(0);
            final PooledByteBuffer pooledByteBuffer = byteBufferPool.getPooledByteBuffer(10);
            pooledByteBuffer.getByteBuffer();
            pooledByteBuffer.close();

            assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(1);

            // will throw exception as buffer has been released
            pooledByteBuffer.getByteBuffer();
        }).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testGetByteBuffer() {
        final int capacity = 10;
        final ByteBufferPool byteBufferPool = getByteBufferPool();
        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(0);
        final PooledByteBuffer pooledByteBuffer = byteBufferPool.getPooledByteBuffer(capacity);

        final ByteBuffer theBuffer = pooledByteBuffer.getByteBuffer();

        assertThat(theBuffer.capacity()).isEqualTo(capacity);
    }

    private void assertPoolSizeAfterMultipleConcurrentGetRequests(final int threadCount,
                                                                  final int minCapacity,
                                                                  final ByteBufferPool byteBufferPool) {
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
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Thread interrupted", e);
                }
                pooledByteBuffer.close();
            }, executorService));
        }

        completableFutures.forEach(completableFuture -> {
            try {
                completableFuture.get();
            } catch (final InterruptedException | ExecutionException e) {
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
                : 1_000;

        if (inProfilingMode) {
//         wait for visualvm to spin up
            sleep(10_000);
        }

        final ExecutorService executorService = Executors.newFixedThreadPool(threads);
        final ByteBufferPool byteBufferPool = getByteBufferPool();

        doPerfTest(new ArrayList<>(), 1, iterations, byteBufferPool, executorService);
    }

    @TestFactory
    List<DynamicTest> comparePerformance() {
        final List<ByteBufferPool> byteBufferPools = new ArrayList<>();
        byteBufferPools.add(new NonPooledByteBufferPool());
        byteBufferPools.add(new stroom.bytebuffer.ByteBufferPoolImpl());
        byteBufferPools.add(new ByteBufferPoolImpl2());
        byteBufferPools.add(new ByteBufferPoolImpl3());
        byteBufferPools.add(new ByteBufferPoolImpl4(ByteBufferPoolConfig::new));
        byteBufferPools.add(new ByteBufferPoolImpl5());
        byteBufferPools.add(new ByteBufferPoolImpl6(ByteBufferPoolConfig::new));
        byteBufferPools.add(new ByteBufferPoolImpl7(new ByteBufferFactoryImpl()));
        byteBufferPools.add(new JettyByteBufferPool());

        final int threads = 10;
        // Set to true for profiling in visualvm
        final boolean inProfilingMode = true;

        final int rounds = 3;
        final int iterations = inProfilingMode
                ? 100_000_000
                : 1_000;

        if (inProfilingMode) {
//         wait for visualvm to spin up
            sleep(10_000);
        }

//        final ExecutorService executorService = Executors.newFixedThreadPool(threads);

        return byteBufferPools
                .stream()
                .map(pool -> DynamicTest.dynamicTest(pool.getClass().getSimpleName(), () -> {
                    for (int i = 0; i < iterations; i++) {
                        try (final PooledByteBuffer pooledByteBuffer = pool.getPooledByteBuffer(128)) {
                            pooledByteBuffer.getByteBuffer().putLong(1);
                        }
                    }

//                    final List<String> results = new ArrayList<>();
//                    // Do them all twice so on the second run the jvm is all warmed up and the pools
//                    // have buffers in the pool
//                    IntStream.rangeClosed(1, rounds).forEach(testRound -> {
//                        try {
//                            doPerfTest(results, testRound, iterations, opType, executorService);
//                            LOGGER.info("---------------------------------------------------------");
//                        } catch (ExecutionException | InterruptedException e) {
//                            throw new RuntimeException(e);
//                        }
//                    });
                }))
                .collect(Collectors.toList());
    }

    //    @Disabled // for manual perf testing only
    @Test
    void testPoolPerformanceComparison() {

        final int threads = 10;
        // Set to true for profiling in visualvm
        final boolean inProfilingMode = false;

        final int rounds = 3;
        final int iterations = inProfilingMode
                ? 5_000_000
                : 1_000;

        if (inProfilingMode) {
//         wait for visualvm to spin up
            sleep(10_000);
        }

        final ExecutorService executorService = Executors.newFixedThreadPool(threads);
        final ByteBufferPool nonPooledByteBufferPool = new NonPooledByteBufferPool();
        final ByteBufferPool byteBufferPool = new stroom.bytebuffer.ByteBufferPoolImpl();
        final ByteBufferPool byteBufferPool2 = new ByteBufferPoolImpl2();
        final ByteBufferPool byteBufferPool3 = new ByteBufferPoolImpl3();
        final ByteBufferPool byteBufferPool4 = new ByteBufferPoolImpl4(ByteBufferPoolConfig::new);
        final ByteBufferPool byteBufferPool5 = new ByteBufferPoolImpl5();
        final ByteBufferPool jettyByteBufferPool = new JettyByteBufferPool();

        final List<String> results = new ArrayList<>();

        // Do them all twice so on the second run the jvm is all warmed up and the pools
        // have buffers in the pool
        IntStream.rangeClosed(1, rounds).forEach(testRound -> {
            try {
                doPerfTest(results, testRound, iterations, nonPooledByteBufferPool, executorService);
//                doPerfTest(results, testRound, iterations, byteBufferPool, executorService);
//                doPerfTest(results, testRound, iterations, byteBufferPool2, executorService);
//                doPerfTest(results, testRound, iterations, byteBufferPool3, executorService);
                doPerfTest(results, testRound, iterations, byteBufferPool4, executorService);
//                doPerfTest(results, testRound, iterations, byteBufferPool5, executorService);
                doPerfTest(results, testRound, iterations, jettyByteBufferPool, executorService);
//                doPerfTest(results, testRound, iterations, hbaseByteBufferPool, executorService);

                LOGGER.info("---------------------------------------------------------");
            } catch (final ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        LOGGER.info("Result:\n{}", String.join("\n", results));
    }

    private void simulateUsingBuffer(final ByteBuffer buffer, final int requestedCapacity) {
        // Check buffer is in a ready state for us
        if (buffer.capacity() < requestedCapacity) {
            throw new RuntimeException(LogUtil.message("Capacity {} is < that {}",
                    buffer.capacity(), requestedCapacity));
        }
        if (buffer.position() != 0) {
            throw new RuntimeException(LogUtil.message("Invalid position {}", buffer.position()));
        }
//        assertThat(buffer.limit())
//                .isEqualTo(buffer.capacity());

        // Fill the buffer up to the capacity we asked for as the buffer may be bigger than what
        // we need.
//        for (int i = 0; i < requestedCapacity; i++) {
//            buffer.put((byte) 1);
//        }
    }

    private void doPerfTest(final List<String> results,
                            final int testRound,
                            final int iterations,
                            final ByteBufferPool byteBufferPool,
                            final ExecutorService executorService) throws ExecutionException, InterruptedException {

        LOGGER.info("Using pool {}", byteBufferPool.getClass().getName());

        final CountDownLatch countDownLatch = new CountDownLatch(1);

        //use consistent seed for a common set of random numbers for each run
        final Random random = new Random(RANDOM_SEED);

        LOGGER.info("Scheduling tasks");
        // submit all the runnables but they will wait till the countDownLatch is counted down
        final List<? extends Future<?>> futures = IntStream.rangeClosed(1, iterations)
                .boxed()
                .map(i -> {
                    return executorService.submit(() -> {
                        try {
                            // wait till all tasks are scheduled.
                            countDownLatch.await(10, TimeUnit.SECONDS);
                        } catch (final InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException(e);
                        }
                        final int capacity = 500 + random.nextInt(1000) + 1;

                        // Using the pool
                        try (final PooledByteBuffer pooledByteBuffer = byteBufferPool.getPooledByteBuffer(capacity)) {
                            final ByteBuffer buffer = pooledByteBuffer.getByteBuffer();
                            simulateUsingBuffer(buffer, capacity);
                        }

                    });
                })
                .collect(Collectors.toList());

        final Instant startTime = Instant.now();

        LOGGER.info("About to release the latch");
        // release the tasks
        countDownLatch.countDown();

        for (final Future<?> future : futures) {
            future.get();
        }

        final Duration duration = Duration.between(startTime, Instant.now());
        final String msg = LogUtil.message("{}, round {}, duration {}",
                byteBufferPool.getClass().getSimpleName(),
                testRound,
                duration.toMillis());
        LOGGER.info(msg);
        results.add(msg);
        LOGGER.info("System info:{}", byteBufferPool.getSystemInfo());
    }

    private void getAndReleaseBuffer(final ByteBufferPool byteBufferPool, final int minCapacity) {
        //will create a new buffer
        final PooledByteBuffer pooledByteBuffer = byteBufferPool.getPooledByteBuffer(minCapacity);
        pooledByteBuffer.getByteBuffer();
        pooledByteBuffer.close();
    }

    private void sleep(final long millis) {
        // Wait for visualvm to spin up
        try {
            Thread.sleep(millis);
        } catch (final InterruptedException e) {
            throw new RuntimeException(e);
        }
    }


    // --------------------------------------------------------------------------------


    private static final class NonPooledByteBufferPool implements ByteBufferPool {

        private ByteBuffer getBuffer(final int minCapacity) {
            return ByteBuffer.allocateDirect(minCapacity);
        }

        @Override
        public PooledByteBuffer getPooledByteBuffer(final int minCapacity) {
            return new PooledByteBufferImpl(
                    () -> this.getBuffer(minCapacity),
                    ByteBufferSupport::unmap);
        }

        @Override
        public PooledByteBufferPair getPooledBufferPair(final int minKeyCapacity, final int minValueCapacity) {
            return new PooledByteBufferPairImpl(
                    ByteBufferSupport::unmap,
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
        public void doWithBufferPair(final int minKeyCapacity,
                                     final int minValueCapacity,
                                     final BiConsumer<ByteBuffer, ByteBuffer> work) {
            final ByteBuffer keyBuffer = getBuffer(minKeyCapacity);
            final ByteBuffer valueBuffer = getBuffer(minValueCapacity);
            work.accept(keyBuffer, valueBuffer);
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


    // --------------------------------------------------------------------------------


    private static final class JettyByteBufferPool implements ByteBufferPool {

        private final org.eclipse.jetty.io.ByteBufferPool delegatePool = new MappedByteBufferPool();

        private ByteBuffer getBuffer(final int minCapacity) {
            final ByteBuffer byteBuffer = delegatePool.acquire(minCapacity, true);

            // Jetty is meant to set the limit but doesn't seem to
            byteBuffer.limit(byteBuffer.capacity());
            return byteBuffer;
        }

        @Override
        public PooledByteBuffer getPooledByteBuffer(final int minCapacity) {
            return new PooledByteBufferImpl(
                    () ->
                            getBuffer(minCapacity),
                    delegatePool::release);
        }

        @Override
        public PooledByteBufferPair getPooledBufferPair(final int minKeyCapacity, final int minValueCapacity) {
            return new PooledByteBufferPairImpl(
                    delegatePool::release,
                    getBuffer(minKeyCapacity),
                    getBuffer(minKeyCapacity));
        }

        @Override
        public int getCurrentPoolSize() {
            return -1;
        }

        @Override
        public SystemInfoResult getSystemInfo() {
            return null;
        }
    }
}
