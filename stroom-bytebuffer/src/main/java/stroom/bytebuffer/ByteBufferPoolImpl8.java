package stroom.bytebuffer;

import stroom.util.NullSafe;
import stroom.util.io.ByteSize;
import stroom.util.logging.AsciiTable;
import stroom.util.logging.AsciiTable.Column;
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
import java.util.Collection;
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
import java.util.function.Supplier;

/**
 * An bounded self-populating pool of directly allocated ByteBuffers.
 * The pool holds buffers in a fixed set of sizes and any request for a buffer
 * will result in a buffer with capacity >= the requested capacity being
 * returned.
 * <p>
 * The sizes used are all powers of 10, i.e. 10, 100, etc. so a request for a buffer of
 * min capacity 50 will always return a buffer with a size equal to the next highest power of
 * ten, i.e. 100. 10 is the smallest buffer size.
 * <p>
 * The following is an example of how the pool can be configured.
 *
 * <pre>
 * buffer size | offset | buffer count
 * ------------|--------|-------------
 *          10 |      1 |          20
 *         100 |      2 |          10
 *        1000 |      3 |           0
 *       10000 |      4 |           5
 * </pre>
 * <p>
 * In the above example there are at most 10 buffers of size 100 managed by the pool and buffers
 * of size 1000 are configured to not be pooled, i.e. always allocated on demand.
 * <p>
 * This approach of using a small number of fixed sizes means we hold fewer buffers in the pool.
 * For each buffer size there is an {@link ArrayBlockingQueue} that holds the managed buffers.
 * With one queue per size it means there is only contention with other threads that want the same
 * size buffer.
 * <p>
 * All buffers are cleared ready for use when obtained from the pool.
 * Once a buffer has been returned to the pool it MUST not be used else
 * bad things will happen.
 * <p>
 * This impl uses {@link ArrayBlockingQueue} and is an evolution of {@link ByteBufferPoolImpl4}.
 * <p>
 * If there aren't sufficient buffers in the appropriate queue for the size requested then this
 * implementation will try to obtain one from the next queue up. If they does not have one available
 * then it will create an un-pooled buffer.
 */
