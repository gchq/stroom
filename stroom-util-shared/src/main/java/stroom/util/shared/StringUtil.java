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
}
