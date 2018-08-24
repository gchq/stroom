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

package stroom.refdata.store.offheapstore;

import stroom.util.logging.LambdaLogger;

import java.nio.ByteBuffer;

public class UnsignedBytes {
    private static final byte[] ZERO_BYTES = new byte[0];

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

    public static void put(final byte[] bytes, final int off, final int len, final long val) {
        validateValue(len, val);

        for (int i = 0; i < len; i++) {
            final int shift = (len - i - 1) * 8;
            bytes[off + i] = (byte) (val >> shift);
        }
    }

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
        final long max = maxValue(len);
        if (val > max) {
            throw new IllegalArgumentException(
                    LambdaLogger.buildMessage(
                            "Value {} exceeds max value of {} that can be stored in {} bytes(s)",
                            val, max, len));
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

    public static long maxValue(final int len) {
        if (len >= 8) {
            return Long.MAX_VALUE;
        }

        return (1L << (8 * len)) - 1;
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
}
