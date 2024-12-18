package stroom.query.language.token;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

public class QuotedStringUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(QuotedStringUtil.class);

    private QuotedStringUtil() {
    }

    /**
     * Remove the outer quotes (first and last char in the array) and unescape all
     * escaped characters.
     *
     * @param start Inclusive array index
     * @param end   Inclusive array index
     */
    public static String unescape(final char[] chars, final int start, final int end, final char escapeChar) {
        // Break the string into quoted text blocks.
        final char[] out = new char[end - start + 1];
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
        final String output = new String(out, 0, index);

        LOGGER.trace(() -> {
            final String input = new String(chars, start, end - start + 1);
            return LogUtil.message("input [{}], output: [{}]", input, output);
        });

        return output;
    }
}
