package stroom.util.logging;

import com.google.common.base.Strings;
import org.slf4j.helpers.MessageFormatter;

import java.util.stream.Collectors;

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
     *
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
     * This constructed message is placed inside a box after a line break.
     *
     * @param format SLF4J style format string
     * @param args   The values for any placeholders in the message format
     * @return A formatted message in a box on a new line
     */
    public static String inBoxOnNewLine(final String format, final Object... args) {
        return inBox(format, true, args);
    }

    /**
     * Constructs a formatted message string using a format string that takes
     * the same placeholders as SLF4J, e.g.
     * "Function called with name {} and value {}"
     * This constructed message is placed inside a box.
     *
     * @param format SLF4J style format string
     * @param args   The values for any placeholders in the message format
     * @return A formatted message in a box.
     */
    public static String inBox(final String format, final Object... args) {
        return inBox(format, false, args);
    }

    private static String inBox(final String format,
                                final boolean addNewLine,
                                final Object... args) {
        if (format == null || format.isBlank()) {
            return "";
        } else {
            final String text = message(format, args);

            final int maxLineLen = text.lines()
                    .mapToInt(String::length)
                    .max()
                    .orElse(0);

            final String topBottomBorder = Strings.repeat("-", maxLineLen + 6);
            final String boxText = text.lines()
                    .map(line -> {
                        final String padding = Strings.repeat(" ", maxLineLen - line.length());
                        return "|  " + line + padding + "  |";
                    })
                    .collect(Collectors.joining("\n"));

            final String boxFormat = """
                {}{}
                {}
                {}""";
            return message(boxFormat, (addNewLine ? "\n" : ""), topBottomBorder, boxText, topBottomBorder);
        }
    }
}
