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

package stroom.data.store.impl.fs.s3v2;


import stroom.bytebuffer.ByteBufferPoolConfig;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.sysinfo.HasSystemInfo;
import stroom.util.sysinfo.SystemInfoResult;

import com.github.luben.zstd.BufferPool;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Queue;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;

/**
 * This is a copy of {@link stroom.bytebuffer.impl6.ByteBufferPoolImpl6} adapted to use heap buffers.
 */
public class HeapBufferPool implements BufferPool, HasSystemInfo {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(HeapBufferPool.class);

    // If no count is provided for a buffer size in the config then this value is used.
    private static final int DEFAULT_MAX_BUFFERS_PER_QUEUE = 50;

    private static final int ONE_BYTE_OFFSET = 0;
    private static final int TEN_BYTES_OFFSET = 1;

    // In each of these collections, the index/offset is the log10 of the buffer size,
    // i.e. 1 => 0, 10 => 1, 100 => 2 etc.
    private final Provider<ByteBufferPoolConfig> byteBufferPoolConfigProvider;

    // One queue for each size of buffer
    private final BufferQueue[] pooledBufferQueues;

    // The number of different buffer sizes. Sizes start from one and go up in contiguous powers of ten.
    private final int sizesCount;
    // The highest offset used in the pool;
    private final int maxOffset;

    @Inject
    public HeapBufferPool(final Provider<ByteBufferPoolConfig> byteBufferPoolConfigProvider) {

        // Don't use a provider as all the props are RequiresRestart and we want system info to
        // report on config that matches what we init'd with.
        this.byteBufferPoolConfigProvider = byteBufferPoolConfigProvider;

        final ByteBufferPoolConfig byteBufferPoolConfig = byteBufferPoolConfigProvider.get();
        final Map<Integer, Integer> pooledByteBufferCounts = byteBufferPoolConfig.getPooledByteBufferCounts();

        // Establish the largest configured buffer size
        final OptionalInt optMaxSize = pooledByteBufferCounts == null
                ? OptionalInt.empty()
                : pooledByteBufferCounts.keySet()
                        .stream()
                        .filter(HeapBufferPool::isPowerOf10)
                        .mapToInt(Integer::intValue)
                        .max();

        if (optMaxSize.isPresent()) {
            maxOffset = getOffset(optMaxSize.getAsInt());
            // Going from a zero based offset to a count so have to add one.
            sizesCount = maxOffset + 1;
        } else {
            maxOffset = -1;
            sizesCount = 0;
        }

        pooledBufferQueues = new BufferQueue[sizesCount];

        // initialise all the queues and counters for each size offset, from zero
        // to the max offset in the config, filling the gaps with a default max count
        // to make it contiguous.
        final List<String> msgs = new ArrayList<>(sizesCount);
        for (int i = 0; i < sizesCount; i++) {

            final int bufferCapacity = (int) Math.pow(10, i);
            final Integer configuredCount = pooledByteBufferCounts.getOrDefault(
                    bufferCapacity,
                    DEFAULT_MAX_BUFFERS_PER_QUEUE);

            msgs.add(bufferCapacity + "=" + configuredCount);

            // ArrayBlockingQueue seems to be marginally faster than a LinkedBlockingQueue
            // If the configuredCount is 0 it means we will allocate on demand so no need to hold the queue/counter
            pooledBufferQueues[i] = configuredCount > 1
                    ? new BufferQueue(
                    configuredCount,
                    bufferCapacity)
                    : null;
        }

        LOGGER.info("Initialising ByteBufferPool with configured max counts: [{}], derived max counts:[{}]",
                (pooledByteBufferCounts == null
                        ? "null"
                        : pooledByteBufferCounts.entrySet()
                                .stream()
                                .sorted(Entry.comparingByKey())
                                .map(entry -> entry.getKey() + "=" + entry.getValue())
                                .collect(Collectors.joining(","))),
                String.join(",", msgs));
    }

    @Override
    public ByteBuffer get(final int minCapacity) {
        LOGGER.debug(() -> LogUtil.message("get() - minCapacity: {}", minCapacity));
        final int offset = getOffset(minCapacity);
        final ByteBuffer buffer;
        if (isUnPooled(offset)) {
            // Too big a buffer to pool so just create one that will have to be destroyed and not put in the pool
            buffer = ByteBuffer.allocate(minCapacity);
        } else {
            buffer = pooledBufferQueues[offset].getByteBuffer();
            // Ensure the buffer goes out ready for use, i.e. with offset at zero and limit at capacity
            buffer.clear();
        }
        return buffer;
    }

    @Override
    public void release(final ByteBuffer byteBuffer) {
        LOGGER.debug(() -> LogUtil.message("release() - capacity: {}", NullSafe.get(byteBuffer, ByteBuffer::capacity)));
        if (byteBuffer != null) {
            if (byteBuffer.isDirect()) {
                throw new IllegalArgumentException("Expecting a heap buffer");
            }
            final int offset = getOffset(byteBuffer);
            if (!isUnPooled(offset)) {
                pooledBufferQueues[offset].release(byteBuffer);
            }
        }
    }

