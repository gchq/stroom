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

package stroom.bytebuffer;

import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBufferPoolImpl6;
import stroom.bytebuffer.impl6.ByteBufferPoolImpl7;
import stroom.util.io.ByteSize;
import stroom.util.json.JsonUtil;
import stroom.util.logging.AsciiTable;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.sysinfo.SystemInfoResult;

import com.google.common.base.Strings;
import jakarta.inject.Provider;
import org.eclipse.jetty.io.MappedByteBufferPool;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.Map.Entry;
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
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestByteBufferPool {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestByteBufferPool.class);

    private static final long RANDOM_SEED = 345649236493L;

    private ByteBufferPool getByteBufferPool() {
        return SimpleByteBufferPoolFactory.getByteBufferPool(new ByteBufferPoolConfig()
                .withPooledByteBufferCounts(
                        Map.ofEntries(
                                Map.entry(10, 1_000),
                                Map.entry(100, 1_000),
                                Map.entry(1_000, 1_000),
                                Map.entry(10_000, 1_000),
                                Map.entry(100_000, 1_000))));
    }

    @Test
    void doWithBuffer() {

        final ByteBufferPool byteBufferPool = getByteBufferPool();

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

        final ByteBufferPool byteBufferPool = getByteBufferPool();

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
    void testBufferReleasing() {
        ByteBufferPool byteBufferPool = getByteBufferPool();

        PooledByteBuffer pooledByteBuffer = byteBufferPool.getPooledByteBuffer(10);

        pooledByteBuffer.getByteBuffer().putLong(Long.MAX_VALUE);

        assertThat(pooledByteBuffer.getByteBuffer().position()).isEqualTo(8);
        assertThat(pooledByteBuffer.getByteBuffer().capacity()).isEqualTo(10);

        // hold a ref to the buffer so we can test it later, shouldn't normally do this
        ByteBuffer firstByteBuffer = pooledByteBuffer.getByteBuffer();

        pooledByteBuffer.close();

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
    void testClear() {
        int threadCount = 50;
        int minCapacity = 10;
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

        final PooledByteBuffer buffer1 = byteBufferPool.getPooledByteBuffer(1); // treated as 10
        final PooledByteBuffer buffer2 = byteBufferPool.getPooledByteBuffer(1); // treated as 10
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

        SystemInfoResult systemInfoResult = byteBufferPool.getSystemInfo();
        LOGGER.info("health: {}", JsonUtil.writeValueAsString(systemInfoResult));

        assertQueueSize(systemInfoResult, 10, 2 + 3); // size 1 + size 10
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
            PooledByteBuffer pooledByteBuffer = byteBufferPool.getPooledByteBuffer(10);
            pooledByteBuffer.getByteBuffer();
            pooledByteBuffer.close();

            assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(1);

            // will throw exception as buffer has been released
            pooledByteBuffer.getByteBuffer();
        }).isInstanceOf(NullPointerException.class);
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
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Thread interrupted", e);
                }
                pooledByteBuffer.close();
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
                : 1_000;

        if (inProfilingMode) {
//         wait for visualvm to spin up
            sleep(10_000);
        }

        final ExecutorService executorService = Executors.newFixedThreadPool(threads);
        final ByteBufferPool byteBufferPool = getByteBufferPool();

        doPerfTest(new Results(), 1, iterations, byteBufferPool, executorService, threads);
    }

    @Disabled
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
        byteBufferPools.add(new ByteBufferPoolImpl8(ByteBufferPoolConfig::new));
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

    @Disabled // for manual perf testing only
    @Test
    void testPoolPerformanceComparison() {

        final int threads = 20;
        // Set to true for profiling in visualvm
        final boolean inProfilingMode = false;

        final int rounds = 3;
//        final int rounds = 1;
        final int iterations = inProfilingMode
                ? 5_000_000
                : 1_000_000;
//                : 200_000;

        if (inProfilingMode) {
//         wait for visualvm to spin up
            sleep(10_000);
        }

        ByteBufferPoolConfig poolConfig = new ByteBufferPoolConfig()
                .withPooledByteBufferCounts(Map.of(
                        10, 100,
                        100, 100,
                        1_000, 100,
                        10_000, 100,
                        100_000, 100,
                        1_000_000, 100
                ));
        final Provider<ByteBufferPoolConfig> configSupplier = () -> poolConfig;

        final ExecutorService executorService = Executors.newFixedThreadPool(threads);
        final ByteBufferPool nonPooledByteBufferPool = new NonPooledByteBufferPool();
//        final ByteBufferPool byteBufferPool = new stroom.bytebuffer.ByteBufferPoolImpl();
//        final ByteBufferPool byteBufferPool2 = new ByteBufferPoolImpl2();
//        final ByteBufferPool byteBufferPool3 = new ByteBufferPoolImpl3();
        final ByteBufferPool byteBufferPool4 = new ByteBufferPoolImpl4(configSupplier);
        final ByteBufferPool byteBufferPool5 = new ByteBufferPoolImpl5();
        final ByteBufferPool byteBufferPool6 = new ByteBufferPoolImpl6(configSupplier);
//        final ByteBufferPool byteBufferPool7 = new ByteBufferPoolImpl7(new ByteBufferFactoryImpl());
        final ByteBufferPool byteBufferPool8 = new ByteBufferPoolImpl8(configSupplier);
        final ByteBufferPool jettyByteBufferPool = new JettyByteBufferPool();

        final Results results = new Results();

        // Do them all twice so on the second run the jvm is all warmed up and the pools
        // have buffers in the pool
        IntStream.rangeClosed(1, rounds).forEach(testRound -> {
            try {
//                doPerfTest(results, testRound, iterations, nonPooledByteBufferPool, executorService, threads);
//                doPerfTest(results, testRound, iterations, byteBufferPool, executorService, threads);
//                doPerfTest(results, testRound, iterations, byteBufferPool2, executorService, threads);
//                doPerfTest(results, testRound, iterations, byteBufferPool3, executorService, threads);
                doPerfTest(results, testRound, iterations, byteBufferPool4, executorService, threads);
//                doPerfTest(results, testRound, iterations, byteBufferPool5, executorService, threads);
                doPerfTest(results, testRound, iterations, byteBufferPool6, executorService, threads);
                doPerfTest(results, testRound, iterations, byteBufferPool8, executorService, threads);
//                doPerfTest(results, testRound, iterations, jettyByteBufferPool, executorService, threads);
//                doPerfTest(results, testRound, iterations, hbaseByteBufferPool, executorService, threads);

                LOGGER.info("---------------------------------------------------------");
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });

        LOGGER.info("--------------------------------------------------------------------------------");
        LOGGER.info("Impl6:\n{}", JsonUtil.writeValueAsString(byteBufferPool6.getSystemInfo().getDetails()));
        LOGGER.info("--------------------------------------------------------------------------------");
        LOGGER.info("Impl8:\n{}", JsonUtil.writeValueAsString(byteBufferPool8.getSystemInfo().getDetails()));
        LOGGER.info("--------------------------------------------------------------------------------");
        LOGGER.info("Result (values are total duration in millis for {} iterations):\n{}",
                ModelStringUtil.formatCsv(iterations), results);
    }

    @Test
    void testMxBeans() {
        final ByteBufferPool byteBufferPool = getByteBufferPool();

        final int cnt = 100;
        final int cap = 100;
        final List<PooledByteBuffer> buffers = new ArrayList<>();
        for (int i = 0; i < cnt; i++) {
            final PooledByteBuffer pooledByteBuffer = byteBufferPool.getPooledByteBuffer(cap);
            buffers.add(pooledByteBuffer);
        }
        logBufferPoolInfo();
        LOGGER.info("Get {} {}b buffers", cnt, cap);
        buffers.forEach(PooledByteBuffer::getByteBuffer);
        logBufferPoolInfo();
        buffers.forEach(PooledByteBuffer::close);
    }

    private static void logBufferPoolInfo() {
        final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        final MemoryUsage heapMemoryUsage = memoryMXBean.getHeapMemoryUsage();
        LOGGER.info("Heap - Init: {}, Committed: {}, Max: {}, Used: {}",
                ByteSize.ofBytes(heapMemoryUsage.getInit()),
                ByteSize.ofBytes(heapMemoryUsage.getCommitted()),
                ByteSize.ofBytes(heapMemoryUsage.getMax()),
                ByteSize.ofBytes(heapMemoryUsage.getUsed()));

        final MemoryUsage nonHeapMemoryUsage = memoryMXBean.getNonHeapMemoryUsage();
        LOGGER.info("NonHeap - Init: {}, Committed: {}, Max: {}, Used: {}",
                ByteSize.ofBytes(nonHeapMemoryUsage.getInit()),
                ByteSize.ofBytes(nonHeapMemoryUsage.getCommitted()),
                nonHeapMemoryUsage.getMax(),
                ByteSize.ofBytes(nonHeapMemoryUsage.getUsed()));

        ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)
                .stream()
                .filter(pool -> "direct".equalsIgnoreCase(pool.getName())
                        || "mapped".equalsIgnoreCase(pool.getName()))
                .forEach(pool ->
                        LOGGER.info("Pool: {}, count: {}, used: {}, capacity: {}",
                                pool.getName(),
                                pool.getCount(),
                                ByteSize.ofBytes(pool.getMemoryUsed()),
                                ByteSize.ofBytes(pool.getTotalCapacity())));
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

    private void doPerfTest(final Results results,
                            final int testRound,
                            final int iterations,
                            final ByteBufferPool byteBufferPool,
                            final ExecutorService executorService,
                            final int threads) throws ExecutionException, InterruptedException {


        final int[] capacities = new int[]{5, 50, 500, 5_000, 50_000, 500_000};
        for (final int capacity : capacities) {
            final Supplier<Integer> supplier = () -> capacity;
            doPerfTest(
                    results, testRound, iterations, byteBufferPool, executorService, threads, supplier,
                    String.valueOf(capacity));
        }

        //use consistent seed for a common set of random numbers for each run
        final Random random = new Random(RANDOM_SEED);
        final Supplier<Integer> supplier = () -> capacities[random.nextInt(capacities.length)];
        doPerfTest(
                results, testRound, iterations, byteBufferPool, executorService, threads, supplier,
                "random");

    }

    private void doPerfTest(final Results results,
                            final int testRound,
                            final int iterations,
                            final ByteBufferPool byteBufferPool,
                            final ExecutorService executorService,
                            final int threads,
                            final Supplier<Integer> capacitySupplier,
                            final String runName) throws ExecutionException, InterruptedException {

        LOGGER.info("Pool: {}, round: {}, runName: {}",
                byteBufferPool.getClass().getSimpleName(), testRound, runName);

        CountDownLatch countDownLatch = new CountDownLatch(1);

        //use consistent seed for a common set of random numbers for each run
//        final Random random = new Random(RANDOM_SEED);
//        final int[] capacities = new int[]{5, 50, 500, 5_000, 50_000, 500_000};

//        LOGGER.info("Scheduling tasks");
        // submit all the runnables, but they will wait till the countDownLatch is counted down to
        // actually run
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
//                        int capacity = 1 + random.nextInt(10_000) + 1;
//                        int capacity = 1 + random.nextInt(100);
//                        int capacity = capacities[random.nextInt(capacities.length)];
                        int capacity = capacitySupplier.get();

                        // Using the pool
                        try (PooledByteBuffer pooledByteBuffer = byteBufferPool.getPooledByteBuffer(capacity)) {
                            ByteBuffer buffer = pooledByteBuffer.getByteBuffer();
                            simulateUsingBuffer(buffer, capacity);
                        }
                    });
                })
                .collect(Collectors.toList());

        final Instant startTime = Instant.now();

