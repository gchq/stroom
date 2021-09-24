package stroom.bytebuffer;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.sysinfo.SystemInfoResult;

import java.nio.ByteBuffer;
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
import javax.inject.Inject;
import javax.inject.Singleton;

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

    // In each of these collections, the index/offset is the log10 of the buffer size,
    // i.e. 1 => 0, 10 => 1, 100 => 2 etc.

    private final ByteBufferPoolConfig byteBufferPoolConfig;

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
    public ByteBufferPoolImpl4(final ByteBufferPoolConfig byteBufferPoolConfig) {
        this.byteBufferPoolConfig = byteBufferPoolConfig;

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

            int bufferCapacity = (int) Math.pow(10, i);
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

    private static boolean isPowerOf10(int n) {
        return
                n == 1L
                        || n == 10L
                        || n == 100L
                        || n == 1_000L
                        || n == 10_000L
                        || n == 100_000L
                        || n == 1_000_000L
                        || n == 10_000_000L
                        || n == 100_000_000L
                        || n == 1_000_000_000L;
    }

    @Override
    public PooledByteBuffer getPooledByteBuffer(final int minCapacity) {
        return new PooledByteBuffer(() -> getBuffer(minCapacity), this::release);
    }

    private ByteBuffer getUnPooledBuffer(final int minCapacity) {
        return ByteBuffer.allocateDirect(minCapacity);
    }

    private ByteBuffer getBuffer(final int minCapacity) {
        final int offset = getOffset(minCapacity);
        if (isUnPooled(offset)) {
            LOGGER.warn("Using un-pooled buffer, size: {}", minCapacity);
            // Too big a buffer to pool so just create one
            return getUnPooledBuffer(minCapacity);
        } else {
            final BlockingQueue<ByteBuffer> byteBufferQueue = pooledBufferQueues[offset];

            ByteBuffer buffer = byteBufferQueue.poll();
            if (buffer == null) {
                // Queue empty so if the pool hasn't reached them limit for this buffer size
                // create a new one.
                buffer = createNewBufferIfAllowed(offset);
            }
            if (buffer == null) {
                try {
                    // At max pooled buffers so we just have to block and wait for another thread to release
                    // TODO @AT At this point instead of waiting we could try the next biggest buffer size,
                    //  i.e. for offset+1. A bigger buffer would be fine but means potentially depriving
                    //  other threads that need the bigger buffers.
                    buffer = byteBufferQueue.take();
                } catch (InterruptedException e) {
                    LOGGER.debug("Thread interrupted waiting for a buffer from the pool", e);
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Thread interrupted while waiting for a buffer from the pool");
                }
            }
            Objects.requireNonNull(
                    buffer,
                    "Something has gone wrong, we should not have a null buffer");
            // Ensure the buffer is ready for use with limits/positions/marks cleared
            buffer.clear();
            return buffer;
        }
    }

    private boolean isUnPooled(final int offset) {
        return offset > maxOffset || maxBufferCounts[offset] == 0;
    }

    void release(final ByteBuffer buffer) {
        final int offset = getOffset(buffer.capacity());
        if (isUnPooled(offset)) {
            // Too big a buffer to pool so do nothing so the JVM can de-reference it
            // TODO @AT Not clear if we need to do anything to help the jvm free up the buffer.
        } else {
            // Use offer rather than put as that will fail if the thread is interrupted but
            // we want the buffer back on the queue whatever happens, else the pool will be
            // exhausted.
            // As pooledBufferCounters controls the number of queued items we don't need to worry
            // about offer failing.
            final boolean didOfferSucceed = pooledBufferQueues[offset].offer(buffer);

            if (!didOfferSucceed) {
                throw new RuntimeException(LogUtil.message("Unable to return buffer to the queue. " +
                                "Should never get here. Queue size: {}, counter value: {}",
                        pooledBufferQueues[offset].size(),
                        pooledBufferCounters[offset].get()));
            }
        }
    }

    @Override
    public PooledByteBufferPair getPooledBufferPair(final int minKeyCapacity, final int minValueCapacity) {
        final ByteBuffer keyBuffer = getBuffer(minKeyCapacity);
        final ByteBuffer valueBuffer = getBuffer(minValueCapacity);
        return new PooledByteBufferPair(this::release, keyBuffer, valueBuffer);
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
                final List<ByteBuffer> drainedBuffers = new ArrayList<>();
                pooledBufferQueue.drainTo(drainedBuffers);

                // As well as removing the buffers we need to reduce the counters to allow new buffers to
                // be created again if needs be
                pooledBufferCounters[offset].addAndGet(-1 * drainedBuffers.size());
                int size = bufferSizes[offset];
                msgs.add(size + ":" + drainedBuffers.size());
            }
        }

        LOGGER.info("Cleared the following buffers from the pool (buffer size:number cleared) - " + String.join(", ",
                msgs));
    }

    @Override
    public SystemInfoResult getSystemInfo() {
        try {
            SystemInfoResult.Builder builder = SystemInfoResult.builder().name(getSystemInfoName())
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
                    final int configuredMaximum = Optional.ofNullable(byteBufferPoolConfig.getPooledByteBufferCounts())
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
            } catch (Exception e) {
                LOGGER.error("Error getting capacity counts", e);
                builder.addDetail("Buffer capacity counts", "Error getting counts");
            }

            return builder.build();
        } catch (RuntimeException e) {
            return SystemInfoResult.builder().name(getSystemInfoName())
                    .addError(e)
                    .build();
        }
    }

    private ByteBuffer createNewBufferIfAllowed(final int offset) {
        final int maxBufferCount = maxBufferCounts[offset];
        final int warningThreshold = warningThresholds[offset];
        final AtomicInteger bufferCounter = pooledBufferCounters[offset];

        ByteBuffer byteBuffer = null;

        while (true) {
            int currBufferCount = bufferCounter.get();


            if (currBufferCount < maxBufferCount) {
                final int newBufferCount = currBufferCount + 1;


                if (bufferCounter.compareAndSet(currBufferCount, newBufferCount)) {
                    // Succeeded in incrementing the count so we can create one
                    final int roundedCapacity = bufferSizes[offset];
                    byteBuffer = ByteBuffer.allocateDirect(roundedCapacity);

                    if (newBufferCount == warningThreshold) {
                        LOGGER.warn("Hit {}% ({}) of the limit of {} for pooled buffers of size {}.",
                                byteBufferPoolConfig.getWarningThresholdPercentage(),
                                warningThreshold,
                                newBufferCount,
                                bufferSizes[offset]);
                    } else if (newBufferCount == maxBufferCount) {
                        LOGGER.warn("Hit limit of {} for pooled buffers of size {}. " +
                                        "Future calls to the pool will not create more buffers of this size. " +
                                        "Consider changing the pool settings.",
                                newBufferCount,
                                roundedCapacity);
                    }

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

    private int getOffset(final int minCapacity) {
        return (int) Math.ceil(Math.log10(minCapacity));
    }
}
