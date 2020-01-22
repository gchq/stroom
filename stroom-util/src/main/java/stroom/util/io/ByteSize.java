package stroom.util.io;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import stroom.util.shared.ModelStringUtil;

import java.util.Objects;

public class ByteSize {
    private final String valueAsStr;
    private final long bytes;

    public static final ByteSize ZERO = new ByteSize(0L);

    private ByteSize(final String valueAsStr) {
        Objects.requireNonNull(valueAsStr);
        Long bytes = ModelStringUtil.parseIECByteSizeString(valueAsStr);

        if (bytes == null) {
            throw new IllegalArgumentException("Unable to parse [" + valueAsStr + "] to a ByteSize.");
        }
        this.valueAsStr = valueAsStr;
        this.bytes = bytes;
    }

    private ByteSize(final long bytes) {
        if (bytes < 0) {
            throw new IllegalArgumentException("bytes [" + bytes + "] cannot be less than zero");
        }

        valueAsStr = ModelStringUtil.formatIECByteSizeString(bytes, true);
        if (valueAsStr.isEmpty()) {
            throw new RuntimeException("Something went wrong, valueAsStr should not be empty.");
        }
        this.bytes = bytes;
    }

    @JsonCreator
    public static ByteSize parse(final String value) {
        return new ByteSize(value);
    }

    public static ByteSize ofBytes(final long bytes) {
        return new ByteSize(bytes);
    }

    public long getBytes() {
        return bytes;
    }

    @JsonValue
    public String getValueAsStr() {
        return valueAsStr;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ByteSize byteSize = (ByteSize) o;
        return bytes == byteSize.bytes;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bytes);
    }
}
