/*
 * Copyright 2018 Crown Copyright
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
 *
 */

package stroom.refdata.offheapstore;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;


public class TestByteBufferUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestByteBufferUtils.class);

    @Test
    public void testIntCompare() {

        ByteBuffer buf1 = ByteBuffer.allocate(Integer.BYTES);
        ByteBuffer buf2 = ByteBuffer.allocate(Integer.BYTES);

        doIntCompareTest(0, 0, buf1, buf2);
        doIntCompareTest(0, 1, buf1, buf2);
        doIntCompareTest(-1, 0, buf1, buf2);
        doIntCompareTest(-1, 1, buf1, buf2);
        doIntCompareTest(-1000, 1000, buf1, buf2);
        doIntCompareTest(Integer.MAX_VALUE, Integer.MAX_VALUE, buf1, buf2);
        doIntCompareTest(Integer.MIN_VALUE, Integer.MIN_VALUE, buf1, buf2);
        doIntCompareTest(Integer.MIN_VALUE, Integer.MAX_VALUE, buf1, buf2);
        doIntCompareTest(Integer.MAX_VALUE, Integer.MIN_VALUE, buf1, buf2);

        // now just run the test with a load of random values
        Random random = new Random();
        for (int i = 0; i < 10000; i++) {
            int val1 = random.nextInt();
            int val2 = random.nextInt();
            doIntCompareTest(val1, val2, buf1, buf2);
        }
    }

    @Test
    public void testLongCompare() {

        ByteBuffer buf1 = ByteBuffer.allocate(Long.BYTES);
        ByteBuffer buf2 = ByteBuffer.allocate(Long.BYTES);
        doLongCompareTest(0L, 0L, buf1, buf2);
        doLongCompareTest(0L, 1L, buf1, buf2);
        doLongCompareTest(-1L, 0L, buf1, buf2);
        doLongCompareTest(-1L, 1L, buf1, buf2);
        doLongCompareTest(-1000L, 1000L, buf1, buf2);
        doLongCompareTest(Long.MAX_VALUE, Long.MAX_VALUE, buf1, buf2);
        doLongCompareTest(Long.MIN_VALUE, Long.MIN_VALUE, buf1, buf2);
        doLongCompareTest(Long.MIN_VALUE, Long.MAX_VALUE, buf1, buf2);
        doLongCompareTest(Long.MAX_VALUE, Long.MIN_VALUE, buf1, buf2);

        // now just run the test with a load of random values
        Random random = new Random();
        for (int i = 0; i < 10000; i++) {
            long val1 = random.nextLong();
            long val2 = random.nextLong();
            doLongCompareTest(val1, val2, buf1, buf2);
        }
    }

    @Test
    public void testContainsPrefix_match() {

        ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[] {0, 1, 2, 3, 4, 5});
        ByteBuffer prefixByteBuffer = ByteBuffer.wrap(new byte[] {0, 1, 2, 3, 4});

        boolean result = ByteBufferUtils.containsPrefix(byteBuffer, prefixByteBuffer);

        assertThat(result).isTrue();
    }

    @Test
    public void testContainsPrefix_match2() {

        ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[] {0, 1, 2, 3, 4, 5});
        ByteBuffer prefixByteBuffer = ByteBuffer.wrap(new byte[] {0});

        boolean result = ByteBufferUtils.containsPrefix(byteBuffer, prefixByteBuffer);

        assertThat(result).isTrue();
    }

    @Test
    public void testContainsPrefix_bufferTooShort() {

        ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[] {0, 1, 2});
        ByteBuffer prefixByteBuffer = ByteBuffer.wrap(new byte[] {0, 1, 2, 3, 4});

        boolean result = ByteBufferUtils.containsPrefix(byteBuffer, prefixByteBuffer);

        assertThat(result).isFalse();
    }

    @Test
    public void testContainsPrefix_noMatch() {

        ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[] {0, 1, 2, 3, 4, 5});
        ByteBuffer prefixByteBuffer = ByteBuffer.wrap(new byte[] {1, 2, 3, 4});

        boolean result = ByteBufferUtils.containsPrefix(byteBuffer, prefixByteBuffer);

        assertThat(result).isFalse();
    }

    private void doLongCompareTest(long val1, long val2, ByteBuffer buf1, ByteBuffer buf2) {
        buf1.clear();
        buf1.putLong(val1);
        buf1.flip();
        buf2.clear();
        buf2.putLong(val2);
        buf2.flip();

        int cmpLong = Long.compare(val1, val2);
        int cmpBuf = ByteBufferUtils.compareAsLong(buf1, buf2);
        LOGGER.trace("Comparing {} [{}] to {} [{}], {} {}",
                val1, ByteBufferUtils.byteBufferToHex(buf1),
                val2, ByteBufferUtils.byteBufferToHex(buf2),
                cmpLong, cmpBuf);

        // ensure comparison of the long value is the same (pos, neg or zero) as our func
        if (cmpLong == cmpBuf ||
                cmpLong < 0 && cmpBuf < 0 ||
                cmpLong > 0 && cmpBuf > 0) {
            // comparison is the same
        } else {
            Assertions.fail("Mismatch on %s [%s] to %s [%s]",
                    val1, ByteBufferUtils.byteBufferToHex(buf1), val2, ByteBufferUtils.byteBufferToHex(buf2));
        }
    }

    private void doIntCompareTest(int val1, int val2, ByteBuffer buf1, ByteBuffer buf2) {
        buf1.clear();
        buf1.putInt(val1);
        buf1.flip();
        buf2.clear();
        buf2.putInt(val2);
        buf2.flip();

        int cmpLong = Integer.compare(val1, val2);
        int cmpBuf = ByteBufferUtils.compareAsInt(buf1, buf2);
        LOGGER.trace("Comparing {} [{}] to {} [{}], {} {}",
                val1, ByteBufferUtils.byteBufferToHex(buf1),
                val2, ByteBufferUtils.byteBufferToHex(buf2),
                cmpLong, cmpBuf);

        // ensure comparison of the int value is the same (pos, neg or zero) as our func
        if (cmpLong == cmpBuf ||
                cmpLong < 0 && cmpBuf < 0 ||
                cmpLong > 0 && cmpBuf > 0) {
            // comparison is the same
        } else {
            Assertions.fail("Mismatch on %s [%s] to %s [%s]",
                    val1, ByteBufferUtils.byteBufferToHex(buf1), val2, ByteBufferUtils.byteBufferToHex(buf2));
        }
    }

    @Test
    public void testHashCode() {

        ByteBuffer byteBuffer1 = ByteBuffer.wrap(new byte[]{0, 0, 1, 2, 3, 4, 5, 0, 0});
        byteBuffer1.position(2);
        byteBuffer1.limit(7);
        LOGGER.info(ByteBufferUtils.byteBufferInfo(byteBuffer1));

        ByteBuffer byteBuffer2 = ByteBuffer.wrap(new byte[]{0, 1, 2, 3, 4, 5, 0, 0});
        byteBuffer2.position(1);
        byteBuffer2.limit(6);
        LOGGER.info(ByteBufferUtils.byteBufferInfo(byteBuffer2));

        int hash1 = ByteBufferUtils.hashCode(byteBuffer1);
        int hash2 = ByteBufferUtils.hashCode(byteBuffer2);

        assertThat(hash1).isEqualTo(hash2);
    }
}