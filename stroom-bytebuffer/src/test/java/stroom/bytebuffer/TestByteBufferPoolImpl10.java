package stroom.bytebuffer;

import stroom.util.NullSafe;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestByteBufferPoolImpl10 {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestByteBufferPoolImpl10.class);
    public static final int REQUESTED_CAPACITY = 50;

    @Test
    void testGetBuffer_defaultConfig() {
        final ByteBufferPoolConfig byteBufferPoolConfig = new ByteBufferPoolConfig();

        doTest(byteBufferPoolConfig, 64, 1);
    }

    @Test
    void testGetBuffer_customConfig() {
        final ByteBufferPoolConfig byteBufferPoolConfig = new ByteBufferPoolConfig()
                .withPooledByteBufferCounts(Map.of(
                        4, 100,
                        8, 100,
                        32, 100,
                        128, 100));

        doTest(byteBufferPoolConfig, 128, 1);
    }

    @Test
    void testAllSizes() {
        ByteBufferPoolConfig config = new ByteBufferPoolConfig().withPooledByteBufferCounts(
                Map.ofEntries(
                        Map.entry(4, 1_000),
                        Map.entry(8, 1_000),
                        Map.entry(16, 1_000),
                        Map.entry(32, 1_000),
                        Map.entry(64, 1_000),
                        Map.entry(128, 1_000),
                        Map.entry(256, 1_000),
                        Map.entry(512, 1_000),
                        Map.entry(1_024, 1_000),
                        Map.entry(2_048, 1_000),
                        Map.entry(4_096, 1_000),
                        Map.entry(8_192, 1_000),
                        Map.entry(16_384, 1_000),
                        Map.entry(32_768, 1_000),
                        Map.entry(65_536, 1_000)));

        final List<Integer> capacities = config.getPooledByteBufferCounts()
                .keySet()
                .stream()
                .sorted()
                .toList();

        ByteBufferPool pool = new ByteBufferPoolImpl10(() -> config);

        // for each capacity request cap-1, cap, cap+1 to make sure we are getting the right
        // buffers back
        for (final int capacity : capacities) {
            Stream.of(-1, 0, 1)
                    .forEach(delta -> {
                        int requestedCap = capacity + delta;
                        pool.doWithBuffer(requestedCap, byteBuffer -> {
                            LOGGER.debug("requestedCap: {}, actual: {}", requestedCap, byteBuffer.capacity());
                            if (requestedCap > 65_536) {
                                // Above our biggest pooled buffer, so we get a non pooled one of requested size
                                assertThat(byteBuffer.capacity())
                                        .isEqualTo(requestedCap);
                            } else if (delta == 1) {
                                assertThat(byteBuffer.capacity())
                                        .isEqualTo(capacity * 2);
                            } else {
                                assertThat(byteBuffer.capacity())
                                        .isEqualTo(capacity);
                            }
                        });
                    });

        }
    }

    @Test
    void testAllSizes_acquireRelease() {
        ByteBufferPoolConfig config = new ByteBufferPoolConfig().withPooledByteBufferCounts(
                Map.ofEntries(
                        Map.entry(4, 1_000),
                        Map.entry(8, 1_000),
                        Map.entry(16, 1_000),
                        Map.entry(32, 1_000),
                        Map.entry(64, 1_000),
                        Map.entry(128, 1_000),
                        Map.entry(256, 1_000),
                        Map.entry(512, 1_000),
                        Map.entry(1_024, 1_000),
                        Map.entry(2_048, 1_000),
                        Map.entry(4_096, 1_000),
                        Map.entry(8_192, 1_000),
                        Map.entry(16_384, 1_000),
                        Map.entry(32_768, 1_000),
                        Map.entry(65_536, 1_000)));
        final List<Integer> capacities = config.getPooledByteBufferCounts()
                .keySet()
                .stream()
                .sorted()
                .toList();
        ByteBufferPoolImpl10 pool = new ByteBufferPoolImpl10(() -> config);

        // for each capacity request cap-1, cap, cap+1 to make sure we are getting the right
        // buffers back
        for (final int capacity : capacities) {
            Stream.of(-1, 0, 1)
                    .forEach(delta -> {
                        int requestedCap = capacity + delta;
                        ByteBuffer byteBuffer = null;
                        try {
                            byteBuffer = pool.acquire(requestedCap);
                            LOGGER.debug("requestedCap: {}, actual: {}", requestedCap, byteBuffer.capacity());

                            if (requestedCap > 65_536) {
                                // Above our biggest pooled buffer, so we get a non pooled one of requested size
                                assertThat(byteBuffer.capacity())
                                        .isEqualTo(requestedCap);
                            } else if (delta == 1) {
                                assertThat(byteBuffer.capacity())
                                        .isEqualTo(capacity * 2);
                            } else {
                                assertThat(byteBuffer.capacity())
                                        .isEqualTo(capacity);
                            }

                        } finally {
                            pool.release(byteBuffer);
                        }
                    });
        }
    }

    @Test
    void testGetBuffer_nullMap() {
        final ByteBufferPoolConfig byteBufferPoolConfig = new ByteBufferPoolConfig()
                .withPooledByteBufferCounts(null);

        // No map means no pooled buffers so you get the size you asked for
        doTest(byteBufferPoolConfig, 50, 0);
    }

    @Test
    void testGetBuffer_emptyMap() {
        final ByteBufferPoolConfig byteBufferPoolConfig = new ByteBufferPoolConfig()
                .withPooledByteBufferCounts(new HashMap<>());

        // No map means no pooled buffers so you get the size you asked for
        doTest(byteBufferPoolConfig, 50, 0);
    }

    @Test
    void testGetBuffer_sparseMap() {
        final ByteBufferPoolConfig byteBufferPoolConfig = new ByteBufferPoolConfig()
                .withPooledByteBufferCounts(Map.of(
                        10, 20,
                        10_000, 10));

        // sparse map means buffer sizes between 1 and 10_000 will get the default count and thus
        // will be pooled
        doTest(byteBufferPoolConfig, 10_000, 1);
    }

    @SuppressWarnings("checkstyle:variabledeclarationusagedistance")
    @Test
    void testGetBuffer_zeroValue() {
        final ByteBufferPoolConfig byteBufferPoolConfig = new ByteBufferPoolConfig()
                .withPooledByteBufferCounts(Map.of(
                        10, 10,
                        100, 0));

        final ByteBufferPool byteBufferPool = doTest(
                byteBufferPoolConfig, REQUESTED_CAPACITY, 0);

        // No make sure we get the same instance back when using the pool twice
        PooledByteBuffer pooledByteBuffer = byteBufferPool.getPooledByteBuffer(10);

        int identityHashCode1 = System.identityHashCode(pooledByteBuffer.getByteBuffer());

        pooledByteBuffer.close();

        pooledByteBuffer = byteBufferPool.getPooledByteBuffer(10);

        int identityHashCode2 = System.identityHashCode(pooledByteBuffer.getByteBuffer());

        pooledByteBuffer.close();

        // Same byteBuffer instance from the pool
        assertThat(identityHashCode2)
                .isEqualTo(identityHashCode1);

        // No make sure we get a different instance back when using the pool twice for an unpooled size
        pooledByteBuffer = byteBufferPool.getPooledByteBuffer(100);

        identityHashCode1 = System.identityHashCode(pooledByteBuffer.getByteBuffer());

        pooledByteBuffer.close();

        pooledByteBuffer = byteBufferPool.getPooledByteBuffer(10);

        identityHashCode2 = System.identityHashCode(pooledByteBuffer.getByteBuffer());

        pooledByteBuffer.close();

        // Different buffer instance as unpooled
        assertThat(identityHashCode2)
                .isNotEqualTo(identityHashCode1);
    }


    @Test
    void testExceedPoolSize_createNew() {
        final int capacity = 100;
        final int poolSize = 10;
        final ByteBufferPoolConfig byteBufferPoolConfig = new ByteBufferPoolConfig()
                .withPooledByteBufferCounts(Map.of(
                        1, poolSize,
                        capacity, poolSize));
        final ByteBufferPoolImpl10 byteBufferPool = new ByteBufferPoolImpl10(() -> byteBufferPoolConfig);

        final List<PooledByteBuffer> pooledByteBuffers = IntStream.rangeClosed(1, poolSize + 5)
                .boxed()
                .map(i -> {
                    return byteBufferPool.getPooledByteBuffer(capacity);

                })
                .toList();

        // get more buffers than the pool allows
        pooledByteBuffers.forEach(pooledByteBuffer -> {
            final ByteBuffer buffer = pooledByteBuffer.getByteBuffer();
            assertThat(buffer.capacity())
                    .isEqualTo(capacity);
        });

        LOGGER.info("System info: {}", byteBufferPool.getSystemInfo().getDetails());

        assertThat(byteBufferPool.getAvailableBufferCount(capacity))
                .isEqualTo(0);
        assertThat(byteBufferPool.getPooledBufferCount(capacity))
                .isEqualTo(poolSize);

        pooledByteBuffers.forEach(PooledByteBuffer::close);

        LOGGER.info("System info: {}", byteBufferPool.getSystemInfo().getDetails());

        assertThat(byteBufferPool.getAvailableBufferCount(capacity))
                .isEqualTo(poolSize);
        assertThat(byteBufferPool.getPooledBufferCount(capacity))
                .isEqualTo(poolSize);
    }

    @Test
    void testExceedPoolSize_useBigger() {
        final int requestedCapacity = 90;
        final int capacity = 100;
        final int nextCapacity = capacity * 10;
        final int poolSize = 10;
        final int excessCount = 5;
        final ByteBufferPoolConfig byteBufferPoolConfig = new ByteBufferPoolConfig()
                .withPooledByteBufferCounts(Map.of(
                        capacity, poolSize,
                        nextCapacity, poolSize));

        final ByteBufferPoolImpl10 byteBufferPool = new ByteBufferPoolImpl10(() -> byteBufferPoolConfig);

        byteBufferPool.doWithBuffer(nextCapacity, byteBuffer -> {
            // This ensures the pool has a buffer with nextCapacity
        });

        final List<PooledByteBuffer> pooledByteBuffers = IntStream.rangeClosed(1, poolSize + excessCount)
                .boxed()
                .map(i -> {
                    return byteBufferPool.getPooledByteBuffer(requestedCapacity);
                })
                .toList();

        // get more buffers than the pool allows
        for (int i = 0; i < pooledByteBuffers.size(); i++) {
            final PooledByteBuffer pooledByteBuffer = pooledByteBuffers.get(i);

            final ByteBuffer buffer = pooledByteBuffer.getByteBuffer();
            LOGGER.debug("i: {}, class: {} - capacity: {}",
                    i, pooledByteBuffer.getClass().getSimpleName(), buffer.capacity());
            if (i < poolSize) {
                assertThat(buffer.capacity())
                        .isEqualTo(capacity);
            } else if (i == poolSize) {
                // ran out of capacity buffers so got the bigger ones
                assertThat(buffer.capacity())
                        .isEqualTo(nextCapacity);
            } else {
                // There was only one capacity 1000 one in the pool so these should
                // all be non pooled ones so at the requested capacity and not a power of 10
                assertThat(buffer.capacity())
                        .isEqualTo(requestedCapacity);
            }
        }

        LOGGER.info("System info: {}", byteBufferPool.getSystemInfo().getDetails());

        assertThat(byteBufferPool.getAvailableBufferCount(capacity))
                .isEqualTo(0);
        assertThat(byteBufferPool.getPooledBufferCount(capacity))
                .isEqualTo(poolSize);
        // We only made one of these
        assertThat(byteBufferPool.getAvailableBufferCount(nextCapacity))
                .isEqualTo(0);
        assertThat(byteBufferPool.getPooledBufferCount(nextCapacity))
                .isEqualTo(1);

        pooledByteBuffers.forEach(PooledByteBuffer::close);

        LOGGER.info("System info: {}", byteBufferPool.getSystemInfo().getDetails());

        assertThat(byteBufferPool.getAvailableBufferCount(capacity))
                .isEqualTo(poolSize);
        assertThat(byteBufferPool.getPooledBufferCount(capacity))
                .isEqualTo(poolSize);
        // We only made one of these
        assertThat(byteBufferPool.getAvailableBufferCount(nextCapacity))
                .isEqualTo(1);
        assertThat(byteBufferPool.getPooledBufferCount(nextCapacity))
                .isEqualTo(1);
    }

    private ByteBufferPool doTest(final ByteBufferPoolConfig byteBufferPoolConfig,
                                  final int expectedBufferCapacity,
                                  final int expectedPoolSize) {
        final ByteBufferPool byteBufferPool = new ByteBufferPoolImpl10(() -> byteBufferPoolConfig);

        PooledByteBuffer pooledByteBuffer = byteBufferPool.getPooledByteBuffer(REQUESTED_CAPACITY);

        assertThat(pooledByteBuffer.getByteBuffer().capacity())
                .isEqualTo(expectedBufferCapacity);

        pooledByteBuffer.close();

        assertThat(byteBufferPool.getCurrentPoolSize())
                .isEqualTo(expectedPoolSize);

        LOGGER.info("System info: {}", byteBufferPool.getSystemInfo().getDetails());

        // Get each of the configured sizes
        final Map<Integer, Integer> pooledByteBufferCounts = byteBufferPoolConfig.getPooledByteBufferCounts();
        if (NullSafe.hasEntries(pooledByteBufferCounts)) {

            final AtomicInteger expectedPooledBufferCount = new AtomicInteger();
            pooledByteBufferCounts.forEach((capacity, count) -> {
                // Get, use and return the buffer for this size
                PooledByteBuffer pooledByteBuffer2 = byteBufferPool.getPooledByteBuffer(capacity);
                final ByteBuffer byteBuffer = pooledByteBuffer2.getByteBuffer();
                assertThat(byteBuffer)
                        .isNotNull();
                LOGGER.debug("capacity: {}, count: {}, received: {}", capacity, count, byteBuffer.capacity());
                pooledByteBuffer2.close();

                if (count > 0) {
                    expectedPooledBufferCount.incrementAndGet();
                }
            });

            LOGGER.info("System info: {}", byteBufferPool.getSystemInfo().getDetails());

            assertThat(byteBufferPool.getCurrentPoolSize())
                    .isEqualTo(expectedPooledBufferCount.get());
        }
        return byteBufferPool;
    }
}
