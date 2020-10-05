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

package stroom.pipeline.refdata.store.offheapstore;

import stroom.pipeline.refdata.util.ByteBufferUtils;
import stroom.util.logging.LogUtil;

import java.nio.ByteBuffer;

public class UnsignedBytes {
    private static final byte[] ZERO_BYTES = new byte[0];
    private static final long[] MAX_VALUES = new long[9];

    static {
        // Pre-compute the max values to speed up validation
        for (int i = 1; i <= 8; i++) {
            MAX_VALUES[i] = computeMaxVal(i);
        }
    }

    public static byte[] toBytes(final int len, final long val) {
        if (len == 0 && val == 0) {
            return ZERO_BYTES;
        } else if (len < 1) {
            throw new IllegalArgumentException("You cannot use less than 1 byte to store a value.");
        }

        final byte[] bytes = new byte[len];
        put(bytes, 0, len, val);
        return bytes;
    }

    /**
     * Puts val as unsigned bytes into arr using a length of len bytes
     */
    public static void put(final byte[] arr, final int off, final int len, final long val) {
        validateValue(len, val);

        for (int i = 0; i < len; i++) {
            final int shift = (len - i - 1) * 8;
            arr[off + i] = (byte) (val >> shift);
        }
    }

    /**
     * Puts val as unsigned bytes into destByteBuffer using a length of len bytes
     */
    public static void put(final ByteBuffer destByteBuffer, final int len, final long val) {
        validateValue(len, val);

        for (int i = 0; i < len; i++) {
            final int shift = (len - i - 1) * 8;
            destByteBuffer.put((byte) (val >> shift));
        }
    }

    private static void validateValue(final int len, final long val) {
        if (val < 0) {
            throw new IllegalArgumentException("Negative values are not permitted.");
        }
        if (len > 8) {
            throw new IllegalArgumentException(
                    "You cannot use more than 8 bytes to store a value as only long values are supported.");
        }
        if (len < 1) {
            throw new IllegalArgumentException("You cannot use less than 1 byte to store a value.");
        }
        if (val > MAX_VALUES[len]) {
            throw new IllegalArgumentException(
                    LogUtil.message(
                            "Value {} exceeds max value of {} that can be stored in {} bytes(s)",
                            val, MAX_VALUES[len], len));
        }
    }

    public static long get(final byte[] bytes, final int off, final int len) {
        long val = 0;
        for (int i = 0; i < len; i++) {
            final int shift = (len - i - 1) * 8;
            val = val | ((long) bytes[off + i] & 0xff) << shift;
        }
        return val;
    }

    public static long get(final ByteBuffer byteBuffer) {
        long val = 0;
        int pos = byteBuffer.position();
        int len = byteBuffer.remaining();

        for (int i = 0; i < len; i++) {
            final int shift = (len - i - 1) * 8;
            val = val | ((long) byteBuffer.get(pos + i) & 0xff) << shift;
        }
        return val;
    }

    private static long computeMaxVal(final int len) {
        if (len >= 8) {
            return Long.MAX_VALUE;
        }

        return (1L << (8 * len)) - 1;
    }

    public static long getMaxVal(final int len) {
        if (len >= 1 && len <= 8) {
            return MAX_VALUES[len];
        } else {
            throw new IllegalArgumentException("len must be >= 1 and <= 8");
        }
    }

    public static int requiredLength(final long val) {
        if (val < 0) {
            throw new IllegalArgumentException("You must supply a positive value");
        } else if (val == 0) {
            return 0;
        }

        final long pos = 64 - Long.numberOfLeadingZeros(val) - 1;
        final double l = pos / 8D;
        final int len = (int) Math.ceil(l);

        return len;
    }

    /**
     * Treating the passed byteBuffer as an unsigned long in <= 8 bytes, increments the
     * value by one.  The byteBuffer's position, limit, marks are unchanged.
     * @param len The number of bytes in the unsigned value
     */
    public static void increment(final ByteBuffer byteBuffer, final int len) {
        final int pos = byteBuffer.position();
        final int cap = byteBuffer.capacity();

        if (cap >= len) {
            // Work from right to left
            for (int i = pos + len - 1; i >= pos; i--) {
                byte b = byteBuffer.get(i);
                if (b == (byte) 0xFF) {
                    if (i == pos) {
                        // Every byte is FF so we can't increment anymore
                        throw new IllegalArgumentException("Can't increment without overflowing");
                    }
                    // Byte rolls around to zero and we need to increment the next one
                    byteBuffer.put(i, (byte) 0x00);
                } else {
                    // Not going to overflow this byte so just increment it then break out
                    // as the rest are unchanged
                    byteBuffer.put(i, (byte) (b + 1));
                    break;
                }
            }
        } else {
            throw new IllegalArgumentException(LogUtil.message(
                    "Capacity {} should be >= {}, buffer: {}",
                    cap,
                    len,
                    ByteBufferUtils.byteBufferInfo(byteBuffer)));
        }
    }
}
