package stroom.util.shared;

/**
 * String utilities for client side or shared code
 */
public class StringUtil {

    private StringUtil() {
    }

    /**
     * @return obj.toString() or an empty string if obj is null.
     */
    public static String toString(final Object obj) {
        if (obj == null) {
            return "";
        }
        return obj.toString();
    }

    /**
     * @return params as is, unless it is empty or blank, in which case return null.
     */
    public static String blankAsNull(final String params) {
        if (params != null && (params.isEmpty() || isBlank(params))) {
            return null;
        } else {
            return params;
        }
    }

    /**
     * GWT doesn't support {@link String#isBlank()}
     *
     * @return True if str is null, empty or contains only whitespace.
     */
    public static boolean isBlank(final String str) {
        if (str == null) {
            return true;
        } else if (str.isEmpty()) {
            return true;
        } else {
            return str.chars()
                    .allMatch(Character::isWhitespace);
        }
    }

    /**
     * Converts user input, that has been double-quoted to include whitespace,
     * into the un-quoted value. Any double quotes inside the outermost double
     * quotes will be escaped with a single backslash. Any Unquoted whitespace
     * or whitespace outside the outer double quotes will be trimmed.
     * See the tests for examples.
     */
    public static String removeWhitespaceQuoting(final String userText) {
        final String storedText;
        if (userText == null || userText.isEmpty()) {
            storedText = userText;
        } else {
            final String trimmedStr = userText.trim();
            if (trimmedStr.startsWith("\"")
                    && trimmedStr.endsWith("\"")) {

                final int openQuoteIdx = trimmedStr.indexOf("\"");
                final int endQuoteIdx = trimmedStr.lastIndexOf("\"");
                if (openQuoteIdx == endQuoteIdx) {
                    // '"'
                    storedText = trimmedStr;
                } else {
                    // Everything inside the db quotes, un-escaping any dbl quote used
                    storedText = trimmedStr.substring(openQuoteIdx + 1, endQuoteIdx)
                            .replace("\\\"", "\"");
                }
            } else {
                storedText = trimmedStr.replace("\\\"", "\"");
            }
        }
        return storedText;
    }

    /**
     * Converts a system stored value into one for display in the UI.
     * If the value has leading or trailing whitespace it will be quoted and any
     * double quotes escaped.
     * See the tests for examples.
     */
    public static String addWhitespaceQuoting(final String storedText) {
        String userText;
        if (storedText == null || storedText.isEmpty()) {
            userText = storedText;
        } else {
            final String escapedText = storedText.replace("\"", "\\\"");
            if (storedText.startsWith(" ")
                    || storedText.startsWith("\t")
                    || storedText.endsWith(" ")
                    || storedText.endsWith("\t")) {
                // leading/trailing whitespace so dbl quote the whole thing
                // ' he said "hello" ' => '" he said \"hello\" "'
                userText = "\""
                        + escapedText
                        + "\"";
            } else {
                userText = escapedText;
            }
        }
        return userText;
    }

    public static String pluralSuffix(final int count) {
        return count > 1
                ? "s"
                : "";
    }
}
