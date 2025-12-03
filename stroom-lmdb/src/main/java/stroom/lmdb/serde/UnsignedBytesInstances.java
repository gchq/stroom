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

package stroom.lmdb.serde;

import stroom.bytebuffer.ByteBufferUtils;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Each instance provides methods for working with unsigned longs that are written
 * to len bytes. The maximum numeric value of the unsigned value will be governed
 * by len, e.g for len == 1, the range of values are 0-255.
 */
public enum UnsignedBytesInstances implements UnsignedBytes {
    ZERO(0), //   max                         0
    ONE(1), //    max                       255
    TWO(2), //    max                    65,535
    THREE(3), //  max                16,777,215
    FOUR(4), //   max             4,294,967,295
    FIVE(5), //   max         1,099,511,627,775
    SIX(6), //    max       281,474,976,710,655
    SEVEN(7), //  max    72,057,594,037,927,935
    EIGHT(8); //  max 9,223,372,036,854,775,807

    private final int len;
    private final long maxVal;
    private final UnsignedLongSerde serde;

    private static final byte[] ZERO_BYTES = new byte[0];
    private static final UnsignedBytes[] INSTANCES = Arrays
            .stream(values())
            .sorted(Comparator.comparingInt(UnsignedBytes::length))
            .toArray(UnsignedBytes[]::new);

    public static void allPositive(final Consumer<UnsignedBytes> consumer) {
        for (int i = 1; i <= 8; i++) {
            consumer.accept(INSTANCES[i]);
        }
    }

    UnsignedBytesInstances(final int len) {
        if (len > 8) {
            throw new IllegalArgumentException(
                    "You cannot use more than 8 bytes to store a value as only long values are supported.");
        }
        if (len < 0) {
            throw new IllegalArgumentException("You cannot use less than 0 bytes to store a value.");
        }
        this.len = len;
        maxVal = computeMaxVal(len);
        serde = new UnsignedLongSerde(len, this);
    }

    public static UnsignedBytes ofLength(final int len) {
        if (len < 0) {
            throw new IllegalArgumentException("Negative length not allowed");
        }
        if (len > 8) {
            throw new IllegalArgumentException("Lengths > 8 not allowed");
        }
        return INSTANCES[len];
    }

    public static UnsignedBytes forValue(final long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Negative values are not allowed");
        } else if (value == 0) {
            return ZERO;
        }

        for (int i = 1; i < INSTANCES.length; i++) {
            final UnsignedBytes unsignedBytes = INSTANCES[i];
            if (unsignedBytes.maxValue() >= value) {
                return unsignedBytes;
            }
        }

