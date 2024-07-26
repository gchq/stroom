package stroom.util.shared.string;

import java.util.Objects;

/**
 * Useful as a case-insensitive cache key that retains the case of the
 * original string at the cost of wrapping it in another object.
 */
public class CIKey {

    public static final CIKey NULL = new CIKey(null);
    public static final CIKey EMPTY = new CIKey("");

    private final String key;
    private final String lowerKey;

    private CIKey(final String key) {
        if (key == null) {
            this.key = null;
            this.lowerKey = null;
        } else {
            this.key = key;
            this.lowerKey = key.toLowerCase();
        }
    }

    public static CIKey of(final String value) {
        if (value == null) {
            return NULL;
        } else if (value.isEmpty()) {
            return EMPTY;
        } else {
            return new CIKey(value);
        }
    }

    public String get() {
        return key;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final CIKey that = (CIKey) object;
        return Objects.equals(lowerKey, that.lowerKey);
    }

    @Override
    public int hashCode() {
        // String lazily caches its hashcode so no need for us to
        return lowerKey != null
                ? lowerKey.hashCode()
                : 0;
    }

    @Override
    public String toString() {
        return key;
    }
}
