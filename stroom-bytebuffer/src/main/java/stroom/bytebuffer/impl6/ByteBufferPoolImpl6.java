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

package stroom.bytebuffer.impl6;

import stroom.bytebuffer.ByteBufferPool;
import stroom.bytebuffer.ByteBufferPoolConfig;
import stroom.bytebuffer.ByteBufferSupport;
import stroom.bytebuffer.PooledByteBuffer;
import stroom.bytebuffer.PooledByteBufferPair;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.sysinfo.SystemInfoResult;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * An bounded self-populating pool of directly allocated ByteBuffers.
 * The pool holds buffers in a fixed set of sizes and any request for a buffer
 * will result in a buffer with capacity >= the requested capacity being
 * returned.
 * <p>
 * The sizes used are all powers of 10, i.e. 1, 10, 100, etc. so a request for a buffer of
 * min capacity 50 will always return a buffer with a size equal to the next highest power of
 * ten, i.e. 100.
 * <p>
 * The following is an example of how the pool can be configured.
 *
 * <pre>
 * buffer size | offset | buffer count
 * ------------|--------|-------------
 *           1 |      0 |          30
 *          10 |      1 |          20
 *         100 |      2 |          10
 *        1000 |      3 |           0
 *       10000 |      4 |           5
 * </pre>
 * <p>
 * In the above example there are at most 10 buffers of size 100 managed by the pool and buffers
 * of size 1000 are configured to not be pooled, i.e. always allocated on demand.
 * <p>
 * This approach of using a small number of fixed sizes means we hold less buffers in the pool.
 * For each buffer size there is an {@link ArrayBlockingQueue} that holds the managed buffers.
 * With one queue per size it means there is only contention with other threads that want the same
 * size buffer.
 * <p>
 * All buffers are cleared ready for use when obtained from the pool.
 * Once a buffer has been returned to the pool it MUST not be used else
 * bad things will happen.
 * <p>
 * This impl uses {@link ArrayBlockingQueue}
 */
