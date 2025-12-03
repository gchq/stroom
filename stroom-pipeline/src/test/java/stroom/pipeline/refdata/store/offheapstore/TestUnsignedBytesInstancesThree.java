/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.pipeline.refdata.store.offheapstore;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.lmdb.serde.UnsignedBytes;
import stroom.lmdb.serde.UnsignedBytesInstances;
import stroom.test.common.TestUtil;
import stroom.util.shared.ModelStringUtil;

import io.vavr.Tuple;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestUnsignedBytesInstancesThree {

    private static final UnsignedBytes THREE_UNSIGNED_BYTES = UnsignedBytesInstances.THREE;

    private static final Logger LOGGER = LoggerFactory.getLogger(TestUnsignedBytesInstancesThree.class);

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
        UnsignedBytesInstances.allPositive(unsignedBytes -> {
            byteBuffer.clear();
            unsignedBytes.put(byteBuffer, 1L);

            LOGGER.info("Buffer {}", ByteBufferUtils.byteBufferInfo(byteBuffer));

            assertThat(byteBuffer.position())
                    .isEqualTo(unsignedBytes.length());
            assertThat(byteBuffer.position())
                    .isEqualTo(unsignedBytes.length());

            byteBuffer.clear();
            unsignedBytes.put(byteBuffer, unsignedBytes.getMaxVal());

            LOGGER.info("Buffer {}", ByteBufferUtils.byteBufferInfo(byteBuffer));
        });
    }

    private void doValTest(final long val) {
        LOGGER.info("val {}", val);
        final ByteBuffer byteBuffer = ByteBuffer.allocate(4);

        THREE_UNSIGNED_BYTES.put(byteBuffer, val);
        byteBuffer.flip();

        LOGGER.info("Buffer {}", ByteBufferUtils.byteBufferInfo(byteBuffer));

        final long val2 = THREE_UNSIGNED_BYTES.get(byteBuffer);

        LOGGER.info("Buffer {}", ByteBufferUtils.byteBufferInfo(byteBuffer));

        assertThat(val2)
                .isEqualTo(val);
        assertThat(byteBuffer.position())
                .isEqualTo(4);
        assertThat(byteBuffer.remaining())
                .isEqualTo(0);

        byteBuffer.flip();

        final long val3 = THREE_UNSIGNED_BYTES.get(byteBuffer);

        assertThat(val3)
                .isEqualTo(val);

        assertThat(byteBuffer.position())
                .isEqualTo(4);
        assertThat(byteBuffer.remaining())
                .isEqualTo(0);

        byteBuffer.flip();

        byteBuffer.position(2);
        LOGGER.info("Buffer {}", ByteBufferUtils.byteBufferInfo(byteBuffer));
        final long val4 = THREE_UNSIGNED_BYTES.get(byteBuffer, 0);

        assertThat(val4)
                .isEqualTo(val);

        assertThat(byteBuffer.position())
                .isEqualTo(2);
        assertThat(byteBuffer.remaining())
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

    @TestFactory
    Stream<DynamicTest> testIncrement() {
        final int len = 3;
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(len);
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(long.class, ByteBuffer.class)
                .withOutputType(long.class)
                .withTestFunction(testCase -> doIncrementTest(
                        testCase.getInput()._1,
                        testCase.getInput()._2))
                .withSimpleEqualityAssertion()
                .addCase(
                        Tuple.of(UnsignedBytesInstances.ONE.getMaxVal(), byteBuffer),
                        UnsignedBytesInstances.ONE.getMaxVal() + 1)
                .addCase(
                        Tuple.of(UnsignedBytesInstances.TWO.getMaxVal(), byteBuffer),
                        UnsignedBytesInstances.TWO.getMaxVal() + 1)
                .addCase(
                        Tuple.of(UnsignedBytesInstances.THREE.getMaxVal() - 1, byteBuffer),
                        UnsignedBytesInstances.THREE.getMaxVal())
                .addThrowsCase(
                        Tuple.of(UnsignedBytesInstances.THREE.getMaxVal(), byteBuffer),
                        IllegalArgumentException.class)
                .build();
    }

    @Test
    void incrementAll() {
        final long max = THREE_UNSIGNED_BYTES.getMaxVal();
        final int len = 3;
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(len);
        // Try increment for all 16.2mil values
        for (long i = 0; i < max; i++) {
            if (i % 1_000_000 == 0) {
                LOGGER.debug("Done {}", i);
            }
            final long output = doIncrementTest(i, byteBuffer);
            if (i == max - 1) {
                LOGGER.debug("i: {}, output: {}", i, output);
            }
            assertThat(output)
                    .isEqualTo(i + 1);
        }
    }

    @Test
    void testIncrementAll_max() {
        final ByteBuffer byteBuffer = ByteBuffer.allocate(10);
        UnsignedBytesInstances.allPositive(unsignedBytes -> {
            byteBuffer.clear();

            unsignedBytes.put(byteBuffer, unsignedBytes.getMaxVal() - 1);
            byteBuffer.flip();

            final long val = unsignedBytes.get(byteBuffer);
            byteBuffer.flip();

            LOGGER.info("Buffer {}", ByteBufferUtils.byteBufferInfo(byteBuffer));

            assertThat(val)
                    .isEqualTo(unsignedBytes.getMaxVal() - 1);

            unsignedBytes.increment(byteBuffer);

            final long val2 = unsignedBytes.get(byteBuffer);

            LOGGER.info("unsignedBytes: {}, val: {}, val2: {}",
                    unsignedBytes, ModelStringUtil.formatCsv(val), ModelStringUtil.formatCsv(val2));

            assertThat(val2)
                    .isEqualTo(val + 1);
        });
    }

    @Test
    void testIncrementAll_zero() {
        final ByteBuffer byteBuffer = ByteBuffer.allocate(10);
        UnsignedBytesInstances.allPositive(unsignedBytes -> {
            byteBuffer.clear();

            unsignedBytes.put(byteBuffer, 0L);
            byteBuffer.flip();

            final long val = unsignedBytes.get(byteBuffer);
            byteBuffer.flip();

            LOGGER.info("Buffer {}", ByteBufferUtils.byteBufferInfo(byteBuffer));

            assertThat(val)
                    .isEqualTo(0L);

            unsignedBytes.increment(byteBuffer);

            final long val2 = unsignedBytes.get(byteBuffer);

            LOGGER.info("unsignedBytes: {}, val: {}, val2: {}",
                    unsignedBytes, ModelStringUtil.formatCsv(val), ModelStringUtil.formatCsv(val2));

            assertThat(val2)
                    .isEqualTo(val + 1);
        });
    }

    @Test
    void testIncrement_bad() {
        final int len = THREE_UNSIGNED_BYTES.length();
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(len);

        Assertions.assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> {
                    doIncrementTest(THREE_UNSIGNED_BYTES.getMaxVal(), byteBuffer);
                })
                .withMessageContaining("Can't increment without overflowing")
                .withMessageContaining(ModelStringUtil.formatCsv(THREE_UNSIGNED_BYTES.getMaxVal()));
    }

    private long doIncrementTest(final long val, final ByteBuffer byteBuffer) {

        byteBuffer.clear();

        THREE_UNSIGNED_BYTES.put(byteBuffer, val);
        byteBuffer.flip();

//        LOGGER.info("val {}, Buffer {}",
//                Strings.padStart(Long.toString(val), 10, '0'),
//                ByteBufferUtils.byteBufferToHexAll(byteBuffer));


        final int pos = byteBuffer.position();
        final int cap = byteBuffer.capacity();
        final int limit = byteBuffer.limit();

        THREE_UNSIGNED_BYTES.increment(byteBuffer);

//        LOGGER.info("Buffer {}", ByteBufferUtils.byteBufferToHexAll(byteBuffer));

        final long output = THREE_UNSIGNED_BYTES.get(byteBuffer, 0);

        assertThat(byteBuffer.capacity()).isEqualTo(cap);
        assertThat(byteBuffer.position()).isEqualTo(pos);
        assertThat(byteBuffer.limit()).isEqualTo(limit);

        return output;
    }

    @Test
    void testDecrement() {
        final int len = THREE_UNSIGNED_BYTES.length();
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
        doDecrementTest(UnsignedBytesInstances.THREE.getMaxVal(), byteBuffer);
    }

    @Test
    void testDecrementAll() {
        final ByteBuffer byteBuffer = ByteBuffer.allocate(10);
        UnsignedBytesInstances.allPositive(unsignedBytes -> {
            byteBuffer.clear();

            unsignedBytes.put(byteBuffer, unsignedBytes.getMaxVal());
            byteBuffer.flip();

            final long val = unsignedBytes.get(byteBuffer);
            byteBuffer.flip();

            LOGGER.info("Buffer {}", ByteBufferUtils.byteBufferInfo(byteBuffer));

            assertThat(val)
                    .isEqualTo(unsignedBytes.getMaxVal());

            unsignedBytes.decrement(byteBuffer);

            final long val2 = unsignedBytes.get(byteBuffer);

            assertThat(val2)
                    .isEqualTo(val - 1);
        });
    }

    @Test
    void testDecrement_bad() {
        final int len = THREE_UNSIGNED_BYTES.length();
        final ByteBuffer byteBuffer = ByteBuffer.allocateDirect(len);

        Assertions.assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> {
                    doDecrementTest(0, byteBuffer);
                })
                .withMessageContaining("Can't decrement without overflowing");
    }

    @Test
    void testZero() {
        final byte[] bytes = THREE_UNSIGNED_BYTES.toBytes(0);
        Assertions.assertThat(bytes)
                .isEqualTo(new byte[]{0, 0, 0});
    }

    private void doDecrementTest(final long val, final ByteBuffer byteBuffer) {

        byteBuffer.clear();

        THREE_UNSIGNED_BYTES.put(byteBuffer, val);
        byteBuffer.flip();

//        LOGGER.info("val {}, Buffer {}",
//                Strings.padStart(Long.toString(val), 10, '0'),
//                ByteBufferUtils.byteBufferToHexAll(byteBuffer));


        final int pos = byteBuffer.position();
        final int cap = byteBuffer.capacity();
        final int limit = byteBuffer.limit();

        THREE_UNSIGNED_BYTES.decrement(byteBuffer);

//        LOGGER.info("Buffer {}", ByteBufferUtils.byteBufferToHexAll(byteBuffer));

        final long val2 = THREE_UNSIGNED_BYTES.get(byteBuffer, 0);

        assertThat(byteBuffer.capacity()).isEqualTo(cap);
        assertThat(byteBuffer.position()).isEqualTo(pos);
        assertThat(byteBuffer.limit()).isEqualTo(limit);

        assertThat(val2).isEqualTo(val - 1);
    }

}
