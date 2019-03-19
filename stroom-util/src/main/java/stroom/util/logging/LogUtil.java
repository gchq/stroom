package stroom.util.logging;

import org.slf4j.helpers.MessageFormatter;

public final class LogUtil {
    private LogUtil() {
        // Utility class.
    }

    /**
     * Constructs a formatted message string using a format string that takes
     * the same placeholders as SLF4J, e.g.
     * "Function called with name {} and value {}"
     *
     * @param format SLF4J style format string
     * @param args   The values for any placeholders in the message format
     * @return A formatted message
     */
    public static String message(String format, Object... args) {
        return MessageFormatter.arrayFormat(format, args).getMessage();
    }
}
