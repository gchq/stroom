/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.util.logging;

import stroom.util.NullSafeExtra;
import stroom.util.concurrent.DurationAdder;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;

import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.helpers.MessageFormatter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
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
    public static String message(final String format, final Object... args) {
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
        final double secs = NullSafeExtra.duration(duration).isZero()
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

    public static <T> String toPaddedMultiLine(final String padding,
                                               final String multiLineMessage) {
        if (multiLineMessage == null || multiLineMessage.isBlank()) {
            return "";
        } else {
            return multiLineMessage.lines()
                    .map(line -> Objects.requireNonNullElse(padding, "") + line)
                    .collect(Collectors.joining("\n"));
        }
    }

    /**
     * Returns the simple class name with the message, useful as some exception messages
     * only make sense if you know the class name, e.g. {@link NullPointerException} and
     * some {@link java.io.IOException}s.
     */
    public static String exceptionMessage(final Throwable t) {
        if (t == null) {
            return null;
        } else if (t.getMessage() == null) {
            return t.getClass().getSimpleName();
        } else {
            return t.getClass().getSimpleName() + " '" + t.getMessage() + "'";
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

                    final BigDecimal pct = BigDecimal.valueOf(valNum / totalNum * 100)
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
                } catch (final Exception e) {
                    LOGGER.error("Error stripping class name from {}", obj, e);
                    return str;
                }
            }
            return str;
        }
    }

    /**
     * @return The simple class name followed by value.toString().
     * If value is null, returns null.
     */
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

    /**
     * @return A string of the form '1 apple' or `2 apples' depending on the count.
     * User {@link LogUtil#namedCount(String, String, String, int)} for plural names
     * that don't end with an 's'.
     */
    public static String namedCount(final String name, final int count) {
        final StringBuilder sb = new StringBuilder()
                .append(count)
                .append(" ")
                .append(name);
        if (count != 1) {
            sb.append("s");
        }
        return sb.toString();
    }

    /**
     * @return A string of the form '1 embassy' or `2 embassies' depending on the count
     */
    public static String namedCount(final String baseName,
                                    final String singularSuffix,
                                    final String pluralSuffix,
                                    final int count) {
        final StringBuilder sb = new StringBuilder()
                .append(count)
                .append(" ")
                .append(baseName);
        if (count == 1) {
            sb.append(singularSuffix);
        } else {
            sb.append(pluralSuffix);
        }
        return sb.toString();
    }

    /**
     * @return The path as an absolute and normalised path or null if path is null
     */
    public static String path(final Path path) {
        return NullSafe.toString(
                path,
                Path::toAbsolutePath,
                Path::normalize);
    }

    /**
     * Log the current thread's stack trace to the logConsumer, prefixed with message and a new line.
     */
    public static void logStackTrace(final String message,
                                     final Consumer<String> logConsumer) {
        if (logConsumer != null) {
            try {
                final Writer writer = new StringWriter();
                final PrintWriter pw = new PrintWriter(writer);
                new Exception("Dumping Stack Trace").printStackTrace(pw);
                logConsumer.accept(
                        Objects.requireNonNullElse(message, "Dumping stack trace") + "\n" + writer);
            } catch (final Exception e) {
                logConsumer.accept(
                        "Error dumping stack trace: " + e.getMessage());
            }
        }
    }

    /**
     * Return the value supplied by supplier. Any exceptions will be swallowed and logged to debug only.
     * Useful for getting values for logging that may throw.
     *
     * @return The supplied value or an empty optional if an exception is thrown and swallowed.
     */
    public static <T> Optional<T> swallowExceptions(final Supplier<T> supplier) {
        try {
            return Optional.ofNullable(supplier.get());
        } catch (final Exception e) {
            LOGGER.debug("Error swallowed", e);
            return Optional.empty();
        }
    }

    /**
     * Return the value supplied by supplier. Any exceptions will be swallowed and logged to debug only.
     * Useful for getting values for logging that may throw.
     *
     * @return The supplied value or an empty optional if an exception is thrown and swallowed.
     */
    public static OptionalLong swallowExceptions(final LongSupplier supplier) {
        try {
            return OptionalLong.of(supplier.getAsLong());
        } catch (final Exception e) {
            LOGGER.debug("Error swallowed", e);
            return OptionalLong.empty();
        }
    }

    /**
     * Return the value supplied by supplier. Any exceptions will be swallowed and logged to debug only.
     * Useful for getting values for logging that may throw.
     *
     * @return The supplied value or an empty optional if an exception is thrown and swallowed.
     */
    public static OptionalInt swallowExceptions(final IntSupplier supplier) {
        try {
            return OptionalInt.of(supplier.getAsInt());
        } catch (final Exception e) {
            LOGGER.debug("Error swallowed", e);
            return OptionalInt.empty();
        }
    }

    /**
     * If DEBUG logging is enabled, create, start and return a new {@link DurationTimer}
     * instance, else just return null.
     * This avoids unnecessary object creation if DEBUG is not enabled.
     */
    public static DurationTimer startTimerIfDebugEnabled(final Logger logger) {
        return logger != null && logger.isDebugEnabled()
                ? DurationTimer.start()
                : null;
    }

    /**
     * If DEBUG logging is enabled, create, start and return a new {@link DurationTimer}
     * instance, else just return null.
     * This avoids unnecessary object creation if TRACE is not enabled.
     */
    public static DurationTimer startTimerIfTraceEnabled(final Logger logger) {
        return logger != null && logger.isTraceEnabled()
                ? DurationTimer.start()
                : null;
    }

    public static String getSimpleClassName(final Object obj) {
        return NullSafe.get(obj, Object::getClass, Class::getSimpleName);
    }

    public static String getClassName(final Object obj) {
        return NullSafe.get(obj, Object::getClass, Class::getName);
    }
}
