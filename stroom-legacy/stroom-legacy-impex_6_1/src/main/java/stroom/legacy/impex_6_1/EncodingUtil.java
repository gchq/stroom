package stroom.legacy.impex_6_1;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@Deprecated
public final class EncodingUtil {
    private static final Charset CHARSET = StandardCharsets.UTF_8;

    private EncodingUtil() {
        // Utility class.
    }

    public static byte[] asBytes(final String string) {
        if (string == null) {
            return null;
        }
        return string.getBytes(CHARSET);
    }

    public static String asString(final byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        return new String(bytes, CHARSET);
    }
}
