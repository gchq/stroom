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

import stroom.util.HasHealthCheck;
import stroom.util.sysinfo.SystemInfoResult;

import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * An bounded self-populating pool of directly allocated ByteBuffers.
 * The pool holds buffers in a fixed set of sizes and any request for a buffer
 * will result in a buffer with capacity >= the requested capacity being
 * returned.
 * All buffers are cleared ready for use when obtained from the pool.
 * Once a buffer has been returned to the pool it MUST not be used else
 * bad things will happen.
 * <p>
 * This impl uses lock free {@link ConcurrentLinkedQueue} and {@link Semaphore}
 */
@Singleton
public class ByteBufferPoolImpl5 implements ByteBufferPool {

    private static final Logger LOGGER = LoggerFactory.getLogger(ByteBufferPoolImpl5.class);

    private static final int[] SIZES = {
            1,
            10,
            100,
            1_000,
            10_000,
            100_000,
            1_000_000,
            10_000_000,
            100_000_000};
    private static final int DEFAULT_MAX_BUFFERS_PER_QUEUE = 50;

    private static final int[] MAX_BUFFER_COUNTS = {
            DEFAULT_MAX_BUFFERS_PER_QUEUE, // 1
            DEFAULT_MAX_BUFFERS_PER_QUEUE, // 10
            DEFAULT_MAX_BUFFERS_PER_QUEUE, // 100
            DEFAULT_MAX_BUFFERS_PER_QUEUE, // 1_000
            DEFAULT_MAX_BUFFERS_PER_QUEUE, // 10_000
            20, // 100_000
            5, // 1_000_000
            2, // 10_000_000
            1}; // 100_000_000

    private final List<Queue<ByteBuffer>> bufferQueues = new ArrayList<>(SIZES.length);
    // This is the number of buffers in the pool or currently released (for each offset)
    private final List<AtomicInteger> pooledBufferCounts = new ArrayList<>(SIZES.length);
    // The semaphore holds a permit for each buffer available in the pool (for each offset)
    private final List<Semaphore> semaphores = new ArrayList<>(SIZES.length);

    public ByteBufferPoolImpl5() {
        if (SIZES.length != MAX_BUFFER_COUNTS.length) {
            throw new RuntimeException("Size mismatch");
        }

        // initialise all the queues and counters for each size offset
        for (int i = 0; i < SIZES.length; i++) {
            bufferQueues.add(new ConcurrentLinkedQueue<>());
            pooledBufferCounts.add(new AtomicInteger(0));

            // Start with no permits as the pool is empty
            semaphores.add(new Semaphore(0, true));
        }
    }

    @Override
    public PooledByteBuffer getPooledByteBuffer(final int minCapacity) {
        return new PooledByteBufferImpl(() -> getBuffer(minCapacity), this::release);
    }

    private ByteBuffer getBuffer(final int minCapacity) {
        final int offset = getOffset(minCapacity);
        final Queue<ByteBuffer> byteBufferQueue = getByteBufferQueue(offset);
        final Semaphore semaphore = getSemaphore(offset);

        final boolean havePermit = semaphore.tryAcquire();
        ByteBuffer buffer;

        if (havePermit) {
            // Queue could be empty at this point, i.e. fresh pool or all are in use.
            buffer = byteBufferQueue.poll();
            if (buffer == null) {
                // This should not really  happen but if there was nothing in the queue then we
                // need to give back our permit.
                semaphore.release();
            }
        } else {
            // No buffers in the pool to use so we need to create one if the pool is not full
            buffer = createNewBufferIfAllowed(offset);
        }

        if (buffer == null) {
            try {
                // Must be at max pooled buffers so we have to wait for another thread to release a permit
                semaphore.acquire();
                // Now we have a permit we can safely get a buffer from the queue
                buffer = byteBufferQueue.poll();
            } catch (final InterruptedException e) {
                LOGGER.error("Thread interrupted", e);
                Thread.currentThread().interrupt();
            }
        }

        Objects.requireNonNull(buffer);
        // Ensure the buffer is ready for use with limits/positions/marks cleared
        buffer.clear();
        return buffer;
    }

    private void release(final ByteBuffer buffer) {
        if (buffer != null && buffer.isDirect()) {
            // Work out which queue to put the buffer back on
            final int offset = getOffset(buffer.capacity());
            final Queue<ByteBuffer> byteBufferQueue = getByteBufferQueue(offset);
            final Semaphore semaphore = getSemaphore(offset);
            byteBufferQueue.offer(buffer);
            // Now release a permit so any thread waiting on this semaphore can now grab the buffer
            // from the queue
            semaphore.release();
        }
    }

