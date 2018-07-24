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

import org.apache.hadoop.hbase.util.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.xml.bind.DatatypeConverter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class ByteBufferUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(ByteBufferUtils.class);
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(ByteBufferUtils.class);

    public static String byteBufferToString(final ByteBuffer byteBuffer) {
        return ByteArrayUtils.byteArrayToString(Bytes.getBytes(byteBuffer));
    }

    public static String byteBufferToHex(final ByteBuffer byteBuffer) {
        if (byteBuffer == null) {
            return "null";
        }
        return ByteArrayUtils.byteArrayToHex(Bytes.getBytes(byteBuffer));
    }

    public static String byteBufferToHexAll(final ByteBuffer byteBuffer) {
        final StringBuilder sb = new StringBuilder();
        if (byteBuffer != null) {
            int endOffsetEx = byteBuffer.limit();
            for (int i = 0; i < endOffsetEx; i++) {
                final byte[] oneByteArr = new byte[1];
                if (i == byteBuffer.position()) {
                    sb.append(">");
                }
                oneByteArr[0] = byteBuffer.get(i);
                sb.append(DatatypeConverter.printHexBinary(oneByteArr));
                if (i == byteBuffer.limit()) {
                    sb.append("<");
                }
                sb.append(" ");
            }
        }
        return sb.toString().replaceAll(" $", "");
    }

    public static String byteBufferInfo(final ByteBuffer byteBuffer) {
        if (byteBuffer == null) {
            return "null";
        }

        final String value = byteBufferToHexAll(byteBuffer);
        return LambdaLogger.buildMessage("Cap: {}, pos: {}, lim: {}, rem: {}, val [{}], asStr [{}]",
                byteBuffer.capacity(),
                byteBuffer.position(),
                byteBuffer.limit(),
                byteBuffer.remaining(),
                value,
                StandardCharsets.UTF_8.decode(byteBuffer.duplicate()));
    }

    public static String byteBufferToAllForms(final ByteBuffer byteBuffer) {
        return ByteArrayUtils.byteArrayToAllForms(Bytes.getBytes(byteBuffer));
    }

    public static int compare(final ByteBuffer left, final ByteBuffer right) {
        int cmpResult = org.apache.hadoop.hbase.util.ByteBufferUtils.compareTo(
                left, left.position(), left.remaining(),
                right, right.position(), right.remaining());

        LAMBDA_LOGGER.trace(() -> LambdaLogger.buildMessage("compare({}, {}) returned {}",
                ByteBufferUtils.byteBufferInfo(left),
                ByteBufferUtils.byteBufferInfo(right),
                cmpResult));
        return cmpResult;

    }

    /**
     * Compare two {@link ByteBuffer} objects as if they are longs
     * @param left A {@link ByteBuffer} representing a long
     * @param right A {@link ByteBuffer} representing a long
     * @return The result of the comparison, 0 if identical, <0 if left < right,
     * >0 if left < right
     */
    public static int compareAsLong(final ByteBuffer left, final ByteBuffer right) {
        return compareAs(left, left.position(), right, left.position(), Long.BYTES);
    }

    /**
     * Compare two {@link ByteBuffer} objects as if they are longs
     * @param left A {@link ByteBuffer} representing a long
     * @param leftPos The absolute position of the long in the the left {@link ByteBuffer}
     * @param right A {@link ByteBuffer} representing a long
     * @param rightPos The absolute position of the long in the the right {@link ByteBuffer}
     * @return The result of the comparison, 0 if identical, <0 if left < right,
     * >0 if left < right
     */
    public static int compareAsLong(final ByteBuffer left, final int leftPos,
                                   final ByteBuffer right, final int rightPos) {
        return compareAs(left, leftPos, right, rightPos, Long.BYTES);
    }

    /**
     * Compare two {@link ByteBuffer} objects as if they are int
     * @param left A {@link ByteBuffer} representing a int
     * @param right A {@link ByteBuffer} representing a int
     * @return The result of the comparison, 0 if identical, <0 if left < right,
     * >0 if left < right
     */
    public static int compareAsInt(final ByteBuffer left, final ByteBuffer right) {
        return compareAs(left, left.position(), right, right.position(), Integer.BYTES);
    }

    /**
     * Compare two {@link ByteBuffer} objects as if they are int
     * @param left A {@link ByteBuffer} representing a int
     * @param right A {@link ByteBuffer} representing a int
     * @return The result of the comparison, 0 if identical, <0 if left < right,
     * >0 if left < right
     */
    public static int compareAsInt(final ByteBuffer left, final int leftPos,
                                   final ByteBuffer right, final int rightPos) {
        return compareAs(left, leftPos, right, rightPos, Integer.BYTES);
    }

    public static boolean containsPrefix(final ByteBuffer buffer, final ByteBuffer prefixBuffer) {
        boolean result = true;
        if (buffer.remaining() < prefixBuffer.remaining()) {
            result = false;
        } else {
            for (int i = 0; i < prefixBuffer.remaining(); i++) {
                if (prefixBuffer.get(i) != buffer.get(i)) {
                    result = false;
                    break;
                }
            }
        }
        return result;
    }

    public static void copy(final ByteBuffer sourceBuffer, final ByteBuffer destBuffer) {
        destBuffer.put(sourceBuffer);
        destBuffer.flip();
        sourceBuffer.rewind();
    }

    /**
     * Credit for this code goes to Dima
     * (see https://stackoverflow.com/questions/34166809/faster-comparison-of-longs-in-byte-format)
     */
    private static int compareAs(final ByteBuffer left, int leftPos,
                                 final ByteBuffer right, int rightPos,
                                 int length) {


        int cmp = 0;
        for(int i = 0; i < length && cmp == 0; i++) {
            int iLeft = i + leftPos;
            int iRight = i + rightPos;
            cmp = (i == 0 || (left.get(iLeft) >= 0 == right.get(iRight) >= 0))
                    ? left.get(iLeft) - right.get(iRight)
                    : right.get(iRight) - left.get(iLeft);
        }
//        final int cmp2 = cmp;
//        LAMBDA_LOGGER.info(() -> LambdaLogger.buildMessage("Comparing {}, {}, {}, {} - {}",
//                byteBufferInfo(left), leftPos,
//                byteBufferInfo(right), rightPos,
//                cmp2));
        return cmp;
    }
}
