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

import stroom.pipeline.refdata.store.offheapstore.serdes.UnsignedLongSerde;
import stroom.pipeline.refdata.util.ByteBufferUtils;
import stroom.util.logging.LogUtil;

import java.nio.ByteBuffer;

/**
 * Each instance provides methods for working with unsigned longs that are written
 * to len bytes. The maximum size of the unsigned value will be governed by len, e.g
 * for len == 1, the range of values are 0-255.
 */
public enum UnsignedBytesInstances implements UnsignedBytes {
    ONE(1),
    TWO(2),
    THREE(3),
    FOUR(4),
    FIVE(5),
    SIX(6),
    SEVEN(7),
    EIGHT(8);

    private final int len;
    private final long maxVal;
    private final UnsignedLongSerde serde;

    private final byte[] ZERO_BYTES = new byte[0];
    private static final UnsignedBytes[] INSTANCES = new UnsignedBytesInstances[9];

    static {
        for (final UnsignedBytesInstances instance : values()) {
            INSTANCES[instance.len] = instance;
        }
    }

    UnsignedBytesInstances(final int len) {
        if (len > 8) {
            throw new IllegalArgumentException(
                    "You cannot use more than 8 bytes to store a value as only long values are supported.");
        }
        if (len < 1) {
            throw new IllegalArgumentException("You cannot use less than 1 byte to store a value.");
        }
        this.len = len;
        maxVal = computeMaxVal(len);
        serde = new UnsignedLongSerde(len, this);
    }

    public static UnsignedBytes of(final int len) {
        if (len == 0) {
            throw new IllegalArgumentException("Length of zero not allowed");
        }
        if (len > 8) {
            throw new IllegalArgumentException("Lengths > 8 not allowed");
        }
        return INSTANCES[len];
    }

    @Override
    public byte[] toBytes(final long val) {
        if (len == 0 && val == 0) {
            return ZERO_BYTES;
        } else if (len < 1) {
            throw new IllegalArgumentException("You cannot use less than 1 byte to store a value.");
        }

        final byte[] bytes = new byte[len];
        put(bytes, 0, val);
        return bytes;
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
                byte b = byteBuffer.get(i);
                if (b == (byte) 0xFF) {
                    if (i == index) {
                        // Every byte is FF so we can't increment anymore
                        throw new IllegalArgumentException("Can't increment without overflowing");
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
                byte b = byteBuffer.get(i);
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

}
