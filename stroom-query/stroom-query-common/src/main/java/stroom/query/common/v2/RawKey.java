package stroom.query.common.v2;

import java.util.Arrays;

public class RawKey {

    private final byte[] bytes;
    private final int hashCode;

    public RawKey(final byte[] bytes) {
        this.bytes = bytes;
        this.hashCode = Arrays.hashCode(bytes);
    }

    public byte[] getBytes() {
        return bytes;
    }

    @SuppressWarnings("checkstyle:needbraces")
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RawKey rawKey = (RawKey) o;
        return Arrays.equals(bytes, rawKey.bytes);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}
