package stroom.docstore;

import java.nio.charset.Charset;

public final class EncodingUtil {
    private static final Charset CHARSET = Charset.forName("UTF-8");

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
