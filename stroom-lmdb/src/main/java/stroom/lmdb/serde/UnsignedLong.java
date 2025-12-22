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

import java.util.Objects;

/**
 * Represents an unsigned long that fits in length bytes. If a length of 8
 * is used then it can only represent numbers up to {@link Long#MAX_VALUE}.
 * See {@link UnsignedBytesInstances} for max values for different byte lengths.
 */
public class UnsignedLong {

    private final long value;
    private final UnsignedBytes unsignedBytes;

    public UnsignedLong(final long value, final UnsignedBytes unsignedBytes) {
        this.unsignedBytes = Objects.requireNonNull(unsignedBytes);
        checkLowerBound(value);
        checkUpperBound(value);
        this.value = value;
    }

    public UnsignedLong(final long value, final int length) {
        this(value, UnsignedBytesInstances.ofLength(length));
    }

    public static UnsignedLong of(final long value) {
        return new UnsignedLong(value, -1);
    }

    public static UnsignedLong of(final long value, final int length) {
        return new UnsignedLong(value, length);
    }

    public static UnsignedLong of(final long value, final UnsignedBytes unsignedBytes) {
        return new UnsignedLong(value, unsignedBytes);
    }

    public UnsignedLong increment() {
        final long newVal = value + 1;
        checkUpperBound(newVal);
        return new UnsignedLong(newVal, unsignedBytes);
    }

    public UnsignedLong increment(final long delta) {
        final long newVal = value + delta;
        checkUpperBound(newVal);
        return new UnsignedLong(newVal, unsignedBytes);
    }

    public UnsignedLong decrement() {
        final long newVal = value - 1;
        checkLowerBound(newVal);
        return new UnsignedLong(newVal, unsignedBytes);
    }

    public UnsignedLong decrement(final long delta) {
        final long newVal = value - delta;
        checkLowerBound(newVal);
        return new UnsignedLong(newVal, unsignedBytes);
    }

    private void checkLowerBound(final long value) {
        if (value < 0) {
            throw new IllegalArgumentException("value must be >= zero");
        }
    }

    private void checkUpperBound(final long value) {
        if (value > unsignedBytes.getMaxVal()) {
            throw new IllegalArgumentException("value must be <= " + unsignedBytes.getMaxVal());
        }
    }

    public long getValue() {
        return value;
    }

    public int getLength() {
        return unsignedBytes.length();
    }

    public UnsignedBytes getUnsignedBytes() {
        return this.unsignedBytes;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final UnsignedLong that = (UnsignedLong) o;
        return value == that.value && unsignedBytes.equals(that.unsignedBytes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, unsignedBytes);
    }

    @Override
    public String toString() {
        return value + " (byte len: " + unsignedBytes.length() + ")";
    }
}
