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

package stroom.bytebuffer;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.xml.bind.DatatypeConverter;
import net.openhft.hashing.LongHashFunction;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class ByteBufferUtils {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ByteBufferUtils.class);
    private static final byte MAX_BYTE_UNSIGNED = (byte) -1;

    private ByteBufferUtils() {
        // static util methods only
    }

    public static String byteBufferToString(final ByteBuffer byteBuffer) {
        return ByteArrayUtils.byteArrayToString(toBytes(byteBuffer));
    }

    public static String byteBufferToHex(final ByteBuffer byteBuffer) {
        if (byteBuffer == null) {
            return "null";
        }
        return ByteArrayUtils.byteArrayToHex(toBytes(byteBuffer));
    }

    public static String byteBufferToHexAll(final ByteBuffer byteBuffer) {
        final StringBuilder sb = new StringBuilder();
        if (byteBuffer != null) {
            final int endOffsetEx = byteBuffer.limit();
            for (int i = 0; i < endOffsetEx; i++) {
                final byte[] oneByteArr = new byte[1];
                if (i == byteBuffer.position()) {
                    sb.append(">");
                }
                oneByteArr[0] = byteBuffer.get(i);
                sb.append(DatatypeConverter.printHexBinary(oneByteArr));
                if (i == byteBuffer.limit() - 1) {
                    sb.append("<");
                    if (i < byteBuffer.capacity() - 1) {
                        sb.append(" ....");
                    }
                }
                sb.append(" ");
            }
//            sb
//                    .append(" pos: ").append(byteBuffer.position())
//                    .append(" lim: ").append(byteBuffer.limit())
//                    .append(" cap: ").append(byteBuffer.capacity());
        }
        return sb.toString();
    }

    /**
     * Increments a Long value at absolute position idx. Does not modify the position
     * of the buffer.
     */
    public static void incrementLong(final ByteBuffer byteBuffer,
                                     final int idx) {
        final long val = byteBuffer.getLong(idx);
        if (val == Long.MAX_VALUE) {
            throw new ArithmeticException("Can't increment beyond max value of " + Long.MAX_VALUE);
        }
        byteBuffer.putLong(idx, val + 1L);
    }

    /**
     * Increments a Integer value at absolute position idx. Does not modify the position
     * of the buffer.
     */
    public static void incrementInt(final ByteBuffer byteBuffer,
                                    final int idx) {
        final int val = byteBuffer.getInt(idx);
        if (val == Integer.MAX_VALUE) {
            throw new ArithmeticException("Can't increment beyond max value of " + Integer.MAX_VALUE);
        }
        byteBuffer.putInt(idx, val + 1);
    }

    /**
     * Increments a Short value at absolute position idx. Does not modify the position
     * of the buffer.
     */
    public static void incrementShort(final ByteBuffer byteBuffer,
                                      final int idx) {
        final short val = byteBuffer.getShort(idx);
        if (val == Short.MAX_VALUE) {
            throw new ArithmeticException("Can't increment beyond max value of " + Short.MAX_VALUE);
        }
        byteBuffer.putShort(idx, (short) (val + 1));
    }

    /**
     * Increments the numeric value with length len at index. Does not modify the position
     * of the buffer.
     * <p>
     * NOTE this appears to be no quicker than doing putInt(getint(...) + 1) type thing.
     * </p>
     */
    public static void increment(final ByteBuffer byteBuffer,
                                 final int idx,
                                 final int len) {
        // Work from right to left
        for (int i = idx + len - 1; i >= idx; i--) {
            final byte b = byteBuffer.get(i);
            if (b == (byte) 0xFF) {
                // Byte rolls around to zero and we need to carry over to the next one
                byteBuffer.put(i, (byte) 0x00);
            } else if (i == idx && b == 0x7F) {
                // 7F is the highest value for the first byte due to the sign bit
                throw new IllegalArgumentException("Can't increment without overflowing");
            } else {
                // Not going to overflow this byte so just increment it then break out
                // as the rest are unchanged
                byteBuffer.put(i, (byte) (b + 1));
                break;
            }
        }
    }

    public static String byteBufferInfoAsInt(final ByteBuffer byteBuffer) {
        if (byteBuffer == null) {
            return "null";
        }

        final String value = byteBufferToHexAll(byteBuffer);
        String asInt;
        try {
            asInt = String.valueOf(byteBuffer.duplicate().getInt());
        } catch (final Exception e) {
            LOGGER.debug("Unable to convert to long", e);
            asInt = "CANT_CONVERT";
        }
        return LogUtil.message("Cap: {}, pos: {}, lim: {}, rem: {}, val [{}], asInt [{}]",
                byteBuffer.capacity(),
                byteBuffer.position(),
                byteBuffer.limit(),
                byteBuffer.remaining(),
                value,
                asInt);
    }

    public static String byteBufferInfoAsLong(final ByteBuffer byteBuffer) {
        if (byteBuffer == null) {
            return "null";
        }

        final String value = byteBufferToHexAll(byteBuffer);
        String asLong;
        try {
            asLong = String.valueOf(byteBuffer.duplicate().getLong());
        } catch (final Exception e) {
            LOGGER.debug("Unable to convert to long", e);
            asLong = "CANT_CONVERT";
        }
        return LogUtil.message("Cap: {}, pos: {}, lim: {}, rem: {}, val [{}], asLong [{}]",
                byteBuffer.capacity(),
                byteBuffer.position(),
                byteBuffer.limit(),
                byteBuffer.remaining(),
                value,
                asLong);
    }

    public static String byteBufferInfo(final ByteBuffer byteBuffer) {
        if (byteBuffer == null) {
            return "null";
        }

        final String value = byteBufferToHexAll(byteBuffer);
        return LogUtil.message("Cap: {}, pos: {}, lim: {}, rem: {}, val [{}], asStr [{}]",
                byteBuffer.capacity(),
                byteBuffer.position(),
                byteBuffer.limit(),
                byteBuffer.remaining(),
                value,
                StandardCharsets.UTF_8.decode(byteBuffer.duplicate()));
    }

