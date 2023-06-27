package stroom.util.logging;

import stroom.util.NullSafe;
import stroom.util.concurrent.DurationAdder;
import stroom.util.shared.ModelStringUtil;

import com.google.common.base.Strings;
import org.slf4j.helpers.MessageFormatter;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class LogUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LogUtil.class);

    // These are 3 byte unicode chars so a bit of a waste of bytes
//    private static final char BOX_HORIZONTAL_LINE = '━';
//    private static final char BOX_VERTICAL_LINE = '┃';
//    private static final char BOX_BTM_LEFT = '┗';
//    private static final char BOX_TOP_LEFT = '┏';
//    private static final char BOX_BTM_RIGHT = '┛';
//    private static final char BOX_TOP_RIGHT = '┓';

    // These are in code page 437 (DOS latin US) + 805 (DOS latin 1)
//    private static final char BOX_HORIZONTAL_LINE = '═';
//    private static final char BOX_VERTICAL_LINE = '║';
//    private static final char BOX_BTM_LEFT = '╚';
//    private static final char BOX_TOP_LEFT = '╔';
//    private static final char BOX_BTM_RIGHT = '╝';
//    private static final char BOX_TOP_RIGHT = '╗';

    // These are in code page 437 (DOS latin US) + 805 (DOS latin 1)
    private static final char BOX_HORIZONTAL_LINE = '─';
    private static final char BOX_VERTICAL_LINE = '│';
    private static final char BOX_BTM_LEFT = '└';
    private static final char BOX_TOP_LEFT = '┌';
    private static final char BOX_BTM_RIGHT = '┘';
    private static final char BOX_TOP_RIGHT = '┐';

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
        final String str = Strings.repeat(String.valueOf(BOX_HORIZONTAL_LINE), 3)
                + " "
                + text
                + " ";
        return Strings.padEnd(str, 100, BOX_HORIZONTAL_LINE);
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
            final String contentText = message(format, args);

            final int maxLineLen = contentText.lines()
                    .mapToInt(String::length)
                    .max()
                    .orElse(0);

            final String horizontalLine = Strings.repeat(String.valueOf(BOX_HORIZONTAL_LINE), maxLineLen + 4);
            final String horizontalSeparator = Strings.repeat(String.valueOf(BOX_HORIZONTAL_LINE), maxLineLen);
            final StringBuilder stringBuilder = new StringBuilder();
            if (addNewLine) {
                stringBuilder.append("\n");
            }
            // Top line
            stringBuilder
                    .append(BOX_TOP_LEFT)
                    .append(horizontalLine)
                    .append(BOX_TOP_RIGHT)
                    .append("\n");

            // Content
            contentText.lines()
                    .map(line -> {
                        // Add a pattern replacement to insert a horizontal rule like markdown.
                        if (line.equals("---")) {
                            return horizontalSeparator;
                        } else {
                            // Pad lines out to all the same length
                            final String variablePadding = Strings.repeat(" ", maxLineLen - line.length());
                            return line + variablePadding;
                        }
                    })
                    .forEach(linePlusPadding ->
                            stringBuilder
                                    .append(BOX_VERTICAL_LINE)
                                    .append("  ")
                                    .append(linePlusPadding)
                                    .append("  ")
                                    .append(BOX_VERTICAL_LINE)
                                    .append("\n"));

            // Bottom line
            stringBuilder
                    .append(BOX_BTM_LEFT)
                    .append(horizontalLine)
                    .append(BOX_BTM_RIGHT);

            return stringBuilder.toString();
        }
    }

    public static String getDurationMessage(final String work, final Duration duration) {
        return LogUtil.message("Completed [{}] in {}",
                work,
                duration);
    }

    public static String getDurationMessage(final String work,
                                            final Duration duration,
                                            final long iterations) {
        final double secs = NullSafe.duration(duration).isZero()
                ? 0
                : duration.toMillis() / (double) 1_000;
        final String rate = secs == 0
                ? "NaN"
                : ModelStringUtil.formatCsv(iterations / secs);
        return LogUtil.message("Completed [{}] in {} ({}/sec)",
                work,
                duration,
                rate);
    }

    /**
     * @return epochMs as an instant or null if epochMs is null.
     */
    public static Instant instant(final Long epochMs) {
        if (epochMs == null) {
            return null;
        } else {
            return Instant.ofEpochMilli(epochMs);
        }
    }

    /**
     * Output the value with its percentage of {@code total}, e.g. {@code 1000 (10%)}.
     * Supports numbers and {@link Duration} and {@link DurationAdder}.
     */
    public static <T> String withPercentage(final T value, final T total) {
        return withPercentage(value, value, total);
    }

    public static <T> String toPaddedMultiLine(final String padding,
                                               final Collection<T> items) {
        return toPaddedMultiLine(padding, items, Objects::toString);
    }

    public static <T> String toPaddedMultiLine(final String padding,
                                               final Collection<T> items,
                                               final Function<T, String> itemMapper) {
        if (items == null || items.isEmpty()) {
            return "";
        } else {
            return items.stream()
                    .filter(Objects::nonNull)
                    .map(itemMapper)
                    .filter(str1 -> !NullSafe.isBlankString(str1))
                    .map(str -> Objects.requireNonNullElse(padding, "") + str)
                    .collect(Collectors.joining("\n"));
        }
    }

    /**
     * Returns the simple class name with the message
     */
    public static String exceptionMessage(final Throwable t) {
        if (t == null) {
            return null;
        } else {
            return t.getClass() + " " + t.getMessage();
        }
    }

    private static <T> String withPercentage(final Object originalValue,
                                             final T value,
                                             final T total) {
        if (value == null || total == null) {
            return null;
        } else {
            if (value instanceof Duration) {
                return withPercentage(value,
                        ((Duration) value).toMillis(),
                        ((Duration) total).toMillis());
            } else if (value instanceof final DurationAdder durationAdder) {
                return withPercentage(value,
                        durationAdder.toMillis(),
                        ((DurationAdder) total).toMillis());
            } else if (value instanceof final DurationTimer durationTimer) {
                return withPercentage(value,
                        durationTimer.get().toMillis(),
                        ((DurationTimer) total).get().toMillis());
            } else if (value instanceof Number) {
                final double valNum = ((Number) value).doubleValue();
                final double totalNum = ((Number) total).doubleValue();
                if (totalNum == 0) {
                    return originalValue + " (undefined%)";
                } else {
//                    final int pct = (int) (valNum / totalNum * 100);

                    BigDecimal pct = BigDecimal.valueOf(valNum / totalNum * 100)
                            .stripTrailingZeros()
                            .round(new MathContext(3, RoundingMode.HALF_UP));
                    return originalValue + " (" + pct.toPlainString() + "%)";
                }
            } else {
                throw new IllegalArgumentException("Type "
                        + value.getClass().getSimpleName()
                        + " not supported");
            }
        }
    }

    /**
     * toString() methods often are of the form 'MyClass{xxx}', so this method
     * converts that to just 'xxx'
     */
    public static <T> String toStringWithoutClassName(final T obj) {
        if (obj == null) {
            return null;
        } else {
            String str = obj.toString();
            if (!str.isBlank()) {
                try {
                    final String className = obj.getClass().getSimpleName();
                    if (str.startsWith(obj.getClass().getSimpleName())) {
                        str = str.replace(className, "");
                    }
                    if (str.startsWith("{") && str.endsWith("}")) {
                        str = str.substring(1, str.length() - 1);
                    }
                } catch (Exception e) {
                    LOGGER.error("Error stripping class name from {}", obj, e);
                    return str;
                }
            }
            return str;
        }
    }

    public static String typedValue(final Object value) {
        if (value == null) {
            return null;
        } else {
            return value.getClass().getSimpleName() + " " + value;
        }
    }

    public static String truncate(final String str, final int maxLength) {
        if (str == null || str.length() < maxLength) {
            return str;
        } else {
            return str.substring(0, maxLength) + "...";
        }
    }

    public static String truncateUnless(final String str,
                                        final int maxLength,
                                        final boolean isTruncationSkipped) {
        if (str == null || str.length() < maxLength || isTruncationSkipped) {
            return str;
        } else {
            return str.substring(0, maxLength) + "...";
        }
    }
}
