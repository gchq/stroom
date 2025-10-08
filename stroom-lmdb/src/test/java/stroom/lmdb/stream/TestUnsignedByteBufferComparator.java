package stroom.lmdb.stream;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TestUnsignedByteBufferComparator {


    /**
     * Test extents for all comparators.
     */
    @Test
    void test() {
        final List<ByteBuffer> list = new ArrayList<>();
        for (int length = 1; length <= 100; length++) {
            for (int pos = 0; pos < length; pos++) {
                final byte[] bytesZero = new byte[length];
                list.add(ByteBuffer.wrap(bytesZero));

                final byte[] bytesMin = new byte[length];
                bytesMin[pos] = Byte.MIN_VALUE;
                list.add(ByteBuffer.wrap(bytesMin));

                final byte[] bytesMax = new byte[length];
                bytesMax[pos] = Byte.MAX_VALUE;
                list.add(ByteBuffer.wrap(bytesMax));
            }
        }

        list.sort(new OldUnsignedByteBufferComparator());

        // Check that a copy sorted with signed bytes is not equal.
        final List<ByteBuffer> copy = new ArrayList<>(list);
        copy.sort(ByteBuffer::compareTo);
        assertThat(copy).isNotEqualTo(list);


        // Check that a copy sorted with unsigned bytes is not equal.
        final List<ByteBuffer> copy2 = new ArrayList<>(list);
        copy2.sort(new UnsignedByteBufferComparator());
        assertThat(copy2).isEqualTo(list);
    }

    /**
     * Test that both comparators behave the same way.
     */
    @Test
    void testRandom() {
        final List<ByteBuffer> list = new ArrayList<>();
        for (int length = 1; length <= 11; length++) {
            for (int i = 0; i < 100000; i++) {
                final byte[] bytes = new byte[length];
                for (int pos = 0; pos < length; pos++) {
                    bytes[pos] = (byte) (Math.random() * 256);
                }
                list.add(ByteBuffer.wrap(bytes));
            }
        }

        final List<ByteBuffer> copy1 = new ArrayList<>(list);
        copy1.sort(new OldUnsignedByteBufferComparator());

        final List<ByteBuffer> copy2 = new ArrayList<>(list);
        copy2.sort(new UnsignedByteBufferComparator());

        assertThat(copy1).isEqualTo(copy2);
    }
}
