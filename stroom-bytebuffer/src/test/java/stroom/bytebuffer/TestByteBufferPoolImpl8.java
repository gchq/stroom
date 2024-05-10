package stroom.bytebuffer;

import stroom.test.common.TestUtil;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestByteBufferPoolImpl8 {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestByteBufferPoolImpl8.class);
    public static final int REQUESTED_CAPACITY = 50;

    @Test
    void testGetBuffer_defaultConfig() {
        final ByteBufferPoolConfig byteBufferPoolConfig = new ByteBufferPoolConfig();

        doTest(byteBufferPoolConfig, 100, 1);
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
        doTest(byteBufferPoolConfig, 100, 1);
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
        final ByteBufferPoolImpl8 byteBufferPool = new ByteBufferPoolImpl8(() -> byteBufferPoolConfig);

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

        final ByteBufferPoolImpl8 byteBufferPool = new ByteBufferPoolImpl8(() -> byteBufferPoolConfig);

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
        final ByteBufferPool byteBufferPool = new ByteBufferPoolImpl8(() -> byteBufferPoolConfig);

        PooledByteBuffer pooledByteBuffer = byteBufferPool.getPooledByteBuffer(REQUESTED_CAPACITY);

        assertThat(pooledByteBuffer.getByteBuffer().capacity())
                .isEqualTo(expectedBufferCapacity);

        pooledByteBuffer.close();

        assertThat(byteBufferPool.getCurrentPoolSize())
                .isEqualTo(expectedPoolSize);

        LOGGER.info("System info: {}", byteBufferPool.getSystemInfo().getDetails());

        // Get each of the configured sizes
        if (byteBufferPoolConfig.getPooledByteBufferCounts() != null
                && !byteBufferPoolConfig.getPooledByteBufferCounts().isEmpty()) {

            int largestNonZeroOffset = byteBufferPoolConfig.getPooledByteBufferCounts().entrySet()
                    .stream()
                    .filter(entry -> entry.getValue() > 0)
                    .mapToInt(entry -> (int) Math.log10(entry.getKey()))
                    .max()
                    .orElse(-1);

            // get a pooled buffer for each of the entries in the config unless thay have
            // a val of zero (i.e. unPooled)
            for (int i = 1; i <= largestNonZeroOffset; i++) {
                int requiredSize = (int) Math.pow(10, i);
                PooledByteBuffer pooledByteBuffer2 = byteBufferPool.getPooledByteBuffer(requiredSize);
                final ByteBuffer byteBuffer = pooledByteBuffer2.getByteBuffer();
                assertThat(byteBuffer)
                        .isNotNull();
                pooledByteBuffer2.close();
            }

            LOGGER.info("System info: {}", byteBufferPool.getSystemInfo().getDetails());

            // Should have at least one buffer due to the getPooledByteBuffer call above
            // -1 to account for there being no pool for offset 0
            // +1 to convert from offset to count
            int expectedPooledBufferCount = Math.max(1, largestNonZeroOffset - 1 + 1);

            assertThat(byteBufferPool.getCurrentPoolSize())
                    .isEqualTo(expectedPooledBufferCount);
        }
        return byteBufferPool;
    }

    @Test
    void testIsValidSize() {
        int i = 1;

        while (i <= 1_000_000_000) {

            // Make sure numbers either side are not powers of ten
            if (i > 1) {
                assertThat(ByteBufferPoolImpl8.isValidSize(i - 1))
                        .isFalse();
            }
            if (i >= 10) {
                assertThat(ByteBufferPoolImpl8.isValidSize(i))
                        .isTrue();
            } else {
                assertThat(ByteBufferPoolImpl8.isValidSize(i))
                        .isFalse();
            }

            assertThat(ByteBufferPoolImpl8.isValidSize(i + 1))
                    .isFalse();
            i *= 10;
        }
    }

    @TestFactory
    Stream<DynamicTest> testGetOffset() {
        return TestUtil.buildDynamicTestStream()
                .withInputAndOutputType(int.class)
                .withSingleArgTestFunction(ByteBufferPoolImpl8::getOffset)
                .withSimpleEqualityAssertion()
                .addCase(-1, 1)
                .addCase(0, 1)
                .addCase(1, 1)
                .addCase(2, 1)
                .addCase(10, 1)
                .addCase(11, 2)
                .addCase(100, 2)
                .addCase(101, 3)
                .addCase(1000, 3)
                .addCase(1001, 4)
                .addCase(10000, 4)
                .addCase(10001, 5)
                .build();
    }

    @Test
    void testGetOffset2() {
        int minCapacity = 1;
        while (minCapacity <= ByteBufferPoolImpl8.MAX_BUFFER_CAPACITY) {
            final int expectedOffset = (int) Math.ceil(Math.log10(minCapacity));
            final int actualOffset = ByteBufferPoolImpl8.getOffset(minCapacity);

            if (minCapacity <= 10) {
                assertThat(actualOffset)
                        .isEqualTo(1);
            } else {
                assertThat(actualOffset)
                        .isEqualTo(expectedOffset);

                // Check one below is in same offset
                assertThat(ByteBufferPoolImpl8.getOffset(minCapacity - 1))
                        .isEqualTo(expectedOffset);

                // Check one above is in next offset
                if (minCapacity < ByteBufferPoolImpl8.MAX_BUFFER_CAPACITY) {
                    assertThat(ByteBufferPoolImpl8.getOffset(minCapacity + 1))
                            .isEqualTo(expectedOffset + 1);
                }
            }
            minCapacity *= 10;
        }
    }
}