    /**
     * @param n The number to test
     * @return True if n is a power of ten, e.g. if n==10
     */
    static boolean isPowerOf10(final int n) {
        return switch (n) {
            case 1,
                 10,
                 100,
                 1_000,
                 10_000,
                 100_000,
                 1_000_000,
                 10_000_000,
                 100_000_000,
                 1_000_000_000 -> true;
            default -> false;
        };
    }

//    @Override
//    public PooledByteBuffer getPooledByteBuffer(final int minCapacity) {
//        return getPooledBufferByMinCapacity(minCapacity);
//    }

//    private PooledByteBuffer getPooledBufferByMinCapacity(final int minCapacity) {
//        final int offset = getOffset(minCapacity);
//        final PooledByteBuffer buffer;
//        if (isUnPooled(offset)) {
//            buffer = getUnPooledBuffer(minCapacity);
//        } else {
//            buffer = getBufferByOffset(offset);
//            buffer.clear();
//        }
//        return buffer;
//    }
//
//    private PooledByteBuffer getUnPooledBuffer(final int minCapacity) {
//        LOGGER.trace("Using un-pooled buffer, size: {}", minCapacity);
//        // Too big a buffer to pool so just create one that will have to be destroyed and not put in the pool
//        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(minCapacity);
//        return new NonPooledByteBuffer(byteBuffer);
//    }

//    private PooledByteBuffer getBufferByOffset(final int offset) {
//        return pooledBufferQueues[offset].get();
//    }

    private boolean isUnPooled(final int offset) {
        return offset > maxOffset || pooledBufferQueues[offset].getMaxBufferCount() == 0;
    }

//    @Override
//    public PooledByteBufferPair getPooledBufferPair(final int minKeyCapacity, final int minValueCapacity) {
//        final PooledByteBuffer keyBuffer = getPooledBufferByMinCapacity(minKeyCapacity);
//        final PooledByteBuffer valueBuffer = getPooledBufferByMinCapacity(minValueCapacity);
//        return new PooledByteBufferPairImpl(keyBuffer, valueBuffer);
//    }

//    public <T> T getWithBuffer(final int minCapacity, final Function<ByteBuffer, T> work) {
//        try (final PooledByteBuffer buffer = getPooledBufferByMinCapacity(minCapacity)) {
//            return work.apply(buffer.getByteBuffer());
//        }
//    }
//
//    public void doWithBuffer(final int minCapacity, final Consumer<ByteBuffer> work) {
//        try (final PooledByteBuffer buffer = getPooledBufferByMinCapacity(minCapacity)) {
//            work.accept(buffer.getByteBuffer());
//        }
//    }
//
//    public void doWithBufferPair(final int minKeyCapacity,
//                                 final int minValueCapacity,
//                                 final BiConsumer<ByteBuffer, ByteBuffer> work) {
//        try (final PooledByteBuffer keyBuffer = getPooledBufferByMinCapacity(minKeyCapacity);
//                final PooledByteBuffer valueBuffer = getPooledBufferByMinCapacity(minValueCapacity)) {
//            work.accept(keyBuffer.getByteBuffer(), valueBuffer.getByteBuffer());
//        }
//    }

    public int getCurrentPoolSize() {
        return Arrays.stream(pooledBufferQueues)
                .filter(Objects::nonNull)
                .mapToInt(BufferQueue::size)
                .sum();
    }

    public void clear() {
        // Allows the UI to clear buffers sat in the pool. Buffers on loan are unaffected
        final List<String> msgs = new ArrayList<>();
        for (final BufferQueue bufferQueue : pooledBufferQueues) {
            if (bufferQueue != null) {
                bufferQueue.clear(msgs);
            }
        }

        LOGGER.info("Cleared the following buffers from the pool (buffer size:number cleared) - " +
                    String.join(", ", msgs));
    }

