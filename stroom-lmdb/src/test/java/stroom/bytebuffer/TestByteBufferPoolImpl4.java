package stroom.bytebuffer;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class TestByteBufferPoolImpl4 {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestByteBufferPoolImpl4.class);

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
                        1, 20,
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
                        1, 20,
                        10, 10,
                        100, 0));

        final ByteBufferPool byteBufferPool = doTest(byteBufferPoolConfig, 50, 0);

        // No make sure we get the same instance back when using the pool twice
        PooledByteBuffer pooledByteBuffer = byteBufferPool.getPooledByteBuffer(10);

        int identityHashCode1 = System.identityHashCode(pooledByteBuffer.getByteBuffer());

        pooledByteBuffer.release();

        pooledByteBuffer = byteBufferPool.getPooledByteBuffer(10);

        int identityHashCode2 = System.identityHashCode(pooledByteBuffer.getByteBuffer());

        pooledByteBuffer.release();

        // Same byteBuffer instance from the pool
        Assertions.assertThat(identityHashCode2)
                .isEqualTo(identityHashCode1);

        // No make sure we get a different instance back when using the pool twice for an unpooled size
        pooledByteBuffer = byteBufferPool.getPooledByteBuffer(100);

        identityHashCode1 = System.identityHashCode(pooledByteBuffer.getByteBuffer());

        pooledByteBuffer.release();

        pooledByteBuffer = byteBufferPool.getPooledByteBuffer(10);

        identityHashCode2 = System.identityHashCode(pooledByteBuffer.getByteBuffer());

        pooledByteBuffer.release();

        // Different buffer instance as unpooled
        Assertions.assertThat(identityHashCode2)
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
        final ByteBufferPoolImpl4 byteBufferPool = new ByteBufferPoolImpl4(() -> byteBufferPoolConfig);

        final List<PooledByteBuffer> pooledByteBuffers = IntStream.rangeClosed(1, poolSize + 5)
                .boxed()
                .map(i -> {
                    return byteBufferPool.getPooledByteBuffer(capacity);

                })
                .collect(Collectors.toList());

        // get more buffers than the pool allows
        pooledByteBuffers.forEach(pooledByteBuffer -> {
            final ByteBuffer buffer = pooledByteBuffer.getByteBuffer();
            Assertions.assertThat(buffer.capacity())
                    .isEqualTo(capacity);
        });

        LOGGER.info("System info: {}", byteBufferPool.getSystemInfo().getDetails());

        Assertions.assertThat(byteBufferPool.getAvailableBufferCount(capacity))
                .isEqualTo(0);
        Assertions.assertThat(byteBufferPool.getPooledBufferCount(capacity))
                .isEqualTo(poolSize);

        pooledByteBuffers.forEach(PooledByteBuffer::release);

        LOGGER.info("System info: {}", byteBufferPool.getSystemInfo().getDetails());

        Assertions.assertThat(byteBufferPool.getAvailableBufferCount(capacity))
                .isEqualTo(poolSize);
        Assertions.assertThat(byteBufferPool.getPooledBufferCount(capacity))
                .isEqualTo(poolSize);
    }

    @Test
    void testExceedPoolSize_useBigger() {
        final int capacity = 100;
        final int poolSize = 10;
        final int excessCount = 5;
        final ByteBufferPoolConfig byteBufferPoolConfig = new ByteBufferPoolConfig()
                .withPooledByteBufferCounts(Map.of(
                        1, poolSize,
                        capacity, poolSize,
                        capacity * 10, poolSize));
        final ByteBufferPoolImpl4 byteBufferPool = new ByteBufferPoolImpl4(() -> byteBufferPoolConfig);

        final List<PooledByteBuffer> pooledByteBuffers = IntStream.rangeClosed(1, poolSize + excessCount)
                .boxed()
                .map(i -> {
                    return byteBufferPool.getPooledByteBuffer(capacity);

                })
                .collect(Collectors.toList());

        // get more buffers than the pool allows
        for (int i = 0; i < pooledByteBuffers.size(); i++) {
            final PooledByteBuffer pooledByteBuffer = pooledByteBuffers.get(i);
            final ByteBuffer buffer = pooledByteBuffer.getByteBuffer();
            if (i < poolSize) {
                Assertions.assertThat(buffer.capacity())
                        .isEqualTo(capacity);
            } else {
                // ran out of capacity buffers so got the bigger ones
                Assertions.assertThat(buffer.capacity())
                        .isEqualTo(capacity * 10);
            }
        }

        LOGGER.info("System info: {}", byteBufferPool.getSystemInfo().getDetails());

        Assertions.assertThat(byteBufferPool.getAvailableBufferCount(capacity))
                .isEqualTo(0);
        Assertions.assertThat(byteBufferPool.getPooledBufferCount(capacity))
                .isEqualTo(poolSize);
        Assertions.assertThat(byteBufferPool.getAvailableBufferCount(capacity * 10))
                .isEqualTo(0);
        Assertions.assertThat(byteBufferPool.getPooledBufferCount(capacity * 10))
                .isEqualTo(excessCount);

        pooledByteBuffers.forEach(PooledByteBuffer::release);

        LOGGER.info("System info: {}", byteBufferPool.getSystemInfo().getDetails());

        Assertions.assertThat(byteBufferPool.getAvailableBufferCount(capacity))
                .isEqualTo(poolSize);
        Assertions.assertThat(byteBufferPool.getPooledBufferCount(capacity))
                .isEqualTo(poolSize);
        // Pool only created excessCount of the bigger ones
        Assertions.assertThat(byteBufferPool.getAvailableBufferCount(capacity * 10))
                .isEqualTo(excessCount);
        Assertions.assertThat(byteBufferPool.getPooledBufferCount(capacity * 10))
                .isEqualTo(excessCount);
    }

    @Test
    void testExceedPoolSize_block() throws InterruptedException {
        final int capacity = 100;
        final int poolSize = 10;
        final int requiredCount = poolSize + 5;
        final ByteBufferPoolConfig byteBufferPoolConfig = new ByteBufferPoolConfig()
                .withPooledByteBufferCounts(Map.of(
                        1, poolSize,
                        capacity, poolSize))
                .withBlockOnExhaustedPool(true);
        final ByteBufferPoolImpl4 byteBufferPool = new ByteBufferPoolImpl4(() -> byteBufferPoolConfig);

        final List<PooledByteBuffer> pooledByteBuffers = IntStream.rangeClosed(1, requiredCount)
                .boxed()
                .map(i -> {
                    return byteBufferPool.getPooledByteBuffer(capacity);

                })
                .collect(Collectors.toList());

        final AtomicInteger counter = new AtomicInteger();

        final CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            // get more buffers than the pool allows, should block once it has poolSize
            pooledByteBuffers.forEach(pooledByteBuffer -> {
                final ByteBuffer buffer = pooledByteBuffer.getByteBuffer();
                counter.incrementAndGet();
                Assertions.assertThat(buffer.capacity())
                        .isEqualTo(capacity);
            });
        });

        Thread.sleep(500);

        LOGGER.info("System info: {}", byteBufferPool.getSystemInfo().getDetails());

        Assertions.assertThat(counter)
                .hasValue(poolSize);

        // Now release the first lot
        for (int i = 0; i < poolSize; i++) {
            pooledByteBuffers.get(i).release();
        }

        Thread.sleep(500);

        // all buffers should now have been acquired
        Assertions.assertThat(counter)
                .hasValue(requiredCount);

        Assertions.assertThat(future)
                .isCompleted();
    }

    private ByteBufferPool doTest(final ByteBufferPoolConfig byteBufferPoolConfig,
                                  final int expectedBufferCapacity,
                                  final int expectedPoolSize) {
        final ByteBufferPool byteBufferPool = new ByteBufferPoolImpl4(() -> byteBufferPoolConfig);

        PooledByteBuffer pooledByteBuffer = byteBufferPool.getPooledByteBuffer(50);

        Assertions.assertThat(pooledByteBuffer.getByteBuffer().capacity())
                .isEqualTo(expectedBufferCapacity);

        pooledByteBuffer.close();

        Assertions.assertThat(byteBufferPool.getCurrentPoolSize())
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
            for (int i = 0; i <= largestNonZeroOffset; i++) {
                int requiredSize = (int) Math.pow(10, i);
                PooledByteBuffer pooledByteBuffer2 = byteBufferPool.getPooledByteBuffer(requiredSize);
                final ByteBuffer byteBuffer = pooledByteBuffer2.getByteBuffer();
                Assertions.assertThat(byteBuffer)
                        .isNotNull();
                pooledByteBuffer2.release();
            }

            LOGGER.info("System info: {}", byteBufferPool.getSystemInfo().getDetails());

            // Should have at least one buffer due to the getPooledByteBuffer call above
            // +1 to convert from offset to count
            int expectedPooledBufferCount = Math.max(1, largestNonZeroOffset + 1);

            Assertions.assertThat(byteBufferPool.getCurrentPoolSize())
                    .isEqualTo(expectedPooledBufferCount);
        }
        return byteBufferPool;
    }
}
