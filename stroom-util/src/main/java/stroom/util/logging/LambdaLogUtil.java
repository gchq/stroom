package stroom.util.logging;

import java.util.function.Supplier;

public final class LambdaLogUtil {
    private LambdaLogUtil() {
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
    public static Supplier<String> message(final String format, final Object... args) {
        return () -> LogUtil.message(format, args);
    }
}