    @Override
    public PooledByteBufferPair getPooledBufferPair(final int minKeyCapacity, final int minValueCapacity) {
        final ByteBuffer keyBuffer = getBuffer(minKeyCapacity);
        final ByteBuffer valueBuffer = getBuffer(minValueCapacity);
        return new PooledByteBufferPairImpl(this::release, keyBuffer, valueBuffer);
    }

    @Override
    public <T> T getWithBuffer(final int minCapacity, final Function<ByteBuffer, T> work) {
        ByteBuffer buffer = null;
        try {
            buffer = getBuffer(minCapacity);
            return work.apply(buffer);
        } finally {
            if (buffer != null) {
                release(buffer);
            }
        }
    }

    @Override
    public void doWithBuffer(final int minCapacity, final Consumer<ByteBuffer> work) {
        ByteBuffer buffer = null;
        try {
            buffer = getBuffer(minCapacity);
            work.accept(buffer);
        } finally {
            if (buffer != null) {
                release(buffer);
            }
        }
    }

    @Override
    public void doWithBufferPair(final int minKeyCapacity,
                                 final int minValueCapacity,
                                 final BiConsumer<ByteBuffer, ByteBuffer> work) {
        ByteBuffer keyBuffer = null;
        ByteBuffer valueBuffer = null;
        try {
            keyBuffer = getBuffer(minKeyCapacity);
            valueBuffer = getBuffer(minValueCapacity);
            work.accept(keyBuffer, valueBuffer);
        } finally {
            if (keyBuffer != null) {
                release(keyBuffer);
            }
            if (valueBuffer != null) {
                release(valueBuffer);
            }
        }
    }

    @Override
    public int getCurrentPoolSize() {
        return bufferQueues.stream()
                .mapToInt(Queue::size)
                .sum();
    }

    @Override
    public void clear() {
        bufferQueues.forEach(Queue::clear);
        semaphores.forEach(Semaphore::drainPermits);
    }

    @Override
    public SystemInfoResult getSystemInfo() {
        try {
            final SystemInfoResult.Builder builder = SystemInfoResult.builder(this)
                    .addDetail("Size", getCurrentPoolSize());

            SortedMap<Integer, Long> capacityCountsMap = null;
            try {
                capacityCountsMap = bufferQueues.stream()
                        .flatMap(Queue::stream)
                        .collect(Collectors.groupingBy(Buffer::capacity, Collectors.counting()))
                        .entrySet()
                        .stream()
                        .collect(HasHealthCheck.buildTreeMapCollector(Map.Entry::getKey, Map.Entry::getValue));

                builder.addDetail("Buffer capacity counts", capacityCountsMap);
            } catch (final Exception e) {
                LOGGER.error("Error getting capacity counts", e);
                builder.addDetail("Buffer capacity counts", "Error getting counts");
            }

            return builder.build();
        } catch (final RuntimeException e) {
            return SystemInfoResult.builder(this)
                    .addError(e)
                    .build();
        }
    }

    private ByteBuffer createNewBufferIfAllowed(final int offset) {
        final int roundedCapacity = SIZES[offset];
        final int maxBufferCount = MAX_BUFFER_COUNTS[offset];
        final AtomicInteger bufferCounter = pooledBufferCounts.get(offset);
        ByteBuffer byteBuffer = null;

        while (true) {
            final int currBufferCount = bufferCounter.get();
            if (currBufferCount < maxBufferCount) {
                if (bufferCounter.compareAndSet(currBufferCount, currBufferCount + 1)) {
                    // Succeeded in incrementing the count so we can create one
                    byteBuffer = ByteBuffer.allocateDirect(roundedCapacity);
                    break;
                } else {
                    // CAS failed so another thread beat us, go round again.
                }
            } else {
                // At max count so can't create any more
                break;
            }
        }
        return byteBuffer;
    }

    private Queue<ByteBuffer> getByteBufferQueue(final int offset) {
        return bufferQueues.get(offset);
    }

    private Semaphore getSemaphore(final int offset) {
        return semaphores.get(offset);
    }

    private int getOffset(final int minCapacity) {
        return (int) Math.ceil(Math.log10(minCapacity));
    }
}
