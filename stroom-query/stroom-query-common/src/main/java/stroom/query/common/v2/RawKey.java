package stroom.query.common.v2;

import com.esotericsoftware.kryo.io.Input;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class RawKey {
    private final byte[] bytes;
    private final int hashCode;

    public RawKey(final byte[] bytes) {
        this.bytes = bytes;
        this.hashCode = Arrays.hashCode(bytes);
    }

    public static RawKey decode(final String base64EncodedBytes) {
        return new RawKey(Base64.getDecoder().decode(base64EncodedBytes));
    }

    static Set<RawKey> convertSet(final Set<String> openGroups) {
        Set<RawKey> rawKeys = Collections.emptySet();
        if (openGroups != null) {
            rawKeys = new HashSet<>();
            for (final String encodedGroup : openGroups) {
                rawKeys.add(decode(encodedGroup));
            }
        }
        return rawKeys;
    }

    public byte[] getBytes() {
        return bytes;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final RawKey rawKey = (RawKey) o;
        return Arrays.equals(bytes, rawKey.bytes);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    Key toKey() {
        try (final Input input = new Input(new ByteArrayInputStream(bytes))) {
            return Key.read(input);
        }
    }

    @Override
    public String toString() {
        return toKey().toString();
    }

    public String encode() {
        return Base64.getEncoder().encodeToString(bytes);
    }
}
