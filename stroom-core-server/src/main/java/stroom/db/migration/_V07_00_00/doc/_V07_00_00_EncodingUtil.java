package stroom.db.migration._V07_00_00.doc;

import java.nio.charset.Charset;

public final class _V07_00_00_EncodingUtil {
    private static final Charset CHARSET = Charset.forName("UTF-8");

    private _V07_00_00_EncodingUtil() {
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