        throw new IllegalArgumentException("Unable to find appropriate unsigned bytes size");
    }

    @Override
    public byte[] toBytes(final long val) {
        if (len == 0 && val == 0) {
            return ZERO_BYTES;
        } else if (len < 1) {
            throw new IllegalArgumentException("You cannot use less than 1 byte to store a value.");
        } else if (val == 0) {
            return new byte[len];
        } else {
            final byte[] bytes = new byte[len];
            put(bytes, 0, val);
            return bytes;
        }
    }

    @Override
    public void put(final byte[] arr, final int off, final long val) {
        validateValue(val);

        for (int i = 0; i < len; i++) {
            final int shift = (len - i - 1) * 8;
            arr[off + i] = (byte) (val >> shift);
        }
    }

    @Override
    public void put(final ByteBuffer destByteBuffer, final long val) {
        validateValue(val);

        for (int i = 0; i < len; i++) {
            final int shift = (len - i - 1) * 8;
            destByteBuffer.put((byte) (val >> shift));
        }
    }

    @Override
    public void put(final ByteBuffer destByteBuffer, final int index, final long val) {
        validateValue(val);
        int idx = index;
        for (int i = 0; i < len; i++) {
            final int shift = (len - i - 1) * 8;
            destByteBuffer.put(idx++, (byte) (val >> shift));
        }

    }

    @Override
    public long get(final byte[] bytes, final int off) {
        long val = 0;
        for (int i = 0; i < len; i++) {
            final int shift = (len - i - 1) * 8;
            val = val | ((long) bytes[off + i] & 0xff) << shift;
        }
        return val;
    }

    @Override
    public long get(final ByteBuffer byteBuffer, final int index) {
        long val = 0;

        for (int i = 0; i < len; i++) {
            final int shift = (len - i - 1) * 8;
            val = val | ((long) byteBuffer.get(index + i) & 0xff) << shift;
        }
        return val;
    }

    @Override
    public long get(final ByteBuffer byteBuffer) {
        long val = 0;

        for (int i = 0; i < len; i++) {
            final int shift = (len - i - 1) * 8;
            val = val | ((long) byteBuffer.get() & 0xff) << shift;
        }
        return val;
    }

    @Override
    public long getMaxVal() {
        return maxVal;
    }

    @Override
    public void increment(final ByteBuffer byteBuffer) {
        increment(byteBuffer, byteBuffer.position());
    }

    @Override
    public void increment(final ByteBuffer byteBuffer, final int index) {
        final int cap = byteBuffer.capacity();

        if (cap >= len) {
            // Work from right to left
            for (int i = index + len - 1; i >= index; i--) {
                final byte b = byteBuffer.get(i);
                if (b == (byte) 0xFF) {
                    if (i == index) {
                        // Every byte is FF so we can't increment anymore
                        throw new IllegalArgumentException(
                                "Can't increment without overflowing. Maximum value is "
                                + ModelStringUtil.formatCsv(maxVal));
                    }
                    // Byte rolls around to zero and we need to carry over to the next one
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

    @Override
    public void decrement(final ByteBuffer byteBuffer) {
        decrement(byteBuffer, byteBuffer.position());
    }

    @Override
    public void decrement(final ByteBuffer byteBuffer, final int index) {
        final int cap = byteBuffer.capacity();

        if (cap >= len) {
            // Work from right to left
            for (int i = index + len - 1; i >= index; i--) {
                final byte b = byteBuffer.get(i);
                if (b == (byte) 0x00) {
                    if (i == index) {
                        // Every byte is 00 so we can't decrement anymore
                        throw new IllegalArgumentException("Can't decrement without overflowing");
                    }
                    // Byte rolls drops back to FF and we need to carry over to the next one
                    byteBuffer.put(i, (byte) 0xFF);
                } else {
                    // Not going to overflow this byte so just decrement it then break out
                    // as the rest are unchanged
                    byteBuffer.put(i, (byte) (b - 1));
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

    @Override
    public int length() {
        return len;
    }

    @Override
    public long maxValue() {
        return maxVal;
    }

    private long computeMaxVal(final int len) {
        if (len >= 8) {
            return Long.MAX_VALUE;
        }

        return (1L << (8 * len)) - 1;
    }

    private void validateValue(final long val) {
        if (val < 0) {
            throw new IllegalArgumentException("Negative values are not permitted.");
        }
        if (val > maxVal) {
            throw new IllegalArgumentException(
                    LogUtil.message(
                            "Value {} exceeds max value of {} that can be stored in {} bytes(s)",
                            val, maxVal, len));
        }
    }

    public UnsignedLongSerde getSerde() {
        return serde;
    }

    @Override
    public int compare(final ByteBuffer buffer1,
                       final int index1,
                       final ByteBuffer buffer2,
                       final int index2) {

        Objects.requireNonNull(buffer1);
        Objects.requireNonNull(buffer2);
        if (buffer1.remaining() != len) {
            throw new IllegalArgumentException(LogUtil.message("buffer1 has remaining {}, expecting {}",
                    buffer1.remaining(), len));
        }
        if (buffer2.remaining() != len) {
            throw new IllegalArgumentException(LogUtil.message("buffer2 has remaining {}, expecting {}",
                    buffer2.remaining(), len));
        }
        return Long.compare(
                get(buffer1, index1),
                get(buffer2, index2));
    }

    @Override
    public int compare(final ByteBuffer buffer1, final ByteBuffer buffer2) {
        return compare(buffer1, buffer1.position(), buffer2, buffer2.position());
    }
}