@Singleton
public class ByteBufferPoolImpl6 implements ByteBufferFactory, ByteBufferPool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ByteBufferPoolImpl6.class);

    // If no count is provided for a buffer size in the config then this value is used.
    private static final int DEFAULT_MAX_BUFFERS_PER_QUEUE = 50;

    private static final int ONE_BYTE_OFFSET = 0;
    private static final int TEN_BYTES_OFFSET = 1;

    // In each of these collections, the index/offset is the log10 of the buffer size,
    // i.e. 1 => 0, 10 => 1, 100 => 2 etc.
    private final Provider<ByteBufferPoolConfig> byteBufferPoolConfigProvider;

    // One queue for each size of buffer
    private final PooledByteBufferQueue[] pooledBufferQueues;

    // The number of different buffer sizes. Sizes start from one and go up in contiguous powers of ten.
    private final int sizesCount;
    // The highest offset used in the pool;
    private final int maxOffset;

    @Inject
    public ByteBufferPoolImpl6(final Provider<ByteBufferPoolConfig> byteBufferPoolConfigProvider) {

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
                        .filter(ByteBufferPoolImpl6::isPowerOf10)
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

        pooledBufferQueues = new PooledByteBufferQueue[sizesCount];

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
                    ? new PooledByteBufferQueue(
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

    /**
     * @param n The number to test
     * @return True if n is a power of ten, e.g. if n==10
     */
    static boolean isPowerOf10(final int n) {
        return switch (n) {
            case 1:
            case 10:
            case 100:
            case 1_000:
            case 10_000:
            case 100_000:
            case 1_000_000:
            case 10_000_000:
            case 100_000_000:
            case 1_000_000_000:
                yield true;
                // fall-through (Comment to tell checkstyle we want to fall through cases)
            default:
                yield false;
        };
    }

    @Override
    public PooledByteBuffer getPooledByteBuffer(final int minCapacity) {
        return getPooledBufferByMinCapacity(minCapacity);
    }

    private PooledByteBuffer getPooledBufferByMinCapacity(final int minCapacity) {
        final int offset = getOffset(minCapacity);
        final PooledByteBuffer buffer;
        if (isUnPooled(offset)) {
            buffer = getUnPooledBuffer(minCapacity);
        } else {
            buffer = getBufferByOffset(offset);
            buffer.clear();
        }
        return buffer;
    }

    @Override
    public ByteBuffer acquire(final int minCapacity) {
        final int offset = getOffset(minCapacity);
        final ByteBuffer buffer;
        if (isUnPooled(offset)) {
            // Too big a buffer to pool so just create one that will have to be destroyed and not put in the pool
            buffer = ByteBuffer.allocateDirect(minCapacity);
        } else {
            buffer = pooledBufferQueues[offset].getByteBuffer();
            buffer.clear();
        }
        return buffer;
    }

    @Override
    public void release(final ByteBuffer byteBuffer) {
        if (byteBuffer != null && byteBuffer.isDirect()) {
            final int offset = getOffset(byteBuffer.capacity());
            if (isUnPooled(offset)) {
                ByteBufferSupport.unmap(byteBuffer);
            } else {
                pooledBufferQueues[offset].release(byteBuffer);
            }
        }
    }

    private PooledByteBuffer getUnPooledBuffer(final int minCapacity) {
        LOGGER.trace("Using un-pooled buffer, size: {}", minCapacity);
        // Too big a buffer to pool so just create one that will have to be destroyed and not put in the pool
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(minCapacity);
        return new NonPooledByteBuffer(byteBuffer);
    }

    private PooledByteBuffer getBufferByOffset(final int offset) {
        return pooledBufferQueues[offset].get();
    }

    private boolean isUnPooled(final int offset) {
        return offset > maxOffset || pooledBufferQueues[offset].getMaxBufferCount() == 0;
    }

    @Override
    public PooledByteBufferPair getPooledBufferPair(final int minKeyCapacity, final int minValueCapacity) {
        final PooledByteBuffer keyBuffer = getPooledBufferByMinCapacity(minKeyCapacity);
        final PooledByteBuffer valueBuffer = getPooledBufferByMinCapacity(minValueCapacity);
        return new PooledByteBufferPairImpl(keyBuffer, valueBuffer);
    }

    @Override
    public <T> T getWithBuffer(final int minCapacity, final Function<ByteBuffer, T> work) {
        try (final PooledByteBuffer buffer = getPooledBufferByMinCapacity(minCapacity)) {
            return work.apply(buffer.getByteBuffer());
        }
    }

    @Override
    public void doWithBuffer(final int minCapacity, final Consumer<ByteBuffer> work) {
        try (final PooledByteBuffer buffer = getPooledBufferByMinCapacity(minCapacity)) {
            work.accept(buffer.getByteBuffer());
        }
    }

    @Override
    public void doWithBufferPair(final int minKeyCapacity,
                                 final int minValueCapacity,
                                 final BiConsumer<ByteBuffer, ByteBuffer> work) {
        try (final PooledByteBuffer keyBuffer = getPooledBufferByMinCapacity(minKeyCapacity);
                final PooledByteBuffer valueBuffer = getPooledBufferByMinCapacity(minValueCapacity)) {
            work.accept(keyBuffer.getByteBuffer(), valueBuffer.getByteBuffer());
        }
    }

    @Override
    public int getCurrentPoolSize() {
        return Arrays.stream(pooledBufferQueues)
                .filter(Objects::nonNull)
                .mapToInt(PooledByteBufferQueue::size)
                .sum();
    }

    @Override
    public void clear() {
        // Allows the UI to clear buffers sat in the pool. Buffers on loan are unaffected
        final List<String> msgs = new ArrayList<>();
        for (final PooledByteBufferQueue pooledBufferQueue : pooledBufferQueues) {
            if (pooledBufferQueue != null) {
                pooledBufferQueue.clear(msgs);
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

                    final PooledByteBufferQueue pooledBufferQueue = pooledBufferQueues[offset];
                    final int availableBuffersOnQueue = pooledBufferQueue != null
                            ? pooledBufferQueue.size()
                            : -1;
                    final int buffersHighWaterMark = pooledBufferQueue != null
                            ? pooledBufferQueue.getPooledBufferCount()
                            : 0;
                    final int bufferCapacity = pooledBufferQueue != null
                            ? pooledBufferQueue.getBufferSize()
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

    private int getOffset(final int minCapacity) {
        if (minCapacity <= 10) {
            // Optimisation for ints/longs
            return minCapacity <= 1
                    ? ONE_BYTE_OFFSET
                    // 0
                    : TEN_BYTES_OFFSET; // 1
        }
        return (int) Math.ceil(Math.log10(minCapacity));
    }
}
