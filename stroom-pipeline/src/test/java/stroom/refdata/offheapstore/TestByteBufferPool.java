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

import com.codahale.metrics.health.HealthCheck;
import com.google.common.util.concurrent.Striped;
import org.assertj.core.api.Assertions;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

public class TestByteBufferPool {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestByteBufferPool.class);

    private static final long RANDOM_SEED = 345649236493L;

    @Test
    public void doWithBuffer() {

        ByteBufferPool byteBufferPool = new ByteBufferPool();

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
    public void doWithBuffer_differentSize() {

        ByteBufferPool byteBufferPool = new ByteBufferPool();

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(0);

        getAndReleaseBuffer(byteBufferPool, 10);

        //will use the 10 buffer
        getAndReleaseBuffer(byteBufferPool, 1);

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(1);

        //will create a new buffer
        getAndReleaseBuffer(byteBufferPool, 1000);

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(2);

        int minCapacity = 100;
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
    public void testBufferClearing() {
        ByteBufferPool byteBufferPool = new ByteBufferPool();

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
    public void testConcurrency() throws InterruptedException {
        int threadCount = 50;
        int minCapacity = 10;
        final ByteBufferPool byteBufferPool = new ByteBufferPool();

        assertPoolSizeAfterMultipleConcurrentGetRequests(threadCount, minCapacity, byteBufferPool);

        //re-run the same thing and the pool size should be the same at the end
        assertPoolSizeAfterMultipleConcurrentGetRequests(threadCount, minCapacity, byteBufferPool);
    }

    @Test
    public void testGetHealthCheck() {
        final ByteBufferPool byteBufferPool = new ByteBufferPool();

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

        HealthCheck.Result result = byteBufferPool.getHealth();
        LOGGER.info("health: {}", result);

        Map<Integer, Long> counts = (Map<Integer, Long>) result.getDetails().get("Buffer capacity counts");

        assertThat(counts).containsExactly(
                Assertions.entry(1, 2L),
                Assertions.entry(10, 3L),
                Assertions.entry(100, 1L));
    }


    @Test(expected = IllegalStateException.class)
    public void testGetByteBuffer_fail() {
        final ByteBufferPool byteBufferPool = new ByteBufferPool();
        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(0);
        PooledByteBuffer pooledByteBuffer = byteBufferPool.getPooledByteBuffer(10);
        pooledByteBuffer.getByteBuffer();
        pooledByteBuffer.release();

        assertThat(byteBufferPool.getCurrentPoolSize()).isEqualTo(1);

        // will throw exception as buffer has been released
        pooledByteBuffer.getByteBuffer();
    }

    @Test
    public void testGetByteBuffer() {
        int capacity = 10;
        final ByteBufferPool byteBufferPool = new ByteBufferPool();
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

                PooledByteBuffer pooledByteBuffer = byteBufferPool.getPooledByteBuffer(minCapacity);
                ByteBuffer byteBuffer = pooledByteBuffer.getByteBuffer();
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

    @Ignore // for manual perf testing only
    @Test
    public void testPoolPerformance() {
        final ByteBufferPool byteBufferPool = new ByteBufferPool();
        final SimpleByteBufferPool simpleByteBufferPool = new SimpleByteBufferPool();

        final IntConsumer pooledMethod = minCapacity -> {
            try (PooledByteBuffer pooledByteBuffer = byteBufferPool.getPooledByteBuffer(minCapacity)){

                ByteBuffer buffer = pooledByteBuffer.getByteBuffer();
                for (int i = 0; i < buffer.limit(); i++) {
                    buffer.put((byte)1);
                }
//                LOGGER.info("requested {}, got {}", minCapacity, buffer.capacity());
            }
        };

        final IntConsumer pooledSimpleMethod = minCapacity -> {
            ByteBuffer buffer = null;
            try {

                buffer = simpleByteBufferPool.getBuffer(minCapacity);
                for (int i = 0; i < buffer.limit(); i++) {
                    buffer.put((byte)1);
                }
//                LOGGER.info("requested {}, got {}", minCapacity, buffer.capacity());
            } finally {
                if (buffer != null) {
                    simpleByteBufferPool.release(buffer);
                }
            }
        };

        final IntConsumer nonPooledMethod = capacity -> {
            ByteBuffer buffer = ByteBuffer.allocateDirect(capacity);
            for (int i = 0; i < buffer.limit(); i++) {
                buffer.put((byte)1);
            }
        };

        doPerfTest("non-pooled", nonPooledMethod);
        doPerfTest("pooled", pooledMethod);
        doPerfTest("pooled (simple)", pooledSimpleMethod);

        LOGGER.info("---------------------------------------------------------");

        // now jvm and pools are warmed up do it again.
        doPerfTest("non-pooled", nonPooledMethod);
        doPerfTest("pooled", pooledMethod);
        doPerfTest("pooled (simple)", pooledSimpleMethod);
    }

    private void doPerfTest(final String name, final IntConsumer method) {

        LOGGER.info("Using method {}", name);

        ExecutorService executorService = Executors.newFixedThreadPool(5);
        CountDownLatch countDownLatch = new CountDownLatch(1);

        //use consistent seed for a common set of random numbers for each run
        Random random = new Random(RANDOM_SEED);

        LOGGER.info("Scheduling tasks");
        // submit all the runnables but they will wait till the countDownLatch is counted down
        IntStream.rangeClosed(1, 10_000_000).forEach(i -> {
            executorService.submit(() -> {
                try {
                    // wait till all tasks are scheduled.
                    countDownLatch.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
                int capacity = random.nextInt(2000) + 1;
                method.accept(capacity);
            });
        });

        Instant startTime = Instant.now();

        LOGGER.info("About to release the latch");
        // release the tasks
        countDownLatch.countDown();

        executorService.shutdown();
        try {
            executorService.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        Instant endTime = Instant.now();

        LOGGER.info("Duration for {} is {}", name, Duration.between(startTime, endTime).toMillis());
    }

    private void getAndReleaseBuffer(ByteBufferPool byteBufferPool, int minCapacity) {
        //will create a new buffer
        PooledByteBuffer pooledByteBuffer = byteBufferPool.getPooledByteBuffer(minCapacity);
        pooledByteBuffer.getByteBuffer();
        pooledByteBuffer.release();
    }

    private static class SimpleByteBufferPool {

        // TODO it would be preferable to use different concurrency constructs to avoid the use
        // of synchronized methods.

        private final TreeMap<Key, ByteBuffer> bufferMap = new TreeMap<>();

        Striped<Lock> stripedLock = Striped.lazyWeakLock(10);

        public synchronized ByteBuffer getBuffer(final int minCapacity) {
            // get a buffer at least as big as minCapacity with the smallest insertionTime
            final Map.Entry<Key, ByteBuffer> entry = bufferMap.ceilingEntry(new Key(minCapacity, 0));

            final ByteBuffer buffer;
            if (entry == null) {
                buffer = ByteBuffer.allocateDirect(minCapacity);
            } else {
                bufferMap.remove(entry.getKey());
                buffer = entry.getValue();
            }
            return buffer;
        }

        public synchronized void release(ByteBuffer buffer) {
            if (buffer != null) {
                if (!buffer.isDirect()) {
                    throw new RuntimeException("Expecting a direct ByteBuffer");
                }
                for (int i = buffer.position(); i < buffer.limit(); i++) {
                    buffer.put((byte)0);
                }
                buffer.clear();

                try {
                    while (true) {
                        final Key key = new Key(buffer.capacity(), System.nanoTime());
                        if (!bufferMap.containsKey(key)) {
                            bufferMap.put(key, buffer);
                            return;
                        }
                        // Buffers are indexed by (capacity, time).
                        // If our key is not unique on the first try, we try again, since the
                        // time will be different.  Since we use nanoseconds, it's pretty
                        // unlikely that we'll loop even once, unless the system clock has a
                        // poor granularity.
                    }
                } catch (Exception e) {
                    // if a buffer is not released back to the pool then it is not the end of the world
                    // is we can just create more as required.
                    throw new RuntimeException("Error releasing buffer back to the pool", e);
                }
            }
        }

        @Override
        public String toString() {
            return "ByteBufferPool{" +
                    "bufferMap=" + bufferMap +
                    '}';
        }

        private static final class Key implements Comparable<Key> {
            private final int capacity;
            private final long insertionTime;

            private final Comparator<Key> comparator = Comparator
                    .comparingInt(Key::getCapacity)
                    .thenComparingLong(Key::getInsertionTime);

            private final int hashCode;

            Key(int capacity, long insertionTime) {
                this.capacity = capacity;
                this.insertionTime = insertionTime;
                this.hashCode = Objects.hash(capacity, insertionTime);
            }

            int getCapacity() {
                return capacity;
            }

            long getInsertionTime() {
                return insertionTime;
            }

            @Override
            public int compareTo(Key other) {
                return comparator.compare(this, other);
            }

            @Override
            public boolean equals(final Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;
                final Key key = (Key) o;
                return capacity == key.capacity &&
                        insertionTime == key.insertionTime;
            }

            @Override
            public int hashCode() {
                return hashCode;
            }
        }
    }

}