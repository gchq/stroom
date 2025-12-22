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

import stroom.bytebuffer.ByteBufferPool;
import stroom.bytebuffer.ByteBufferPoolFactory;
import stroom.lmdb.serde.Serde;
import stroom.lmdb.serde.StringSerde;
import stroom.lmdb.serde.UnsignedBytes;
import stroom.lmdb.serde.UnsignedBytesInstances;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ModelStringUtil;

import com.google.common.base.Strings;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lmdbjava.KeyRange;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class TestLmdbOrdering extends AbstractLmdbDbTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestLmdbOrdering.class);

    private static final UnsignedBytes TWO_BYTE_UNSIGNED = UnsignedBytesInstances.TWO;
    private static final UnsignedBytes THREE_BYTE_UNSIGNED = UnsignedBytesInstances.THREE;

    private final ByteBufferPool byteBufferPool = new ByteBufferPoolFactory().getByteBufferPool();

    @BeforeEach
    void setup() {
    }

    @Test
    void testOrder_customKey() {
        final BasicLmdbDb<TestKey, String> db = new BasicLmdbDb<>(
                lmdbEnv,
                byteBufferPool,
                new TestKeySerde(),
                new StringSerde(),
                "customKeyLmdbDb");

        final long part1Max = TWO_BYTE_UNSIGNED.getMaxVal();
        LOGGER.info("part1MaxVal: {}", ModelStringUtil.formatCsv(part1Max));
        final long part2Max = THREE_BYTE_UNSIGNED.getMaxVal();
        LOGGER.info("part2MaxVal: {}", ModelStringUtil.formatCsv(part2Max));
        int i = 1;

        for (final Long part1 : List.of(0L, 1L, part1Max)) {
            for (final Long part2 : List.of(0L, 1L, part2Max)) {
                for (final String str : List.of("a", "z")) {
                    final TestKey testKey = TestKey.of(part1, part2, str);
                    final String val = "val-" + i++;
                    LOGGER.info("Putting {} : {}", testKey, val);
                    db.put(testKey, val, false);
                }
            }
        }

        db.logDatabaseContents(LOGGER::info);
        db.logRawDatabaseContents(LOGGER::info);

        lmdbEnv.doWithReadTxn(txn -> {
            final List<Long> part1Values = db.streamEntries(txn, KeyRange.all(), stream ->
                    stream.map(entry -> entry.getKey().part1)
                            .distinct()
                            .collect(Collectors.toList()));

            Assertions.assertThat(part1Values)
                    .containsExactly(
                            0L,
                            1L,
                            part1Max);

            final List<Long> part2Values = db.streamEntries(txn, KeyRange.all(), stream ->
                    stream.map(entry -> entry.getKey().part2)
                            .distinct()
                            .collect(Collectors.toList()));

            Assertions.assertThat(part2Values)
                    .containsExactly(
                            0L,
                            1L,
                            part2Max);
        });
    }

    @Test
    void testOrder_signedLongs() {
        final BasicLmdbDb<Long, String> db = new BasicLmdbDb<>(
                lmdbEnv,
                byteBufferPool,
                new SignedLongSerde(),
                new StringSerde(),
                "customKeyLmdbDb");

        int i = 1;
        db.put(Long.MIN_VALUE, "val-" + i++, false);
        db.put(-2L, "val-" + i++, false);
        db.put(-1L, "val-" + i++, false);
        db.put(0L, "val-" + i++, false);
        db.put(1L, "val-" + i++, false);
        db.put(2L, "val-" + i++, false);
        db.put(Long.MAX_VALUE, "val-" + i++, false);

        db.logDatabaseContents(LOGGER::info);
        db.logRawDatabaseContents(LOGGER::info);

        lmdbEnv.doWithReadTxn(txn -> {
            final List<Long> keys = db.streamEntries(txn, KeyRange.all(), stream ->
                    stream.map(Entry::getKey)
                            .collect(Collectors.toList()));
            Assertions.assertThat(keys)
                    .containsExactly(
                            0L,
                            1L,
                            2L,
                            Long.MAX_VALUE,
                            Long.MIN_VALUE,
                            -2L,
                            -1L);
        });
    }

    private static class SignedLongSerde implements Serde<Long> {

        @Override
        public Long deserialize(final ByteBuffer byteBuffer) {
            final long val = byteBuffer.getLong();
            byteBuffer.rewind();
            return val;
        }

        @Override
        public void serialize(final ByteBuffer byteBuffer, final Long val) {
            byteBuffer.putLong(val);
            byteBuffer.flip();
        }
    }
    // --------------------------------------------------------------------------------

    private static class TestKey {

        // 2 bytes
        private final long part1;
        // 3 bytes
        private final long part2;
        private final String str;

        private TestKey(final long part1, final long part2, final String str) {
            this.part1 = part1;
            this.part2 = part2;
            this.str = str;
        }

        private static TestKey of(final long part1, final long part2, final String str) {
            return new TestKey(part1, part2, str);
        }

        @Override
        public String toString() {
            return Strings.padStart(
                    String.valueOf(part1),
                    String.valueOf(TWO_BYTE_UNSIGNED.maxValue()).length(),
                    '0')
                    + "_"
                    + Strings.padStart(
                    String.valueOf(part2),
                    String.valueOf(THREE_BYTE_UNSIGNED.maxValue()).length(),
                    '0')
                    + "_"
                    + str;
        }
    }

    // --------------------------------------------------------------------------------

    private static class TestKeySerde implements Serde<TestKey> {

        @Override
        public TestKey deserialize(final ByteBuffer byteBuffer) {
            final long part1 = TWO_BYTE_UNSIGNED.get(byteBuffer);
            final long part2 = THREE_BYTE_UNSIGNED.get(byteBuffer);
            final String str = StandardCharsets.UTF_8.decode(byteBuffer).toString();
            byteBuffer.rewind();
            return TestKey.of(part1, part2, str);
        }

        @Override
        public void serialize(final ByteBuffer byteBuffer, final TestKey testKey) {
            TWO_BYTE_UNSIGNED.put(byteBuffer, testKey.part1);
            THREE_BYTE_UNSIGNED.put(byteBuffer, testKey.part2);
            byteBuffer.put(testKey.str.getBytes(StandardCharsets.UTF_8));
            byteBuffer.flip();
        }
    }
}
