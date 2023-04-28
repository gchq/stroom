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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class TestLmdbOrdering extends AbstractLmdbDbTest{

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestLmdbOrdering.class);

    private static final UnsignedBytes TWO_BYTE_UNSIGNED = UnsignedBytesInstances.TWO;
    private static final UnsignedBytes THREE_BYTE_UNSIGNED = UnsignedBytesInstances.THREE;

    private final ByteBufferPool byteBufferPool = new ByteBufferPoolFactory().getByteBufferPool();
    private BasicLmdbDb<TestKey, String> customKeyLmdbDb;

    @BeforeEach
    void setup() {
        customKeyLmdbDb = new BasicLmdbDb<>(
                lmdbEnv,
                byteBufferPool,
                new TestKeySerde(),
                new StringSerde(),
                "customKeyLmdbDb");
    }

    @Test
    void testOrder() {
        final long part1Max = TWO_BYTE_UNSIGNED.getMaxVal();
        LOGGER.info("part1MaxVal: {}", ModelStringUtil.formatCsv(part1Max));
        final long part2Max = THREE_BYTE_UNSIGNED.getMaxVal();
        LOGGER.info("part2MaxVal: {}", ModelStringUtil.formatCsv(part2Max));
        int i = 1;
        customKeyLmdbDb.put(TestKey.of(0L, 0L, "a"), "val-" + i++, false);
        customKeyLmdbDb.put(TestKey.of(0L, 0L, "z"), "val-" + i++, false);

        customKeyLmdbDb.put(TestKey.of(0L, 1L, "a"), "val-" + i++, false);
        customKeyLmdbDb.put(TestKey.of(0L, 1L, "z"), "val-" + i++, false);

        customKeyLmdbDb.put(TestKey.of(0L, part2Max, "a"), "val-" + i++, false);
        customKeyLmdbDb.put(TestKey.of(0L, part2Max, "z"), "val-" + i++, false);


        customKeyLmdbDb.put(TestKey.of(1L, 0L, "a"), "val-" + i++, false);
        customKeyLmdbDb.put(TestKey.of(1L, 0L, "z"), "val-" + i++, false);

        customKeyLmdbDb.put(TestKey.of(1L, 1L, "a"), "val-" + i++, false);
        customKeyLmdbDb.put(TestKey.of(1L, 1L, "z"), "val-" + i++, false);

        customKeyLmdbDb.put(TestKey.of(1L, part2Max, "a"), "val-" + i++, false);
        customKeyLmdbDb.put(TestKey.of(1L, part2Max, "z"), "val-" + i++, false);


        customKeyLmdbDb.put(TestKey.of(part1Max, 0L, "a"), "val-" + i++, false);
        customKeyLmdbDb.put(TestKey.of(part1Max, 0L, "z"), "val-" + i++, false);

        customKeyLmdbDb.put(TestKey.of(part1Max, 1L, "a"), "val-" + i++, false);
        customKeyLmdbDb.put(TestKey.of(part1Max, 1L, "z"), "val-" + i++, false);

        customKeyLmdbDb.put(TestKey.of(part1Max, part2Max, "a"), "val-" + i++, false);
        customKeyLmdbDb.put(TestKey.of(part1Max, part2Max, "z"), "val-" + i++, false);

        customKeyLmdbDb.logDatabaseContents(LOGGER::info);

        customKeyLmdbDb.logRawDatabaseContents(LOGGER::info);
    }

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