//        LOGGER.info("About to release the latch");
        // release the tasks
        countDownLatch.countDown();

        for (Future<?> future : futures) {
            future.get();
        }

        final Duration duration = Duration.between(startTime, Instant.now());
        results.putResult(
                byteBufferPool.getClass(),
                testRound,
                runName,
                duration);

//        final String msg = LogUtil.message("{} round {}, run {} duration {}",
//                Strings.padEnd(byteBufferPool.getClass().getSimpleName() + ",", 30, ' '),
//                testRound,
//                Strings.padEnd(runName, 30, ' '),
//                duration.toMillis());
//        LOGGER.info("\n{}", results.toString());
//        LOGGER.info("System info:{}", byteBufferPool.getSystemInfo());
    }

    private void getAndReleaseBuffer(ByteBufferPool byteBufferPool, int minCapacity) {
        //will create a new buffer
        PooledByteBuffer pooledByteBuffer = byteBufferPool.getPooledByteBuffer(minCapacity);
        pooledByteBuffer.getByteBuffer();
        pooledByteBuffer.close();
    }

    private void sleep(final long millis) {
        // Wait for visualvm to spin up
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
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


    // --------------------------------------------------------------------------------


    private static class Results {

        // Map of roundNum => Map of className => Map of runName => duration
        private final Map<Integer, Map<String, Map<String, Duration>>> map = new HashMap<>();

        void putResult(final Class<?> clazz,
                       final int round,
                       final String runName,
                       final Duration duration) {
            map.computeIfAbsent(round, k -> new HashMap<>())
                    .computeIfAbsent(clazz.getSimpleName(), k -> new HashMap<>())
                    .put(runName, duration);
        }

        private String padRight(final String str) {
            return Strings.padEnd(str, 30, ' ');
        }

        private String padRun(final String str) {
            return Strings.padStart(str, 10, ' ');
        }

        private String padBar(final String str) {
            return Strings.padEnd(str, 10, ' ');
        }

        private String padLeft(final String str) {
            return Strings.padStart(str, 10, ' ');
        }

        @Override
        public String toString() {
            final List<String> lines = new ArrayList<>();
            final List<String> runNames = map.values()
                    .stream()
                    .flatMap(entry -> entry.values()
                            .stream()
                            .flatMap(entry2 -> entry2.keySet().stream()))
                    .distinct()
                    .sorted()
                    .toList();
            final LongSummaryStatistics durationStats = map.values()
                    .stream()
                    .flatMap(map2 -> map2.values()
                            .stream()
                            .flatMap(map3 -> map3.values()
                                    .stream()))
                    .mapToLong(Duration::toMillis)
                    .summaryStatistics();


            final List<String> headings = new ArrayList<>();
            headings.add(padRight("Class"));
            runNames.forEach(name -> {
                headings.add(padRun(name));
                headings.add(padBar("Bar"));
            });

            final String delimiter = ", ";
            final String headingRow = String.join(delimiter, headings);

            map.entrySet().stream()
                    .sorted(Entry.comparingByKey())
                    .forEach(roundEntry -> {
                        int roundNum = roundEntry.getKey();
                        var mapByClassName = roundEntry.getValue();
                        lines.add("Round " + roundNum);
                        lines.add(headingRow);
                        mapByClassName.entrySet()
                                .stream()
                                .sorted(Entry.comparingByKey())
                                .forEach(classNameEntry -> {
                                    final List<String> values = new ArrayList<>();
                                    final String className = classNameEntry.getKey();
                                    final var mapByRunName = classNameEntry.getValue();
                                    values.add(padRight(className));
                                    runNames.forEach(runName -> {
                                        final Duration duration = mapByRunName.get(runName);
                                        final long millis = duration.toMillis();
                                        values.add(padRun(ModelStringUtil.formatCsv(millis)));
                                        values.add(padBar(AsciiTable.asciiBar(
                                                millis,
                                                durationStats.getMin(),
                                                durationStats.getMax(),
                                                10)));
                                    });
                                    lines.add(String.join(delimiter, values));
                                });
                        lines.add("");
                    });
            return String.join("\n", lines);
        }
    }
}
