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
