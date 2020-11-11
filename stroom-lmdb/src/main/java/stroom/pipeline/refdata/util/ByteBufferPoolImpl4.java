package stroom.pipeline.refdata.util;

import stroom.util.HasHealthCheck;
import stroom.util.sysinfo.SystemInfoResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Queue;
import java.util.SortedMap;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(ByteBufferPoolImpl4.class);

    // If no count is provided for a buffer size in the config then this value is used.
    private static final int DEFAULT_MAX_BUFFERS_PER_QUEUE = 50;

    // In each of these collections, the index/offset is the log10 of the buffer size,
    // i.e. 1 => 0, 10 => 1, 100 => 2 etc.

    // One queue for each size of buffer
    private final BlockingQueue<ByteBuffer>[] pooledBufferQueues;
    // One counter for each size of buffer. Keeps track of the number of buffers known to the pool
    // whether in the pool or currently on loan.
    private final AtomicInteger[] pooledBufferCounters;
    // The max number of buffers for each buffer size that the pool should manage.
    private final int[] maxBufferCounts;
    // The buffer capacity for each offset/index. Saves computing a Math.pow each time.
    private final int[] bufferSizes;
    // The number of different buffer sizes. Sizes start from one and go up in contiguous powers of ten.
    private final int sizesCount;
    // The highest offset used in the pool;
    private final int maxOffset;

    @Inject
    public ByteBufferPoolImpl4(final ByteBufferPoolConfig byteBufferPoolConfig) {
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
            // Going from a zero based offset to a count so have to add one.
            maxOffset = getOffset(optMaxSize.getAsInt());
            sizesCount = maxOffset + 1;
        } else {
            maxOffset = -1;
            sizesCount = 0;
        }

        pooledBufferQueues = new BlockingQueue[sizesCount];
        pooledBufferCounters = new AtomicInteger[sizesCount];
        maxBufferCounts = new int[sizesCount];
        bufferSizes = new int[sizesCount];

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
            // If the configuredCount is 0 it means we will allocate on demand do no need to hold the queue/counter
            pooledBufferQueues[i] = configuredCount > 1 ? new ArrayBlockingQueue<>(configuredCount) : null;
            pooledBufferCounters[i] = configuredCount > 1 ? new AtomicInteger(0) : null;
            maxBufferCounts[i] = configuredCount;
            bufferSizes[i] = bufferCapacity;
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
            // Too big a buffer to pool or configured not to pool so just create one
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
                    buffer = byteBufferQueue.take();
                } catch (InterruptedException e) {
                    LOGGER.error("Thread interrupted waiting for a buffer from the pool", e);
                    Thread.currentThread().interrupt();
                }
            }
            Objects.requireNonNull(buffer, "Something has gone wrong, we should have a non-null buffer");
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
            try {
                pooledBufferQueues[offset].put(buffer);
            } catch (InterruptedException e) {
                LOGGER.error("Thread interrupted", e);
                Thread.currentThread().interrupt();
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
        for (final BlockingQueue<ByteBuffer> pooledBufferQueue : pooledBufferQueues) {
            if (pooledBufferQueue != null) {
                pooledBufferQueue.clear();
            }
        }
    }

    @Override
    public SystemInfoResult getSystemInfo() {
        try {
            SystemInfoResult.Builder builder = SystemInfoResult.builder(getSystemInfoName())
                    .withDetail("Total buffers in pool", getCurrentPoolSize());

            SortedMap<Integer, Long> capacityCountsMap = null;
            try {
                capacityCountsMap = Arrays.stream(pooledBufferQueues)
                        .filter(Objects::nonNull)
                        .flatMap(Queue::stream)
                        .collect(Collectors.groupingBy(Buffer::capacity, Collectors.counting()))
                        .entrySet()
                        .stream()
                        .collect(HasHealthCheck.buildTreeMapCollector(Map.Entry::getKey, Map.Entry::getValue));

                long totalSizeBytes = 0;

                for (int i = 0; i < sizesCount; i++) {
                    if (pooledBufferCounters[i] != null) {
                        totalSizeBytes += pooledBufferCounters[i].get() * bufferSizes[i];
                    }
                }

                builder
                        .withDetail("Buffer capacity counts", capacityCountsMap)
                        .withDetail("Total size (bytes)", totalSizeBytes);
            } catch (Exception e) {
                LOGGER.error("Error getting capacity counts", e);
                builder.withDetail("Buffer capacity counts", "Error getting counts");
            }

            return builder.build();
        } catch (RuntimeException e) {
            return SystemInfoResult.builder(getSystemInfoName())
                    .withError(e)
                    .build();
        }
    }

    private ByteBuffer createNewBufferIfAllowed(final int offset) {
        final int maxBufferCount = maxBufferCounts[offset];
        final AtomicInteger bufferCounter = pooledBufferCounters[offset];
        ByteBuffer byteBuffer = null;

        while (true) {
            int currBufferCount = bufferCounter.get();
            if (currBufferCount < maxBufferCount) {
                if (bufferCounter.compareAndSet(currBufferCount, currBufferCount + 1)) {
                    // Succeeded in incrementing the count so we can create one
                    final int roundedCapacity = bufferSizes[offset];
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

    private int getOffset(final int minCapacity) {
        return (int) Math.ceil(Math.log10(minCapacity));
    }
}
