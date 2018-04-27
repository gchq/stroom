package stroom.refdata.saxevents;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class StringValue extends AbstractOffHeapInternPoolValue {

    static final short TYPE_ID = 0;

    private final String value;

    private StringValue(final String value) {
        this.value = value;
    }

    public static StringValue of(String value) {
        return new StringValue(value);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final StringValue that = (StringValue) o;
        return Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public byte[] getValueBytes() {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public short getTypeId() {
        return TYPE_ID;
    }

    static StringValue fromByteBuffer(final ByteBuffer byteBuffer) {
        return new StringValue(StandardCharsets.UTF_8.decode(byteBuffer).toString());
    }

    public static ByteBuffer putContent(final ByteBuffer byteBuffer, final int valueHashCode, final int uniqueId) {
        return byteBuffer
                .putInt(valueHashCode)
                .putInt(uniqueId);
    }

    @Override
    public String toString() {
        return "StringValue{" +
                "value='" + value + '\'' +
                '}';
    }
}