@Singleton
public class ByteBufferPoolImpl8 implements ByteBufferPool {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ByteBufferPoolImpl8.class);

    // If no count is provided for a buffer size in the config then this value is used.
    private static final int DEFAULT_MAX_BUFFERS_PER_QUEUE = 50;
    private static final int MIN_OFFSET = 1;
    static final int MAX_BUFFER_CAPACITY = 1_000_000_000;

    // In each of these collections, the index/offset is the log10 of the buffer size,
    // i.e. 10 => 100, 100 => 25, 1_000 => 50 etc.
    private final Provider<ByteBufferPoolConfig> byteBufferPoolConfigProvider;

    // One queue for each size of buffer
    private final PooledByteBufferQueue[] pooledBufferQueues;
    // The number of different buffer sizes. Sizes start from one and go up in contiguous powers of ten.
    private final int sizesCount;
    // The highest offset used in the pool;
    private final int maxOffset;

    @Inject
    public ByteBufferPoolImpl8(final Provider<ByteBufferPoolConfig> byteBufferPoolConfigProvider) {

        // Don't use a provider as all the props are RequiresRestart and we want system info to
        // report on config that matches what we init'd with.
        this.byteBufferPoolConfigProvider = byteBufferPoolConfigProvider;

        final ByteBufferPoolConfig byteBufferPoolConfig = byteBufferPoolConfigProvider.get();
        final Map<Integer, Integer> pooledByteBufferCounts = byteBufferPoolConfig.getPooledByteBufferCounts();

        // Establish the largest configured buffer size
        final OptionalInt optMaxSize = pooledByteBufferCounts == null
                ? OptionalInt.empty()
                : pooledByteBufferCounts.entrySet()
                        .stream()
                        .filter(entry -> {
                            final Integer size = entry.getKey();
                            final Integer count = entry.getValue();
                            boolean isValidSize = isValidSize(size);
                            if (!isValidSize) {
                                // We used to have a pool for 1 byte buffers but that is a bit pointless so
                                // ignore+warn for those legacy config entries.
                                LOGGER.warn("Configured buffer count entry of {}:{} (in property '{}') " +
                                                "is not valid. It must be a power of ten, be >= 10 and <= {}. " +
                                                "This entry will be ignored.",
                                        size,
                                        count,
                                        byteBufferPoolConfig.getFullPathStr(
                                                ByteBufferPoolConfig.PROP_NAME_BUFFER_COUNTS),
                                        MAX_BUFFER_CAPACITY);
                            }
                            return isValidSize;
                        })
                        .map(Entry::getKey)
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
        final List<Info> infoList = new ArrayList<>(sizesCount);
        // Start at offset 1 (capacity 10)
        for (int offset = MIN_OFFSET; offset < sizesCount; offset++) {
            int bufferCapacity = (int) Math.pow(10, offset);
            final Integer configuredCount = pooledByteBufferCounts.get(bufferCapacity);
            final int effectiveCount = pooledByteBufferCounts.getOrDefault(
                    bufferCapacity,
                    DEFAULT_MAX_BUFFERS_PER_QUEUE);

            // Explicitly setting configuredCount to 0 means always create un-pooled buffers, while
            // null means use the default count.
            if (effectiveCount > 0) {
                final PooledByteBufferQueue queue = new PooledByteBufferQueue(
                        offset,
                        effectiveCount,
                        bufferCapacity,
                        byteBufferPoolConfig.getWarningThresholdPercentage());

                pooledBufferQueues[offset] = queue;
                final int maxPooledBytes = bufferCapacity * effectiveCount;

                infoList.add(new Info(
                        bufferCapacity,
                        configuredCount,
                        effectiveCount,
                        queue.warningThreshold,
                        ByteSize.ofBytes(maxPooledBytes)));
            }
        }
        showInfo(infoList, byteBufferPoolConfig);
    }

    private void showInfo(final List<Info> infoList, final ByteBufferPoolConfig byteBufferPoolConfig) {
        final long maxPooledBytes = Arrays.stream(pooledBufferQueues)
                .filter(Objects::nonNull)
                .mapToLong(pooledByteBufferQueue ->
                        (long) pooledByteBufferQueue.bufferSize * pooledByteBufferQueue.maxBufferCount)
                .sum();

        final String asciiTable = AsciiTable.builder(infoList)
                .withColumn(Column.integer("Buffer Size", Info::bufferSize))
                .withColumn(Column.integer("Configured Count", Info::configuredCount))
                .withColumn(Column.integer("Effective Count", Info::effectiveCount))
                .withColumn(Column.integer("Warning Threshold Count", Info::warningThresholdCount))
                .withColumn(Column.builder("Max Pooled", Info::maxPooledBytes)
                        .rightAligned()
                        .build())
                .build();

        LOGGER.info("Initialising Byte Buffer Pool with warning threshold: {}%, " +
                        "total max pooled: {}, pool details:\n{}",
                byteBufferPoolConfig.getWarningThresholdPercentage(),
                ByteSize.ofBytes(maxPooledBytes),
                asciiTable);
    }

    /**
     * @param n The number to test
     * @return True if n is a power of ten, e.g. if n==10
     */
    static boolean isValidSize(Integer n) {
        if (n == null) {
            return false;
        } else {
            return switch (n) {
                case 10:
                case 100:
                case 1_000:
                case 10_000:
                case 100_000:
                case 1_000_000:
                case 10_000_000:
                case 100_000_000:
                case MAX_BUFFER_CAPACITY: // 1_000_000_000
                    yield true;
                    // fall-through (Comment to tell checkstyle we want to fall through cases)
                default:
                    yield false;
            };
        }
    }

    @Override
    public PooledByteBuffer getPooledByteBuffer(final int minCapacity) {
        return new PooledByteBufferImpl8(() -> getBufferByMinCapacity(minCapacity));
    }

    private ByteBuffer getUnPooledBuffer(final int minCapacity) {
//        LOGGER.warn(() -> LogUtil.message("Using un-pooled buffer, size: {}", ModelStringUtil.formatCsv(minCapacity)));
        // Too big a buffer to pool so just create one that will have to be destroyed and not put in the pool
        return ByteBuffer.allocateDirect(minCapacity);
    }

    private OwnedBuffer getBufferByMinCapacity(final int minCapacity) {
        final int offset = getOffset(minCapacity);
        final PooledByteBufferQueue queue = getQueue(offset);
        OwnedBuffer ownedBuffer;
        if (queue == null) {
            ownedBuffer = OwnedBuffer.unowned(getUnPooledBuffer(minCapacity));
        } else {
            // Get or create a pooled buffer
            ownedBuffer = queue.getOwnedBuffer(true);

            if (ownedBuffer == null) {
                // No buffer at that size so try the next size up, but only if it has one available
                // in the pool, don't create one to avoid skewing the stats.
                // Only go one level up
                final PooledByteBufferQueue nextQueue = getQueue(offset + 1);
                if (nextQueue != null) {
//                    LOGGER.debug("Trying new offset {} (originalOffset: {})", offset, offset);
                    ownedBuffer = nextQueue.getOwnedBuffer(false);
                }

                if (ownedBuffer == null) {
                    // None in the pool and not allowed to create any more pool buffers, so we just
                    // create an un-pooled one.
                    // Excess buffers will have to be unmapped rather than returned to the pool
                    // This probably means the pool config needs changing.
                    LOGGER.debug(() -> LogUtil.message(
                            "Creating new non-pooled buffer (capacity: {}). No pooled buffers available " +
                                    "capacity {} or {}",
                            minCapacity,
                            queue.bufferSize,
                            NullSafe.get(nextQueue, PooledByteBufferQueue::getBufferSize)));
                    ownedBuffer = OwnedBuffer.unowned(getUnPooledBuffer(minCapacity));
                }
            }
            // Make sure it is clear before giving it out
            ownedBuffer.byteBuffer.clear();
        }
        return ownedBuffer;
    }

    @Override
    public PooledByteBufferPair getPooledBufferPair(final int minKeyCapacity, final int minValueCapacity) {
        final PooledByteBuffer pooledKeyBuffer = getPooledByteBuffer(minKeyCapacity);
        final PooledByteBuffer pooledValueBuffer = getPooledByteBuffer(minValueCapacity);
        return new PooledByteBufferPairImpl8(pooledKeyBuffer, pooledValueBuffer);
    }

    @Override
    public <T> T getWithBuffer(final int minCapacity, final Function<ByteBuffer, T> work) {
        Objects.requireNonNull(work);
        OwnedBuffer ownedBuffer = null;
        try {
            ownedBuffer = getBufferByMinCapacity(minCapacity);
            return work.apply(ownedBuffer.byteBuffer);
        } finally {
            if (ownedBuffer != null) {
                ownedBuffer.release();
            }
        }
    }

    @Override
    public void doWithBuffer(final int minCapacity, final Consumer<ByteBuffer> work) {
        if (work != null) {
            Objects.requireNonNull(work);
            OwnedBuffer ownedBuffer = null;
            try {
                ownedBuffer = getBufferByMinCapacity(minCapacity);
                work.accept(ownedBuffer.byteBuffer);
            } finally {
                if (ownedBuffer != null) {
                    ownedBuffer.release();
                }
            }
        }
    }

    @Override
    public void doWithBufferPair(final int minKeyCapacity,
                                 final int minValueCapacity,
                                 final BiConsumer<ByteBuffer, ByteBuffer> work) {
        OwnedBuffer keyBuffer = null;
        OwnedBuffer valueBuffer = null;
        try {
            keyBuffer = getBufferByMinCapacity(minKeyCapacity);
            valueBuffer = getBufferByMinCapacity(minValueCapacity);
            work.accept(keyBuffer.byteBuffer, valueBuffer.byteBuffer);
        } finally {
            if (keyBuffer != null) {
                keyBuffer.release();
            }
            if (valueBuffer != null) {
                valueBuffer.release();
            }
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
        for (int offset = MIN_OFFSET; offset < pooledBufferQueues.length; offset++) {

            final PooledByteBufferQueue pooledBufferQueue = pooledBufferQueues[offset];

            if (pooledBufferQueue != null) {
                // The queue of buffers is the source of truth so clear that out
                final Queue<OwnedBuffer> drainedBuffers = new ArrayDeque<>(pooledBufferQueue.size());
                pooledBufferQueue.drainTo(drainedBuffers);

                // As well as removing the buffers we need to reduce the counters to allow new buffers to
                // be created again if needs be. It doesn't matter that this happens sometime later than
                // the draining of the queue.
                pooledBufferQueue.bufferCounter.addAndGet(-1 * drainedBuffers.size());
                int size = pooledBufferQueue.bufferSize;
                msgs.add(size + ":" + drainedBuffers.size());

                // Destroy all the cleared buffers
                while (true) {
                    final OwnedBuffer ownedBuffer = drainedBuffers.poll();
                    if (ownedBuffer == null) {
                        break;
                    } else {
                        ownedBuffer.unmapBuffer();
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
            SystemInfoResult.Builder builder = SystemInfoResult.builder(this)
                    .addDetail("Total buffers in pool", getCurrentPoolSize());

            final SortedMap<Integer, Map<String, Integer>> offsetMapOfInfoMaps = new TreeMap<>();
            int overallTotalSizeBytes = 0;

            try {
                for (int offset = MIN_OFFSET; offset < sizesCount; offset++) {
                    final SortedMap<String, Integer> infoMap = new TreeMap<>();

                    final PooledByteBufferQueue pooledBufferQueue = pooledBufferQueues[offset];
                    if (pooledBufferQueue != null) {
                        final int availableBuffersOnQueue = pooledBufferQueue.size();
                        final int buffersHighWaterMark = pooledBufferQueue.bufferCounter.get();
                        final int bufferCapacity = pooledBufferQueue.bufferSize;
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
            return SystemInfoResult.builder(this)
                    .addError(e)
                    .build();
        }
    }

    // Pkg private for testing
    static int getOffset(final int minCapacity) {
        // This is a lot faster than just doing (int) Math.ceil(Math.log10(minCapacity));
        // See TestPowerOf10Performance for the benchmark
        // No point having a bytebuffer of size 1 so start at 10
        if (minCapacity <= 10) {
            return 1;
        } else if (minCapacity <= 100) {
            return 2;
        } else if (minCapacity <= 1_000) {
            return 3;
        } else if (minCapacity <= 10_000) {
            return 4;
        } else if (minCapacity <= 100_000) {
            return 5;
        } else if (minCapacity <= 1_000_000) {
            return 6;
        } else if (minCapacity <= 10_000_000) {
            return 7;
        } else if (minCapacity <= 100_000_000) {
            return 8;
        } else if (minCapacity <= 1_000_000_000) {
            return 9;
        } else {
            return (int) Math.ceil(Math.log10(minCapacity));
        }
    }

    private PooledByteBufferQueue getQueue(final int offset) {
        if (offset <= maxOffset) {
            return pooledBufferQueues[offset];
        } else {
            return null;
        }
    }

    /**
     * For testing.
     *
     * @return Number of buffers known to the pool, i.e. in the pool or on loan.
     */
    int getPooledBufferCount(final int minCapacity) {
        final int offset = getOffset(minCapacity);
        return pooledBufferQueues[offset].bufferCounter.get();
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


    // --------------------------------------------------------------------------------


    private static class PooledByteBufferQueue {

        private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PooledByteBufferQueue.class);

        private final BlockingQueue<OwnedBuffer> queue;
        private final int offset;
        // The max number of buffers for each buffer size that the pool should manage.
        private final int maxBufferCount;
        // The buffer capacity for each offset/index. Saves computing a Math.pow each time.
        private final int bufferSize;
        // The threshold for the number of created buffers (for each offset) to warn at.
        private final int warningThreshold;
        private final int warningThresholdPercent;
        // Keeps track of the number of buffers known to the pool
        // whether in the pool or currently on loan. Each AtomicInteger will increase until it hits its
        // configured limit then will never go down unless clear() is called.
        private final AtomicInteger bufferCounter;

        private PooledByteBufferQueue(final int offset,
                                      final int maxBufferCount,
                                      final int bufferSize,
                                      final int warningThresholdPercent) {
            this.offset = offset;
            // ArrayBlockingQueue seems to be marginally faster than a LinkedBlockingQueue
            this.queue = new ArrayBlockingQueue<>(maxBufferCount);
            this.maxBufferCount = maxBufferCount;
            this.bufferSize = bufferSize;
            this.warningThreshold = maxBufferCount > 1
                    ? (int) Math.ceil(maxBufferCount
                    * ((double) warningThresholdPercent / 100))
                    : -1;
            this.warningThresholdPercent = warningThresholdPercent;
            this.bufferCounter = new AtomicInteger(0);
            final int maxPooledBytes = maxBufferCount * bufferSize;
            LOGGER.debug(() ->
                    LogUtil.message("Creating byte buffer pool with bufferSize: {}, maxBufferCount: {}, " +
                                    "warningThresholdPercent: {}%, warningThreshold: {} buffers, maxPooledBytes: {}",
                            ModelStringUtil.formatCsv(bufferSize),
                            ModelStringUtil.formatCsv(maxBufferCount),
                            warningThresholdPercent,
                            ModelStringUtil.formatCsv(warningThreshold),
                            ByteSize.ofBytes(maxPooledBytes)));
        }

        private int size() {
            return queue.size();
        }

        public int getBufferSize() {
            return bufferSize;
        }

        private int drainTo(Collection<OwnedBuffer> collection) {
            return queue.drainTo(collection);
        }

        private void release(final OwnedBuffer ownedBuffer) {
            // should only be called for a non-null direct buffer that used to belong to this queue

            // Use offer rather than put as that will fail if the thread is interrupted but
            // we want the buffer back on the queue whatever happens, else the pool will be
            // exhausted.
            // As pooledBufferCounters controls the number of queued items we don't need to worry
            // about offer failing.
            final boolean didOfferSucceed = queue.offer(ownedBuffer);

            if (!didOfferSucceed) {
                // We must have created more buffers than there are under pool control, so we just have
                // to unmap it
//                LOGGER.debug(() -> LogUtil.message("Unable to return buffer to the queue so will destroy it " +
//                                "(capacity: {}, queue size: {}, counter value: {}, configured limit: {}",
//                        ModelStringUtil.formatCsv(ownedBuffer.byteBuffer.capacity()),
//                        queue.size(),
//                        bufferCounter.get(),
//                        maxBufferCount));

                ByteBufferSupport.unmap(ownedBuffer.byteBuffer);
            }
        }

        private OwnedBuffer getOwnedBuffer(final boolean createIfEmpty) {

            OwnedBuffer ownedBuffer = queue.poll();
            if (ownedBuffer == null) {
                // Queue empty so if the pool hasn't reached them limit for this buffer size
                // create a new one.
                if (createIfEmpty) {
                    final ByteBuffer buffer = createNewBufferIfAllowed();
                    if (buffer != null) {
                        ownedBuffer = new OwnedBuffer(buffer, this);
                    }
                }
//            } else {
                // buffer not final, so no lambda
//                if (LOGGER.isTraceEnabled()) {
//                    LOGGER.trace("Got byteBuffer with capacity: {} from the pool with offset: {}, queue size: {}",
//                            ModelStringUtil.formatCsv(ownedBuffer.byteBuffer.capacity()),
//                            offset,
//                            queue.size());
//                }
            }
            return ownedBuffer;
        }

        private ByteBuffer createNewBufferIfAllowed() {
            final ByteBuffer byteBuffer;

            // Atomically update up to the limit
            final int prevCount = bufferCounter.getAndUpdate(count ->
                    count < maxBufferCount
                            ? count + 1
                            : count);
            // Other threads may have advanced the counter by now so work off what we set it to,
            // so that the warnings get fired if applicable
            final int newCount = prevCount + 1;

            if (newCount <= maxBufferCount) {
                // We were successful in incrementing the count so we can create a new buffer
                if (newCount == warningThreshold) {
                    LOGGER.warn("Hit {}% ({}) of the limit of {} for pooled buffers of size {}.",
                            warningThresholdPercent,
                            warningThreshold,
                            maxBufferCount,
                            ModelStringUtil.formatCsv(bufferSize));
                } else if (newCount == maxBufferCount) {
                    LOGGER.warn("Hit limit of {} for pooled buffers of size {}. " +
                                    "Future calls to the pool will create new buffers but excess buffers " +
                                    "will have to be freed rather than returned to the pool. This may incur a " +
                                    "performance overhead. Consider changing the pool settings.",
                            newCount,
                            ModelStringUtil.formatCsv(bufferSize));
                }
//                        LOGGER.debug(() -> LogUtil.message("Creating new pooled buffer (capacity: {})",
//                                ModelStringUtil.formatCsv(bufferSize)));
                byteBuffer = ByteBuffer.allocateDirect(bufferSize);
            } else {
                byteBuffer = null;
            }
            return byteBuffer;
        }

        @Override
        public String toString() {
            return "PooledByteBufferQueue{" +
                    ", offset=" + offset +
                    ", maxBufferCount=" + maxBufferCount +
                    ", bufferSize=" + bufferSize +
                    ", warningThreshold=" + warningThreshold +
                    ", warningThresholdPercent=" + warningThresholdPercent +
                    ", bufferCounter=" + bufferCounter +
                    '}';
        }
    }


    // --------------------------------------------------------------------------------


    private record OwnedBuffer(ByteBuffer byteBuffer,
                               PooledByteBufferQueue pooledByteBufferQueue) {

        private OwnedBuffer {
            Objects.requireNonNull(byteBuffer);
        }

        private static OwnedBuffer unowned(final ByteBuffer byteBuffer) {
            return new OwnedBuffer(byteBuffer, null);
        }

        private void release() {
            if (pooledByteBufferQueue != null) {
                pooledByteBufferQueue.release(this);
            } else {
                ByteBufferSupport.unmap(byteBuffer);
            }
        }

        private void unmapBuffer() {
            ByteBufferSupport.unmap(byteBuffer);
        }
    }


    // --------------------------------------------------------------------------------


    /**
     * For use by one thread.
     */
    private static final class PooledByteBufferImpl8 implements PooledByteBuffer {

        // There is some code somewhere that gets a PooledByteBuffer but may not call getByteBuffer()
        // so make the buffer lazily fetched
        private Supplier<OwnedBuffer> ownedBufferSupplier;
        private OwnedBuffer ownedBuffer;

        private PooledByteBufferImpl8(final Supplier<OwnedBuffer> ownedBufferSupplier) {
            this.ownedBufferSupplier = ownedBufferSupplier;
        }

        private OwnedBuffer getOwnedBuffer() {
            if (ownedBufferSupplier != null) {
                ownedBuffer = ownedBufferSupplier.get();
                ownedBufferSupplier = null;
            }
            return ownedBuffer;
        }

        @Override
        public ByteBuffer getByteBuffer() {
            final OwnedBuffer ownedBuffer = getOwnedBuffer();
            Objects.requireNonNull(ownedBuffer, "Already released");
            return ownedBuffer.byteBuffer;
        }

        @Override
        public void doWithByteBuffer(final Consumer<ByteBuffer> byteBufferConsumer) {
            final OwnedBuffer ownedBuffer = getOwnedBuffer();
            Objects.requireNonNull(ownedBuffer, "Already released");
            try (this) {
                byteBufferConsumer.accept(ownedBuffer.byteBuffer);
            }
        }

        @Override
        public void clear() {
            final OwnedBuffer ownedBuffer = getOwnedBuffer();
            Objects.requireNonNull(ownedBuffer, "Already released");
            ownedBuffer.byteBuffer.clear();
        }

        @Override
        public Integer getCapacity() {
            final OwnedBuffer ownedBuffer = getOwnedBuffer();
            Objects.requireNonNull(ownedBuffer, "Already released");
            return ownedBuffer.byteBuffer.capacity();
        }

        @Override
        public void close() {
            //noinspection StatementWithEmptyBody
            if (ownedBufferSupplier != null) {
                // supplier not called so nothing to return
            } else {
                Objects.requireNonNull(ownedBuffer, "Already released");
                ownedBuffer.release();
            }
            ownedBuffer = null;
        }
    }


    // --------------------------------------------------------------------------------


    record Info(
            int bufferSize,
            Integer configuredCount,
            int effectiveCount,
            int warningThresholdCount,
            ByteSize maxPooledBytes) {

    }
}