//    public static String byteBufferToAllForms(final ByteBuffer byteBuffer) {
//        if (byteBuffer == null) {
//            return "null";
//        }
//        return ByteArrayUtils.byteArrayToAllForms(toBytes(byteBuffer));
//    }
//
//    public static int compare(final ByteBuffer left, final ByteBuffer right) {
//        int cmpResult = stroom.bytebuffer.hbase.ByteBufferUtils.compareTo(
//                left, left.position(), left.remaining(),
//                right, right.position(), right.remaining());
//
//        LOGGER.trace(() -> LogUtil.message("compare({}, {}) returned {}",
//                ByteBufferUtils.byteBufferInfo(left),
//                ByteBufferUtils.byteBufferInfo(right),
//                cmpResult));
//        return cmpResult;
//
//    }

    public static int compareTo(final ByteBuffer buf1,
                                final int o1,
                                final int l1,
                                final ByteBuffer buf2,
                                final int o2,
                                final int l2) {
        return stroom.bytebuffer.hbase.ByteBufferUtils.compareTo(buf1, o1, l1, buf2, o2, l2);
    }

    /**
     * Compare two {@link ByteBuffer} objects as if they are longs
     *
     * @param left  A {@link ByteBuffer} representing a long
     * @param right A {@link ByteBuffer} representing a long
     * @return The result of the comparison, 0 if identical, <0 if left < right,
     * >0 if left < right
     */
    public static int compareAsLong(final ByteBuffer left, final ByteBuffer right) {
        return compareAs(left, left.position(), right, left.position(), Long.BYTES);
    }

    /**
     * Compare two {@link ByteBuffer} objects as if they are longs
     *
     * @param left     A {@link ByteBuffer} representing a long
     * @param leftPos  The absolute position of the long in the the left {@link ByteBuffer}
     * @param right    A {@link ByteBuffer} representing a long
     * @param rightPos The absolute position of the long in the the right {@link ByteBuffer}
     * @return The result of the comparison, 0 if identical, <0 if left < right,
     * >0 if left < right
     */
    public static int compareAsLong(final ByteBuffer left, final int leftPos,
                                    final ByteBuffer right, final int rightPos) {
        return compareAs(left, leftPos, right, rightPos, Long.BYTES);
    }

    public static int compareAsLong(final long left, final ByteBuffer right) {
        return compareAsLong(left, right, 0);
    }

    public static int compareAsLong(final long left, final ByteBuffer right, final int rightPos) {

        long val = left;
        final byte[] leftBytes = new byte[8];
        for (int i = 7; i >= 0; i--) {
            leftBytes[i] = (byte) (val & 0xFF);
            val >>= 8;
        }

        return compareAsLong(leftBytes, right, rightPos);
    }

    public static int compareAsLong(final byte[] leftBytes, final ByteBuffer right, final int rightPos) {

        int cmp = 0;

        for (int i = 0; i < Long.BYTES && cmp == 0; i++) {
            final int iRight = i + rightPos;
            cmp = (i == 0 || (leftBytes[i] >= 0 == right.get(iRight) >= 0))
                    ? leftBytes[i] - right.get(iRight)
                    : right.get(iRight) - leftBytes[i];
        }
//        final int cmp2 = cmp;
//        LAMBDA_LOGGER.info(() -> LogUtil.message("Comparing {}, {}, {}, {} - {}",
//                byteBufferInfo(left), leftPos,
//                byteBufferInfo(right), rightPos,
//                cmp2));
        return cmp;
    }

    /**
     * Compare two {@link ByteBuffer} objects as if they are int
     *
     * @param left  A {@link ByteBuffer} representing a int
     * @param right A {@link ByteBuffer} representing a int
     * @return The result of the comparison, 0 if identical, <0 if left < right,
     * >0 if left < right
     */
    public static int compareAsInt(final ByteBuffer left, final ByteBuffer right) {
        return compareAs(left, left.position(), right, right.position(), Integer.BYTES);
    }

    /**
     * Compare two {@link ByteBuffer} objects as if they are int
     *
     * @param left  A {@link ByteBuffer} representing a int
     * @param right A {@link ByteBuffer} representing a int
     * @return The result of the comparison, 0 if identical, <0 if left < right,
     * >0 if left < right
     */
    public static int compareAsInt(final ByteBuffer left, final int leftPos,
                                   final ByteBuffer right, final int rightPos) {
        return compareAs(left, leftPos, right, rightPos, Integer.BYTES);
    }

    public static boolean containsPrefix(final ByteBuffer buffer, final ByteBuffer prefixBuffer) {
        final int pos =  prefixBuffer.mismatch(buffer);
        return pos == -1 || pos == prefixBuffer.limit();
    }

    public static void copy(final ByteBuffer sourceBuffer, final ByteBuffer destBuffer) {
        destBuffer.put(sourceBuffer);
        destBuffer.flip();
        sourceBuffer.rewind();
    }

    public static void copy(final ByteBuffer sourceBuffer,
                            final ByteBuffer destBuffer,
                            final int sourceIndex,
                            final int destIndex,
                            final int length) {
        for (int i = 0; i < length; i++) {
            destBuffer.put(destIndex + i, sourceBuffer.get(sourceIndex + i));
        }
    }

    /**
     * Creates a new direct {@link ByteBuffer} from the input {@link ByteBuffer}.
     * The bytes from position() to limit() will be copied into a newly allocated
     * buffer. The new buffer will be flipped to set its position read for get operations
     */
    public static ByteBuffer copyToDirectBuffer(final ByteBuffer input) {
        final ByteBuffer output = ByteBuffer.allocateDirect(input.remaining());
        output.put(input);
        output.flip();
        input.rewind();
        return output;
    }

    /**
     * Creates a new direct {@link ByteBuffer} from the input {@link ByteBuffer}.
     * The bytes from position() to limit() will be copied into a newly allocated
     * buffer. The new buffer will be flipped to set its position read for get operations
     */
    public static ByteBuffer copyToHeapBuffer(final ByteBuffer input) {
        final ByteBuffer output = ByteBuffer.allocate(input.remaining());
        output.put(input);
        output.flip();
        input.rewind();
        return output;
    }

    /**
     * Credit for this code goes to Dima
     * (see https://stackoverflow.com/questions/34166809/faster-comparison-of-longs-in-byte-format)
     */
    private static int compareAs(final ByteBuffer left, final int leftPos,
                                 final ByteBuffer right, final int rightPos,
                                 final int length) {


        int cmp = 0;
        for (int i = 0; i < length && cmp == 0; i++) {
            final int iLeft = i + leftPos;
            final int iRight = i + rightPos;
            cmp = (i == 0 || (left.get(iLeft) >= 0 == right.get(iRight) >= 0))
                    ? left.get(iLeft) - right.get(iRight)
                    : right.get(iRight) - left.get(iLeft);
        }
//        final int cmp2 = cmp;
//        LAMBDA_LOGGER.info(() -> LogUtil.message("Comparing {}, {}, {}, {} - {}",
//                byteBufferInfo(left), leftPos,
//                byteBufferInfo(right), rightPos,
//                cmp2));
        return cmp;
    }


    /**
     * Generate a fast non-crypto 64bit hash of the passed buffer
     */
    public static long xxHash(final ByteBuffer byteBuffer) {
        return LongHashFunction.xx().hashBytes(byteBuffer);
    }

    public static int basicHashCode(final ByteBuffer byteBuffer) {
        int hash = 1;

        final int pos = byteBuffer.position();
        final int limit = byteBuffer.limit();
        for (int i = pos; i < limit; ++i) {
            hash = 31 * hash + byteBuffer.get(i);
        }
        return hash;
    }

    public static byte[] toBytes(final ByteBuffer byteBuffer) {
        final ByteBuffer dupBuffer = byteBuffer.duplicate();
        final byte[] arr = new byte[dupBuffer.remaining()];
        dupBuffer.get(arr);
        return arr;
    }

    /**
     * Get the requested length number of bytes from the specified index.
     */
    public static byte[] toBytes(final ByteBuffer byteBuffer, final int index, final int length) {
        final byte[] arr = new byte[length];
        byteBuffer.get(index, arr, 0, length);
        return arr;
    }

    public static byte[] getBytes(final ByteBuffer byteBuffer) {
        final ByteBuffer dupBuffer = byteBuffer.duplicate();
        final byte[] result = new byte[dupBuffer.remaining()];
        dupBuffer.get(result);
        return result;
    }

    public static String toString(final ByteBuffer byteBuffer) {
        return new String(toBytes(byteBuffer), StandardCharsets.UTF_8);
    }