    @Override
    public SystemInfoResult getSystemInfo() {
        try {
            final SystemInfoResult.Builder builder = SystemInfoResult.builder(this)
                    .addDetail("Total buffers in pool", getCurrentPoolSize());

            final SortedMap<Integer, Map<String, Integer>> offsetMapOfInfoMaps = new TreeMap<>();
            int overallTotalSizeBytes = 0;

            try {
                for (int offset = 0; offset < sizesCount; offset++) {
                    final SortedMap<String, Integer> infoMap = new TreeMap<>();

                    final BufferQueue bufferQueue = pooledBufferQueues[offset];
                    final int availableBuffersOnQueue = bufferQueue != null
                            ? bufferQueue.size()
                            : -1;
                    final int buffersHighWaterMark = bufferQueue != null
                            ? bufferQueue.getPooledBufferCount()
                            : 0;
                    final int bufferCapacity = bufferQueue != null
                            ? bufferQueue.getBufferSize()
                            : 0;

                    final int buffersOnLoan = buffersHighWaterMark - availableBuffersOnQueue;
                    final int configuredMaximum = Optional.ofNullable(
                                    byteBufferPoolConfigProvider.get().getPooledByteBufferCounts())
                            .map(map -> map.get(bufferCapacity))
                            .orElse(DEFAULT_MAX_BUFFERS_PER_QUEUE);

                    final int totalSizeBytes = buffersHighWaterMark * bufferCapacity;
                    overallTotalSizeBytes += totalSizeBytes;

                    infoMap.put("Buffers available in pool", availableBuffersOnQueue);
                    infoMap.put("Buffers available or on loan", buffersHighWaterMark);
                    infoMap.put("Buffers on loan", buffersOnLoan);
                    infoMap.put("Total size (bytes)", totalSizeBytes);
                    infoMap.put("Configured max buffers", configuredMaximum);

                    offsetMapOfInfoMaps.put(bufferCapacity, infoMap);
                }

                builder
                        .addDetail("Pooled buffers (grouped by buffer capacity)", offsetMapOfInfoMaps)
                        .addDetail("Overall total size (bytes)", overallTotalSizeBytes);
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

    /**
     * The switch approach seems to be a lot quicker than {@link Math#log10(double)}
     * <strong>IF</strong> we expect the values to be exactly a power of 10, which they ought to be
     * for buffers coming back to the pool (because we created them with power of ten sizes).
     */
    static int getOffset(final ByteBuffer byteBuffer) {
        final int capacity = byteBuffer.capacity();
        return switch (capacity) {
            case 1 -> 0;
            case 10 -> 1;
            case 100 -> 2;
            case 1_000 -> 3;
            case 10_000 -> 4;
            case 100_000 -> 5;
            case 1_000_000 -> 6;
            case 10_000_000 -> 7;
            case 100_000_000 -> 8;
            case 1_000_000_000 -> 9;
            default -> getOffset(capacity);
        };
    }

    static int getOffset(final int minCapacity) {
        if (minCapacity <= 10) {
            // Optimisation for ints/longs
            return minCapacity <= 1
                    ? ONE_BYTE_OFFSET
                    // 0
                    : TEN_BYTES_OFFSET; // 1
        }
        return (int) Math.ceil(Math.log10(minCapacity));
    }


    // --------------------------------------------------------------------------------


    private static class BufferQueue {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(BufferQueue.class);

        private final BlockingQueue<ByteBuffer> queue;

        // The max number of buffers for each buffer size that the pool should manage.
        private final int maxBufferCount;
        // The buffer capacity for each offset/index. Saves computing a Math.pow each time.
        private final int bufferSize;


        public BufferQueue(final int maxBufferCount,
                           final int bufferSize) {
            this.maxBufferCount = maxBufferCount;
            this.bufferSize = bufferSize;
            this.queue = new ArrayBlockingQueue<>(maxBufferCount);
        }

//        public PooledByteBuffer get() {
//            return new PooledByteBufferImpl(this, getByteBuffer());
//        }

        public ByteBuffer getByteBuffer() {
            ByteBuffer buffer = queue.poll();
            if (buffer == null) {
                // Queue empty so if the pool hasn't reached them limit for this buffer size
                // create a new one.
                buffer = ByteBuffer.allocate(bufferSize);
            }
            return buffer;
        }

        void release(final ByteBuffer buffer) {
            // Use offer rather than put as that will fail if the thread is interrupted but
            // we want the buffer back on the queue whatever happens, else the pool will be
            // exhausted.
            // As pooledBufferCounters controls the number of queued items we don't need to worry
            // about offer failing.
            final boolean didOfferSucceed = queue.offer(buffer);

            if (!didOfferSucceed) {
                // We must have created more buffers than there are under pool control so we just have
                // to unmap it
                LOGGER.trace(() -> LogUtil.message("Unable to return buffer to the queue so will destroy it " +
                                                   "(capacity: {}, queue size: {}, counter value: {}, configured limit: {}",
                        buffer.capacity(),
                        size(),
                        queue.size(),
                        maxBufferCount));

                // Nothing to do. Just let GC clean it up
            }
        }

        public int getPooledBufferCount() {
            return queue.size();
        }

        public int size() {
            return queue.size();
        }

        public int getBufferSize() {
            return bufferSize;
        }

        public int getMaxBufferCount() {
            return maxBufferCount;
        }

        public void clear(final List<String> msgs) {
            // The queue of buffers is the source of truth so clear that out
            final Queue<ByteBuffer> drainedBuffers = new ArrayDeque<>(queue.size());
            queue.drainTo(drainedBuffers);

            // As well as removing the buffers we need to reduce the counters to allow new buffers to
            // be created again if needs be. It doesn't matter that this happens sometime later than
            // the draining of the queue.
            msgs.add(bufferSize + ":" + drainedBuffers.size());

            // Destroy all the cleared buffers
            drainedBuffers.clear();
        }
    }
}
