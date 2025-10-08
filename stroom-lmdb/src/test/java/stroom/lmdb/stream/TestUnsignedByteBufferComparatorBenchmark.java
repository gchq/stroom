package stroom.lmdb.stream;

import stroom.test.common.TestUtil;
import stroom.test.common.TestUtil.TimedCase;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class TestUnsignedByteBufferComparatorBenchmark {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestUnsignedByteBufferComparatorBenchmark.class);

    @Test
    void test() {

        // Make a big list of ByteBuffers
        final List<ByteBuffer> list = new ArrayList<>();
        for (int length = 1; length <= 110; length++) {
            for (int i = 0; i < 10000; i++) {
                final ByteBuffer bb = ByteBuffer.allocateDirect(length);
                for (int pos = 0; pos < length; pos++) {
                    bb.put((byte) (Math.random() * 256));
                }
                bb.flip();
                list.add(bb);
            }
        }

        final OldUnsignedByteBufferComparator comparator1 = new OldUnsignedByteBufferComparator();
        final UnsignedByteBufferComparator comparator2 = new UnsignedByteBufferComparator();

        final TimedCase testOldUnsignedByteBufferComparator = TimedCase.of("Test OldUnsignedByteBufferComparator",
                (round, iterations) -> {
                    final List<ByteBuffer> copy1 = new ArrayList<>(list);
                    copy1.sort(comparator1);
                });

        final TimedCase testUnsignedByteBufferComparator = TimedCase.of("Test UnsignedByteBufferComparator",
                (round, iterations) -> {
                    final List<ByteBuffer> copy2 = new ArrayList<>(list);
                    copy2.sort(comparator2);
                });

        final int iterations = 10000000;
        TestUtil.comparePerformance(
                10,
                iterations,
                LOGGER::info,
                testOldUnsignedByteBufferComparator,
                testUnsignedByteBufferComparator);
    }
}
