package stroom.query.language;

public class QuotedStringUtil {
    public static String unescape(final char[] chars, final int start, final int end, final char escapeChar) {
        // Break the string into quoted text blocks.
        final char[] out = new char[start - end + 1];
        boolean escape = false;
        int index = 0;
        for (int i = start + 1; i < end; i++) {
            final char c = chars[i];
            if (escape) {
                escape = false;
                out[index++] = c;
            } else {
                if (c == escapeChar) {
                    escape = true;
                } else {
                    out[index++] = c;
                }
            }
        }
        return new String(out, 0, index);
    }
}
