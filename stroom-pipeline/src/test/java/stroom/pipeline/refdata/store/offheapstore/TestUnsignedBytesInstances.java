package stroom.pipeline.refdata.store.offheapstore;

import stroom.pipeline.refdata.util.ByteBufferUtils;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

class TestUnsignedBytesInstances {

    private static final UnsignedBytes FOUR_UNSIGNED_BYTES = UnsignedBytesInstances.FOUR;

    private static final Logger LOGGER = LoggerFactory.getLogger(TestUnsignedBytesInstances.class);

    @Test
    void testPutGet() {
        doValTest(1);
        doValTest(Integer.MAX_VALUE);
        // Ensure we can go over integer's max value into 4 bytes.
        doValTest(Integer.MAX_VALUE + 1L);

        doValTest(FOUR_UNSIGNED_BYTES.getMaxVal());
    }

    @Test
    void testPutGet2() {
        Assertions.assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> {
                    doValTest(Long.MAX_VALUE);
                })
                .withMessageContaining("exceeds max value");
    }

    @Test
    void testPutAll() {
        final ByteBuffer byteBuffer = ByteBuffer.allocate(10);
        for (final UnsignedBytesInstances unsignedBytes : UnsignedBytesInstances.values()) {

            byteBuffer.clear();
            unsignedBytes.put(byteBuffer, 1L);

            LOGGER.info("Buffer {}", ByteBufferUtils.byteBufferInfo(byteBuffer));

            Assertions.assertThat(byteBuffer.position())
                    .isEqualTo(unsignedBytes.length());
            Assertions.assertThat(byteBuffer.position())
                    .isEqualTo(unsignedBytes.length());

            byteBuffer.clear();
            unsignedBytes.put(byteBuffer, unsignedBytes.getMaxVal());

            LOGGER.info("Buffer {}", ByteBufferUtils.byteBufferInfo(byteBuffer));
        }
    }

    private void doValTest(final long val) {
        LOGGER.info("val {}", val);
        final ByteBuffer byteBuffer = ByteBuffer.allocate(4);

        FOUR_UNSIGNED_BYTES.put(byteBuffer, val);
        byteBuffer.flip();

        LOGGER.info("Buffer {}", ByteBufferUtils.byteBufferInfo(byteBuffer));

        final long val2 = FOUR_UNSIGNED_BYTES.get(byteBuffer);

        LOGGER.info("Buffer {}", ByteBufferUtils.byteBufferInfo(byteBuffer));

        Assertions.assertThat(val2)
                .isEqualTo(val);
        Assertions.assertThat(byteBuffer.position())
                .isEqualTo(4);
        Assertions.assertThat(byteBuffer.remaining())
                .isEqualTo(0);

        byteBuffer.flip();

        final long val3 = FOUR_UNSIGNED_BYTES.get(byteBuffer);

        Assertions.assertThat(val3)
                .isEqualTo(val);

        Assertions.assertThat(byteBuffer.position())
                .isEqualTo(4);
        Assertions.assertThat(byteBuffer.remaining())
                .isEqualTo(0);

        byteBuffer.flip();

        byteBuffer.position(2);
        LOGGER.info("Buffer {}", ByteBufferUtils.byteBufferInfo(byteBuffer));
        final long val4 = FOUR_UNSIGNED_BYTES.get(byteBuffer, 0);

        Assertions.assertThat(val4)
                .isEqualTo(val);

        Assertions.assertThat(byteBuffer.position())
                .isEqualTo(2);
        Assertions.assertThat(byteBuffer.remaining())
                .isEqualTo(2);
    }

    @Test
    void testMaxVal() {
        for (final UnsignedBytesInstances unsignedBytes : UnsignedBytesInstances.values()) {
            LOGGER.info("Len {}, maxValue {}", unsignedBytes.length(), unsignedBytes.getMaxVal());
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
//        long max = FOUR_UNSIGNED_BYTES.getMaxVal();
//
//        for (long i = 0; i < max; i++) {
//            doAdditionTest(i, byteBuffer);
//        }

        doIncrementTest(0, byteBuffer);
        // Test the byte boundaries
        doIncrementTest(UnsignedBytesInstances.ONE.getMaxVal(), byteBuffer);
        doIncrementTest(UnsignedBytesInstances.TWO.getMaxVal(), byteBuffer);
        doIncrementTest(UnsignedBytesInstances.THREE.getMaxVal(), byteBuffer);
        doIncrementTest(UnsignedBytesInstances.FOUR.getMaxVal() - 1, byteBuffer);
    }

    @Test
    void testIncrementAll() {
        final ByteBuffer byteBuffer = ByteBuffer.allocate(10);

        for (final UnsignedBytesInstances unsignedBytes : UnsignedBytesInstances.values()) {
            byteBuffer.clear();

            unsignedBytes.put(byteBuffer, unsignedBytes.getMaxVal() - 1);
            byteBuffer.flip();

            long val = unsignedBytes.get(byteBuffer);
            byteBuffer.flip();

            LOGGER.info("Buffer {}", ByteBufferUtils.byteBufferInfo(byteBuffer));

            Assertions.assertThat(val)
                    .isEqualTo(unsignedBytes.getMaxVal() - 1);

            unsignedBytes.increment(byteBuffer);

            long val2 = unsignedBytes.get(byteBuffer);

            Assertions.assertThat(val2)
                    .isEqualTo(val + 1);
        }
    }

    @Test
    void testIncrement_bad() {
        final int len = FOUR_UNSIGNED_BYTES.length();
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(len);

        Assertions.assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> {
                    doIncrementTest(FOUR_UNSIGNED_BYTES.getMaxVal(), byteBuffer);
                })
                .withMessageContaining("Can't increment without overflowing");
    }

    private void doIncrementTest(final long val, final ByteBuffer byteBuffer) {

        byteBuffer.clear();

        FOUR_UNSIGNED_BYTES.put(byteBuffer, val);
        byteBuffer.flip();

//        LOGGER.info("val {}, Buffer {}",
//                Strings.padStart(Long.toString(val), 10, '0'),
//                ByteBufferUtils.byteBufferToHexAll(byteBuffer));


        final int pos = byteBuffer.position();
        final int cap = byteBuffer.capacity();
        final int limit = byteBuffer.limit();

        FOUR_UNSIGNED_BYTES.increment(byteBuffer);

//        LOGGER.info("Buffer {}", ByteBufferUtils.byteBufferToHexAll(byteBuffer));

        long val2 = FOUR_UNSIGNED_BYTES.get(byteBuffer, 0);

        Assertions.assertThat(byteBuffer.capacity()).isEqualTo(cap);
        Assertions.assertThat(byteBuffer.position()).isEqualTo(pos);
        Assertions.assertThat(byteBuffer.limit()).isEqualTo(limit);

        Assertions.assertThat(val2).isEqualTo(val + 1);
    }

    @Test
    void testDecrement() {
        int len = FOUR_UNSIGNED_BYTES.length();
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(len);

        // the following will test all values but takes a good few minutes
//        long max = FOUR_UNSIGNED_BYTES.getMaxVal();
//        int cnt = 0;
//        for (long i = max; i > 0; i--) {
//            doDecrementTest(i, byteBuffer);
//            cnt++;
//        }
//        LOGGER.info("Tested {} values", cnt);

        doDecrementTest(1, byteBuffer);
        // Test the byte boundaries
        doDecrementTest(UnsignedBytesInstances.ONE.getMaxVal() + 1, byteBuffer);
        doDecrementTest(UnsignedBytesInstances.TWO.getMaxVal() + 1, byteBuffer);
        doDecrementTest(UnsignedBytesInstances.THREE.getMaxVal() + 1, byteBuffer);
        doDecrementTest(UnsignedBytesInstances.FOUR.getMaxVal(), byteBuffer);
    }

    @Test
    void testDecrementAll() {
        final ByteBuffer byteBuffer = ByteBuffer.allocate(10);

        for (final UnsignedBytesInstances unsignedBytes : UnsignedBytesInstances.values()) {
            byteBuffer.clear();

            unsignedBytes.put(byteBuffer, unsignedBytes.getMaxVal());
            byteBuffer.flip();

            long val = unsignedBytes.get(byteBuffer);
            byteBuffer.flip();

            LOGGER.info("Buffer {}", ByteBufferUtils.byteBufferInfo(byteBuffer));

            Assertions.assertThat(val)
                    .isEqualTo(unsignedBytes.getMaxVal());

            unsignedBytes.decrement(byteBuffer);

            long val2 = unsignedBytes.get(byteBuffer);

            Assertions.assertThat(val2)
                    .isEqualTo(val - 1);
        }
    }

    @Test
    void testDecrement_bad() {
        final int len = FOUR_UNSIGNED_BYTES.length();
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(len);

        Assertions.assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> {
                    doDecrementTest(0, byteBuffer);
                })
                .withMessageContaining("Can't decrement without overflowing");
    }

    private void doDecrementTest(final long val, final ByteBuffer byteBuffer) {

        byteBuffer.clear();

        FOUR_UNSIGNED_BYTES.put(byteBuffer, val);
        byteBuffer.flip();

//        LOGGER.info("val {}, Buffer {}",
//                Strings.padStart(Long.toString(val), 10, '0'),
//                ByteBufferUtils.byteBufferToHexAll(byteBuffer));


        final int pos = byteBuffer.position();
        final int cap = byteBuffer.capacity();
        final int limit = byteBuffer.limit();

        FOUR_UNSIGNED_BYTES.decrement(byteBuffer);

//        LOGGER.info("Buffer {}", ByteBufferUtils.byteBufferToHexAll(byteBuffer));

        long val2 = FOUR_UNSIGNED_BYTES.get(byteBuffer, 0);

        Assertions.assertThat(byteBuffer.capacity()).isEqualTo(cap);
        Assertions.assertThat(byteBuffer.position()).isEqualTo(pos);
        Assertions.assertThat(byteBuffer.limit()).isEqualTo(limit);

        Assertions.assertThat(val2).isEqualTo(val - 1);
    }

}