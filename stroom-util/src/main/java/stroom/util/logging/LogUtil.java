package stroom.util.logging;

import stroom.util.shared.ModelStringUtil;

import org.slf4j.Logger;
import org.slf4j.helpers.MessageFormatter;

import java.util.function.Supplier;

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

    public static void logDuration(final Logger logger, final String message, final Runnable runnable) {
        long startTime = 0;
        if (logger.isDebugEnabled()) {
            startTime = System.currentTimeMillis();
        }

        try {
            runnable.run();
        } finally {
            if (startTime > 0) {
                final long duration = System.currentTimeMillis() - startTime;
                logger.debug(message + " in " + ModelStringUtil.formatDurationString(duration));
            }
        }
    }

    public static <R> R logDurationResult(final Logger logger, final String message, final Supplier<R> supplier) {
        long startTime = 0;
        if (logger.isDebugEnabled()) {
            startTime = System.currentTimeMillis();
        }

        try {
            return supplier.get();
        } finally {
            if (startTime > 0) {
                final long duration = System.currentTimeMillis() - startTime;
                logger.debug(message + " in " + ModelStringUtil.formatDurationString(duration));
            }
        }
    }
}
