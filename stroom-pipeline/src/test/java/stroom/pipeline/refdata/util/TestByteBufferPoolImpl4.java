package stroom.pipeline.refdata.util;

import stroom.pipeline.refdata.ReferenceDataConfig;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        doTest(referenceDataConfig, 50, 0);
    }

    private void doTest(final ReferenceDataConfig referenceDataConfig,
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

    }
}