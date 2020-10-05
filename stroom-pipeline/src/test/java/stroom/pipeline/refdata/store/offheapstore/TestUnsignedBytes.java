package stroom.pipeline.refdata.store.offheapstore;

import stroom.pipeline.refdata.util.ByteBufferUtils;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

class TestUnsignedBytes {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestUnsignedBytes.class);

    @Test
    void testPutGet() {
        doValTest(1);
        doValTest(Integer.MAX_VALUE);
        // Ensure we can go over integer's max value into 4 bytes.
        doValTest(Integer.MAX_VALUE + 1L);

        doValTest(UnsignedBytes.getMaxVal(4));
    }

    @Test
    void testPutGet2() {
        Assertions.assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> {
                    doValTest(Long.MAX_VALUE);
                })
                .withMessageContaining("exceeds max value");
    }

    private void doValTest(final long val) {
        LOGGER.info("val {}", val);
        final ByteBuffer byteBuffer = ByteBuffer.allocate(4);

        UnsignedBytes.put(byteBuffer, byteBuffer.capacity(), val);
        byteBuffer.flip();

        LOGGER.info("Buffer {}", ByteBufferUtils.byteBufferInfo(byteBuffer));

        final long val2 = UnsignedBytes.get(byteBuffer);

        Assertions.assertThat(val2)
                .isEqualTo(val);
    }

    @Test
    void testMaxVal() {
        for (int i = 1; i <= 8; i++) {
            final long maxValue = UnsignedBytes.getMaxVal(i);
            LOGGER.info("Len {}, maxValue {}", i, maxValue);
        }
        LOGGER.info("Signed:");
        LOGGER.info("Len {}, maxValue {}", 4, Integer.MAX_VALUE);
        LOGGER.info("Len {}, maxValue {}", 8, Long.MAX_VALUE);
    }

    @Test
    void testIncrement() {
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4);

        // the following will test all values but takes a few minutes
//        long max = UnsignedBytes.getMaxVal(4);
//
//        for (long i = 0; i < max; i++) {
//            doAdditionTest(i, byteBuffer);
//        }

        doAdditionTest(0, byteBuffer);
        doAdditionTest(UnsignedBytes.getMaxVal(1), byteBuffer);
        doAdditionTest(UnsignedBytes.getMaxVal(2), byteBuffer);
        doAdditionTest(UnsignedBytes.getMaxVal(3), byteBuffer);
        doAdditionTest(UnsignedBytes.getMaxVal(4) - 1, byteBuffer);
    }

    @Test
    void testIncrement_bad() {
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4);

        Assertions.assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> {
                    doAdditionTest(UnsignedBytes.getMaxVal(4), byteBuffer);
                })
                .withMessageContaining("Can't increment without overflowing");
    }

    private void doAdditionTest(final long val, final ByteBuffer byteBuffer) {

        byteBuffer.clear();

        UnsignedBytes.put(byteBuffer, byteBuffer.capacity(), val);
        byteBuffer.flip();

//        LOGGER.info("val {}, Buffer {}",
//                Strings.padStart(Long.toString(val), 10, '0'),
//                ByteBufferUtils.byteBufferToHexAll(byteBuffer));


        final int pos = byteBuffer.position();
        final int cap = byteBuffer.capacity();
        final int limit = byteBuffer.limit();

        UnsignedBytes.increment(byteBuffer, 4);

//        LOGGER.info("Buffer {}", ByteBufferUtils.byteBufferToHexAll(byteBuffer));

        long val2 = UnsignedBytes.get(byteBuffer);

        Assertions.assertThat(byteBuffer.capacity()).isEqualTo(cap);
        Assertions.assertThat(byteBuffer.position()).isEqualTo(pos);
        Assertions.assertThat(byteBuffer.limit()).isEqualTo(limit);

        Assertions.assertThat(val2).isEqualTo(val + 1);
    }
}