//    public static void debug(final ByteBuffer byteBuffer) {
//        System.out.println(Arrays.toString(toBytes(byteBuffer.slice())));
//    }
//
//    public static void debugCurrent(final ByteBuffer byteBuffer) {
//        System.out.println(Arrays.toString(toBytes(byteBuffer.slice(0, byteBuffer.position()))));
//    }
//
//    public static void debugCurrent(final UnsafeByteBufferOutput output) {
//        debugCurrent(output.getByteBuffer());
//    }

    /**
     * Add max unsigned byte padding to the supplied buffer.
     *
     * @param byteBuffer The buffer to write to.
     * @param length     The number of bytes to write.
     */
    public static void padMax(final ByteBuffer byteBuffer, final int length) {
        for (int i = 0; i < length; i++) {
            byteBuffer.put(MAX_BYTE_UNSIGNED);
        }
    }

    /**
     * Add max unsigned byte padding to the supplied buffer.
     *
     * @param byteBuffer The buffer to write to.
     * @param offset     The offset to start writing at.
     * @param length     The number of bytes to write.
     */
    public static void padMax(final ByteBuffer byteBuffer, final int offset, final int length) {
        for (int i = offset; i < offset + length; i++) {
            byteBuffer.put(i, MAX_BYTE_UNSIGNED);
        }
    }

    public static void skip(final ByteBuffer byteBuffer, final int len) {
        byteBuffer.position(byteBuffer.position() + len);
    }
}
