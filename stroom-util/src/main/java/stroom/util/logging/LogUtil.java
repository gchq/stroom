package stroom.util.logging;

import com.google.common.base.Strings;
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

    /**
     * Constructs a formatted message string using a format string that takes
     * the same placeholders as SLF4J, e.g.
     * "Function called with name {} and value {}"
     * This constructed message is placed inside a separator line padded out to 100 chars, e.g.
     * === Function called with name foo and value bar ====================================================
     * @param format SLF4J style format string
     * @param args   The values for any placeholders in the message format
     * @return A formatted message in a separator line
     */
    public static String inSeparatorLine(final String format, final Object... args) {
        final String text = message(format, args);
        return Strings.padEnd("=== " + text + " ", 100, '=');
    }

    /**
     * Constructs a formatted message string using a format string that takes
     * the same placeholders as SLF4J, e.g.
     * "Function called with name {} and value {}"
     * This constructed message is placed inside a box after a line break
     * @param format SLF4J style format string
     * @param args   The values for any placeholders in the message format
     * @return A formatted message in a box on a new line
     */
    public static String inBox(final String format, final Object... args) {
        final String text = message(format, args);

        final String line = Strings.repeat("-", text.length() + 6);
        final String boxFormat = """
                
                {}
                |  {}  |
                {}""";
        return message(boxFormat, line, text, line);
    }
}
