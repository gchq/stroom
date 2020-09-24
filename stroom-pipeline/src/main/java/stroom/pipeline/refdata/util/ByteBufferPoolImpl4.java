package stroom.pipeline.refdata.util;

import stroom.pipeline.refdata.ReferenceDataConfig;
import stroom.util.HasHealthCheck;
import stroom.util.sysinfo.SystemInfoResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
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
 *
 * This impl uses {@link ArrayBlockingQueue}
 */
@Singleton
public class ByteBufferPoolImpl4 implements ByteBufferPool {

    private static final Logger LOGGER = LoggerFactory.getLogger(ByteBufferPoolImpl4.class);

//    private static final int[] SIZES = {
//            1,
//            10,
//            100,
//            1_000,
//            10_000,
//            100_000,
//            1_000_000};
    private static final int DEFAULT_MAX_BUFFERS_PER_QUEUE = 50;
//


    // In each of these collections, the index/offset is the log10 of the buffer size,
    // i.e. 1 => 0, 10 => 1, etc.

    // One queue for each size of buffer
    private final List<BlockingQueue<ByteBuffer>> pooledBufferQueues;
    // One counter for each size of buffer. Keeps track of the number of buffers known to the pool
    // whether in the pool or currently on loan.
    private final List<AtomicInteger> pooledBufferCounters;
    // The max number of buffers for each buffer size that the pool should manage.
    private final int[] maxBufferCounts;
    // The buffer capacity for each offset/index. Saves computing a Math.pow each time.
    private final int[] bufferSizes;
    // The number of different buffer sizes. Sizes start from one and go up in contiguous powers of ten.
    private final int sizesCount;

    @Inject
    public ByteBufferPoolImpl4(final ReferenceDataConfig referenceDataConfig) {
        final Map<Integer, Integer> pooledByteBufferCounts = referenceDataConfig.getPooledByteBufferCounts();

        final OptionalInt optMaxSize = pooledByteBufferCounts == null
                ? OptionalInt.empty()
                : pooledByteBufferCounts.keySet()
                    .stream()
                    .filter(ByteBufferPoolImpl4::isPowerOf10)
                    .mapToInt(Integer::intValue)
                    .max();

        if (optMaxSize.isPresent()) {
            // Going from a zero based offset to a count so have to add one.
            sizesCount = getOffset(optMaxSize.getAsInt()) + 1;
        } else {
            sizesCount = 0;
        }

        pooledBufferQueues = new ArrayList<>(sizesCount);
        pooledBufferCounters = new ArrayList<>(sizesCount);
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
            pooledBufferQueues.add(new ArrayBlockingQueue<>(configuredCount > 0 ? configuredCount : 1));
            pooledBufferCounters.add(new AtomicInteger(0));
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

    @Override
    public PooledByteBuffer getPooledByteBuffer(final int minCapacity) {
        return new PooledByteBuffer(() -> getBuffer(minCapacity), this::release);
    }

    private ByteBuffer getUnPooledBuffer(final int minCapacity) {
        return ByteBuffer.allocateDirect(minCapacity);
    }

    private ByteBuffer getBuffer(final int minCapacity) {
        final int offset = getOffset(minCapacity);
        if (isUnpooled(offset)) {
            LOGGER.warn("Using un-pooled buffer, size: {}", minCapacity);
            // Too big a buffer to pool so just create one
            return getUnPooledBuffer(minCapacity);
        } else {
            final BlockingQueue<ByteBuffer> byteBufferQueue = getByteBufferQueue(offset);

            ByteBuffer buffer = byteBufferQueue.poll();
            if (buffer == null) {
                buffer = createNewBufferIfAllowed(offset);
            }
            if (buffer == null) {
                try {
                    // At max pooled buffers so we have to wait for another thread to release
                    buffer = byteBufferQueue.take();
                } catch (InterruptedException e) {
                    LOGGER.error("Thread interrupted", e);
                    Thread.currentThread().interrupt();
                }
            }
            Objects.requireNonNull(buffer);
            // Ensure the buffer is ready for use with limits/positions/marks cleared
            buffer.clear();
            return buffer;
        }
    }

    void release(final ByteBuffer buffer) {
        final int offset = getOffset(buffer.capacity());
        if (isUnpooled(offset)) {
            // Too big a buffer to pool so do nothing so the JVM can de-reference it
        } else {
            final BlockingQueue<ByteBuffer> byteBufferQueue = getByteBufferQueue(offset);
            try {
                byteBufferQueue.put(buffer);
            } catch (InterruptedException e) {
                LOGGER.error("Thread interrupted", e);
                Thread.currentThread().interrupt();
            }
        }
    }

    private boolean isUnpooled(final int offset) {
        return offset > sizesCount || maxBufferCounts[offset] == 0;
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
    public int getCurrentPoolSize() {
        return pooledBufferQueues.stream()
                .mapToInt(Queue::size)
                .sum();
    }

    @Override
    public void clear() {
        pooledBufferQueues.forEach(Queue::clear);
    }

    @Override
    public SystemInfoResult getSystemInfo() {
        try {
            SystemInfoResult.Builder builder = SystemInfoResult.builder(getSystemInfoName())
                    .withDetail("Total buffers in pool", getCurrentPoolSize());

            SortedMap<Integer, Long> capacityCountsMap = null;
            try {
                capacityCountsMap = pooledBufferQueues.stream()
                        .flatMap(Queue::stream)
                        .collect(Collectors.groupingBy(Buffer::capacity, Collectors.counting()))
                        .entrySet()
                        .stream()
                        .collect(HasHealthCheck.buildTreeMapCollector(Map.Entry::getKey, Map.Entry::getValue));

                long totalSizeBytes = 0;

                for (int i = 0; i < sizesCount; i++) {
                    totalSizeBytes += pooledBufferCounters.get(i).get() * bufferSizes[i];
                }

                builder
                        .withDetail("Buffer capacity counts", capacityCountsMap)
                        .withDetail("Total size (bytes)",totalSizeBytes);
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
        final AtomicInteger bufferCounter = pooledBufferCounters.get(offset);
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

    private BlockingQueue<ByteBuffer> getByteBufferQueue(final int offset) {
        return pooledBufferQueues.get(offset);
    }

    private int getOffset(final int minCapacity) {
        return (int) Math.ceil(Math.log10(minCapacity));
    }

    private static boolean isPowerOf10(int n) {
        return n == 1L
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
}
