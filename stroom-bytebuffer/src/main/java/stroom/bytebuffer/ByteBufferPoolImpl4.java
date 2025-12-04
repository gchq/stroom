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

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;
import stroom.util.sysinfo.SystemInfoResult;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

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
public class ByteBufferPoolImpl4 implements ByteBufferPool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ByteBufferPoolImpl4.class);

    // If no count is provided for a buffer size in the config then this value is used.
    private static final int DEFAULT_MAX_BUFFERS_PER_QUEUE = 50;
    // The offset for a one byte buffer
    private static final int ONE_BYTE_BUFFER_OFFSET = 0;
    // The offset for a ten byte buffer
    private static final int TEN_BYTE_BUFFER_OFFSET = 1;

    // In each of these collections, the index/offset is the log10 of the buffer size,
    // i.e. 1 => 0, 10 => 1, 100 => 2 etc.

    private final Provider<ByteBufferPoolConfig> byteBufferPoolConfigProvider;

    // One queue for each size of buffer
    private final BlockingQueue<ByteBuffer>[] pooledBufferQueues;
    // One counter for each size of buffer. Keeps track of the number of buffers known to the pool
    // whether in the pool or currently on loan. Each AtomicInteger will increase until it hits its
    // configured limit then will never go down unless clear() is called.
    private final AtomicInteger[] pooledBufferCounters;
    // The max number of buffers for each buffer size that the pool should manage.
    private final int[] maxBufferCounts;
    // The buffer capacity for each offset/index. Saves computing a Math.pow each time.
    private final int[] bufferSizes;
    // The threshold for the number of created buffers (for each offset) to warn at.
    private final int[] warningThresholds;
    // The number of different buffer sizes. Sizes start from one and go up in contiguous powers of ten.
    private final int sizesCount;
    // The highest offset used in the pool;
    private final int maxOffset;

    @Inject
    public ByteBufferPoolImpl4(final Provider<ByteBufferPoolConfig> byteBufferPoolConfigProvider) {

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
                        .filter(ByteBufferPoolImpl4::isPowerOf10)
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

        pooledBufferQueues = new BlockingQueue[sizesCount];
        pooledBufferCounters = new AtomicInteger[sizesCount];
        maxBufferCounts = new int[sizesCount];
        bufferSizes = new int[sizesCount];
        warningThresholds = new int[sizesCount];

        // initialise all the queues and counters for each size offset, from zero
        // to the max offset in the config, filling the gaps with a default max count
        // to make it contiguous.
        final List<String> msgs = new ArrayList<>(sizesCount);
        for (int i = 0; i < sizesCount; i++) {

            final int bufferCapacity = (int) Math.pow(10, i);
            final Integer configuredCount = pooledByteBufferCounts.getOrDefault(
                    bufferCapacity,
                    DEFAULT_MAX_BUFFERS_PER_QUEUE);

            // ArrayBlockingQueue seems to be marginally faster than a LinkedBlockingQueue
            // If the configuredCount is 0 it means we will allocate on demand so no need to hold the queue/counter
            pooledBufferQueues[i] = configuredCount > 1
                    ? new ArrayBlockingQueue<>(configuredCount)
                    : null;

            pooledBufferCounters[i] = configuredCount > 1
                    ? new AtomicInteger(0)
                    : null;

            maxBufferCounts[i] = configuredCount;
            bufferSizes[i] = bufferCapacity;
            warningThresholds[i] = configuredCount > 1
                    ? (int) Math.ceil(configuredCount
                    * ((double) byteBufferPoolConfig.getWarningThresholdPercentage() / 100))
                    : -1;

            msgs.add(bufferCapacity + "=" + configuredCount);
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
        return new PooledByteBufferImpl(() -> getBufferByMinCapacity(minCapacity), this::release);
    }

    private ByteBuffer getUnPooledBuffer(final int minCapacity) {
        LOGGER.warn(() -> LogUtil.message("Using un-pooled buffer, size: {}", ModelStringUtil.formatCsv(minCapacity)));
        // Too big a buffer to pool so just create one that will have to be destroyed and not put in the pool
        return ByteBuffer.allocateDirect(minCapacity);
    }

    private ByteBuffer getBufferByMinCapacity(final int minCapacity) {
        final int originalOffset = getOffset(minCapacity);
        int offset = originalOffset;
        ByteBuffer buffer = null;
        if (isUnPooled(offset)) {
            buffer = getUnPooledBuffer(minCapacity);
        } else {
            buffer = getBufferByOffset(offset);

            if (buffer == null) {
                // No buffer at that size so try the next size up
                // We could loop and try all bigger sizes but then we risk creating a huge buffer
                // for a tiny requested capacity and having that huge buffer hang around in the pool.
                offset = getNextOffset(offset);
                if (offset == -1) {
                    // Reached max offset
                } else {
                    LOGGER.debug("Trying new offset {} (originalOffset: {})", offset, originalOffset);
                    buffer = getBufferByOffset(offset);
                }
            }

            if (buffer == null) {
                // None in the pool and not allowed to create any more pool buffers so we either
                // wait on the queue or just create an excess one that will have to be destroyed on release.
                // Creation of an excess one is a last resort due to the cost of creation/destruction
                if (byteBufferPoolConfigProvider.get().isBlockOnExhaustedPool()) {
                    try {
                        // At max pooled buffers so we just have to block and wait for another thread to release
                        final BlockingQueue<ByteBuffer> byteBufferQueue = pooledBufferQueues[originalOffset];
                        LOGGER.debug("Taking from queue, may block");
                        buffer = byteBufferQueue.take();
                    } catch (final InterruptedException e) {
                        LOGGER.debug("Thread interrupted waiting for a buffer from the pool", e);
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Thread interrupted while waiting for a buffer from the pool");
                    }
                } else {
                    // Don't want to block so create a new one
                    // Excess buffers will have to be unmapped rather than returned to the pool
                    final int roundedCapacity = bufferSizes[originalOffset];
                    LOGGER.debug("Creating new buffer beyond the pool limit (capacity: {})",
                            ModelStringUtil.formatCsv(roundedCapacity));
                    buffer = ByteBuffer.allocateDirect(roundedCapacity);
                }
            }

            Objects.requireNonNull(
                    buffer,
                    "Something has gone wrong, we should not have a null buffer");

            buffer.clear();
        }
        return buffer;
    }

    private ByteBuffer getBufferByOffset(final int offset) {
        final BlockingQueue<ByteBuffer> byteBufferQueue = pooledBufferQueues[offset];

        ByteBuffer buffer = byteBufferQueue.poll();
        if (buffer == null) {
            // Queue empty so if the pool hasn't reached them limit for this buffer size
            // create a new one.
            buffer = createNewBufferIfAllowed(offset);
        } else {
            // buffer not final, so no lambda
            if (LOGGER.isTraceEnabled()) {
                LOGGER.trace("Got byteBuffer with capacity: {} from the pool with offset: {}, queue size: {}",
                        ModelStringUtil.formatCsv(buffer.capacity()),
                        offset,
                        byteBufferQueue.size());
            }
        }
        return buffer;
    }

    private boolean isUnPooled(final int offset) {
        return offset > maxOffset || maxBufferCounts[offset] == 0;
    }

    private void release(final ByteBuffer buffer) {
        if (buffer != null && buffer.isDirect()) {
            LOGGER.trace(() -> LogUtil.message("Releasing buffer with capacity: {}",
                    ModelStringUtil.formatCsv(buffer.capacity())));
            final int offset = getOffset(buffer.capacity());
            if (isUnPooled(offset)) {
                // Not pooled so need to release the memory via the cleaner
                unmapBuffer(buffer);
            } else {
                // Use offer rather than put as that will fail if the thread is interrupted but
                // we want the buffer back on the queue whatever happens, else the pool will be
                // exhausted.
                // As pooledBufferCounters controls the number of queued items we don't need to worry
                // about offer failing.
                final boolean didOfferSucceed = pooledBufferQueues[offset].offer(buffer);

                if (!didOfferSucceed) {
                    // We must have created more buffers than there are under pool control so we just have
                    // to unmap it
                    LOGGER.debug(() -> LogUtil.message("Unable to return buffer to the queue so will destroy it " +
                                    "(capacity: {}, queue size: {}, counter value: {}, configured limit: {}",
                            ModelStringUtil.formatCsv(buffer.capacity()),
                            pooledBufferQueues[offset].size(),
                            pooledBufferCounters[offset].get(),
                            maxBufferCounts[offset]));
                    unmapBuffer(buffer);
                }
            }
        } else {
            LOGGER.debug("buffer is null");
        }
    }

    private void unmapBuffer(final ByteBuffer buffer) {
        if (buffer.isDirect()) {
            try {
                LOGGER.debug("Unmapping buffer {}", buffer);
                ByteBufferSupport.unmap(buffer);
            } catch (final Exception e) {
                LOGGER.error("Error releasing direct byte buffer", e);
            }
        }
    }

    @Override
    public PooledByteBufferPair getPooledBufferPair(final int minKeyCapacity, final int minValueCapacity) {
        final ByteBuffer keyBuffer = getBufferByMinCapacity(minKeyCapacity);
        final ByteBuffer valueBuffer = getBufferByMinCapacity(minValueCapacity);
        return new PooledByteBufferPairImpl(this::release, keyBuffer, valueBuffer);
    }

    @Override
    public <T> T getWithBuffer(final int minCapacity, final Function<ByteBuffer, T> work) {
        ByteBuffer buffer = null;
        try {
            buffer = getBufferByMinCapacity(minCapacity);
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
            buffer = getBufferByMinCapacity(minCapacity);
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
            keyBuffer = getBufferByMinCapacity(minKeyCapacity);
            valueBuffer = getBufferByMinCapacity(minValueCapacity);
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
        return Arrays.stream(pooledBufferQueues)
                .filter(Objects::nonNull)
                .mapToInt(Queue::size)
                .sum();
    }

    @Override
    public void clear() {
        // Allows the UI to clear buffers sat in the pool. Buffers on loan are unaffected
        final List<String> msgs = new ArrayList<>();
        for (int offset = 0; offset < pooledBufferQueues.length; offset++) {
            final BlockingQueue<ByteBuffer> pooledBufferQueue = pooledBufferQueues[offset];

            if (pooledBufferQueue != null) {
                // The queue of buffers is the source of truth so clear that out
                final Queue<ByteBuffer> drainedBuffers = new ArrayDeque<>(pooledBufferQueue.size());
                pooledBufferQueue.drainTo(drainedBuffers);

                // As well as removing the buffers we need to reduce the counters to allow new buffers to
                // be created again if needs be. It doesn't matter that this happens sometime later than
                // the draining of the queue.
                pooledBufferCounters[offset].addAndGet(-1 * drainedBuffers.size());
                final int size = bufferSizes[offset];
                msgs.add(size + ":" + drainedBuffers.size());

                // Destroy all the cleared buffers
                while (true) {
                    final ByteBuffer byteBuffer = drainedBuffers.poll();
                    if (byteBuffer == null) {
                        break;
                    } else {
                        unmapBuffer(byteBuffer);
                    }
                }
            }
        }

        LOGGER.info("Cleared the following buffers from the pool (buffer size:number cleared) - "
                + String.join(", ", msgs));
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

                    final BlockingQueue<ByteBuffer> pooledBufferQueue = pooledBufferQueues[offset];
                    final int availableBuffersOnQueue = pooledBufferQueue != null
                            ? pooledBufferQueue.size()
                            : -1;
                    final AtomicInteger pooledBufferCounter = pooledBufferCounters[offset];
                    final int buffersHighWaterMark = pooledBufferCounter != null
                            ? pooledBufferCounter.get()
                            : 0;
                    final int bufferCapacity = bufferSizes[offset];
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

    private ByteBuffer createNewBufferIfAllowed(final int offset) {
        final int maxBufferCount = maxBufferCounts[offset];
        final int warningThreshold = warningThresholds[offset];
        final AtomicInteger bufferCounter = pooledBufferCounters[offset];
        final int roundedCapacity = bufferSizes[offset];

        ByteBuffer byteBuffer = null;

        while (true) {
            final int currBufferCount = bufferCounter.get();


            if (currBufferCount < maxBufferCount) {
                final int newBufferCount = currBufferCount + 1;

                if (bufferCounter.compareAndSet(currBufferCount, newBufferCount)) {
                    // Succeeded in incrementing the count so we can create one
                    LOGGER.debug(() -> LogUtil.message("Creating new pooled buffer (capacity: {})",
                            ModelStringUtil.formatCsv(roundedCapacity)));
                    byteBuffer = ByteBuffer.allocateDirect(roundedCapacity);

                    if (newBufferCount == warningThreshold) {
                        LOGGER.warn("Hit {}% ({}) of the limit of {} for pooled buffers of size {}.",
                                byteBufferPoolConfigProvider.get().getWarningThresholdPercentage(),
                                warningThreshold,
                                maxBufferCount,
                                ModelStringUtil.formatCsv(bufferSizes[offset]));
                    } else if (newBufferCount == maxBufferCount) {
                        LOGGER.warn("Hit limit of {} for pooled buffers of size {}. " +
                                        "Future calls to the pool will create new buffers but excess buffers " +
                                        "will have to be freed rather than returned to the pool. This may incur a " +
                                        "performance overhead. Consider changing the pool settings.",
                                newBufferCount,
                                ModelStringUtil.formatCsv(roundedCapacity));
                    }

                    break;
                } else {
                    // CAS failed so another thread beat us, go round again.
                }
            } else {
                // At max count so can't add any more to the pool
                break;
            }
        }
        return byteBuffer;
    }

    // Pkg private for testing
    static int getOffset(final int minCapacity) {
        if (minCapacity <= 10) {
            // Minor optimisation as a lot of the requests for buffers will be for int/longs
            // so this saves a bit of maths.
            return minCapacity <= 1
                    ? ONE_BYTE_BUFFER_OFFSET
                    : TEN_BYTE_BUFFER_OFFSET;
        } else {
            return (int) Math.ceil(Math.log10(minCapacity));
        }
    }

    private int getNextOffset(final int offset) {
        return offset >= pooledBufferCounters.length - 1
                ? -1
                : offset + 1;
    }

    /**
     * For testing.
     *
     * @return Number of buffers known to the pool, i.e. in the pool or on loan.
     */
    int getPooledBufferCount(final int minCapacity) {
        final int offset = getOffset(minCapacity);
        return pooledBufferCounters[offset].get();
    }

    /**
     * For testing.
     *
     * @return Number of buffers available in the pool now.
     */
    int getAvailableBufferCount(final int minCapacity) {
        final int offset = getOffset(minCapacity);
        return pooledBufferQueues[offset].size();
    }
}
