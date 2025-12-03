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

package stroom.lmdb;

import stroom.bytebuffer.ByteBufferPoolFactory;
import stroom.bytebuffer.ByteBufferUtils;
import stroom.lmdb.serde.Serde;
import stroom.lmdb.serde.StringSerde;
import stroom.test.common.TestUtil;
import stroom.test.common.TestUtil.TimedCase;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.lmdbjava.KeyRange;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.SequencedMap;

import static java.lang.Long.reverseBytes;
import static java.nio.ByteOrder.LITTLE_ENDIAN;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

public class TestLmdbJavaComparator extends AbstractLmdbDbTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestLmdbJavaComparator.class);

    final ByteBuffer buffer1 = ByteBuffer.allocate(20);
    final ByteBuffer buffer2 = ByteBuffer.allocate(20);

    @Test
    void testLmdbOrder() {
        final List<Long> longs = List.of(Long.MIN_VALUE, -10L, -1L, 0L, -1L, 10L, Long.MAX_VALUE);

        for (int i = 0; i <= 5; i++) {
            final int padding = i;
            LOGGER.debug(LogUtil.inSeparatorLine("Padding: {}", padding));
            final PaddedLongSerde keySerde = new PaddedLongSerde(i);
            final BasicLmdbDb<PaddedLong, String> db = new BasicLmdbDb<>(
                    lmdbEnv,
                    new ByteBufferPoolFactory().getByteBufferPool(),
                    keySerde,
                    new StringSerde(),
                    "padding-" + i);

            lmdbEnv.doWithWriteTxn(writeTxn -> {
                for (final Long aLong : longs) {
                    db.put(writeTxn,
                            new PaddedLong(padding, aLong),
                            "val-" + padding + "-" + aLong,
                            false);
                }
//                db.logDatabaseContents(writeTxn, LOGGER::debug);
                db.forEachEntryAsBytes(writeTxn, keyVal -> {
                    final PaddedLong paddedLong = db.deserializeKey(keyVal.key());
                    LOGGER.debug("paddedLong: {}, key: {}, val: {}",
                            paddedLong,
                            ByteBufferUtils.byteBufferToHex(keyVal.key()),
                            ByteBufferUtils.byteBufferInfo(keyVal.val()));
                });
            });
        }
    }

    @Test
    void testSigned() {
        final BasicLmdbDb<ByteBuffer, String> db = new BasicLmdbDb<>(
                lmdbEnv,
                new ByteBufferPoolFactory().getByteBufferPool(),
                new ByteBufferSerde(),
                new StringSerde(),
                "testSigned");

        final ByteBuffer byteBuffer1 = ByteBuffer.allocate(2);
        final ByteBuffer byteBuffer2 = ByteBuffer.allocate(2);

        byteBuffer1.put((byte) 0x00);
        byteBuffer1.put((byte) 0x00);

        byteBuffer2.put((byte) 0xFF);
        byteBuffer2.put((byte) 0xFD);

        byteBuffer1.flip();
        byteBuffer2.flip();

        lmdbEnv.doWithWriteTxn(writeTxn -> {
            db.put(writeTxn, byteBuffer1, "A", false);
            db.put(writeTxn, byteBuffer2, "B", false);

            db.forEachEntryAsBytes(writeTxn, keyVal -> {
                LOGGER.debug("key: {}, val: {}",
                        ByteBufferUtils.byteBufferInfo(keyVal.key()),
                        ByteBufferUtils.byteBufferInfo(keyVal.val()));
            });
            final SequencedMap<ByteBuffer, String> data = db.asSequencedMap(writeTxn, KeyRange.all());
            assertThat(data.firstEntry().getValue())
                    .isEqualTo("A");
            assertThat(data.lastEntry().getValue())
                    .isEqualTo("B");
        });

        // This is a signed compare, so is different to the LMDB sort order
        final int compare1 = byteBuffer1.compareTo(byteBuffer2);
        // This is LmdbJava's unsigned compare method, which should match LMDB
        final int compare2 = compareBuff(byteBuffer1, byteBuffer2);
        LOGGER.debug("compare1: {}, compare2: {}", compare1, compare2);

//        Assertions.assertThat(compare2)
//                .isEqualTo(compare1);
    }

    @Test
    void testCompareMethods() {
        final List<Long> longs = List.of(0L, 1L, 10L, 100L, 1_000L, 10_000L, Long.MAX_VALUE);
        for (int i = 0; i < longs.size(); i++) {
            for (int j = 0; j < longs.size(); j++) {
                final long long1 = longs.get(i);
                final long long2 = longs.get(j);
                final int compare1a = Long.compareUnsigned(long1, long2);
                final int compare1b = Long.compare(long1, long2);

                for (int padding = 0; padding <= 9; padding++) {
                    writeBufs(padding, long1, long2);
                    // Now compare the buffers and make sure that the result matches
                    // the long compare as the padding is always equal
                    final int compare2 = compareBuff(buffer1, buffer2);
//                    final int compare3 = buffer1.compareTo(buffer2);
//                    final int compare3 = jdkCompare(buffer1, buffer2);

                    LOGGER.debug("Comparing {} to {} with padding {}: {} vs {} vs {}, " +
                                 "buffer1: {}, buffer2: {}",
                            long1, long2, padding, compare1a, compare1b, compare2,
                            ByteBufferUtils.byteBufferToHex(buffer1),
                            ByteBufferUtils.byteBufferToHex(buffer2));
                    assertThat(compare1a)
                            .isEqualTo(compare1b);
                    assertThat(compare2)
                            .isEqualTo(compare1a);
//                    assertCompareResults(compare3, compare2);
                }
            }
        }
    }

    @Disabled
    @Test
    void testReflectionPerf() {
        writeBufs(1, 38383, 23523525);
        final TimedCase jdkCompare = TimedCase.of("jdk", (round, iterations) -> {
            final int res = buffer1.compareTo(buffer2);
            if (res >= 0) {
                throw new RuntimeException("bad");
            }
        });

        final TimedCase compareBuf = TimedCase.of("compareBuf", (round, iterations) -> {
            final int res = compareBuff(buffer1, buffer2);
            if (res >= 0) {
                throw new RuntimeException("bad");
            }
        });

        TestUtil.comparePerformance(3,
                10_000_000L,
                LOGGER::debug,
                jdkCompare,
                compareBuf);
    }

    private void assertCompareResults(final int actual, final int expected) {
        assertThat(normaliseCompareResult(actual))
                .isEqualTo(normaliseCompareResult(expected));
    }

    private int normaliseCompareResult(final int compareResult) {
        if (compareResult < 0) {
            return -1;
        } else if (compareResult > 0) {
            return 1;
        } else {
            return 0;
        }
    }

    private void writeBufs(final int padding, final long long1, final long long2) {
        buffer1.clear();
        buffer2.clear();
        // Pad out the buffer before the value
        for (int i = 0; i < padding; i++) {
            buffer1.put((byte) 0);
            buffer2.put((byte) 0);
        }

        buffer1.putLong(long1);
        buffer2.putLong(long2);
        buffer1.flip();
        buffer2.flip();
    }


    /**
     * Borrowed from org.lmdbjava.ByteBufferProxy for testing
     */
    public static int compareBuff(final ByteBuffer o1, final ByteBuffer o2) {
        requireNonNull(o1);
        requireNonNull(o2);
        if (o1.equals(o2)) {
            return 0;
        }
        final int minLength = Math.min(o1.limit(), o2.limit());
        final int minWords = minLength / Long.BYTES;

        final boolean reverse1 = o1.order() == LITTLE_ENDIAN;
        final boolean reverse2 = o2.order() == LITTLE_ENDIAN;
        for (int i = 0; i < minWords * Long.BYTES; i += Long.BYTES) {
            final long lw = reverse1
                    ? reverseBytes(o1.getLong(i))
                    : o1.getLong(i);
            final long rw = reverse2
                    ? reverseBytes(o2.getLong(i))
                    : o2.getLong(i);
            final int diff = Long.compareUnsigned(lw, rw);
            if (diff != 0) {
                return diff;
            }
        }

        for (int i = minWords * Long.BYTES; i < minLength; i++) {
            final int lw = Byte.toUnsignedInt(o1.get(i));
            final int rw = Byte.toUnsignedInt(o2.get(i));
            final int result = Integer.compareUnsigned(lw, rw);
            if (result != 0) {
                return result;
            }
        }
        return o1.remaining() - o2.remaining();
    }

    // --------------------------------------------------------------------------------


    private record PaddedLong(int padding, long val) {

    }


    // --------------------------------------------------------------------------------


    private static class PaddedLongSerde implements Serde<PaddedLong> {

        private final int padding;

        private PaddedLongSerde(final int padding) {
            this.padding = padding;
        }

        @Override
        public PaddedLong deserialize(final ByteBuffer byteBuffer) {

            // Consume padding
            for (int i = 0; i < padding; i++) {
                byteBuffer.get();
            }
            final long val = byteBuffer.getLong();

            byteBuffer.flip();
            return new PaddedLong(padding, val);
        }

        @Override
        public void serialize(final ByteBuffer byteBuffer, final PaddedLong paddedLong) {
            if (paddedLong.padding != padding) {
                throw new IllegalArgumentException(LogUtil.message("Padding mismatch, {} vs {}",
                        padding, paddedLong.padding));
            }
            // Add padding
            for (int i = 0; i < padding; i++) {
                byteBuffer.put((byte) 0);
            }
            byteBuffer.putLong(paddedLong.val);
            byteBuffer.flip();
        }
    }


    // --------------------------------------------------------------------------------


    public static class ByteBufferSerde implements Serde<ByteBuffer> {

        @Override
        public ByteBuffer deserialize(final ByteBuffer byteBuffer) {
            final ByteBuffer copy = ByteBuffer.allocate(byteBuffer.remaining());
            ByteBufferUtils.copy(byteBuffer, copy);
            return copy;
        }

        @Override
        public void serialize(final ByteBuffer byteBuffer, final ByteBuffer copy) {
            ByteBufferUtils.copy(copy, byteBuffer);
        }
    }
}
