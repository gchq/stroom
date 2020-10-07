package stroom.pipeline.refdata.util;

import stroom.pipeline.refdata.ReferenceDataConfig;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

class TestByteBufferPoolImpl4 {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestByteBufferPoolImpl4.class);

    @Test
    void testGetBuffer_defaultConfig() {
        final ReferenceDataConfig referenceDataConfig = new ReferenceDataConfig();

        doTest(referenceDataConfig, 100, 1);
    }

    @Test
    void testGetBuffer_nullMap() {
        final ReferenceDataConfig referenceDataConfig = new ReferenceDataConfig();
        referenceDataConfig.setPooledByteBufferCounts(null);

        // No map means no pooled buffers so you get the size you asked for
        doTest(referenceDataConfig, 50, 0);
    }

    @Test
    void testGetBuffer_emptyMap() {
        final ReferenceDataConfig referenceDataConfig = new ReferenceDataConfig();
        referenceDataConfig.setPooledByteBufferCounts(new HashMap<>());

        // No map means no pooled buffers so you get the size you asked for
        doTest(referenceDataConfig, 50, 0);
    }

    @Test
    void testGetBuffer_sparseMap() {
        final ReferenceDataConfig referenceDataConfig = new ReferenceDataConfig();
        referenceDataConfig.setPooledByteBufferCounts(Map.of(
                1, 20,
                10_000, 10));

        // sparse map means buffer sizes between 1 and 10_000 will get the default count and thus
        // will be pooled
        doTest(referenceDataConfig, 100, 1);
    }

    @Test
    void testGetBuffer_zeroValue() {
        final ReferenceDataConfig referenceDataConfig = new ReferenceDataConfig();
        referenceDataConfig.setPooledByteBufferCounts(Map.of(
                1, 20,
                10, 10,
                100, 0));

        final ByteBufferPool byteBufferPool = doTest(referenceDataConfig, 50, 0);

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

    private ByteBufferPool doTest(final ReferenceDataConfig referenceDataConfig,
                                  final int expectedBufferCapacity,
                                  final int expectedPoolSize) {
        final ByteBufferPool byteBufferPool = new ByteBufferPoolImpl4(referenceDataConfig);

        PooledByteBuffer pooledByteBuffer = byteBufferPool.getPooledByteBuffer(50);

        Assertions.assertThat(pooledByteBuffer.getByteBuffer().capacity())
                .isEqualTo(expectedBufferCapacity);

        pooledByteBuffer.close();

        Assertions.assertThat(byteBufferPool.getCurrentPoolSize())
                .isEqualTo(expectedPoolSize);

        LOGGER.info("System info: {}", byteBufferPool.getSystemInfo().getDetails());

        // Get each of the configured sizes
        if (referenceDataConfig.getPooledByteBufferCounts() != null
                && !referenceDataConfig.getPooledByteBufferCounts().isEmpty()) {

            int largestNonZeroOffset = referenceDataConfig.getPooledByteBufferCounts().entrySet()
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