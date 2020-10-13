package stroom.pipeline.refdata.store.offheapstore;

import java.util.Objects;

/**
 * Represents an unsigned long that fits in length bytes
 */
public class UnsignedLong {
    private final long value;
    private final int length;

    public UnsignedLong(final long value, final int length) {
        this.value = value;
        this.length = length;
    }

    public static UnsignedLong of(final long value) {
        return new UnsignedLong(value, -1);
    }

    public static UnsignedLong of(final long value, final int length) {
        return new UnsignedLong(value, length);
    }

    public long getValue() {
        return value;
    }

    public int getLength() {
        return length;
    }

    public UnsignedBytes getUnsignedBytes() {
        return UnsignedBytesInstances.of(length);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final UnsignedLong that = (UnsignedLong) o;
        return value == that.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "UnsignedLong{" +
                "value=" + value +
                ", length=" + length +
                '}';
    }
}
