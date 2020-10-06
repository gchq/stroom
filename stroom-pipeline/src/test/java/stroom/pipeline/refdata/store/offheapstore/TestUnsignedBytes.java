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

        LOGGER.info("Buffer {}", ByteBufferUtils.byteBufferInfo(byteBuffer));

        Assertions.assertThat(val2)
                .isEqualTo(val);
        Assertions.assertThat(byteBuffer.position())
                .isEqualTo(4);
        Assertions.assertThat(byteBuffer.remaining())
                .isEqualTo(0);

        byteBuffer.flip();

        final long val3 = UnsignedBytes.get(byteBuffer, 4);

        Assertions.assertThat(val3)
                .isEqualTo(val);

        Assertions.assertThat(byteBuffer.position())
                .isEqualTo(4);
        Assertions.assertThat(byteBuffer.remaining())
                .isEqualTo(0);

        byteBuffer.flip();

        byteBuffer.position(2);
        LOGGER.info("Buffer {}", ByteBufferUtils.byteBufferInfo(byteBuffer));
        final long val4 = UnsignedBytes.get(byteBuffer, 0, 4);

        Assertions.assertThat(val4)
                .isEqualTo(val);

        Assertions.assertThat(byteBuffer.position())
                .isEqualTo(2);
        Assertions.assertThat(byteBuffer.remaining())
                .isEqualTo(2);
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
        final int len = 4;
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(len);

        // the following will test all values but takes a few minutes
//        long max = UnsignedBytes.getMaxVal(4);
//
//        for (long i = 0; i < max; i++) {
//            doAdditionTest(i, byteBuffer);
//        }

        doIncrementTest(0, byteBuffer, len);
        doIncrementTest(UnsignedBytes.getMaxVal(1), byteBuffer, len);
        doIncrementTest(UnsignedBytes.getMaxVal(2), byteBuffer, len);
        doIncrementTest(UnsignedBytes.getMaxVal(3), byteBuffer, len);
        doIncrementTest(UnsignedBytes.getMaxVal(4) - 1, byteBuffer, len);
    }

    @Test
    void testIncrement_bad() {
        final int len = 4;
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(len);

        Assertions.assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> {
                    doIncrementTest(UnsignedBytes.getMaxVal(len), byteBuffer, len);
                })
                .withMessageContaining("Can't increment without overflowing");
    }

    private void doIncrementTest(final long val, final ByteBuffer byteBuffer, final int len) {

        byteBuffer.clear();

        UnsignedBytes.put(byteBuffer, byteBuffer.capacity(), val);
        byteBuffer.flip();

//        LOGGER.info("val {}, Buffer {}",
//                Strings.padStart(Long.toString(val), 10, '0'),
//                ByteBufferUtils.byteBufferToHexAll(byteBuffer));


        final int pos = byteBuffer.position();
        final int cap = byteBuffer.capacity();
        final int limit = byteBuffer.limit();

        UnsignedBytes.increment(byteBuffer, len);

//        LOGGER.info("Buffer {}", ByteBufferUtils.byteBufferToHexAll(byteBuffer));

        long val2 = UnsignedBytes.get(byteBuffer, 0, len);

        Assertions.assertThat(byteBuffer.capacity()).isEqualTo(cap);
        Assertions.assertThat(byteBuffer.position()).isEqualTo(pos);
        Assertions.assertThat(byteBuffer.limit()).isEqualTo(limit);

        Assertions.assertThat(val2).isEqualTo(val + 1);
    }

    @Test
    void testDecrement() {
        int len = 4;
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4);

        // the following will test all values but takes a good few minutes
//        long max = UnsignedBytes.getMaxVal(4);
//        int cnt = 0;
//        for (long i = max; i > 0; i--) {
//            doDecrementTest(i, byteBuffer);
//            cnt++;
//        }
//        LOGGER.info("Tested {} values", cnt);

        doDecrementTest(1, byteBuffer, len);
        doDecrementTest(UnsignedBytes.getMaxVal(1) + 1, byteBuffer, len);
        doDecrementTest(UnsignedBytes.getMaxVal(2) + 1, byteBuffer, len);
        doDecrementTest(UnsignedBytes.getMaxVal(3) + 1, byteBuffer, len);
        doDecrementTest(UnsignedBytes.getMaxVal(4), byteBuffer, len);
    }

    @Test
    void testDecrement_bad() {
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(4);

        Assertions.assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> {
                    doDecrementTest(0, byteBuffer, 4);
                })
                .withMessageContaining("Can't decrement without overflowing");
    }

    private void doDecrementTest(final long val, final ByteBuffer byteBuffer, final int len) {

        byteBuffer.clear();

        UnsignedBytes.put(byteBuffer, byteBuffer.capacity(), val);
        byteBuffer.flip();

//        LOGGER.info("val {}, Buffer {}",
//                Strings.padStart(Long.toString(val), 10, '0'),
//                ByteBufferUtils.byteBufferToHexAll(byteBuffer));


        final int pos = byteBuffer.position();
        final int cap = byteBuffer.capacity();
        final int limit = byteBuffer.limit();

        UnsignedBytes.decrement(byteBuffer, len);

//        LOGGER.info("Buffer {}", ByteBufferUtils.byteBufferToHexAll(byteBuffer));

        long val2 = UnsignedBytes.get(byteBuffer, 0, len);

        Assertions.assertThat(byteBuffer.capacity()).isEqualTo(cap);
        Assertions.assertThat(byteBuffer.position()).isEqualTo(pos);
        Assertions.assertThat(byteBuffer.limit()).isEqualTo(limit);

        Assertions.assertThat(val2).isEqualTo(val - 1);
    }

}